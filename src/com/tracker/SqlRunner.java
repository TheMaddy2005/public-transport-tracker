package com.tracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs a .sql file against the database.
 * The file is split into statements on ';' at the end of a line.
 * Comment lines (starting with --) are skipped.
 */
public class SqlRunner {

    public static void runScript(Connection conn, Path file) throws IOException, SQLException {
        // H2 (used for quick demos without a MySQL server) has no CREATE DATABASE / USE.
        boolean isH2 = conn.getMetaData().getURL().contains(":h2:");
        String content = Files.readString(file);
        StringBuilder statement = new StringBuilder();
        try (Statement st = conn.createStatement()) {
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                    continue; // skip comments and blank lines
                }
                statement.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String sql = statement.toString().trim();
                    sql = sql.substring(0, sql.length() - 1); // drop the trailing ';'
                    String upper = sql.toUpperCase();
                    if (isH2 && (upper.startsWith("CREATE DATABASE") || upper.startsWith("DROP DATABASE")
                            || upper.startsWith("USE "))) {
                        statement.setLength(0);
                        continue;
                    }
                    if (!sql.isEmpty()) {
                        st.execute(sql);
                    }
                    statement.setLength(0);
                }
            }
        }
    }
}
