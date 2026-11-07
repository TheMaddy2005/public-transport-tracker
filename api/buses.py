"""GET /api/buses - current state of every bus, plus routes, stops and history.

Serverless function (Python stdlib only, no dependencies). Data comes from
api/data.json, the demo snapshot of the MySQL sample dataset.
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _core


def get_payload():
    data = _core.load_data()
    routes = []
    for route in data["routes"]:
        r = dict(route)
        r["stops"] = _core.stops_on_route(data, route["route_id"])
        routes.append(r)
    return {
        "city": "Rampur (fictional demo city)",
        "generated_at": data["generated_at"],
        "routes": routes,
        "buses": [_core.bus_state(data, b) for b in data["buses"]],
        "note": "Demo data: simulated GPS pings, identical to "
                "sql/02_sample_data.sql. Ping times are generated relative "
                "to 'now' so the demo always looks live.",
    }


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(get_payload()).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
