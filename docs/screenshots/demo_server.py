#!/usr/bin/env python3
"""Demo backend for README screenshots.

Serves a built web UI (``backend/web/dist``) plus a fake ``/api`` filled with
invented artists and albums and procedurally generated SVG cover art, so the
screenshots never show real artwork or real artist names.

    python3 docs/screenshots/demo_server.py path/to/web/dist [port]

Then run ``capture.py`` against it. No Lidarr, network or database needed.
"""
import hashlib
import json
import math
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

DIST = os.path.realpath(sys.argv[1] if len(sys.argv) > 1 else "backend/web/dist")
PORT = int(sys.argv[2] if len(sys.argv) > 2 else 5199)

# ---------------------------------------------------------------- fake catalogue
ARTISTS = {
    "lumen-drive": ("Lumen Drive", [
        ("Night Bus Hymns", "Album", 2023, True), ("Afterglow FM", "Album", 2021, True),
        ("Paper Satellites", "Album", 2018, False), ("Low Orbit", "Album", 2015, True),
        ("Glasshouse Summer", "Album", 2012, True), ("Signal Bloom", "Album", 2010, True),
        ("Tape Weather", "Album", 2007, True), ("Kite Season", "Album", 2004, True),
        ("Lanterns", "EP", 2022, False), ("Soft Static", "Single", 2024, False),
    ]),
}
DECADE_1990 = [
    ("Sunburn Theory", "Coral Static", 1994), ("Velvet Underpass", "The Hollow Kings", 1997),
    ("Midnight Mile", "Orla Finch", 1991), ("Mercury Tide", "Ivy Crane", 1995),
    ("Chrome Orchard", "Glass Parade", 1999), ("Paper Moons", "Dusk Avenue", 1993),
    ("Radio Saints", "Kid Ozone", 1998), ("Harbour Lights", "Ada Monroe", 1996),
    ("Cold Brass", "Iron Meadow", 1992), ("Wire & Wool", "Fern Theory", 1990),
]
NEW = [
    ("Neon Liturgy", "Coral Static", 2026), ("Holiday Rain", "Pale Atlas", 2026),
    ("Hologram Heart", "Static Doves", 2026), ("Slow Comets", "Orla Finch", 2026),
    ("Rooftop Theory", "Kid Ozone", 2025), ("Blue Hour Club", "Saint Marlowe", 2025),
    ("Motorway Prayers", "The Lantern Rooms", 2025), ("Sugar Radio", "Polly Kestrel", 2025),
]

def fid(*parts):
    return hashlib.md5("|".join(parts).encode()).hexdigest()[:12]

def album(title, artist, year, kind="Album", in_lib=False, requested=False):
    return {"type": "album", "foreignId": fid(artist, title), "title": title, "artist": artist,
            "year": year, "albumType": kind, "imageUrl": f"/covers/{fid(artist, title)}.svg",
            "inLibrary": in_lib, "requested": requested, "artistForeignId": "lumen-drive" if artist == "Lumen Drive" else fid(artist)}

def artist_albums(slug):
    name, rows = ARTISTS.get(slug, ARTISTS["lumen-drive"])
    return [album(t, name, y, k, lib) for t, k, y, lib in rows]

def pipeline(stage, percent, detail, files=0, count=0):
    stages = ["requested", "searching", "downloading", "importing", "available"]
    return {"stage": stage, "stageIndex": stages.index(stage), "stages": stages, "percent": percent,
            "trackFiles": files, "trackCount": count, "detail": detail, "failed": False, "stuck": False}

REQUESTS = [
    {"id": 3, "type": "album", "foreignId": fid("Lumen Drive", "Paper Satellites"), "title": "Paper Satellites",
     "artist": "Lumen Drive", "imageUrl": f"/covers/{fid('Lumen Drive', 'Paper Satellites')}.svg", "status": "downloading",
     "createdAt": "2026-11-20T09:12:00Z", "pipeline": pipeline("downloading", 64, "Downloading 7 of 11 tracks…", 7, 11)},
    {"id": 2, "type": "album", "foreignId": fid("Coral Static", "Sunburn Theory"), "title": "Sunburn Theory",
     "artist": "Coral Static", "imageUrl": f"/covers/{fid('Coral Static', 'Sunburn Theory')}.svg", "status": "available",
     "createdAt": "2026-11-19T18:40:00Z", "pipeline": pipeline("available", 100, "In your library.", 12, 12)},
    {"id": 1, "type": "album", "foreignId": fid("Cassette Ghosts", "Tape Weather"), "title": "Tape Weather",
     "artist": "Cassette Ghosts", "imageUrl": f"/covers/{fid('Cassette Ghosts', 'Tape Weather')}.svg", "status": "available",
     "createdAt": "2026-11-18T21:05:00Z", "pipeline": pipeline("available", 100, "In your library.", 10, 10)},
]

