#!/usr/bin/env python3
"""SmartClass REST gateway: in-memory demo or Home Assistant device bridge."""

from __future__ import annotations

import argparse
import hmac
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen

LOCK = threading.RLock()
DEMO_DEVICES = {
    "light_front": True,
    "light_center": True,
    "light_back": False,
    "fan_main": False,
    "ac_main": False,
    "projector": True,
    "security_alarm": False,
}
DEMO_SAMPLE = 0
WEB_ROOT = Path(__file__).resolve().parents[1] / "platforms" / "web-shared"
STATIC_FILES = {
    "/": ("index.html", "text/html; charset=utf-8"),
    "/index.html": ("index.html", "text/html; charset=utf-8"),
    "/tv.css": ("tv.css", "text/css; charset=utf-8"),
    "/tv.js": ("tv.js", "application/javascript; charset=utf-8"),
    "/icon.png": ("icon.png", "image/png"),
}


def _to_bool(value: Any, true_values: list[str] | None = None) -> bool | None:
    if isinstance(value, bool):
        return value
    if value is None:
        return None
    text = str(value).strip().lower()
    if text in {"unknown", "unavailable", "none", ""}:
        return None
    return text in (true_values or ["on", "open", "true", "yes", "home", "playing"])


def _to_number(value: Any) -> float | int | None:
    if isinstance(value, bool) or value is None:
        return None
    try:
        number = float(str(value).strip())
        return int(number) if number.is_integer() else number
    except (TypeError, ValueError):
        return None


class DemoBackend:
    mode = "demo"

    def snapshot(self) -> dict[str, Any]:
        global DEMO_SAMPLE
        with LOCK:
            DEMO_SAMPLE += 1
            step = DEMO_SAMPLE
            devices = {key: {"state": "on" if value else "off", "connected": True} for key, value in DEMO_DEVICES.items()}
            return {
                "devices": devices,
                "sensors": {
                    "temperatureC": 32.4 if step % 10 == 0 else round(22.5 + (step % 8) * 0.7, 1),
                    "humidityPercent": 43 + step % 14,
                    "airQualityPpm": 570 + (step * 31 % 400),
                    "motionDetected": step % 2 == 0,
                    "doorOpen": step % 12 == 0,
                    "windowOpen": False,
                    "connected": {key: True for key in (
                        "sensor_temperature", "sensor_humidity", "sensor_air",
                        "sensor_motion", "sensor_door", "sensor_window",
                    )},
                },
            }

    def set_device(self, device_id: str, enabled: bool) -> None:
        if device_id not in DEMO_DEVICES:
            raise KeyError("unknown_device")
        with LOCK:
            DEMO_DEVICES[device_id] = enabled


