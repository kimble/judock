package com.developerb.judock;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration testing with Docker.
 * 
 * @author Kim A. Betti
 */
public class JUDock {

    private final static Logger log = LoggerFactory.getLogger(JUDock.class);

    protected final DockerClient client;

    private final List<Runnable> cleanupTasks = new ArrayList<>();

    public JUDock() {
        this.client = provideDockerClient();
    }


    private DockerClient provideDockerClient() {
        DockerClientConfig.DockerClientConfigBuilder configBuilder = DockerClientConfig.createDefaultConfigBuilder()
            .withUri("http://localhost:2375")
            .withLoggingFilter(false);

        changeClientConfiguration(configBuilder);

        DockerClientConfig config = configBuilder.build();
        return DockerClientBuilder.getInstance(config).build();
    }


    protected CreateContainerCmd createContainerCommand(String image) {
        return client.createContainerCmd(image);
    }

    protected StartContainerCmd startContainerCommand(String containerId) {
        cleanupTasks.add(new StopContainer(containerId));
        cleanupTasks.add(new RemoveContainer(containerId));

        return client.startContainerCmd(containerId);
    }

    /**
     * Override this if you want to change any part of
     * the Docker client configuration.
     *
     * @param config add your configuration to this instance
     */
    protected void changeClientConfiguration(DockerClientConfig.DockerClientConfigBuilder config) { }


    protected int availableTcpPort() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();

        return port;
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

    @After
    public final void closeClient() throws IOException {
        for (Runnable cleanupTask : cleanupTasks) {
            try {
                log.info(cleanupTask.toString());
                cleanupTask.run();
            }
            catch (RuntimeException ex) {
                log.error("Cleanup task failed {}", cleanupTask, ex);
            }
        }

        client.close();
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
