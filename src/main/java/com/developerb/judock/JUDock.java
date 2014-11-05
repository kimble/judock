package com.developerb.judock;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Integration testing with Docker.
 *
 * @author Kim A. Betti
 */
public class JUDock extends ExternalResource {

    private final static Logger log = LoggerFactory.getLogger(JUDock.class);


    private final List<Runnable> cleanupTasks = new ArrayList<>();
    private final DockerClientConfig.DockerClientConfigBuilder configuration;
    private DockerClient client;


    public JUDock() {
        this(DockerClientConfig.createDefaultConfigBuilder()
                .withVersion("1.13")
                .withUri("http://localhost:2375")
                .withLoggingFilter(false));
    }

    public JUDock(DockerClientConfig.DockerClientConfigBuilder configuration) {
        this.configuration = configuration;
    }

    public DockerClient client() {
        return client;
    }

    public CreateContainerCmd createContainerCommand(String image) {
        return client.createContainerCmd(image);
    }

    public StartContainerCmd startContainerCommand(String containerId) {
        cleanupTasks.add(new StopContainer(containerId));
        cleanupTasks.add(new RemoveContainer(containerId));

        return client.startContainerCmd(containerId);
    }

    public Ports tcpPortBindings(String... formatted) {
        final Ports portBindings = new Ports();

        for (String format : formatted) {
            String[] split = format.split(":");
            ExposedPort exposedPort = ExposedPort.tcp(Integer.parseInt(split[1]));
            Ports.Binding boundPort = Ports.Binding(Integer.parseInt(split[0]));

            portBindings.bind(exposedPort, boundPort);
        }

        return portBindings;
    }

    @Override
    protected void before() throws Throwable {
        final DockerClientConfig config = configuration.build();
        client = DockerClientBuilder.getInstance(config).build();
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


        try {
            if (client != null) {
                client.close();
            }
        }
        catch (IOException ex) {
            log.warn("Failed to close client");
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
                client.stopContainerCmd(containerId)
                        .withTimeout(1)
                        .exec();
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
                client.removeContainerCmd(containerId)
                        .withForce(true)
                        .exec();
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

        public Container startContainer() {
            log.info("Starting {}", id);
            createStartCommand().exec();
            return this;
        }

        public StartContainerCmd createStartCommand() {
            return startContainerCommand(id);
        }

        public void waitFor(Predicate predicate) throws InterruptedException {
            waitFor(60, TimeUnit.SECONDS, predicate);
        }

        public void pause() {
            log.info("Pausing {}", id);
            client.pauseContainerCmd(id).exec();
        }

        public void unPause() {
            log.info("Un-pausing {}", id);
            client.unpauseContainerCmd(id).exec();
        }

        public String tailLinesOfLog(int lines) throws IOException {
            try (InputStream stream = client.logContainerCmd(id)
                    .withTail(lines)
                    .withStdErr()
                    .withStdOut()
                    .exec()) {

                byte[] bytes = ByteStreams.toByteArray(stream);
                return new String(bytes, Charsets.UTF_8);
            }
        }

        public void waitFor(int duration, TimeUnit unit, Predicate predicate) throws InterruptedException {
            long startedAt = System.currentTimeMillis();
            long cutoff = startedAt + unit.toMillis(duration);

            log.info("Waiting for: {}", predicate);
            while (System.currentTimeMillis() < cutoff) {
                if (predicate.isOkay()) {
                    log.info("{} became available after {}ms", id, System.currentTimeMillis() - startedAt);
                    return;
                }
                else {
                    Thread.sleep(200);
                }
            }

            try {
                String logs = tailLinesOfLog(100);
                throw new IllegalStateException("Container " + id + " never reached 'running' before timeout, but " + inspect().getState()
                        + ". Following is the latest output from the container:\n" + logs);
            }
            catch (IOException e) {
                throw new IllegalStateException("Container " + id + " never reached 'running' before timeout, but " + inspect().getState()
                        + ". Unable to grad output from the container.");
            }
        }

        public String getIpAddress() {
            return inspect().getNetworkSettings().getIpAddress();
        }

        public InspectContainerResponse inspect() {
            return client.inspectContainerCmd(id).exec();
        }

        public String id() {
            return id;
        }

    }

}
