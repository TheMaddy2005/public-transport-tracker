-- Public Transport Tracking System (small cities) - database schema
-- Target database: MySQL 8
-- Tables: routes, stops, route_stops, buses, gps_logs

DROP DATABASE IF EXISTS bus_tracker;
CREATE DATABASE bus_tracker CHARACTER SET utf8mb4;
USE bus_tracker;

-- A bus route, e.g. "Old Bus Stand to College Road"
CREATE TABLE routes (
    route_id     INT AUTO_INCREMENT PRIMARY KEY,
    route_name   VARCHAR(100) NOT NULL,
    start_point  VARCHAR(100) NOT NULL,
    end_point    VARCHAR(100) NOT NULL
);

-- A physical bus stop with its GPS coordinates
CREATE TABLE stops (
    stop_id   INT AUTO_INCREMENT PRIMARY KEY,
    stop_name VARCHAR(100) NOT NULL,
    latitude  DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL
);

-- Which stops belong to a route, in order, with distance along the route.
-- distance_from_start_km lets us estimate "how far is the bus from stop X".
CREATE TABLE route_stops (
    route_id               INT NOT NULL,
    stop_id                INT NOT NULL,
    stop_order             INT NOT NULL,
    distance_from_start_km DECIMAL(6,2) NOT NULL,
    PRIMARY KEY (route_id, stop_order),
    FOREIGN KEY (route_id) REFERENCES routes(route_id),
    FOREIGN KEY (stop_id)  REFERENCES stops(stop_id)
);

-- A bus assigned to a route
CREATE TABLE buses (
    bus_id      INT AUTO_INCREMENT PRIMARY KEY,
    bus_number  VARCHAR(20) NOT NULL UNIQUE,
    route_id    INT NOT NULL,
    driver_name VARCHAR(100),
    FOREIGN KEY (route_id) REFERENCES routes(route_id)
);

-- One row per GPS ping sent by a bus device.
-- This is the "transit log" the system stores and queries.
CREATE TABLE gps_logs (
    log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id      INT NOT NULL,
    latitude    DECIMAL(9,6) NOT NULL,
    longitude   DECIMAL(9,6) NOT NULL,
    speed_kmph  DECIMAL(5,2) NOT NULL,
    logged_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id) REFERENCES buses(bus_id)
);

-- Indexes for the two most common lookups: latest pings of a bus, pings in a time range
CREATE INDEX idx_gps_bus_time ON gps_logs (bus_id, logged_at);
CREATE INDEX idx_gps_time ON gps_logs (logged_at);

-- Helper views -----------------------------------------------------------

-- The latest GPS ping of each bus
CREATE VIEW latest_ping AS
SELECT g.bus_id, g.latitude, g.longitude, g.speed_kmph, g.logged_at
FROM gps_logs g
WHERE g.logged_at = (SELECT MAX(g2.logged_at) FROM gps_logs g2 WHERE g2.bus_id = g.bus_id);

-- Average recorded speed of each bus
CREATE VIEW bus_avg_speed AS
SELECT bus_id, AVG(speed_kmph) AS avg_speed FROM gps_logs GROUP BY bus_id;
