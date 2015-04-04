package com.developerb.judock;


import com.google.common.collect.Lists;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Where JUnit meets Docker!
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

    public <C extends ManagedContainer> C manage(ContainerFactory<C> containerFactory) throws Exception {
        C managedContainer = containerFactory.create(docker);
        ReadyPredicate ready = containerFactory.isReady(managedContainer);
        managedContainer.boot(ready);

        cleanupTasks.add(() -> {
            managedContainer.stop();
            managedContainer.remove();
        });

        return managedContainer;
    }

    @Override
    protected void before() throws Throwable {
        log.info("Running: {}", docker.info());
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

}
