package com.developerb.judock;

import com.google.common.base.Optional;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.NetworkSettings;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static com.spotify.docker.client.DockerClient.LogsParameter.*;

/**
 *
 */
public abstract class ManagedContainer {

    private final Logger log;

    private final String containerName, containerId;
    private final DockerClient docker;

    public ManagedContainer(DockerClient docker, String containerName, String containerId) {
        this.log = LoggerFactory.getLogger("container." + containerName);

        this.containerName = containerName;
        this.containerId = containerId;
        this.docker = docker;
    }

    public String containerName() {
        return containerName;
    }

    protected abstract void isReady(BootContext context);

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

    public void waitForIt() throws Exception {

        log.info("Waiting for the container to boot");
        try (LogStream logStream = docker.logs(containerId, STDERR, STDOUT, TIMESTAMPS)) {
            BootContext context = new BootContext();


            while (context.stillWaiting()) {
                try {
                    isReady(context);

                    if (context.gracetime().isPresent()) {
                        Thread.sleep(context.gracetime().get().getMillis());
                    }
                }
                catch (Exception ex) {
                    log.error("Ready predicate should not throw exception", ex);
                    context.failed(ex);
                }
            }

            if (context.hasGivenUp()) {
                String containerLogs = readLogStream(logStream);
                throw new IllegalStateException(String.format("Container %s never reached 'running' before timeout, but %s.\n" +
                        "Following is the latest output from the container:\n%s", containerId, inspect().state(), containerLogs));
            }
        }
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

    public static class BootContext {

        private final DateTime started;

        private volatile Duration gracetime;
        private volatile boolean givenUp;
        private volatile boolean booted;
        private volatile String message;

        public BootContext() throws Exception {
            this.started = DateTime.now();
        }

        public boolean runningForMoreThen(long val, TimeUnit unit) {
            Duration other = new Duration(unit.toMillis(val));
            Duration sinceStartup = new Duration(started, DateTime.now());
            return sinceStartup.isLongerThan(other);
        }

        public boolean stillWaiting() {
            return !booted && !givenUp;
        }

        public void failed(String reason) {
            message = reason;
            givenUp = true;
        }

        public void failed(Exception ex) {
            message = ex.getMessage();
            givenUp = true;
        }

        public void ready(String successMessage) {
            message = successMessage;
            booted = true;
        }

        public void tryAgain(int val, TimeUnit unit, String msg) {
            gracetime = Duration.millis(unit.toMillis(val));
            givenUp = false;
            message = msg;
        }

        public Optional<Duration> gracetime() {
            return Optional.fromNullable(gracetime);
        }

        public boolean hasGivenUp() {
            return givenUp;
        }

    }

}
