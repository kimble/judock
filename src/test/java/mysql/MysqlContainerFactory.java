package mysql;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.developerb.judock.ReadyPredicate;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import java.sql.Connection;
import java.sql.DriverManager;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;


public class MysqlContainerFactory extends ContainerFactory<MysqlContainerFactory.Container> {

    private static final String container_name = "mysql-test-container";

    public MysqlContainerFactory() {
        super(container_name);
    }

    @Override
    public void prepare(DockerClient docker) throws Exception {
        docker.pull("tutum/mysql:5.6");
    }

    @Override
    public ContainerConfig containerConfiguration(ContainerConfig.Builder cfg) {
        return cfg.image("tutum/mysql:5.6")
                .env("MYSQL_PASS=qwerty123")
                .build();
    }

    @Override
    protected HostConfig hostConfiguration(HostConfig.Builder cfg) {
        return cfg.build();
    }

    @Override
    protected ReadyPredicate isReady(Container container) throws Exception {
        return context -> {
            log.info("Verifying whether the container is running or not");

            try (Connection connection = container.open()) {
                return ReadyPredicate.Result.success("Got connection to: " + connection.getMetaData().getDatabaseProductVersion());
            }
            catch (Exception ex) {
                if (context.runningForMoreThen(5, MINUTES)) {
                    return ReadyPredicate.Result.kill("Giving up now.. " + ex.getMessage());
                }
                else {
                    return ReadyPredicate.Result.tryAgain(2, SECONDS, "Trying again (" + ex.getMessage() + ")");
                }
            }
        };
    }

    @Override
    protected Container wrapContainer(DockerClient docker, HostConfig hostConfiguration, String containerId) throws Exception {
        return new Container(docker, hostConfiguration, containerId);
    }



    public class Container extends ManagedContainer {

        public Container(DockerClient docker, HostConfig hostConfiguration, String containerId) throws Exception {
            super(container_name, docker, hostConfiguration, containerId);
        }

        public Connection open() throws Exception {
            String hostname = ipAddress();
            String connectionUri = String.format("jdbc:mysql://%s:3306?connectTimeout=1000&socketTimeout=1000", hostname);

            return DriverManager.getConnection(connectionUri, "admin", "qwerty123");
        }

    }

}
