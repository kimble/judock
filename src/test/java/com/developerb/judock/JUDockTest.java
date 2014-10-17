package com.developerb.judock;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.Assert.assertNotNull;

public class JUDockTest extends JUDock {

    @Test
    public void experimenting() throws Exception {
        final String containerId = createContainerCommand("tutum/mysql:5.6")
                .withEnv("MYSQL_PASS=qwerty123")
                .exec()
                .getId();


        int localPort = availableTcpPort();

        startContainerCommand(containerId)
                .withPortBindings(tcpPortBindings(localPort + ":3306"))
                .exec();


        System.out.println("Attempting to connect to MySQL on port " + localPort);

        try (Connection connection = getConnection(localPort)) {
            System.out.println("Got connection: " + connection);
            assertNotNull(connection);
        }
    }

    private Connection getConnection(int port) throws Exception {
        int retries = 30;

        while (true) {
            try {
                return DriverManager.getConnection("jdbc:mysql://localhost:" + port, "admin", "qwerty123");
            }
            catch (Exception ex) {
                if (retries-- > 0) {
                    Thread.sleep(1000);
                }
                else {
                    throw new IllegalStateException("Giving up connection to the database, see the latest exception", ex);
                }
            }
        }
    }

}