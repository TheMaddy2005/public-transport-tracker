-- Sample data for a fictional small city ("Rampur") so the app and queries
-- can be run and demoed immediately.

USE bus_tracker;

INSERT INTO routes (route_name, start_point, end_point) VALUES
('R1 - City Circle',   'Old Bus Stand',   'Civil Hospital'),
('R2 - College Line',  'Railway Station', 'Government College');

INSERT INTO stops (stop_name, latitude, longitude) VALUES
('Old Bus Stand',      18.408800, 76.560400),
('Market Chowk',       18.410900, 76.565300),
('Town Hall',          18.414200, 76.570100),
('Civil Hospital',     18.418300, 76.574800),
('Railway Station',    18.399500, 76.553200),
('Nehru Park',         18.404600, 76.558700),
('Government College', 18.413600, 76.563900);

-- Route R1 stops in order (km measured from Old Bus Stand)
INSERT INTO route_stops (route_id, stop_id, stop_order, distance_from_start_km) VALUES
(1, 1, 1, 0.00),
(1, 2, 2, 0.90),
(1, 3, 3, 1.80),
(1, 4, 4, 2.70);

-- Route R2 stops in order (km measured from Railway Station)
INSERT INTO route_stops (route_id, stop_id, stop_order, distance_from_start_km) VALUES
(2, 5, 1, 0.00),
(2, 6, 2, 1.20),
(2, 7, 3, 2.40);

INSERT INTO buses (bus_number, route_id, driver_name) VALUES
('MH-24-B-1001', 1, 'Suresh Patil'),
('MH-24-B-1002', 1, 'Anil Shinde'),
('MH-24-B-2001', 2, 'Ramesh Yadav');

-- GPS pings for bus 1 travelling R1 from Old Bus Stand towards Civil Hospital
-- over ~20 minutes (timestamps relative to "now" so the demo always looks live).
-- Ping 1 is at Old Bus Stand, ping 3 at Market Chowk, ping 5 at Town Hall.
INSERT INTO gps_logs (bus_id, latitude, longitude, speed_kmph, logged_at) VALUES
(1, 18.408800, 76.560400,  0.0, NOW() - INTERVAL '20' MINUTE),
(1, 18.409600, 76.562500, 18.5, NOW() - INTERVAL '16' MINUTE),
(1, 18.410900, 76.565300, 22.0, NOW() - INTERVAL '12' MINUTE),
(1, 18.412400, 76.567600, 19.2, NOW() - INTERVAL  '8' MINUTE),
(1, 18.414200, 76.570100, 15.8, NOW() - INTERVAL  '4' MINUTE),
(1, 18.415900, 76.572200, 21.4, NOW() - INTERVAL  '1' MINUTE);

-- GPS pings for bus 2, also on R1 but behind bus 1 (last ping at Market Chowk)
INSERT INTO gps_logs (bus_id, latitude, longitude, speed_kmph, logged_at) VALUES
(2, 18.408800, 76.560400,  0.0, NOW() - INTERVAL '14' MINUTE),
(2, 18.409800, 76.563000, 17.0, NOW() - INTERVAL  '9' MINUTE),
(2, 18.410900, 76.565300, 20.5, NOW() - INTERVAL  '3' MINUTE);

-- GPS pings for bus 3 on R2 (Railway Station -> Government College)
INSERT INTO gps_logs (bus_id, latitude, longitude, speed_kmph, logged_at) VALUES
(3, 18.399500, 76.553200,  0.0, NOW() - INTERVAL '18' MINUTE),
(3, 18.402000, 76.556000, 24.0, NOW() - INTERVAL '12' MINUTE),
(3, 18.404600, 76.558700, 16.5, NOW() - INTERVAL  '6' MINUTE),
(3, 18.408000, 76.561000, 19.8, NOW() - INTERVAL  '2' MINUTE);
