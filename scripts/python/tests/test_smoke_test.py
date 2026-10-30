import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

LOCAL_DIR = Path(__file__).resolve().parents[1] / "local"
sys.path.insert(0, str(LOCAL_DIR))
import smoke_test  # noqa: E402


class _Handler(BaseHTTPRequestHandler):
    seen_host = None

    def do_GET(self):  # noqa: N802 - http.server API
        type(self).seen_host = self.headers.get("Host")
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def log_message(self, *args):
        pass


class SmokeRequestTests(unittest.TestCase):
    def setUp(self):
        self.server = HTTPServer(("127.0.0.1", 0), _Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"
        _Handler.seen_host = None

    def tearDown(self):
        smoke_test.HOST_HEADER = None
        self.server.shutdown()
        self.server.server_close()

    def test_host_header_is_sent_when_configured(self):
        smoke_test.HOST_HEADER = "shop.example.com"
        status, body = smoke_test.request(self.base, "/")
        self.assertEqual((status, body), (200, "OK"))
        self.assertEqual(_Handler.seen_host, "shop.example.com")

    def test_default_host_is_untouched(self):
        status, _ = smoke_test.request(self.base, "/")
        self.assertEqual(status, 200)
        self.assertTrue(_Handler.seen_host.startswith("127.0.0.1"))


if __name__ == "__main__":
    unittest.main()
