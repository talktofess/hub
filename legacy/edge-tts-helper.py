#!/usr/bin/env python3
"""
Edge TTS helper for the TikTok composer HTML files.

Proxies Microsoft Edge's neural voices (the same ones in the Edge browser's
Read Aloud feature) to a local HTTP endpoint that the HTML files can call.

Install once:
    pip install edge-tts

Run:
    python edge-tts-helper.py

Then in any of the HTML files: Audio tab > toggle "Use Edge TTS (local helper)".
The status should turn green.

Stop with Ctrl+C.

Recommended child voices for English:
    en-US-AnaNeural    girl, ~10y, US
    en-GB-MaisieNeural girl, ~12y, UK
    en-US-DavisNeural  younger male, US
"""

import asyncio
import json
import socket
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

try:
    import edge_tts
except ImportError:
    print("Missing dependency. Install with:")
    print("  pip install edge-tts")
    sys.exit(1)

PORT = 5005
BIND = "0.0.0.0"  # listen on all interfaces so phones on same WiFi can connect


def get_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return  # quiet

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/ping":
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"edge-tts-helper online")
            return

        if parsed.path == "/voices":
            try:
                voices = asyncio.run(edge_tts.list_voices())
            except Exception as e:
                self.send_response(500)
                self._cors()
                self.end_headers()
                self.wfile.write(f"voices error: {e}".encode())
                return
            payload = json.dumps(voices).encode("utf-8")
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        self.send_response(404)
        self._cors()
        self.end_headers()

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length).decode("utf-8")
            body = json.loads(raw) if raw else {}
            text = (body.get("text") or "").strip()
            voice = body.get("voice") or "en-US-AnaNeural"
            rate = body.get("rate") or "+0%"
            pitch = body.get("pitch") or "+0Hz"
            volume = body.get("volume") or "+0%"
            if not text:
                self.send_response(400)
                self._cors()
                self.end_headers()
                self.wfile.write(b"empty text")
                return

            async def gen():
                buf = bytearray()
                comm = edge_tts.Communicate(
                    text, voice, rate=rate, pitch=pitch, volume=volume
                )
                async for chunk in comm.stream():
                    if chunk["type"] == "audio":
                        buf.extend(chunk["data"])
                return bytes(buf)

            audio = asyncio.run(gen())
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "audio/mpeg")
            self.send_header("Content-Length", str(len(audio)))
            self.end_headers()
            self.wfile.write(audio)
        except Exception as e:
            self.send_response(500)
            self._cors()
            self.end_headers()
            self.wfile.write(f"tts error: {e}".encode())


def main():
    lan = get_lan_ip()
    print(f"Edge TTS helper listening on:")
    print(f"  this PC:        http://localhost:{PORT}")
    print(f"  same-WiFi LAN:  http://{lan}:{PORT}     <- use this URL in your phone")
    print()
    print("Stop with Ctrl+C")
    print()
    print("Recommended kid voices:")
    print("  en-US-AnaNeural    (girl, ~10y, US)")
    print("  en-GB-MaisieNeural (girl, ~12y, UK)")
    print("  en-US-DavisNeural  (younger male, US)")
    print()
    print("If a Windows firewall popup appears, allow Python on Private networks.")
    print()
    try:
        HTTPServer((BIND, PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
