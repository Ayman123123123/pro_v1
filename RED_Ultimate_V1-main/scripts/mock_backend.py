#!/usr/bin/env python3
"""Minimal local mock backend for ci-build-all.sh (real stub, stdlib only).

Serves:
  GET /health        -> 200 {"status": "ok", "service": "red-mock-backend"}
  GET /api/*         -> 200 {"ok": true, ...} (generic placeholder payload)
  anything else      -> 404 JSON

Binds 127.0.0.1:${MOCK_PORT:-8080}. No third-party dependencies.
"""
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

HOST = "127.0.0.1"
PORT = int(os.environ.get("MOCK_PORT", "8080"))


class Handler(BaseHTTPRequestHandler):
    server_version = "REDMock/1.0"

    def _send_json(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802 (http.server convention)
        path = self.path.split("?", 1)[0]
        if path == "/health":
            self._send_json(200, {"status": "ok", "service": "red-mock-backend"})
        elif path.startswith("/api/"):
            self._send_json(200, {"ok": True, "path": path, "mock": True})
        else:
            self._send_json(404, {"ok": False, "error": "not found"})

    def log_message(self, fmt, *args):  # keep CI logs quiet
        pass


if __name__ == "__main__":
    HTTPServer((HOST, PORT), Handler).serve_forever()
