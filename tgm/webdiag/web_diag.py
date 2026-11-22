"""TGM: render the web tabs at a phone's CSS size (default 320 px wide, like a Note8 with large
display size) with the app's injected scripts, then save screenshots and layout facts.

Env: TGM_TEST_USER, TGM_TEST_APP_PASSWORD, TGM_SERVER, TGM_VIEW_W (320), TGM_VIEW_H (524),
TGM_OUT (web-diag).
"""
import base64
import json
import os
import re
import time

from playwright.sync_api import sync_playwright

SERVER = os.environ.get("TGM_SERVER", "https://chat.tgmdental.net").rstrip("/")
USER = os.environ["TGM_TEST_USER"].strip()
PASSWORD = os.environ["TGM_TEST_APP_PASSWORD"].strip()
W = int(os.environ.get("TGM_VIEW_W", "320"))
H = int(os.environ.get("TGM_VIEW_H", "524"))
OUT = os.environ.get("TGM_OUT", "web-diag")
KT = "app/src/main/java/com/nextcloud/talk/tgm/TgmWebActivity.kt"


def injected(name):
    src = open(KT, encoding="utf-8").read()
    m = re.search(name + r' = """(.*?)"""', src, re.S)
    return m.group(1)


SCRIPTS = [injected("SKIP_BROWSER_CHECK_JS"), injected("HIDE_WEB_SHELL_JS")]
CAL_BAR = injected("CALENDAR_BAR_JS")

DESCRIBE = """
(pts) => pts.map(([x, y]) => {
  const e = document.elementFromPoint(x, y);
  if (!e) return [x, y, null];
  const r = e.getBoundingClientRect(), cs = getComputedStyle(e);
  const chain = []; let p = e;
  for (let i = 0; p && i < 6; i++, p = p.parentElement) chain.push(p.tagName.toLowerCase() + (p.id ? '#' + p.id : '') + (p.className && typeof p.className === 'string' ? '.' + p.className.trim().split(/\\s+/).join('.') : ''));
  return {x, y, chain, rect: [r.left, r.top, r.width, r.height].map(Math.round), bg: cs.backgroundColor, border: cs.border, pos: cs.position, html: e.outerHTML.slice(0, 300)};
})
"""

SCROLLERS = """
() => [...document.querySelectorAll('*')].filter(e => e.scrollTop > 0 || (e.scrollHeight > e.clientHeight + 2 && /(auto|scroll)/.test(getComputedStyle(e).overflowY)))
  .slice(0, 15).map(e => ({el: e.tagName.toLowerCase() + '.' + (typeof e.className === 'string' ? e.className.trim().split(/\\s+/).join('.') : ''), scrollTop: e.scrollTop, sh: e.scrollHeight, ch: e.clientHeight, top: Math.round(e.getBoundingClientRect().top)}))
"""

BAR = """
() => [...document.querySelectorAll('#tgm-cal-bar, #tgm-cal-bar *')].map(e => [e.className || e.id, Math.round(e.getBoundingClientRect().width), e.scrollWidth, e.clientWidth, getComputedStyle(e).fontSize, e.textContent])
"""

SEARCH = """
() => [...document.querySelectorAll('input')].map(e => { const r = e.getBoundingClientRect(), cs = getComputedStyle(e);
  const chain = []; let p = e; for (let i = 0; p && i < 5; i++, p = p.parentElement) chain.push(p.tagName.toLowerCase() + '.' + (typeof p.className === 'string' ? p.className.trim().split(/\\s+/).join('.') : ''));
  return {ph: e.placeholder, rect: [r.left, r.top, r.width, r.height].map(Math.round), font: cs.fontSize, border: cs.border, bg: cs.backgroundColor, h: cs.height, chain}; })
"""


