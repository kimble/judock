package com.developerb.judock;

import com.spotify.docker.client.messages.ContainerConfig;
import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.joda.time.Duration.standardSeconds;

public class JUDockTest {

    @Rule
    public JUDock jd = new JUDock();

    @Test
    public void startDatabaseServerAndConnectToIt() throws Exception {
        ContainerConfig containerConfiguration = jd.createContainerConfig("tutum/mysql:5.6")
                .env("MYSQL_PASS=qwerty123")
                .build();

        JUDock.Container mysql = jd.replaceOrCreateContainer(containerConfiguration, "mysql-test-container");
        mysql.startContainer();

        {
            MySQLReadyPredicate readyPredicate = new MySQLReadyPredicate(mysql);
            mysql.waitFor(standardSeconds(60), readyPredicate);
        }
    }



    static class MySQLReadyPredicate implements JUDock.Predicate {

        private final JUDock.Container container;

        MySQLReadyPredicate(JUDock.Container container) {
            this.container = container;
        }

        @Override
        public boolean isOkay() {
            try {
                final String connectionUri = String.format("jdbc:mysql://%s:3306?connectTimeout=1000&socketTimeout=1000", container.getIpAddress());
                try (Connection connection = DriverManager.getConnection(connectionUri, "admin", "qwerty123")) {
                    return connection != null;
                }
                catch (Exception ex) {
                    return false;
                }
            }
            catch (Exception ignored) {
                return false;
            }
        }

        @Override
        public String toString() {
            try {
                return String.format("MySQL to become available on %s:3306", container.getIpAddress());
            }
            catch (Exception ex) {
                return "Unable to determine container ip address";
            }
        }

    }

}