# Download-Chan — Change History

Every user prompt is recorded VERBATIM, followed by what was done.

---

## Session 1 — Project creation (v1.0 → v1.1)

### Prompt (verbatim)
> so you know how my app creating and building process is

> tell me how i creat apk from your experince

> i want you apply past learning and creat a new app for android a app that has 4
> tab add option you see fit the main perpous of the app is to download if i shair
> any link it will download  like if i shair a fb link it will download that if i
> shair youtube link i will download it and tiktok douyin instagram and reel any
> now build it later i cn push updates also pu build and surce app in git

### Decisions (confirmed with user)
- **Name:** Download-Chan → package `com.downloadchan.app`.
- **Engine:** bundle yt-dlp inside the app (offline, no server).
- **Git:** new repo under `nahid77777771-maker`.

### What was built
- New Capacitor 6 Android project, cloned from the KanaPet scaffold, renamed
  everywhere (appId, namespace, applicationId, strings, package dir, build script).
- **4 tabs:** Download / Library / Browse / Settings (`www/index.html` + CSS + JS).
- **Native share receiver:** `ACTION_SEND` (text/plain) intent-filter →
  `MainActivity` → `DownloaderPlugin.setPendingShare()` + live `sharedUrl` event.
- **`DownloaderPlugin.java`:** wraps `io.github.junkfood02.youtubedl-android`
  (0.18.1, Maven Central). Methods: `init`, `isReady`, `getSharedUrl`, `getInfo`,
  `download`, `cancel`, `listFiles`, `deleteFile`, `openFile`, `shareFile`,
  `openExternal`. Emits `progress` / `completed` / `error` / `cancelled` events.
- **v1.0 APK** built (214 MB — all 4 ABIs).
- **v1.1 APK** (109 MB) — added `abiFilters 'arm64-v8a','armeabi-v7a'` to drop the
  huge x86/x86_64 ffmpeg/python libs.

### Deliverables
- `project files/apk/Download-Chan-v1.0.apk` (214 MB)
- `project files/apk/Download-Chan-v1.1.apk` (109 MB) ← LATEST

### Notes
- v1.0 shipped before ABI filtering; v1.1 is the recommended build.
- Engine = yt-dlp 0.18.1 (bundled). ffmpeg bundled for merging / MP3 extraction.