def main():
    os.makedirs(OUT, exist_ok=True)
    report = {"viewport": [W, H]}
    with sync_playwright() as p:
        browser = p.chromium.launch()
        report["chromium"] = browser.version
        ctx = browser.new_context(
            viewport={"width": W, "height": H}, device_scale_factor=3.375, is_mobile=True, has_touch=True,
            locale="vi-VN", timezone_id="Asia/Ho_Chi_Minh",
            user_agent="Mozilla/5.0 (Linux; Android 9; SM-N950F) AppleWebKit/537.36 (KHTML, like Gecko) "
                       "Chrome/101.0.4951.61 Mobile Safari/537.36",
        )
        for s in SCRIPTS:
            ctx.add_init_script(s)
        page = ctx.new_page()
        # every request carries basic auth (session-only login drops back to /login here)
        auth = base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()
        page.set_extra_http_headers({"Authorization": "Basic " + auth})
        page.goto(SERVER + "/apps/dashboard/", wait_until="domcontentloaded", timeout=90000)
        report["first_page"] = page.url
        timings = []
        page.on("requestfinished", lambda r: timings.append(
            (r.url[:160], round(r.timing["responseEnd"]), r.timing["startTime"])))

        for name, path in [("files", "/apps/files/"), ("calendar", "/apps/calendar/dayGridMonth/now"),
                           ("contacts", "/apps/contacts/")]:
            timings.clear()
            t0 = time.time()
            page.goto(SERVER + path, wait_until="domcontentloaded", timeout=90000)
            page.evaluate(SCRIPTS[1])
            if name == "calendar":
                page.evaluate(CAL_BAR)
            shots = []
            for t in (1.5, 4, 10):
                page.wait_for_timeout(int((t - (time.time() - t0)) * 1000) if t > time.time() - t0 else 0)
                f = f"{OUT}/{name}-{t}s.png"
                page.screenshot(path=f)
                shots.append(f)
            r = {"url": page.url, "load_s": round(time.time() - t0, 1)}
            r["slow_requests"] = sorted([t for t in timings if t[1] > 500], key=lambda t: -t[1])[:15]
            r["scrollers"] = page.evaluate(SCROLLERS)
            if name == "calendar":
                r["bar"] = page.evaluate(BAR)
                pts = [[x, y] for x in (4, 10, 16, 30) for y in (40, 46, 55, 65)] + [[160, 120], [160, 160]]
                r["points"] = page.evaluate(DESCRIBE, pts)
                r["stack"] = page.evaluate("""() => [...document.querySelectorAll('body *')].filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.left <= 5 && r.right >= 5 && r.top <= 60 && r.bottom >= 60; })
                  .map(e => { const r = e.getBoundingClientRect(), cs = getComputedStyle(e); return [e.tagName.toLowerCase() + '#' + e.id + '.' + (typeof e.className === 'string' ? e.className : ''), [r.left, r.top, r.width, r.height].map(Math.round), cs.position, cs.zIndex, cs.backgroundColor, cs.pointerEvents, e.textContent.trim().slice(0, 30)]; })""")
                r["rows"] = page.evaluate("""() => { const f = document.querySelector('.fc-daygrid-day-frame'), td = f && f.parentElement, t = document.querySelector('.fc-daygrid-body table');
                  return f && {frameMinH: getComputedStyle(f).minHeight, frameH: f.getBoundingClientRect().height, tdStyle: td.getAttribute('style'), tableStyle: t && t.getAttribute('style'), bodyStyle: document.querySelector('.fc-daygrid-body').getAttribute('style'), rows: document.querySelectorAll('.fc-daygrid-body tr').length,
                    eventsH: [...f.children].map(c => c.className + ':' + Math.round(c.getBoundingClientRect().height) + ':' + getComputedStyle(c).minHeight)}; }""")
                r["fc"] = page.evaluate("""() => ['.fc-scrollgrid', '.fc-col-header', '.fc-daygrid-body', '.fc-scroller', '.fc-view-harness', '.app-content', '#app-navigation-vue', '.app-navigation-toggle', '.new-event']
                  .map(s => { const e = document.querySelector(s); if (!e) return [s, null]; const r = e.getBoundingClientRect(); return [s, [r.left, r.top, r.width, r.height].map(Math.round), getComputedStyle(e).height]; })""")
            if name == "contacts":
                r["inputs"] = page.evaluate(SEARCH)[-2:]
                r["header"] = page.evaluate("""() => { const h = document.querySelector('.contacts-list__header'); if (!h) return null; return [...h.querySelectorAll('*')].slice(0, 25).map(e => { const r = e.getBoundingClientRect(), cs = getComputedStyle(e); return [e.tagName.toLowerCase() + '.' + (typeof e.className === 'string' ? e.className : ''), [r.left, r.top, r.width, r.height].map(Math.round), cs.display, cs.position, cs.height, cs.border]; }); }""")
                r["points"] = page.evaluate(DESCRIBE, [[60, 10], [60, 20], [20, 30], [160, 10]])
            report[name] = r
        browser.close()
    with open(f"{OUT}/report.json", "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=1)
    print(json.dumps(report, ensure_ascii=False, indent=1)[:20000])


if __name__ == "__main__":
    main()
