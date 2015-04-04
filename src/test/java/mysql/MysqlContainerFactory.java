package mysql;

import com.developerb.judock.ContainerFactory;
import com.developerb.judock.ManagedContainer;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;

import static java.util.concurrent.TimeUnit.SECONDS;


public class MysqlContainerFactory extends ContainerFactory<MysqlContainerFactory.Container> {

    public MysqlContainerFactory() {
        super("mysql-test-container");
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
    protected Container wrapContainer(DockerClient docker, String containerId) throws Exception {
        return new Container(docker, containerId);
    }



    public class Container extends ManagedContainer {

        public Container(DockerClient docker, String containerId) throws Exception {
            super(docker, name(), containerId);
        }

        @Override
        protected void isReady(BootProcess context) {
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
