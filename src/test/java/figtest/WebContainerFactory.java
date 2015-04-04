package figtest;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.developerb.judock.ReadyPredicate;
import com.developerb.judock.ReadyPredicate.Result;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import java.io.File;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 *
 */
public class WebContainerFactory extends ContainerFactory<WebContainerFactory.Container> {

    private final static String container_name = "figtest-web";

    private volatile String imageId;
    private final RedisContainerFactory.Container redisContainer;

    public WebContainerFactory(RedisContainerFactory.Container redisContainer) {
        super(container_name);
        this.redisContainer = redisContainer;
    }

    @Override
    protected void prepare(DockerClient dockerClient) throws Exception {
        if (imageId == null) {
            log.info("Building image");

            Path path = new File("docker/figtest/web").toPath();
            imageId = dockerClient.build(path);
        }
    }

    @Override
    protected ContainerConfig provideConfiguration(ContainerConfig.Builder builder) {
        return builder.image(imageId).build();
    }

    @Override
    protected ReadyPredicate isReady(Container managedContainer) throws Exception {
        return context -> {
            try {
                final String html = managedContainer.httpGet("http://%s/");

                if (html.contains("Hello... I have been seen 1 times.")) {
                    return Result.success(html.trim());
                }
                else {
                    return Result.kill("Unexpected message - " + html.trim());
                }
            }
            catch (Exception ex) {
                if (context.runningForMoreThen(60, SECONDS)) {
                    return Result.kill("Been running too long - Giving up");
                }
                else {
                    return Result.tryAgain(2, SECONDS, "Trying again after exception - " + ex.getMessage());
                }
            }
        };
    }

    @Override
    protected Container wrapContainer(DockerClient docker, String containerId) throws Exception {
        return new Container(docker, containerId);
    }

    public class Container extends ManagedContainer {

        public Container(DockerClient docker, String containerId) {
            super(container_name, docker, containerId);
        }

        @Override
        protected HostConfig hostConfig(HostConfig.Builder builder) {
            return builder.links(redisContainer.containerName() + ":redis").build();
        }

    }

}



