package com.developerb.judock;


import com.google.common.base.Preconditions;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.spotify.docker.client.DockerClient.ListContainersParam.allContainers;
import static com.spotify.docker.client.DockerClient.LogsParameter.*;

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


    public ContainerCreation createContainer(ContainerConfig containerConfiguration) throws DockerException, InterruptedException {
        return docker.createContainer(containerConfiguration);
    }

    public JUDock.Container replaceOrCreateContainer(String name) throws DockerException, InterruptedException {
        return replaceOrCreateContainer(ContainerConfig.builder().build(), name);
    }

    public JUDock.Container replaceOrCreateContainer(ContainerConfig containerConfiguration, String name) throws DockerException, InterruptedException {
        for (com.spotify.docker.client.messages.Container container : docker.listContainers(allContainers())) {
            if (container.names().contains("/" + name)) {
                log.info("Removing existing container {}", container.id());
                docker.stopContainer(container.id(), 1);
                docker.removeContainer(container.id(), true);
            }
        }

        final ContainerCreation creation = docker.createContainer(containerConfiguration, name);

        if (creation.getWarnings() != null) {
            for (String warning : creation.getWarnings()) {
                log.warn("Warning occurred while creating container {}: {}", creation.id(), warning);
            }
        }

        return new Container(creation.id());
    }

    @Override
    protected void before() throws Throwable {

    }

    @Override
    protected void after() {
        for (Runnable cleanupTask : cleanupTasks) {
            try {
                log.info(cleanupTask.toString());
                cleanupTask.run();
            }
            catch (RuntimeException ex) {
                log.error("Cleanup task failed {}", cleanupTask, ex);
            }
        }

        if (docker != null) {
            docker.close();
        }
    }



    public interface Predicate {

        boolean isOkay();

    }



    private class StopContainer implements Runnable {

        private final String containerId;

        private StopContainer(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.stopContainer(containerId, 1);
            }
            catch (Exception ex) {
                log.error("Failed to shut down container {}", containerId);
            }
        }

        @Override
        public String toString() {
            return "Shutting down container " + containerId;
        }
    }

    private class RemoveContainer implements Runnable {

        private final String containerId;

        private RemoveContainer(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public void run() {
            try {
                docker.removeContainer(containerId, true);
            }
            catch (Exception ex) {
                log.error("Failed to remove container {}", containerId);
            }
        }

        @Override
        public String toString() {
            return "Removing container " + containerId;
        }
    }



    public Container createTestContainer(String containerId) {
        return new Container(containerId);
    }

    public class Container {

        private final String id;

        private Container(String id) {
            this.id = Preconditions.checkNotNull(id, "ID");
        }

        public Container startContainer() throws DockerException, InterruptedException {
            return startContainer(HostConfig.builder().build());
        }

        public Container startContainer(HostConfig hostConfig) throws DockerException, InterruptedException {
            log.info("Starting {}", id);

            cleanupTasks.add(new StopContainer(id));
            cleanupTasks.add(new RemoveContainer(id));

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
            return inspect().networkSettings().ipAddress();
        }

        public ContainerInfo inspect() throws DockerException, InterruptedException {
            return docker.inspectContainer(id);
        }

        public String id() {
            return id;
        }

        @Override
        public String toString() {
            return "Container[ID: " + id + "]";
        }
    }

}
