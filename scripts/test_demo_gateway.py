"""Local unit tests for the SmartClass demo and Home Assistant adapters."""

import json
import sys
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import demo_gateway


class FakeResponse:
    def __init__(self, payload):
        self.body = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self.body


class GatewayTests(unittest.TestCase):
    def test_demo_snapshot_varies_and_device_command_changes_state(self):
        backend = demo_gateway.DemoBackend()
        before = backend.snapshot()
        first_temperature = before["sensors"]["temperatureC"]
        backend.set_device("projector", False)
        after = backend.snapshot()
        self.assertFalse(demo_gateway.DEMO_DEVICES["projector"])
        self.assertNotEqual(first_temperature, after["sensors"]["temperatureC"])
        with self.assertRaises(KeyError):
            backend.set_device("not-a-device", True)

    def test_home_assistant_maps_states_and_calls_configured_service(self):
        mapping = {
            "classroom_id": "aula-test",
            "devices": {
                "light_front": {
                    "entity_id": "light.front",
                    "on_service": "light/turn_on",
                    "off_service": "light/turn_off",
                }
            },
            "sensors": {
                "temperatureC": {"entity_id": "sensor.temperature", "connected_id": "sensor_temperature"},
                "doorOpen": {"entity_id": "binary_sensor.door", "connected_id": "sensor_door", "true_states": ["on"]},
            },
        }
        backend = demo_gateway.HomeAssistantBackend("http://ha.local:8123", "ha-secret", mapping)
        requests = []

        def fake_urlopen(request, timeout):
            requests.append(request)
            if request.full_url.endswith("/api/states"):
                return FakeResponse([
                    {"entity_id": "light.front", "state": "on", "attributes": {}},
                    {"entity_id": "sensor.temperature", "state": "23.7", "attributes": {}},
                    {"entity_id": "binary_sensor.door", "state": "on", "attributes": {}},
                ])
            return FakeResponse([])

        with patch.object(demo_gateway, "urlopen", side_effect=fake_urlopen):
            snapshot = backend.snapshot()
            backend.set_device("light_front", False)

        self.assertEqual("on", snapshot["devices"]["light_front"]["state"])
        self.assertEqual(23.7, snapshot["sensors"]["temperatureC"])
        self.assertTrue(snapshot["sensors"]["doorOpen"])
        self.assertTrue(snapshot["sensors"]["connected"]["sensor_door"])
        command = requests[-1]
        self.assertTrue(command.full_url.endswith("/api/services/light/turn_off"))
        self.assertEqual("Bearer ha-secret", command.get_header("Authorization"))
        self.assertEqual({"entity_id": "light.front"}, json.loads(command.data.decode("utf-8")))

    def test_http_api_requires_client_token_and_serves_tv_panel(self):
        old_backend = demo_gateway.GatewayHandler.backend
        old_token = demo_gateway.GatewayHandler.client_token
        demo_gateway.GatewayHandler.backend = demo_gateway.DemoBackend()
        demo_gateway.GatewayHandler.client_token = "tv-test-token"
        server = ThreadingHTTPServer(("127.0.0.1", 0), demo_gateway.GatewayHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = "http://127.0.0.1:%s" % server.server_port
        try:
            with urlopen(base_url + "/") as response:
                self.assertEqual(200, response.status)
                self.assertIn(b"SmartClass TV", response.read())
            with self.assertRaises(HTTPError) as response:
                urlopen(base_url + "/api/v1/config")
            self.assertEqual(401, response.exception.code)
            request = Request(base_url + "/api/v1/config", headers={"Authorization": "Bearer tv-test-token"})
            with urlopen(request) as response:
                self.assertEqual("demo", json.loads(response.read())["mode"])
        finally:
            server.shutdown()
            server.server_close()
            demo_gateway.GatewayHandler.backend = old_backend
            demo_gateway.GatewayHandler.client_token = old_token

    def test_value_converters_handle_unavailable_entities(self):
        self.assertEqual(23.5, demo_gateway._to_number("23.5"))
        self.assertIsNone(demo_gateway._to_number("unavailable"))
        self.assertTrue(demo_gateway._to_bool("open", ["open"]))
        self.assertIsNone(demo_gateway._to_bool("unknown"))


if __name__ == "__main__":
    unittest.main()
