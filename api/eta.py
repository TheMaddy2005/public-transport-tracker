"""GET /api/eta?bus_id=<n>&stop_id=<n> - ETA of one bus to one stop.

Mirrors SQL query #5 from sql/03_queries.sql. Serverless function
(Python stdlib only, no dependencies).
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _core


def get_payload(query):
    try:
        bus_id = int(query.get("bus_id", [""])[0])
        stop_id = int(query.get("stop_id", [""])[0])
    except ValueError:
        return 400, {"error": "bus_id and stop_id must be integers"}
    data = _core.load_data()
    result, err = _core.eta_to_stop(data, bus_id, stop_id)
    if err:
        return 404, {"error": err}
    return 200, result


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        query = parse_qs(urlparse(self.path).query)
        status, payload = get_payload(query)
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
