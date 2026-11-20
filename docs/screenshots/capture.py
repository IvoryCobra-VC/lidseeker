#!/usr/bin/env python3
"""Capture the README screenshots from demo_server.py with headless Chromium.

    python3 docs/screenshots/demo_server.py backend/web/dist 5199 &
    CHROME=/path/to/chrome python3 docs/screenshots/capture.py http://127.0.0.1:5199

Needs the ``websocket-client`` package. Writes discover/search/artist/requests.png
next to this file (430x920 viewport at 2x = 860x1840, matching the others).
"""
import base64, json, os, subprocess, sys, time, urllib.request
import websocket

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:5199"
OUT = os.path.dirname(os.path.abspath(__file__))
CHROME = os.environ.get("CHROME", "chromium")
PORT = 9334

proc = subprocess.Popen([CHROME, "--headless=new", "--no-sandbox", "--hide-scrollbars", f"--remote-debugging-port={PORT}",
                         "--remote-allow-origins=*", "about:blank"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(100):
    try:
        tabs = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json")); break
    except Exception:
        time.sleep(.1)
ws = websocket.create_connection([t for t in tabs if t["type"] == "page"][0]["webSocketDebuggerUrl"], max_size=None)
n = 0
def cdp(method, **params):
    global n; n += 1; ws.send(json.dumps({"id": n, "method": method, "params": params}))
    while True:
        m = json.loads(ws.recv())
        if m.get("id") == n:
            return m.get("result", {})
def js(expr):
    return cdp("Runtime.evaluate", expression=expr, returnByValue=True, awaitPromise=True).get("result", {}).get("value")
def go(path, settle=1.5):
    cdp("Page.navigate", url=BASE + path); time.sleep(settle)
def shot(name):
    js("document.fonts.ready.then(() => new Promise(r => setTimeout(r, 400)))")
    data = cdp("Page.captureScreenshot", format="png")["data"]
    open(os.path.join(OUT, name), "wb").write(base64.b64decode(data)); print("wrote", name)

try:
    cdp("Emulation.setDeviceMetricsOverride", width=430, height=920, deviceScaleFactor=2, mobile=True)
    cdp("Emulation.setEmulatedMedia", features=[{"name": "prefers-color-scheme", "value": "dark"}])
    go("/login", 1)
    js("localStorage.setItem('lidseeker_token', 'demo')")

    go("/?decade=1990"); shot("discover.png")

    go("/search")
    js("""(() => { const i = document.querySelector('input'); const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        set.call(i, 'lumen drive'); i.dispatchEvent(new Event('input', { bubbles: true }));
        (i.form ? i.form.requestSubmit() : [...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Search').click()); })()""")
    time.sleep(1.2); js("document.activeElement && document.activeElement.blur()"); shot("search.png")

    go("/artist/lumen-drive?name=Lumen%20Drive"); shot("artist.png")

    go("/requests")
    js("""(() => { const el = [...document.querySelectorAll('*')].filter(e => e.children.length === 0 && e.textContent.trim() === 'Paper Satellites')[0];
        (el.closest('button, [role=button], .row, .card, li') || el).click(); })()""")
    time.sleep(.8); shot("requests.png")
finally:
    ws.close(); proc.terminate()
