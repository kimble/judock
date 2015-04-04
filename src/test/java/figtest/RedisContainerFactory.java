package figtest;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.developerb.judock.ReadyPredicate;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Kim A. Betti
 */
public class RedisContainerFactory extends ContainerFactory<RedisContainerFactory.Container> {

    private final static String container_name = "redis-test";

    protected RedisContainerFactory() {
        super(container_name);
    }

    @Override
    protected void prepare(DockerClient dockerClient) throws Exception {
        dockerClient.pull("redis:2.8.19");
    }

    @Override
    protected ContainerConfig provideConfiguration(ContainerConfig.Builder docker) {
        return docker.image("redis:2.8.19").build();
    }

    @Override
    protected ReadyPredicate isReady(RedisContainerFactory.Container managedContainer) throws Exception {
        return context -> managedContainer.canConnectTcp(6379)
                ? ReadyPredicate.Result.success("Connected")
                : context.runningForMoreThen(5, MINUTES)
                        ? ReadyPredicate.Result.kill("Giving up")
                        : ReadyPredicate.Result.tryAgain(2, SECONDS, "Trying again");
    }

    @Override
    protected RedisContainerFactory.Container wrapContainer(DockerClient docker, String containerId) throws Exception {
        return new Container(docker, containerId);
    }

    public static class Container extends ManagedContainer {

        public Container(DockerClient docker, String containerId) {
            super(container_name, docker, containerId);
        }

    }

}
