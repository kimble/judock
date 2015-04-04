package figtest;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;


public class RedisContainerFactory extends ContainerFactory<RedisContainerFactory.Container> {

    protected RedisContainerFactory() {
        super("redis-test");
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
    protected RedisContainerFactory.Container wrapContainer(DockerClient docker, String containerId) throws Exception {
        return new Container(docker, containerId);
    }


    public class Container extends ManagedContainer {

        public Container(DockerClient docker, String containerId) {
            super(docker, name(), containerId);
        }

        @Override
        protected void isReady(BootProcess context) {
            if (canConnectTcp(6379)) {
                context.ready("Could connect on tcp");
            }
            else {
                if (context.runningForMoreThen(1, MINUTES)) {
                    context.failed("Took too long, giving up");
                }
                else {
                    context.tryAgain(2, SECONDS, "Trying to re-connect soon");
                }
            }
        }

    }

}