class HomeAssistantBackend:
    mode = "home_assistant"

    def __init__(self, base_url: str, token: str, mapping: dict[str, Any], timeout: float = 5.0):
        parsed = urlparse(base_url)
        if parsed.scheme not in ("http", "https") or not parsed.netloc:
            raise ValueError("SMARTCLASS_HA_URL must be an http(s) URL")
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.mapping = mapping
        self.timeout = timeout
        self.classroom_id = str(mapping.get("classroom_id", "aula-204"))
        if not token:
            raise ValueError("SMARTCLASS_HA_TOKEN is required for physical mode")
        if not isinstance(mapping.get("devices"), dict) or not mapping["devices"]:
            raise ValueError("mapping must contain a non-empty devices object")

    def _request(self, path: str, payload: dict[str, Any] | None = None) -> Any:
        body = json.dumps(payload).encode("utf-8") if payload is not None else None
        request = Request(
            self.base_url + path,
            data=body,
            method="POST" if body is not None else "GET",
            headers={
                "Authorization": "Bearer " + self.token,
                "Accept": "application/json",
                "Content-Type": "application/json" if body is not None else "application/json",
            },
        )
        try:
            with urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                return json.loads(raw.decode("utf-8")) if raw else None
        except HTTPError as error:
            detail = error.read(512).decode("utf-8", errors="replace")
            raise RuntimeError(f"Home Assistant HTTP {error.code}: {detail}") from error
        except (URLError, TimeoutError) as error:
            raise RuntimeError(f"Home Assistant connection failed: {error}") from error

    @staticmethod
    def _connected(state: Any) -> bool:
        return str(state).lower() not in ("unknown", "unavailable", "none", "")

    def snapshot(self) -> dict[str, Any]:
        all_states = self._request("/api/states")
        if not isinstance(all_states, list):
            raise RuntimeError("Home Assistant returned an invalid state list")
        states_by_id = {item.get("entity_id"): item for item in all_states if isinstance(item, dict) and item.get("entity_id")}

        def state_for(entity_id: str) -> dict[str, Any]:
            state_obj = states_by_id.get(entity_id)
            if state_obj is None:
                raise KeyError(f"Home Assistant entity not found: {entity_id}")
            return state_obj

        devices: dict[str, Any] = {}
        for device_id, spec in self.mapping["devices"].items():
            try:
                state_obj = state_for(spec["entity_id"])
                raw_state = state_obj.get("state")
                active_values = spec.get("on_states", ["on"])
                is_connected = self._connected(raw_state)
                devices[device_id] = {"state": "on" if is_connected and str(raw_state).lower() in [str(v).lower() for v in active_values] else "off", "connected": is_connected}
            except Exception:
                devices[device_id] = {"state": "off", "connected": False}

        sensor_values: dict[str, Any] = {}
        sensor_connected: dict[str, bool] = {}
        for smartclass_key, spec in self.mapping.get("sensors", {}).items():
            entity_id = spec.get("entity_id", "")
            connected_key = spec.get("connected_id", "sensor_" + smartclass_key.removesuffix("C").removesuffix("Percent").removesuffix("Ppm").replace("Detected", "").replace("Open", ""))
            try:
                state_obj = state_for(entity_id)
                raw_value = state_obj.get("attributes", {}).get(spec["attribute"]) if spec.get("attribute") else state_obj.get("state")
                is_connected = self._connected(state_obj.get("state"))
                if smartclass_key in ("motionDetected", "doorOpen", "windowOpen"):
                    value = _to_bool(raw_value, spec.get("true_states"))
                else:
                    value = _to_number(raw_value)
                sensor_values[smartclass_key] = value
                sensor_connected[connected_key] = is_connected
            except Exception:
                sensor_values[smartclass_key] = None
                sensor_connected[connected_key] = False
        sensor_values["connected"] = sensor_connected
        return {"devices": devices, "sensors": sensor_values}

    def set_device(self, device_id: str, enabled: bool) -> None:
        spec = self.mapping["devices"].get(device_id)
        if not spec:
            raise KeyError("unknown_device")
        entity_id = spec.get("entity_id", "")
        if "." not in entity_id:
            raise ValueError(f"Invalid entity_id for {device_id}")
        service_key = "on_service" if enabled else "off_service"
        service = spec.get(service_key)
        if service:
            parts = service.split("/", 1)
            if len(parts) != 2 or not all(parts):
                raise ValueError(f"Invalid {service_key} for {device_id}; expected domain/service")
            domain, action = parts
        else:
            domain, action = entity_id.split(".", 1)[0], "turn_on" if enabled else "turn_off"
        data = dict(spec.get("on_data" if enabled else "off_data", {}))
        data.setdefault("entity_id", entity_id)
        self._request(f"/api/services/{quote(domain, safe='')}/{quote(action, safe='')}", data)


