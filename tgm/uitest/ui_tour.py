#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""TGM Chat UI tour on an emulator: log in with a test account, open every tab,
time how long each takes to show content, and save screenshots.

Env: TGM_TEST_USER, TGM_TEST_APP_PASSWORD, TGM_SERVER (default https://chat.tgmdental.net),
OUT (default ui-shots). Needs adb on PATH and the debug APK installed.
"""
import os
import re
import subprocess
import sys
import time
import urllib.parse
import xml.etree.ElementTree as ET

PKG = "net.tgmdental.chat"
OUT = os.environ.get("OUT", "ui-shots")
SERVER = os.environ.get("TGM_SERVER", "https://chat.tgmdental.net")
TABS = ["Danh bạ", "Lịch", "Tệp tin", "Tin nhắn", "Lịch"]
report = []


def adb(*args, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"adb {args[:3]} failed ({r.returncode}): {r.stdout.strip()} {r.stderr.strip()}")
    return r.stdout


def shot(name):
    path = os.path.join(OUT, f"{len(os.listdir(OUT)):02d}-{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout)
    print("screenshot", path)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
    try:
        return list(ET.fromstring(xml[xml.find("<"):]).iter("node"))
    except ET.ParseError:
        return []


def find(text, exact=True):
    for n in nodes():
        t = n.get("text", "") or n.get("content-desc", "")
        if (t == text) if exact else (text in t):
            return n
    return None


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


POPUPS = ("Wait", "Bỏ qua", "Để sau", "Allow", "Cho phép", "Dismiss")


def dismiss_popups():
    """Tap away system ANR dialogs and first-run prompts that cover the app."""
    for n in nodes():
        if (n.get("text") or "") in POPUPS:
            tap(n)
            time.sleep(1)
            return True
    return False


NAV_LABELS = {"Tin nhắn", "Danh bạ", "Lịch", "Tệp tin", "Cài đặt"}


def web_texts():
    """Texts shown by the web page (inside a WebView), not the app's own bottom bar."""
    out, stack = [], []
    for n in nodes():
        if n.get("class") == "android.webkit.WebView":
            stack = list(n.iter("node"))
            break
    for n in stack:
        t = (n.get("text") or n.get("content-desc") or "").strip()
        if t and t not in NAV_LABELS:
            out.append(t)
    return out


def wait_for(pred, timeout=60):
    start = time.time()
    while time.time() - start < timeout:
        if pred():
            return time.time() - start
        time.sleep(0.5)
    return None


def check_credentials(user, password):
    """HTTP status of the user endpoint with the test account, plus the login id the server returns."""
    import base64
    import json
    import urllib.request
    req = urllib.request.Request(
        f"{SERVER}/ocs/v2.php/cloud/user?format=json",
        headers={
            "OCS-APIRequest": "true",
            "Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode(),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            data = json.load(r)["ocs"]["data"]
            return f"HTTP {r.status}, id {data.get('id')}, admin={'admin' in data.get('groups', [])}"
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code} (user name or app password not accepted)"
    except Exception as e:  # noqa: BLE001
        return f"failed: {type(e).__name__}"


def main():
    os.makedirs(OUT, exist_ok=True)
    # the CI emulator is slow; keep "app isn't responding" dialogs from covering the screen
    adb("shell", "settings", "put", "global", "hide_error_dialogs", "1", check=False)
    user, password = os.environ["TGM_TEST_USER"].strip(), os.environ["TGM_TEST_APP_PASSWORD"].strip()
    q = urllib.parse.quote
    report.append("credential check: " + check_credentials(user, password))
    uri = f"nc://login/user:{q(user)}&password:{q(password)}&server:{q(SERVER)}"
    adb("shell", "am", "start", "-n", f"{PKG}/com.nextcloud.talk.tgm.TgmTestLogin", "--es", "KEY_FROM_QR", f"'{uri}'")
    # quoted: adb shell joins args into one device shell line, where "&" would split it
    time.sleep(8)
    shot("dang-xac-minh")
    time.sleep(3)
    shot("sau-xac-minh")
    t = wait_for(lambda: (dismiss_popups() and False) or find("Tin nhắn") is not None, 180)
    report.append(f"login to conversation list: {t if t is None else round(t, 1)} s")
    if t is None:
        # keep the app log for diagnosis, with the secrets masked
        log = adb("logcat", "-d", "-v", "time", check=False)
        for secret in {user, password, q(user), q(password)}:
            log = log.replace(secret, "***")
        keep = [l for l in log.splitlines() if re.search(r"tgm|talk|login|account|verif|okhttp|ssl|http", l, re.I)]
        with open(os.path.join(OUT, "logcat.txt"), "w") as f:
            f.write("\n".join(keep[-1500:]) + "\n")
    time.sleep(3)
    # dismiss permission dialogs if any
    while dismiss_popups():
        pass
    shot("tin-nhan")
    for tab in TABS:
        n = find(tab)
        if n is None:
            report.append(f"{tab}: tab not found")
            continue
        dismiss_popups()
        tap(n)
        # content shown = web view has some text nodes or the list is back
        t = wait_for(lambda: len(web_texts()) > 3, 90)
        report.append(f"{tab}: {t if t is None else round(t, 1)} s")
        time.sleep(2)
        shot(tab.replace(" ", "-"))
    # Lịch: the app's month bar, press "next"
    n = find("Lịch")
    if n is not None:
        tap(n)
        time.sleep(2)
        nxt = find("Sau")
        report.append("Lịch month bar: " + ("found" if nxt is not None else "NOT FOUND"))
        if nxt is not None:
            tap(nxt)
            time.sleep(4)
            shot("Lịch-thang-sau")
    n = find("Cài đặt")
    if n is not None:
        tap(n)
        time.sleep(4)
        shot("cai-dat")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    # second look at Danh bạ after a long wait: is it slow or stuck?
    n = find("Danh bạ")
    if n is not None:
        tap(n)
        time.sleep(30)
        shot("Danh-bạ-sau-30s")
        report.append(f"Danh bạ after 30 s: {len(web_texts())} web texts")
    # web page console output (errors from Contacts, Files, Calendar)
    log = adb("logcat", "-d", "-v", "time", "-s", "chromium:*", "Console:*", "TgmWeb:*", check=False)
    with open(os.path.join(OUT, "web-console.txt"), "w") as f:
        f.write(log)
    crash = adb("logcat", "-d", "-b", "crash", check=False)
    report.append("crash log: " + ("none" if PKG not in crash else "FOUND"))
    with open(os.path.join(OUT, "report.txt"), "w") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))
    return 1 if PKG in crash else 0


if __name__ == "__main__":
    sys.exit(main())
