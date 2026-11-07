# Public Transport Tracking System (Small Cities)

A simple bus tracking prototype for small cities, written in **Java (JDBC)** with a
**MySQL** database. Buses periodically send GPS pings (latitude, longitude, speed)
that are stored as transit logs. On top of those logs the system answers the two
questions a commuter actually asks:

- *Where is my bus right now?* -> latest known position of every bus
- *When will it reach my stop?* -> an ETA estimate from route distance and average speed

## Project structure

| Path | What it is |
|---|---|
| `sql/01_schema.sql` | Tables (`routes`, `stops`, `route_stops`, `buses`, `gps_logs`), indexes, and 2 helper views |
| `sql/02_sample_data.sql` | Sample data for a fictional small city: 2 routes, 7 stops, 3 buses, ~13 GPS pings |
| `sql/03_queries.sql` | 5 practical queries over the transit logs (see below) |
| `src/com/tracker/` | Java console app (plain JDBC, no frameworks) |
| `run.sh` | Compile-and-run script |

## The five tracking queries

1. **Latest position of every bus** - join each bus to its most recent `gps_logs` row
2. **Full history of one bus** - all pings of a bus, oldest first
3. **Buses active in the last 15 minutes** - pings seen recently
4. **Average speed per bus** - over all recorded pings
5. **ETA to a stop** - snap the bus to the stop nearest its latest ping, take the
   remaining route distance (`distance_from_start_km` of target minus nearest stop),
   and divide by the bus's average speed

## Tech stack

- Java 17+ (console app, `java.sql` JDBC API only)
- MySQL 8
- Optional: H2 database in MySQL mode, for a quick demo without installing MySQL

## How to run

### Option A - quick demo, no MySQL install needed

The repo includes the H2 driver jar, so this works out of the box:

```bash
git clone https://github.com/TheMaddy2005/public-transport-tracker.git
cd public-transport-tracker
export DB_URL='jdbc:h2:mem:bus_tracker;MODE=MySQL;DB_CLOSE_DELAY=-1'
./run.sh --demo
```

`--demo` loads the schema and sample data on a fresh database, then runs all the
tracking queries and prints the results, including adding one live GPS ping.

### Option B - with a real MySQL server

1. Install MySQL 8 and download
   [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/) into `lib/`
2. Set the connection variables (defaults shown):

```bash
export DB_URL='jdbc:mysql://localhost:3306/?allowMultiQueries=true'
export DB_USER='root'
export DB_PASS='yourpassword'
```

3. Create the schema and load sample data:

```bash
./run.sh --init
```

4. Run the interactive app or the one-shot demo:

```bash
./run.sh          # interactive menu
./run.sh --demo   # print everything once
```

The same SQL runs standalone too: `mysql -u root -p < sql/03_queries.sql`

## Interactive menu

```
1. Latest position of all buses
2. GPS history of one bus
3. Buses active in the last 15 minutes
4. ETA of buses to a stop
5. Add a GPS ping (simulate a device)
0. Exit
```

## Live web demo

**https://public-transport-tracker-phi.vercel.app**


The repo also ships a small hosted demo so the tracking queries can be tried in a
browser: a read-only dashboard (`web/index.html`) plus two tiny serverless API
endpoints (`api/buses.py`, `api/eta.py`, Python stdlib only) that serve the same
sample dataset as `sql/02_sample_data.sql` (bundled as `api/data.json`, with ping
times stored as offsets so the demo always looks live). The Java + MySQL core
remains the actual implementation; the web demo exists because a hosted demo
cannot ship a MySQL server. Deployed on Vercel's free tier.

## Honest limitations

- GPS pings are simulated sample data - there is no real GPS hardware or live feed.
- The ETA assumes a bus is at its nearest stop and keeps its average speed; it does
  not model traffic, signals, or dwell time at stops.
- The web demo is read-only and serves the bundled sample dataset, not a live
  MySQL database. Adding and storing real pings happens through the Java app.
