#!/usr/bin/env python3
"""Copy Firebase values from google-services.json into app/src/main/res/values/setup.xml.

The app reads Firebase options from string resources (no google-services Gradle plugin),
so a TGM build needs the TGM Firebase project values there. Run in CI from the secret,
never commit the real values.
"""
import json
import re
import sys

PACKAGE = "net.tgmdental.chat"
SETUP = "app/src/main/res/values/setup.xml"

data = json.load(open(sys.argv[1], encoding="utf-8"))
info = data["project_info"]
client = next(c for c in data["client"]
              if c["client_info"]["android_client_info"]["package_name"] == PACKAGE)
api_key = client["api_key"][0]["current_key"]
web_client = next((o["client_id"] for o in client.get("oauth_client", []) if o.get("client_type") == 3), "")

values = {
    "default_web_client_id": web_client,
    "firebase_database_url": info.get("firebase_url", ""),
    "gcm_defaultSenderId": info["project_number"],
    "google_api_key": api_key,
    "google_app_id": client["client_info"]["mobilesdk_app_id"],
    "project_id": info["project_id"],
    "google_crash_reporting_api_key": api_key,
    "google_storage_bucket": info.get("storage_bucket", ""),
}

xml = open(SETUP, encoding="utf-8").read()
for name, value in values.items():
    xml, n = re.subn(r'(<string name="%s"[^>]*>)[^<]*(</string>)' % name, r"\g<1>%s\g<2>" % value, xml)
    if n != 1:
        sys.exit("setup.xml: %s not found" % name)
open(SETUP, "w", encoding="utf-8").write(xml)
print("Firebase values applied for project", info["project_id"])
