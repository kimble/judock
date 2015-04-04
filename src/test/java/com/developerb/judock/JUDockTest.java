package com.developerb.judock;

import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;

import static org.junit.Assert.assertNotNull;

public class JUDockTest {

    @Rule
    public JUDock jd = new JUDock();

    @Test
    public void connectToMySQL() throws Exception {
        MysqlContainerFactory containerFactory = new MysqlContainerFactory();
        MysqlContainerFactory.Container mySQL = jd.manage(containerFactory);

        try (Connection connection = mySQL.open()) {
            assertNotNull(connection);
        }
    }

}