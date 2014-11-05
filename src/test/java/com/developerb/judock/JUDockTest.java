package com.developerb.judock;

import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

public class JUDockTest {

    @Rule
    public JUDock jd = new JUDock();

    @Test
    public void startDatabaseServerAndConnectToIt() throws Exception {
        final String containerId = jd.createContainerCommand("tutum/mysql:5.6")
                .withEnv("MYSQL_PASS=qwerty123")
                .exec()
                .getId();

        JUDock.Container mysql = jd.createTestContainer(containerId);
        mysql.startContainer();

        MySQLReadyPredicate readyPredicate = new MySQLReadyPredicate (
                mysql.getIpAddress()
        );

        mysql.waitFor(10, TimeUnit.SECONDS, readyPredicate);
    }


    static class MySQLReadyPredicate implements JUDock.Predicate {

        private final String ipAddress;

        MySQLReadyPredicate(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        @Override
        public boolean isOkay() {
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + ipAddress + ":3306", "admin", "qwerty123")) {
                return connection != null;
            }
            catch (Exception ex) {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("MySQL to become available on %s:3306", ipAddress);
        }

    }

}