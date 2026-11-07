"""Shared demo-data helpers for the hosted web demo.

The production design stores GPS pings in MySQL (see sql/). For the hosted
demo there is no database server, so the same sample dataset ships as
api/data.json with ping times stored as 'minutes_ago' offsets. At request
time we convert offsets into real timestamps, so the demo always looks live.
"""
import json
import math
import os
from datetime import datetime, timedelta, timezone

_DATA_CACHE = None


def load_data():
    """Load the sample dataset, converting minute offsets to timestamps."""
    global _DATA_CACHE
    if _DATA_CACHE is not None:
        return _DATA_CACHE
    here = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(here, "data.json"),
        os.path.join(os.getcwd(), "api", "data.json"),
        "/var/task/api/data.json",
    ]
    raw = None
    for path in candidates:
        if os.path.isfile(path):
            with open(path, "r") as f:
                raw = json.load(f)
            break
    if raw is None:
        raise FileNotFoundError("api/data.json not found")

    now = datetime.now(timezone.utc).replace(microsecond=0)
    logs = []
    for row in raw["gps_logs"]:
        logged_at = now - timedelta(minutes=row["minutes_ago"])
        logs.append({
            "bus_id": row["bus_id"],
            "latitude": row["latitude"],
            "longitude": row["longitude"],
            "speed_kmph": row["speed_kmph"],
            "minutes_ago": row["minutes_ago"],
            "logged_at": logged_at.isoformat().replace("+00:00", "Z"),
        })
    data = {
        "routes": raw["routes"],
        "stops": raw["stops"],
        "route_stops": raw["route_stops"],
        "buses": raw["buses"],
        "gps_logs": logs,
        "generated_at": now.isoformat().replace("+00:00", "Z"),
    }
    _DATA_CACHE = data
    return data


def route_of(data, route_id):
    return next(r for r in data["routes"] if r["route_id"] == route_id)


def stop_of(data, stop_id):
    return next(s for s in data["stops"] if s["stop_id"] == stop_id)


def stops_on_route(data, route_id):
    """Stops of one route, in order, with distance_from_start_km attached."""
    rows = [rs for rs in data["route_stops"] if rs["route_id"] == route_id]
    rows.sort(key=lambda rs: rs["stop_order"])
    out = []
    for rs in rows:
        stop = dict(stop_of(data, rs["stop_id"]))
        stop["stop_order"] = rs["stop_order"]
        stop["distance_from_start_km"] = rs["distance_from_start_km"]
        out.append(stop)
    return out


def pings_of(data, bus_id):
    """All pings of one bus, oldest first (mirrors query #2)."""
    rows = [g for g in data["gps_logs"] if g["bus_id"] == bus_id]
    rows.sort(key=lambda g: g["logged_at"])
    return rows


def latest_ping(data, bus_id):
    pings = pings_of(data, bus_id)
    return pings[-1] if pings else None


def avg_speed(data, bus_id):
    """Average speed over all recorded pings (mirrors query #4)."""
    pings = pings_of(data, bus_id)
    if not pings:
        return None
    return sum(p["speed_kmph"] for p in pings) / len(pings)


def _distance_km(lat1, lon1, lat2, lon2):
    """Great-circle distance in km (haversine)."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def nearest_stop(data, bus, ping):
    """Stop on the bus's route nearest to a position (used by query #5)."""
    best = None
    best_km = None
    for stop in stops_on_route(data, bus["route_id"]):
        d = _distance_km(ping["latitude"], ping["longitude"],
                         stop["latitude"], stop["longitude"])
        if best is None or d < best_km:
            best, best_km = stop, d
    return best, best_km


def bus_state(data, bus):
    """Everything the dashboard shows about one bus."""
    ping = latest_ping(data, bus["bus_id"])
    route = route_of(data, bus["route_id"])
    state = {
        "bus_id": bus["bus_id"],
        "bus_number": bus["bus_number"],
        "driver_name": bus["driver_name"],
        "route_id": bus["route_id"],
        "route_name": route["route_name"],
        "avg_speed_kmph": avg_speed(data, bus["bus_id"]),
        "history": pings_of(data, bus["bus_id"]),
        "latest": None,
    }
    if ping is not None:
        stop, stop_km = nearest_stop(data, bus, ping)
        state["latest"] = {
            "latitude": ping["latitude"],
            "longitude": ping["longitude"],
            "speed_kmph": ping["speed_kmph"],
            "logged_at": ping["logged_at"],
            "minutes_ago": ping["minutes_ago"],
            "active": ping["minutes_ago"] <= 15,  # mirrors query #3
            "nearest_stop": {
                "stop_id": stop["stop_id"],
                "stop_name": stop["stop_name"],
                "distance_km": round(stop_km, 2),
                "distance_from_start_km": stop["distance_from_start_km"],
            },
        }
    return state


def eta_to_stop(data, bus_id, stop_id):
    """ETA of a bus to a stop, mirroring SQL query #5.

    Snap the bus to the route stop nearest its latest ping, take the
    remaining route distance to the target stop, divide by the bus's
    average speed. Assumes constant speed; no traffic or dwell time.
    """
    bus = next((b for b in data["buses"] if b["bus_id"] == bus_id), None)
    if bus is None:
        return None, "unknown bus_id"
    target = next((s for s in stops_on_route(data, bus["route_id"])
                   if s["stop_id"] == stop_id), None)
    if target is None:
        return None, "stop_id is not on this bus's route"
    ping = latest_ping(data, bus_id)
    if ping is None:
        return None, "bus has no GPS pings"
    speed = avg_speed(data, bus_id)
    near, near_km = nearest_stop(data, bus, ping)
    remaining_km = round(target["distance_from_start_km"]
                         - near["distance_from_start_km"], 2)
    result = {
        "bus_id": bus_id,
        "bus_number": bus["bus_number"],
        "route_name": route_of(data, bus["route_id"])["route_name"],
        "stop_id": stop_id,
        "stop_name": target["stop_name"],
        "avg_speed_kmph": round(speed, 1),
        "snapped_to_stop": near["stop_name"],
        "remaining_km": max(remaining_km, 0.0),
        "assumption": "Bus is at its nearest stop and keeps its average "
                      "speed; traffic and dwell time are not modelled.",
    }
    if remaining_km <= 0:
        result["eta_minutes"] = 0.0
        result["status"] = "passed"
        result["message"] = ("The bus has already passed "
                             f"{target['stop_name']} (nearest stop: "
                             f"{near['stop_name']}).")
    elif not speed or speed <= 0:
        result["eta_minutes"] = None
        result["status"] = "unknown"
        result["message"] = "The bus has not moved yet, so no ETA can be computed."
    else:
        minutes = remaining_km / speed * 60
        result["eta_minutes"] = round(minutes, 1)
        result["status"] = "ok"
        result["message"] = (f"About {result['eta_minutes']} min to "
                             f"{target['stop_name']} ({remaining_km} km at "
                             f"avg {round(speed, 1)} km/h).")
    return result, None
