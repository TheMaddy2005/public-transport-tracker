package com.tracker;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

/**
 * Public Transport Tracking System (small cities) - console app.
 *
 * Modes:
 *   java BusTrackerApp --init   create schema + load sample data (sql/01 and sql/02)
 *   java BusTrackerApp --demo   run the main tracking queries once and print results
 *   java BusTrackerApp          interactive menu
 */
public class BusTrackerApp {

    public static void main(String[] args) {
        try (Connection conn = Db.connect()) {
            if (args.length > 0 && args[0].equals("--init")) {
                SqlRunner.runScript(conn, Path.of("sql/01_schema.sql"));
                SqlRunner.runScript(conn, Path.of("sql/02_sample_data.sql"));
                System.out.println("Database 'bus_tracker' created and sample data loaded.");
                return;
            }
            if (args.length > 0 && args[0].equals("--demo")) {
                initIfEmpty(conn); // demo works even on a fresh database
                runDemo(conn);
                return;
            }
            menu(conn);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- interactive menu ----------

    static void menu(Connection conn) throws SQLException {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("=== Public Transport Tracker ===");
            System.out.println("1. Latest position of all buses");
            System.out.println("2. GPS history of one bus");
            System.out.println("3. Buses active in the last 15 minutes");
            System.out.println("4. ETA of buses to a stop");
            System.out.println("5. Add a GPS ping (simulate a device)");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> showLatestPositions(conn);
                    case "2" -> {
                        System.out.print("Bus id: ");
                        showBusHistory(conn, Integer.parseInt(sc.nextLine().trim()));
                    }
                    case "3" -> showActiveBuses(conn);
                    case "4" -> {
                        System.out.print("Stop id: ");
                        showEtaToStop(conn, Integer.parseInt(sc.nextLine().trim()));
                    }
                    case "5" -> {
                        System.out.print("Bus id: ");
                        int busId = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Latitude: ");
                        double lat = Double.parseDouble(sc.nextLine().trim());
                        System.out.print("Longitude: ");
                        double lon = Double.parseDouble(sc.nextLine().trim());
                        System.out.print("Speed (km/h): ");
                        double speed = Double.parseDouble(sc.nextLine().trim());
                        addGpsPing(conn, busId, lat, lon, speed);
                    }
                    case "0" -> { System.out.println("Bye!"); return; }
                    default -> System.out.println("Unknown option.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }

    // ---------- queries (same SQL as sql/03_queries.sql) ----------

    /** Q1: latest known position of every bus. */
    static void showLatestPositions(Connection conn) throws SQLException {
        String sql = """
                SELECT b.bus_number, r.route_name, g.latitude, g.longitude, g.speed_kmph, g.logged_at
                FROM buses b
                JOIN routes r ON r.route_id = b.route_id
                JOIN gps_logs g ON g.bus_id = b.bus_id
                WHERE g.logged_at = (SELECT MAX(g2.logged_at) FROM gps_logs g2 WHERE g2.bus_id = b.bus_id)
                ORDER BY b.bus_number
                """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-14s %-20s %-11s %-11s %-7s %s%n",
                    "Bus", "Route", "Lat", "Lon", "Speed", "Last ping");
            while (rs.next()) {
                System.out.printf("%-14s %-20s %-11s %-11s %-7s %s%n",
                        rs.getString("bus_number"), rs.getString("route_name"),
                        rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                        rs.getBigDecimal("speed_kmph"), rs.getTimestamp("logged_at"));
            }
        }
    }

    /** Q2: full GPS history of one bus, oldest first. */
    static void showBusHistory(Connection conn, int busId) throws SQLException {
        String sql = "SELECT logged_at, latitude, longitude, speed_kmph FROM gps_logs "
                   + "WHERE bus_id = ? ORDER BY logged_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, busId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-21s %-11s %-11s %s%n", "Time", "Lat", "Lon", "Speed");
                while (rs.next()) {
                    System.out.printf("%-21s %-11s %-11s %s%n",
                            rs.getTimestamp("logged_at"), rs.getBigDecimal("latitude"),
                            rs.getBigDecimal("longitude"), rs.getBigDecimal("speed_kmph"));
                }
            }
        }
    }

    /** Q3: buses that sent a ping in the last 15 minutes. */
    static void showActiveBuses(Connection conn) throws SQLException {
        String sql = """
                SELECT b.bus_number, MAX(g.logged_at) AS last_ping
                FROM buses b JOIN gps_logs g ON g.bus_id = b.bus_id
                WHERE g.logged_at >= NOW() - INTERVAL '15' MINUTE
                GROUP BY b.bus_number
                """;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-14s %s%n", "Bus", "Last ping");
            while (rs.next()) {
                System.out.printf("%-14s %s%n", rs.getString("bus_number"), rs.getTimestamp("last_ping"));
            }
        }
    }

    /**
     * Q5: estimated time of arrival of every bus on its route to the given stop.
     * Method: snap the bus to the stop nearest its latest ping, take the
     * remaining route distance (target stop km - nearest stop km) and divide
     * by the bus's average recorded speed.
     */
    static void showEtaToStop(Connection conn, int stopId) throws SQLException {
        // Uses the latest_ping and bus_avg_speed views from sql/01_schema.sql.
        String sql = """
                SELECT
                    b.bus_number,
                    target.stop_name AS towards_stop,
                    ROUND(target_rs.distance_from_start_km - near_rs.distance_from_start_km, 2) AS km_remaining,
                    ROUND(spd.avg_speed, 1) AS avg_speed_kmph,
                    ROUND(60.0 * (target_rs.distance_from_start_km - near_rs.distance_from_start_km)
                                 / NULLIF(spd.avg_speed, 0), 1) AS eta_minutes
                FROM buses b
                JOIN bus_avg_speed spd ON spd.bus_id = b.bus_id
                JOIN latest_ping lp ON lp.bus_id = b.bus_id
                JOIN stops target ON target.stop_id = ?
                JOIN route_stops target_rs
                     ON target_rs.route_id = b.route_id AND target_rs.stop_id = target.stop_id
                JOIN route_stops near_rs ON near_rs.route_id = b.route_id
                WHERE near_rs.stop_id = (
                    SELECT rs2.stop_id
                    FROM route_stops rs2
                    JOIN stops s2 ON s2.stop_id = rs2.stop_id
                    WHERE rs2.route_id = b.route_id
                    ORDER BY ABS(s2.latitude - lp.latitude) + ABS(s2.longitude - lp.longitude)
                    LIMIT 1
                )
                AND target_rs.distance_from_start_km >= near_rs.distance_from_start_km
                ORDER BY eta_minutes
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stopId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-14s %-18s %-13s %-13s %s%n",
                        "Bus", "Towards", "Km remaining", "Avg speed", "ETA (min)");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("%-14s %-18s %-13s %-13s %s%n",
                            rs.getString("bus_number"), rs.getString("towards_stop"),
                            rs.getBigDecimal("km_remaining"), rs.getBigDecimal("avg_speed_kmph"),
                            rs.getBigDecimal("eta_minutes"));
                }
                if (!any) {
                    System.out.println("(no buses heading to that stop right now)");
                }
            }
        }
    }

    /** Insert one GPS ping, like a real tracking device would. */
    static void addGpsPing(Connection conn, int busId, double lat, double lon, double speed)
            throws SQLException {
        String sql = "INSERT INTO gps_logs (bus_id, latitude, longitude, speed_kmph) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, busId);
            ps.setDouble(2, lat);
            ps.setDouble(3, lon);
            ps.setDouble(4, speed);
            ps.executeUpdate();
        }
        System.out.println("Ping stored for bus " + busId + ".");
    }

    /** Loads schema + sample data when the buses table does not exist yet. */
    static void initIfEmpty(Connection conn) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "buses", null)) {
            if (rs.next()) {
                return;
            }
        }
        SqlRunner.runScript(conn, Path.of("sql/01_schema.sql"));
        SqlRunner.runScript(conn, Path.of("sql/02_sample_data.sql"));
        System.out.println("(fresh database detected - schema and sample data loaded)");
    }

    /** Non-interactive demo used to check the system end to end. */
    static void runDemo(Connection conn) throws SQLException {
        System.out.println("--- Latest position of all buses ---");
        showLatestPositions(conn);
        System.out.println();
        System.out.println("--- History of bus 1 ---");
        showBusHistory(conn, 1);
        System.out.println();
        System.out.println("--- Buses active in the last 15 minutes ---");
        showActiveBuses(conn);
        System.out.println();
        System.out.println("--- ETA of buses to Town Hall (stop 3) ---");
        showEtaToStop(conn, 3);
        System.out.println();
        System.out.println("--- Adding one test ping for bus 3, then re-reading its latest position ---");
        addGpsPing(conn, 3, 18.4095, 76.5620, 23.5);
        showLatestPositions(conn);
    }
}
