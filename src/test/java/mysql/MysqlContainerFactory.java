package mysql;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;

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
    protected Container wrapContainer(DockerClient docker, HostConfig hostConfiguration, String containerId) throws Exception {
        return new Container(docker, hostConfiguration, containerId);
    }



    public class Container extends ManagedContainer {

        public Container(DockerClient docker, HostConfig hostConfiguration, String containerId) throws Exception {
            super(container_name, docker, hostConfiguration, containerId);
        }

        @Override
        protected void isReady(BootContext context) {
            try (Connection connection = open()) {
                DatabaseMetaData metadata = connection.getMetaData();
                context.ready("Got connection: " + metadata.getDatabaseProductVersion());
            }
            catch (Exception ex) {
                context.failed(ex);
                context.tryAgain(3, SECONDS, ex.getMessage());
            }
        }

        public Connection open() throws Exception {
            String hostname = ipAddress();
            String connectionUri = String.format("jdbc:mysql://%s:3306?connectTimeout=1000&socketTimeout=1000", hostname);

            return DriverManager.getConnection(connectionUri, "admin", "qwerty123");
        }

    }

}
