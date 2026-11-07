package com.tracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central place for database connection settings.
 *
 * Reads three environment variables so the same code runs against
 * a local MySQL server or any other JDBC database:
 *
 *   DB_URL   (default: jdbc:mysql://localhost:3306/?allowMultiQueries=true)
 *   DB_USER  (default: root)
 *   DB_PASS  (default: empty)
 */
public class Db {

    public static final String URL  = System.getenv().getOrDefault("DB_URL",
            "jdbc:mysql://localhost:3306/?allowMultiQueries=true");
    public static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    public static final String PASS = System.getenv().getOrDefault("DB_PASS", "");

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
