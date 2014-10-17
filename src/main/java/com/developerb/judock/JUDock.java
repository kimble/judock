package com.developerb.judock;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Integration testing with Docker.
 *
 * @author Kim A. Betti
 */
public class JUDock implements TestRule {

    private final static Logger log = LoggerFactory.getLogger(JUDock.class);


    private final List<Runnable> cleanupTasks = new ArrayList<>();
    private final DockerClientConfig.DockerClientConfigBuilder configuration;
    private DockerClient client;


    public JUDock() {
        this(DockerClientConfig.createDefaultConfigBuilder()
                .withUri("http://localhost:2375")
                .withLoggingFilter(false));
    }

    public JUDock(DockerClientConfig.DockerClientConfigBuilder configuration) {
        this.configuration = configuration;
    }

    public DockerClient client() {
        return client;
    }

    protected CreateContainerCmd createContainerCommand(String image) {
        return client.createContainerCmd(image);
    }

    protected StartContainerCmd startContainerCommand(String containerId) {
        cleanupTasks.add(new StopContainer(containerId));
        cleanupTasks.add(new RemoveContainer(containerId));

        return client.startContainerCmd(containerId);
    }

    protected Ports tcpPortBindings(String... formatted) {
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
    public Statement apply(final Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                createClient();

                try {
                    base.evaluate();
                }
                finally {
                    runCleanup();
                }
            }

            private void createClient() {
                final DockerClientConfig config = configuration.build();
                client = DockerClientBuilder.getInstance(config).build();
            }

            private void runCleanup() throws IOException {
                for (Runnable cleanupTask : cleanupTasks) {
                    try {
                        log.info(cleanupTask.toString());
                        cleanupTask.run();
                    }
                    catch (RuntimeException ex) {
                        log.error("Cleanup task failed {}", cleanupTask, ex);
                    }
                }

                closeClient();
            }

            private void closeClient() throws IOException {
                client.close();
                client = null;
            }

        };
    }

    public int localPort(String containerId, int exposedPort) {
        InspectContainerResponse response = client.inspectContainerCmd(containerId).exec();

        final Ports ports = response
                .getNetworkSettings()
                .getPorts();

        Map<ExposedPort, Ports.Binding> bindings = ports.getBindings();
        Ports.Binding binding = bindings.get(ExposedPort.tcp(exposedPort));

        if (binding != null) {
            return binding.getHostPort();
        }
        else {
            throw new IllegalStateException("Unable to determine binding for " + exposedPort);
        }
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

}
