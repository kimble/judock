package figtest;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.developerb.judock.ReadyPredicate;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import static java.lang.Enum.valueOf;
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
    protected ContainerConfig containerConfiguration(ContainerConfig.Builder docker) {
        return docker.image("redis:2.8.19").build();
    }

    @Override
    protected HostConfig hostConfiguration(HostConfig.Builder cfg) {
        return cfg.build();
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
    protected RedisContainerFactory.Container wrapContainer(DockerClient docker, HostConfig hostConfiguration, String containerId) throws Exception {
        return new Container(docker, hostConfiguration, containerId);
    }

    public static class Container extends ManagedContainer {

        public Container(DockerClient docker, HostConfig hostConfiguration, String containerId) {
            super(container_name, docker, hostConfiguration, containerId);
        }

    }

}
