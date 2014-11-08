package com.developerb.judock;


import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import org.joda.time.Duration;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.spotify.docker.client.DockerClient.ListContainersParam.allContainers;
import static com.spotify.docker.client.DockerClient.LogsParameter.*;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Integration testing with Docker.
 *
 * @author Kim A. Betti
 */
public class JUDock extends ExternalResource {

    private final static Logger log = LoggerFactory.getLogger(JUDock.class);

    private final List<Runnable> cleanupTasks = new ArrayList<>();
    private DockerClient docker;


    public JUDock() {
        this(new DefaultDockerClient("http://localhost:2375"));
    }

    public JUDock(DockerClient docker) {
        this.docker = docker;
    }

    public DockerClient client() {
        return docker;
    }

    public ContainerConfig.Builder createContainerConfig(String image) {
        return ContainerConfig.builder()
                .image(image);
    }


    public JUDock.Container replaceOrCreateContainer(ContainerConfig containerConfiguration, String name) throws DockerException, InterruptedException {
        for (com.spotify.docker.client.messages.Container container : docker.listContainers(allContainers())) {
            if (container.names().contains("/" + name)) {
                log.info("Removing existing container {} [id: {}]", name, container.id().substring(0, 8));
                docker.stopContainer(container.id(), 2);
                docker.removeContainer(container.id(), true);
            }
        }

        final ContainerCreation creation = docker.createContainer(containerConfiguration, name);

        if (creation.getWarnings() != null) {
            for (String warning : creation.getWarnings()) {
                log.warn("Warning occurred while creating container {} [id: {}]: {}", name, creation.id().substring(0, 8), warning);
            }
        }

        return new Container(creation.id(), name);
    }

    @Override
    protected void before() throws Throwable {

    }

    @Override
    protected void after() {
        List<Runnable> reversedTaskList = Lists.reverse(cleanupTasks);
        Iterator<Runnable> iterator = reversedTaskList.iterator();

        while (iterator.hasNext()) {
            Runnable cleanupTask = iterator.next();
            iterator.remove();

            try {
                log.info("Beginning: {}", cleanupTask.toString());
                cleanupTask.run();

                log.info("Completed: {}", cleanupTask.toString());
            }
            catch (RuntimeException ex) {
                log.error("Failed: {}", cleanupTask, ex);
            }
        }

        if (docker != null) {
            docker.close();
        }
    }



    public interface Predicate {

        boolean isOkay();

    }


    private class StopAndRemoveContainer implements Runnable {

        private final String name;
        private final String containerId;

        private StopAndRemoveContainer(String name, String containerId) {
            this.name = name;
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.stopContainer(containerId, 2);

                try {
                    log.info("Removing container {} [id: {}]", name, containerId.substring(0, 8));
                    docker.removeContainer(containerId);
                }
                catch (Exception ex) {
                    log.error("Failed to remove container {} [id: {}]", name, containerId.substring(0, 8), ex);
                }
            }
            catch (Exception ex) {
                log.error("Failed to shut down container {} [id: {}], will attempt to kill it!", name, containerId.substring(0, 8), ex);

                try {
                    docker.killContainer(containerId);
                }
                catch (Exception exx) {
                    log.error("Failed to kill container {} [id: {}]", name, containerId.substring(0, 8), exx);
                }
            }
        }

        @Override
        public String toString() {
            return "Stopping and removing container " + name + " [id: " + containerId.substring(0, 8) + "]";
        }

    }

    private class StopContainer implements Runnable {

        private final String name;
        private final String containerId;

        private StopContainer(String name, String containerId) {
            this.name = name;
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.stopContainer(containerId, 2);
            }
            catch (Exception ex) {
                log.error("Failed to shut down container {}", containerId);
            }
        }

        @Override
        public String toString() {
            return "Stopping down container " + name + "[id: " + containerId.substring(0, 8) + "]";
        }
    }

    private class KillContainer implements Runnable {

        private final String name;
        private final String containerId;

        private KillContainer(String name, String containerId) {
            this.name = name;
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.killContainer(containerId);
            }
            catch (Exception ex) {
                log.error("Failed to kill container {}, [id: {}]", name, containerId);
            }
        }

        @Override
        public String toString() {
            return "Killing container " + name + "[id: " + containerId.substring(0, 8) + "]";
        }
    }

    private class RemoveContainer implements Runnable {

        private final String name;
        private final String containerId;

        private RemoveContainer(String name, String containerId) {
            this.name = name;
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.removeContainer(containerId, true);
            }
            catch (Exception ex) {
                log.error("Failed to remove container {} [id: {}]", name, containerId);
            }
        }

        @Override
        public String toString() {
            return "Removing container " + name + "[id: " + containerId.substring(0, 8) + "]";
        }
    }



    public Container createTestContainer(String containerId, String name) {
        return new Container(containerId, name);
    }

    public class Container {

        private final String name;
        private final String id;

        private Container(String id, String name) {
            this.id = Preconditions.checkNotNull(id, "ID");
            this.name = Preconditions.checkNotNull(name, "Name");
        }

        public Container startContainer() throws DockerException, InterruptedException {
            return startContainer(HostConfig.builder().build());
        }

        public Container startContainer(HostConfig hostConfig) throws DockerException, InterruptedException {
            log.info("Starting {} [id: {}]", name, id.substring(0, 8));

            cleanupTasks.add (
                    new StopAndRemoveContainer(name, id)
            );

            docker.startContainer(id, hostConfig);
            return this;
        }

        public void waitFor(Predicate predicate) throws InterruptedException, DockerException {
            waitFor(Duration.standardSeconds(60), predicate);
        }

        public void stopWithin(Duration beforeKilling) throws DockerException, InterruptedException {
            docker.stopContainer(id, beforeKilling.toStandardSeconds().getSeconds());
        }

        public void waitFor(Duration limit, Predicate predicate) throws InterruptedException, DockerException {
            try (LogStream logStream = docker.logs(id, STDERR, STDOUT, TIMESTAMPS)) {
                long startedAt = System.currentTimeMillis();
                long cutoff = startedAt + limit.getMillis();

                log.info("Waiting for: {}", predicate);
                while (System.currentTimeMillis() < cutoff) {
                    if (predicate.isOkay()) {
                        log.info("{} became available after {}ms", id, System.currentTimeMillis() - startedAt);
                        return;
                    }
                    else {
                        Thread.sleep(1000);
                    }
                }

                final String containerLogs = readLogStream(logStream);

                throw new IllegalStateException("Container " + id + " never reached 'running' before timeout, but " + inspect().state()
                            + ".\nFollowing is the latest output from the container:\n" + containerLogs);
            }
        }

        private String readLogStream(LogStream logStream) {
            String containerLogs = "No log available..";

            try {
                containerLogs = logStream.readFully();
            }
            catch (Exception ex) {
                log.warn("Failed to grab log output from container " + id, ex);
            }
            return containerLogs;
        }

        public String getIpAddress() throws DockerException, InterruptedException {
            final String ipAddress = inspect().networkSettings().ipAddress();

            if (isBlank(ipAddress)) {
                throw new IllegalStateException("Unable to determine ip address");
            }
            else {
                return ipAddress;
            }
        }

        public ContainerInfo inspect() throws DockerException, InterruptedException {
            return docker.inspectContainer(id);
        }

        public String id() {
            return id;
        }

        @Override
        public String toString() {
            return "Container[ID: " + id.substring(0, 8) + "]";
        }
    }

}
