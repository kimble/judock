package com.developerb.judock;

import com.developerb.judock.ReadyPredicate.Result;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.NetworkSettings;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

import static com.spotify.docker.client.DockerClient.LogsParameter.*;
import static java.util.concurrent.TimeUnit.*;

/**
 *
 */
public class ManagedContainer {

    private final Logger log;

    private final String containerName, containerId;
    private final DockerClient docker;

    public ManagedContainer(String containerName, DockerClient docker, String containerId) {
        this.log = LoggerFactory.getLogger("container." + containerName);

        this.containerName = containerName;
        this.containerId = containerId;
        this.docker = docker;
    }

    public String containerName() {
        return containerName;
    }

    public String httpGet(String format) {
        String host = ipAddress();
        String uri = String.format(format, host);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(uri);

            try (CloseableHttpResponse response = httpClient.execute(httpGet, new BasicHttpContext())) {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to get ", ex);
        }
    }

    public void boot(ReadyPredicate ready) throws Exception {
        log.info("Booting container");
        docker.startContainer(containerId, hostConfig(HostConfig.builder()));

        log.info("Waiting for the container to boot");
        try (LogStream logStream = docker.logs(containerId, STDERR, STDOUT, TIMESTAMPS)) {
            ReadyPredicate.Context context = new ReadyPredicate.Context(ipAddress());
            Result result = Result.tryAgain(100, MILLISECONDS, "first attempt");

            while (!result.shouldBeKilled() && !result.wasSuccessful()) {
                try {
                    result = ready.isReady(context);
                }
                catch (Exception ex) {
                    if (context.runningForMoreThen(10, MINUTES)) {
                        result = Result.kill("Giving up");
                    }
                    else {
                        result = Result.tryAgain(5, SECONDS, "Trying again");
                    }
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

    public String ipAddress() {
        return inspectNetwork().ipAddress();
    }

    public NetworkSettings inspectNetwork() {
        return inspect().networkSettings();
    }

    public ContainerInfo inspect() {
        try {
            return docker.inspectContainer(containerId);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to inspect container details", ex);
        }
    }

    public boolean canConnectTcp(int port) {
        try {
            String hostAddress = ipAddress();
            new Socket(hostAddress, port);
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

}
