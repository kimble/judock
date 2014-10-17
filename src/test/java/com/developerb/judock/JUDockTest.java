package com.developerb.judock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.Assert.assertNotNull;

public class JUDockTest {

    private final static Logger log = LoggerFactory.getLogger(JUDockTest.class);


    @Rule
    public JUDock jd = new JUDock();


    @Test
    public void startDatabaseServerAndConnectToIt() throws Exception {
        final String containerId = jd.createContainerCommand("tutum/mysql:5.6")
                .withEnv("MYSQL_PASS=qwerty123")
                .exec()
                .getId();


        int localPort = jd.availableTcpPort();

        jd.startContainerCommand(containerId)
                .withPortBindings(jd.tcpPortBindings(localPort + ":3306"))
                .exec();


        System.out.println("Attempting to connect to MySQL on port " + localPort);

        try (Connection connection = getConnection(localPort)) {
            log.info("Got connection: " + connection);
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