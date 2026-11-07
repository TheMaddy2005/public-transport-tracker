#!/usr/bin/env bash
# Compile and run the Public Transport Tracker.
# Usage:
#   ./run.sh --init    create schema + load sample data
#   ./run.sh --demo    run all tracking queries once (non-interactive)
#   ./run.sh           interactive menu
#
# Requires: JDK 17+ and a JDBC driver jar in lib/
# (MySQL Connector/J for MySQL, or h2.jar for the bundled H2 test mode).
set -e
mkdir -p out
CP=$(ls lib/*.jar | tr '\n' ':')
javac -cp "$CP" -d out src/com/tracker/*.java
java -cp "out:$CP" com.tracker.BusTrackerApp "$@"
