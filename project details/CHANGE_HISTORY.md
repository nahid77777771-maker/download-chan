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

---

## Session 2 — Downloads failed to initialize + new icon (v1.2)

### Prompt (verbatim)
> i try to download videos and it says faild to inisilized rechack and fix code and
> change app icon to cuter japanise green hair girl and holding a download sing

### Diagnosis
- Symptom: "failed to initialize". Root cause: under **AGP 8** the manifest defaults
  to `android:extractNativeLibs="false"`, so youtubedl-android's bundled
  Python/FFmpeg `.so` files are never extracted to `nativeLibraryDir` → init throws
  (`YoutubeDLException: failed to initialize`, caused by `NoSuchFileException ...
  libpython.zip.so`). Well-known youtubedl-android issue (#105/#300).

### Fixes
- `AndroidManifest.xml`: added `android:extractNativeLibs="true"` on `<application>`.
- `android/app/build.gradle`: added `packagingOptions { jniLibs { useLegacyPackaging
  = true } }` at the `android {}` level (forces install-time extraction).
- `DownloaderPlugin.java`: errors now surface the **full cause chain**
  (`fullMessage()`), logged with `Log.e` — easier to diagnose next time.
- **New app icon:** cute green-haired chibi girl holding a download sign, authored
  as SVG (`rsvg-convert`) → all mipmap densities (legacy square + round + adaptive
  foreground). Adaptive background = pink→blue gradient VectorDrawable.
- **Splash screens** regenerated: gradient background + centered logo (were blank
  white with a leftover default logo).

### Deliverables
- `project files/apk/Download-Chan-v1.2.apk` (108 MB) ← LATEST (working)
- Removed the broken v1.0/v1.1 APKs.

### Notes
- Verified in the built APK: `extractNativeLibs=0xffffffff` (true), libs legacy
  packaged, all icon densities present.