class GatewayHandler(BaseHTTPRequestHandler):
    server_version = "SmartClassGateway/2.0"
    backend: DemoBackend | HomeAssistantBackend = DemoBackend()
    client_token = ""
    classroom_id = "aula-204"

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"{self.address_string()} - {fmt % args}")

    def end_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def send_json(self, status: int, value: object) -> None:
        body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, name: str, content_type: str) -> None:
        try:
            body = (WEB_ROOT / name).read_bytes()
        except OSError:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self) -> bool:
        if not self.client_token:
            return True
        header = self.headers.get("Authorization", "")
        supplied = header[7:].strip() if header.lower().startswith("bearer ") else ""
        return hmac.compare_digest(supplied, self.client_token)

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self.end_headers()

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path in STATIC_FILES:
            filename, mime = STATIC_FILES[path]
            self.send_file(filename, mime)
            return
        if path == "/healthz":
            self.send_json(200, {"ok": True, "mode": self.backend.mode})
            return
        if not path.startswith("/api/v1/"):
            self.send_json(404, {"error": "route_not_found"})
            return
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        parts = [unquote(part) for part in path.strip("/").split("/")]
        if parts == ["api", "v1", "config"]:
            self.send_json(200, {"mode": self.backend.mode, "classroomId": self.classroom_id})
            return
        if len(parts) == 5 and parts[:3] == ["api", "v1", "classrooms"] and parts[4] == "state":
            if parts[3] != self.classroom_id:
                self.send_json(404, {"error": "unknown_classroom", "id": parts[3]})
                return
            try:
                self.send_json(200, self.backend.snapshot())
            except Exception as error:
                self.send_json(502, {"error": "gateway_read_failed", "message": str(error)[:400]})
            return
        self.send_json(404, {"error": "route_not_found"})

    def do_POST(self) -> None:
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized"})
            return
        parts = [unquote(part) for part in urlparse(self.path).path.strip("/").split("/")]
        if len(parts) != 7 or parts[:3] != ["api", "v1", "classrooms"] or parts[4] != "devices" or parts[6] != "command":
            self.send_json(404, {"error": "route_not_found"})
            return
        if parts[3] != self.classroom_id:
            self.send_json(404, {"error": "unknown_classroom", "id": parts[3]})
            return
        device_id = parts[5]
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
        try:
            self.backend.set_device(device_id, state == "on")
            self.send_json(200, {"accepted": True, "id": device_id, "state": state})
        except KeyError:
            self.send_json(404, {"error": "unknown_device", "id": device_id})
        except Exception as error:
            self.send_json(502, {"error": "device_command_failed", "message": str(error)[:400]})


def create_backend() -> HomeAssistantBackend | DemoBackend:
    ha_url = os.environ.get("SMARTCLASS_HA_URL", "").strip()
    ha_token = os.environ.get("SMARTCLASS_HA_TOKEN", "").strip()
    mapping_path = os.environ.get("SMARTCLASS_HA_MAPPING", "").strip()
    if not any((ha_url, ha_token, mapping_path)):
        return DemoBackend()
    if not all((ha_url, ha_token, mapping_path)):
        raise SystemExit("Physical mode needs SMARTCLASS_HA_URL, SMARTCLASS_HA_TOKEN and SMARTCLASS_HA_MAPPING.")
    try:
        mapping = json.loads(Path(mapping_path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"Cannot read Home Assistant mapping: {error}") from error
    return HomeAssistantBackend(ha_url, ha_token, mapping)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("SMARTCLASS_BIND", "127.0.0.1"), help="Use 0.0.0.0 only on a trusted LAN")
    parser.add_argument("--port", type=int, default=int(os.environ.get("SMARTCLASS_PORT", "8765")))
    args = parser.parse_args()
    backend = create_backend()
    GatewayHandler.backend = backend
    GatewayHandler.classroom_id = getattr(backend, "classroom_id", "aula-204")
    GatewayHandler.client_token = os.environ.get("SMARTCLASS_CLIENT_TOKEN", "").strip()
    if backend.mode == "home_assistant" and not GatewayHandler.client_token:
        raise SystemExit("Physical mode requires SMARTCLASS_CLIENT_TOKEN to protect control API calls.")
    server = ThreadingHTTPServer((args.host, args.port), GatewayHandler)
    print(f"SmartClass {backend.mode} gateway listening on http://{args.host}:{args.port}")
    if args.host == "127.0.0.1":
        print("Only this computer can connect. Use --host 0.0.0.0 for a trusted local network.")
    if backend.mode == "home_assistant":
        print("Real-device mode enabled; entity commands are sent to Home Assistant.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
