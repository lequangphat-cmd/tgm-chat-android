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


def wait_for(pred, timeout=60):
    start = time.time()
    while time.time() - start < timeout:
        if pred():
            return time.time() - start
        time.sleep(0.5)
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    user, password = os.environ["TGM_TEST_USER"], os.environ["TGM_TEST_APP_PASSWORD"]
    q = urllib.parse.quote
    uri = f"nc://login/user:{q(user)}&password:{q(password)}&server:{q(SERVER)}"
    adb("shell", "am", "start", "-n", f"{PKG}/com.nextcloud.talk.tgm.TgmTestLogin", "--es", "KEY_FROM_QR", f"'{uri}'")
    # quoted: adb shell joins args into one device shell line, where "&" would split it
    t = wait_for(lambda: find("Tin nhắn") is not None, 120)
    report.append(f"login to conversation list: {t if t is None else round(t, 1)} s")
    time.sleep(3)
    # dismiss permission dialogs if any
    for label in ("Allow", "Cho phép", "Dismiss", "Để sau"):
        n = find(label)
        if n is not None:
            tap(n)
            time.sleep(1)
    shot("tin-nhan")
    for tab in TABS:
        n = find(tab)
        if n is None:
            report.append(f"{tab}: tab not found")
            continue
        tap(n)
        # content shown = web view has some text nodes or the list is back
        t = wait_for(lambda: len([x for x in nodes() if x.get("text")]) > 8, 60)
        report.append(f"{tab}: {t if t is None else round(t, 1)} s")
        time.sleep(2)
        shot(tab.replace(" ", "-"))
    n = find("Cài đặt")
    if n is not None:
        tap(n)
        time.sleep(4)
        shot("cai-dat")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    crash = adb("logcat", "-d", "-b", "crash", check=False)
    report.append("crash log: " + ("none" if PKG not in crash else "FOUND"))
    with open(os.path.join(OUT, "report.txt"), "w") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))
    return 1 if PKG in crash else 0


if __name__ == "__main__":
    sys.exit(main())
