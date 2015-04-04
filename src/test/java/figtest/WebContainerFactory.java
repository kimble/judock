package figtest;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import java.io.File;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;


public class WebContainerFactory extends ContainerFactory<WebContainerFactory.Container> {

    private volatile String imageId;
    private final RedisContainerFactory.Container redisContainer;

    public WebContainerFactory(RedisContainerFactory.Container redisContainer) {
        super("figtest-web");
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
    protected ContainerConfig containerConfiguration(ContainerConfig.Builder builder) {
        return builder.image(imageId).build();
    }

    @Override
    protected HostConfig hostConfiguration(HostConfig.Builder cfg) {
        return cfg.links(redisContainer.containerName() + ":redis").build();
    }

    @Override
    protected Container wrapContainer(DockerClient docker, String containerId) throws Exception {
        return new Container(docker, containerId);
    }


    public class Container extends ManagedContainer {

        public Container(DockerClient docker, String containerId) {
            super(docker, name(), containerId);
        }

        @Override
        protected void isReady(BootContext context) {
            try {
                final String html = httpGet("http://%s/");

                if (html.contains("Hello... I have been seen 1 times.")) {
                    context.ready(html);
                }
                else {
                    context.failed("Unexpected message - " + html.trim());
                }
            }
            catch (Exception ex) {
                if (context.runningForMoreThen(60, SECONDS)) {
                    context.failed("Been running too long - Giving up");
                }
                else {
                    context.tryAgain(2, SECONDS, "Trying again after exception - " + ex.getMessage());
                }
            }
        }

    }

}



