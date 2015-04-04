package com.developerb.judock;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.NetworkSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.spotify.docker.client.DockerClient.LogsParameter.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Kim A. Betti
 */
public class ManagedContainer<C extends ManagedContainer> {

    private final Logger log;

    private final String containerId;
    private final DockerClient docker;

    public ManagedContainer(String containerName, DockerClient docker, String containerId) {
        this.log = LoggerFactory.getLogger("container." + containerName);

        this.containerId = containerId;
        this.docker = docker;
    }

    public void boot(ReadyPredicate ready) throws Exception {
        log.info("Booting container");
        docker.startContainer(containerId, hostConfig(HostConfig.builder()));

        log.info("Waiting for the container to boot");
        try (LogStream logStream = docker.logs(containerId, STDERR, STDOUT, TIMESTAMPS)) {
            ReadyPredicate.Context context = new ReadyPredicate.Context(ipAddress());
            ReadyPredicate.Result result = ReadyPredicate.Result.tryAgain(100, MILLISECONDS, "first attempt");

            while (!result.shouldBeKilled() && !result.wasSuccessful()) {
                try {
                    result = ready.isReady(context);
                }
                catch (Exception ex) {
                    log.error("Predicate should not throw exception", ex);
                }

                log.info("Result: {}", result.toString());
                result.sleep();
            }

            if (result.shouldBeKilled()) {
                String containerLogs = readLogStream(logStream);
                throw new IllegalStateException(String.format("Container %s never reached 'running' before timeout, but %s.\n" +
                        "Following is the latest output from the container:\n%s", containerId, inspect().state(), containerLogs));
            }
        }
    }

    protected HostConfig hostConfig(HostConfig.Builder builder) {
        return builder.build();
    }

    private String readLogStream(LogStream logStream) {
        String containerLogs = "No log available..";

        try {
            containerLogs = logStream.readFully();
        }
        catch (Exception ex) {
            log.warn("Failed to grab log output from container " + containerId, ex);
        }
        return containerLogs;
    }


    public boolean stop() {
        try {
            log.info("Stopping container");
            docker.stopContainer(containerId, 2);
            return true;
        }
        catch (Exception ex) {
            log.error("Failed to stop container, will attempt to kill it!");

            try {
                docker.killContainer(containerId);
                return true;
            }
            catch (Exception exx) {
                log.error("Attempt to kill it failed as well (id: {})", containerId);
                return false;
            }
        }
    }

    public boolean remove() {
        try {
            log.info("Removing container");
            docker.removeContainer(containerId);
            return true;
        }
        catch (Exception ex) {
            log.error("Failed to remove container (id: {})", containerId.substring(0, 8), ex);
            return false;
        }
    }

    public String ipAddress() throws Exception {
        return inspectNetwork().ipAddress();
    }

    public NetworkSettings inspectNetwork() throws Exception {
        return inspect().networkSettings();
    }

    public ContainerInfo inspect() throws Exception {
        return docker.inspectContainer(containerId);
    }

}
