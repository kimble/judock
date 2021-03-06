package com.developerb.judock;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.spotify.docker.client.DockerClient.ListContainersParam.allContainers;

/**
 *
 */
public abstract class ContainerFactory<C extends ManagedContainer> {

    protected final Logger log;
    private final String containerName;

    protected ContainerFactory(String containerName) {
        this.log = LoggerFactory.getLogger("container." + containerName);
        this.containerName = containerName;
    }

    protected String name() {
        return containerName;
    }


    public C launch(DockerClient docker) throws Exception {
        log.info("Preparing");
        prepare(docker);

        for (Container container : docker.listContainers(allContainers())) {
            if (container.names() != null && container.names().contains("/" + containerName)) {
                log.info("Removing existing container {} [id: {}]", containerName, container.id().substring(0, 8));
                docker.stopContainer(container.id(), 2);
                docker.removeContainer(container.id(), true);
            }
        }

        final ContainerConfig containerConfiguration = containerConfiguration(ContainerConfig.builder());
        final HostConfig hostConfiguration = hostConfiguration(HostConfig.builder());
        final ContainerCreation creation = docker.createContainer(containerConfiguration, containerName);
        final String containerId = creation.id();

        if (creation.getWarnings() != null) {
            for (String warning : creation.getWarnings()) {
                log.warn("Warning occurred while creating container {} [id: {}]: {}", containerName, containerId.substring(0, 8), warning);
            }
        }

        log.info("Booting container");
        docker.startContainer(containerId, hostConfiguration);

        return wrapContainer(docker, containerId);
    }

    /**
     * Use this opportunity to pull any images you depend on
     * or do any other form or preparation.
     */
    protected abstract void prepare(DockerClient dockerClient) throws Exception;

    /**
     * Provide the configuration that will be used to started based upon.
     */
    protected abstract ContainerConfig containerConfiguration(ContainerConfig.Builder docker);

    protected HostConfig hostConfiguration(HostConfig.Builder cfg) {
        return cfg.build();
    }

    /**
     * An option to provide client code for functionality exposed by the container.
     */
    protected abstract C wrapContainer(DockerClient docker, String containerId) throws Exception;

}
