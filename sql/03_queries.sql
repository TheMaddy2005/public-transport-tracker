-- Useful queries for the Public Transport Tracking System.
-- These are the same queries the Java app runs (see src/com/tracker/BusTrackerApp.java).

USE bus_tracker;

-- Q1. Latest known position of every bus
-- (join each bus to its most recent gps_logs row)
SELECT b.bus_number, r.route_name, g.latitude, g.longitude, g.speed_kmph, g.logged_at
FROM buses b
JOIN routes r ON r.route_id = b.route_id
JOIN gps_logs g ON g.bus_id = b.bus_id
WHERE g.logged_at = (SELECT MAX(g2.logged_at) FROM gps_logs g2 WHERE g2.bus_id = b.bus_id)
ORDER BY b.bus_number;

-- Q2. Full travel history of one bus (here: bus 1)
SELECT g.logged_at, g.latitude, g.longitude, g.speed_kmph
FROM gps_logs g
WHERE g.bus_id = 1
ORDER BY g.logged_at;

-- Q3. Which buses sent a ping in the last 15 minutes? ("buses currently active")
SELECT b.bus_number, MAX(g.logged_at) AS last_ping
FROM buses b
JOIN gps_logs g ON g.bus_id = b.bus_id
WHERE g.logged_at >= NOW() - INTERVAL '15' MINUTE
GROUP BY b.bus_number;

-- Q4. Average speed of each bus over all recorded pings
SELECT b.bus_number, ROUND(AVG(g.speed_kmph), 1) AS avg_speed_kmph, COUNT(*) AS ping_count
FROM buses b
JOIN gps_logs g ON g.bus_id = b.bus_id
GROUP BY b.bus_number;

-- Q5. ETA of every active bus to a given stop (here: stop 3, Town Hall, on route 1).
-- Idea: snap the bus to the stop nearest its latest ping, subtract that stop's
-- distance along the route from the target stop's distance, and divide the
-- remaining kilometres by the bus's average speed (views: latest_ping, bus_avg_speed).
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
JOIN stops target ON target.stop_id = 3
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
ORDER BY eta_minutes;
