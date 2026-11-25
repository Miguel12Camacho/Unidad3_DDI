#!/usr/bin/env python3
"""Disposable in-memory SmartClass gateway for emulator demonstrations only."""

from __future__ import annotations

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

LOCK = threading.Lock()
DEVICES = {
    "light_front": True,
    "light_center": True,
    "light_back": False,
    "fan_main": False,
    "ac_main": False,
    "projector": True,
    "security_alarm": False,
}
SAMPLE = 0


class GatewayHandler(BaseHTTPRequestHandler):
    server_version = "SmartClassDemoGateway/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"{self.address_string()} - {fmt % args}")

    def send_json(self, status: int, value: object) -> None:
        body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def snapshot(self) -> dict[str, object]:
        global SAMPLE
        SAMPLE += 1
        device_state = {key: {"state": "on" if value else "off", "connected": True} for key, value in DEVICES.items()}
        sensors = {
            "temperatureC": 32.4 if SAMPLE % 10 == 0 else round(22.5 + (SAMPLE % 8) * 0.7, 1),
            "humidityPercent": 43 + SAMPLE % 14,
            "airQualityPpm": 570 + (SAMPLE * 31 % 400),
            "motionDetected": SAMPLE % 2 == 0,
            "doorOpen": SAMPLE % 12 == 0,
            "windowOpen": False,
            "connected": {
                "sensor_temperature": True,
                "sensor_humidity": True,
                "sensor_air": True,
                "sensor_motion": True,
                "sensor_door": True,
                "sensor_window": True,
            },
        }
        return {"devices": device_state, "sensors": sensors}

    def do_GET(self) -> None:
        parts = [unquote(part) for part in urlparse(self.path).path.strip("/").split("/")]
        if len(parts) == 5 and parts[:3] == ["api", "v1", "classrooms"] and parts[4] == "state":
            with LOCK:
                self.send_json(200, self.snapshot())
            return
        self.send_json(404, {"error": "route_not_found"})

    def do_POST(self) -> None:
        parts = [unquote(part) for part in urlparse(self.path).path.strip("/").split("/")]
        if len(parts) != 7 or parts[:3] != ["api", "v1", "classrooms"] or parts[4] != "devices" or parts[6] != "command":
            self.send_json(404, {"error": "route_not_found"})
            return
        device_id = parts[5]
        if device_id not in DEVICES:
            self.send_json(404, {"error": "unknown_device", "id": device_id})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            command = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_json"})
            return
        state = command.get("state") if isinstance(command, dict) else None
        if state not in ("on", "off"):
            self.send_json(400, {"error": "state_must_be_on_or_off"})
            return
        with LOCK:
            DEVICES[device_id] = state == "on"
        self.send_json(200, {"accepted": True, "id": device_id, "state": state})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1", help="Use loopback for a local-only demonstration")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), GatewayHandler)
    print(f"Demo gateway listening on http://{args.host}:{args.port}")
    print("Emulator URL: http://10.0.2.2:%d · Ctrl+C stops the in-memory demo." % args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
