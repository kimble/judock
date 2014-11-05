package com.developerb.judock;

import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JUDockTest {

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

        mysql.waitFor(readyPredicate);
    }

    @Test
    public void pauseAndUnPauseRunningContainer() throws Exception {
        final String containerId = jd.createContainerCommand("tutum/mysql:5.6")
                .withEnv("MYSQL_PASS=qwerty123")
                .exec()
                .getId();

        JUDock.Container mysql = jd.createTestContainer(containerId);
        mysql.startContainer();

        MySQLReadyPredicate readyPredicate = new MySQLReadyPredicate (
                mysql.getIpAddress()
        );

        mysql.waitFor(30, TimeUnit.SECONDS, readyPredicate);

        assertTrue("Should be able to connect", readyPredicate.isOkay());

        mysql.pause();

        assertFalse("Should not be allowed to connect while paused", readyPredicate.isOkay());

        mysql.unPause();

        assertTrue("Should be able to connect after un-pausing", readyPredicate.isOkay());
    }

    @Rule
    public JUDock jd = new JUDock();


    static class MySQLReadyPredicate implements JUDock.Predicate {

        private final String ipAddress;

        MySQLReadyPredicate(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        @Override
        public boolean isOkay() {
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + ipAddress + ":3306?connectTimeout=1000&socketTimeout=1000", "admin", "qwerty123")) {
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