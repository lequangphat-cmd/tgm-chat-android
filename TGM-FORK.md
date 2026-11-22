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
| API user agent keeps upstream version (`UPSTREAM_VERSION_NAME`, update on upstream merge) so the Talk server does not answer 426 "app is outdated" | `app/build.gradle.kts`, `utils/ApiUtils.kt` |
| Web look (`tgm/TgmUi.kt`): list preview hides system lines and job titles, red unread counters, quiet system lines hidden in chat, web bubble colours (light theme), member count in group header. Call sites marked `TGM:` | `conversationlist/ui/ConversationListItem.kt`, `chat/viewmodels/ChatViewModel.kt`, `ui/chat/ChatMessageScaffold.kt`, `chat/ChatActivity.kt`, `res/values*/tgm_strings.xml` |
| Login page shown in an in-app WebView (Login Flow v2, polling starts at once) instead of the default browser; switch `TGM_IN_APP_LOGIN` | `account/BrowserLoginActivity.kt`, `res/layout/activity_web_view_login.xml` |
| Bottom navigation Tin nhắn / Danh bạ / Lịch / Tệp tin / Nhiệm vụ: chat stays native, other tabs show the web pages in `TgmWebActivity` (Basic auth with the app password, web menu hidden by injected CSS, chat/call links open the native chat; one WebView per tab kept in `TgmWebPool` and the other tabs preloaded, so switching tabs does not reload; only the first page sends the app password, the rest share its session cookie, with one re-login if a page lands on /login; the "unsupported browser" check is pre-dismissed for older WebViews; Lịch gets a top bar with previous, month title, next and Hôm nay that presses Calendar's own hidden buttons) | `tgm/TgmTabs.kt`, `tgm/TgmWebActivity.kt`, `conversationlist/ui/ConversationsListScreen.kt`, `AndroidManifest.xml` |
| Same features as the web: `TgmUi.WEB_TABS_ENABLED` (web tabs on), `TgmUi.HIDE_APP_ONLY_ATTACHMENTS` hides location and phone contact attachments | `tgm/TgmUi.kt`, `ui/dialog/AttachmentDialog.kt`, `conversationlist/ui/ConversationsListScreen.kt` |
| App always in Vietnamese (`tgm/TgmLocale.kt`, `attachBaseContext` override) | `application/NextcloudTalkApplication.kt`, `activities/BaseActivity.kt`, the four activities that extend `AppCompatActivity` directly |
| Bottom tabs per contract (Phát 18/11): Tin nhắn, Danh bạ, Lịch, Tệp tin, Cài đặt (native settings); Nhiệm vụ off via `TgmTab.enabled` | `tgm/TgmTabs.kt`, `res/values*/tgm_strings.xml` |
| CI UI tour on an emulator (test account from secrets `TGM_TEST_USER`, `TGM_TEST_APP_PASSWORD`; debug-only exported login alias) | `.github/workflows/tgm-ui-test.yml`, `tgm/uitest/ui_tour.py`, `app/src/debug/AndroidManifest.xml` |
| Web tabs: old web cookies dropped once per app start (stale login-page session bounced Tệp tin to /login), text zoom capped at 115 %, algorithmic darkening on so web tabs follow dark mode | `tgm/TgmWebActivity.kt` |
| Chat floating date: smaller raised pill, hides after 0.7 s (was 1.2 s) so it covers sender names less | `ui/chat/ChatView.kt` |
| CI build, publish to pre-release `build-<branch>` | `.github/workflows/tgm-build.yml` (upstream workflows removed) |
| Firebase values from secret | `tgm/apply-firebase.py` |

## CI secrets

| Secret | Content |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | `google-services.json` from the TGM Firebase project (Android app `net.tgmdental.chat`) |
| `TGM_KEYSTORE_BASE64` | upload keystore, base64 |
| `TGM_KEYSTORE_PASSWORD`, `TGM_KEY_ALIAS`, `TGM_KEY_PASSWORD` | keystore credentials |

## Firebase

Project `tgm-dental` (Spark plan). Android app `net.tgmdental.chat` registered 11/11/2026.

## Push

The app registers with the TGM push proxy at `https://chat.tgmdental.net/tgm-push`
(no trailing slash, see `tgm-talk/push-proxy/README.md`). The proxy must hold the FCM
service account of the same Firebase project.
