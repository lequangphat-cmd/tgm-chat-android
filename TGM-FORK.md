# TGM Chat for Android

Branded build of [Nextcloud Talk for Android](https://github.com/nextcloud/talk-android) (GPL-3.0-or-later)
for TGM, locked to `https://chat.tgmdental.net`.

## Branches

- `upstream/stable` - unmodified upstream release imports only (currently v25.0.2).
- `main` - upstream + TGM changes. Merge new upstream releases from `upstream/stable`.

## TGM changes

| Area | File |
| --- | --- |
| App name, server URL, push proxy URL, single account, branded flags | `app/src/main/res/values/setup.xml` |
| Package name `net.tgmdental.chat` | `app/build.gradle.kts`, `res/xml/*.xml`, contacts mime type |
| Icons and logo | `res/mipmap-*`, `res/drawable-*dpi/ic_launcher_foreground.png`, `ic_logo.png`, `drawable/ic_launcher_background.xml` |
| Brand colour | `res/values/colors.xml` |
| Vietnamese translation (upstream has none) | `res/values-vi/strings.xml` |
| Release signing from environment, CI version code | `app/build.gradle.kts` |
| CI build, publish to pre-release `build-<branch>` | `.github/workflows/tgm-build.yml` (upstream workflows removed) |
| Firebase values from secret | `tgm/apply-firebase.py` |

## CI secrets

| Secret | Content |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | `google-services.json` from the TGM Firebase project (Android app `net.tgmdental.chat`) |
| `TGM_KEYSTORE_BASE64` | upload keystore, base64 |
| `TGM_KEYSTORE_PASSWORD`, `TGM_KEY_ALIAS`, `TGM_KEY_PASSWORD` | keystore credentials |

## Push

The app registers with the TGM push proxy at `https://chat.tgmdental.net/tgm-push`
(no trailing slash, see `tgm-talk/push-proxy/README.md`). The proxy must hold the FCM
service account of the same Firebase project.