# ---------------------------------------------------------------- generated covers
PALETTES = [("#ff6b6b", "#ffd93d", "#2b2d42"), ("#7bdff2", "#2a3b8f", "#f2f7ff"), ("#f72585", "#7209b7", "#4cc9f0"),
            ("#06d6a0", "#118ab2", "#073b4c"), ("#ff9f1c", "#2ec4b6", "#011627"), ("#e63946", "#1d3557", "#f1faee"),
            ("#cdb4db", "#ffafcc", "#3d2c4f"), ("#ffbe0b", "#fb5607", "#3a0ca3"), ("#8338ec", "#3a86ff", "#ffbe0b"),
            ("#00f5d4", "#9b5de5", "#f15bb5"), ("#f4a261", "#264653", "#e9c46a"), ("#a2d2ff", "#023047", "#fb8500")]

def cover_svg(key):
    h = int(hashlib.md5(key.encode()).hexdigest(), 16)
    a, b, c = PALETTES[int(hashlib.sha1(key.encode()).hexdigest(), 16) % len(PALETTES)]
    style = int(hashlib.sha256(key.encode()).hexdigest(), 16) % 5
    r = lambda n: ((h >> (n * 3)) % 1000) / 1000
    body = ""
    if style == 0:  # sunset
        body = f'<circle cx="150" cy="170" r="80" fill="{c}"/>' + "".join(
            f'<rect x="0" y="{170 + i * 14}" width="300" height="{7 - i * .7:.1f}" fill="{b}"/>' for i in range(8))
    elif style == 1:  # rings
        body = "".join(f'<circle cx="{80 + r(1) * 140:.0f}" cy="{80 + r(2) * 140:.0f}" r="{k * 26}" fill="{c if k % 2 else a}"/>'
                       for k in range(10, 0, -1))
    elif style == 2:  # dots
        body = "".join(f'<circle cx="{19 + x * 37}" cy="{19 + y * 37}" r="{4 + 11 * abs(math.sin(x * .7 + y * .5 + h % 7)):.1f}" fill="{c}"/>'
                       for x in range(8) for y in range(8))
    elif style == 3:  # stripes
        body = f'<g transform="rotate({int(r(3) * 180)} 150 150)">' + "".join(
            f'<rect x="{k * 34}" y="-150" width="17" height="600" fill="{c}"/>' for k in range(-6, 12)) + "</g>"
    else:  # waves
        body = "".join('<path d="M0 {0} '.format(30 + k * 24) + " ".join(
            f"L{x} {30 + k * 24 + 14 * math.sin(x * .03 + k * .6 + h % 5):.1f}" for x in range(0, 301, 10))
            + f'" stroke="{c}" stroke-width="5" fill="none"/>' for k in range(12))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>'
            f'<clipPath id="k"><rect width="300" height="300"/></clipPath></defs>'
            f'<g clip-path="url(#k)"><rect width="300" height="300" fill="url(#g)"/>{body}</g></svg>')

# ---------------------------------------------------------------- server
class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=DIST, **kw)

    def log_message(self, *a):
        pass

    def send_json(self, obj, code=200):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def api(self, method):
        u = urlparse(self.path)
        p, q = u.path[4:], {k: v[0] for k, v in parse_qs(u.query).items()}
        if p == "/auth/login":
            return self.send_json({"token": "demo"})
        if p == "/me":
            return self.send_json({"username": "demo", "role": "user"})
        if p == "/discover/categories":
            return self.send_json({"genres": [], "decades": [2020, 2010, 2000, 1990, 1980, 1970]})
        if p == "/discover":
            rows = DECADE_1990 if q.get("decade") == "1990" else NEW
            return self.send_json([album(t, a, y) for t, a, y in rows])
        if p == "/search":
            return self.send_json(artist_albums("lumen-drive")[:8])
        if p.startswith("/artist/") and p.endswith("/albums"):
            return self.send_json(artist_albums(unquote(p.split("/")[2])))
        if p == "/requests":
            return self.send_json(REQUESTS)
        if p == "/stats":
            return self.send_json({"total": 3, "available": 2, "pending": 0, "downloading": 1, "failed": 0})
        if p == "/services":
            return self.send_json([])
        if p == "/settings":
            return self.send_json({"quality": None, "ntfyTopic": None, "ntfyUrl": None})
        if method != "GET":
            return self.send_json({"ok": True})
        return self.send_json([])

    def do_GET(self):
        path = urlparse(self.path).path
        if path.startswith("/api/"):
            return self.api("GET")
        if path.startswith("/covers/"):
            data = cover_svg(path).encode()
            self.send_response(200)
            self.send_header("Content-Type", "image/svg+xml")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            return self.wfile.write(data)
        target = os.path.realpath(os.path.join(DIST, path.lstrip("/")))
        if not target.startswith(DIST + os.sep) or not os.path.isfile(target):
            self.path = "/index.html"  # SPA fallback; never serve outside DIST
        return super().do_GET()

    def do_POST(self):
        return self.api("POST")

    do_PUT = do_DELETE = do_POST


if __name__ == "__main__":
    print(f"demo server on http://127.0.0.1:{PORT}  (dist: {DIST})", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
