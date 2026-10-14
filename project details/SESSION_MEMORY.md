# Download-Chan — Session Memory (resume playbook)
> Read this FIRST in any new session. Update at the END of every session.

**Last dump:** Session 5 · v1.5
**Next build will be:** v1.6

---

## 1. Identity
- **Project:** Download-Chan — Android share-to-download app. Share/paste a link
  (YouTube, TikTok, Instagram, Facebook, Douyin, Reels, X, Vimeo, Reddit…), it
  saves the video or extracts MP3 audio.
- **Stack:** Capacitor 6 (vanilla HTML/CSS/JS in `www/`) wrapped for Android, plus
  a native Java bridge that bundles **yt-dlp** (youtubedl-android 0.18.1).
- **Package:** `com.downloadchan.app` · **App name:** Download-Chan.
- **Repo (intended):** https://github.com/nahid77777771-maker/download-chan
- Language of prompts: English. Replies: English.

## 2. Folder layout
```
~/Downloads/download-chan/
├── .gitignore
├── README.md
├── project details/
│   ├── CHANGE_HISTORY.md      ← prompts verbatim + changes
│   └── SESSION_MEMORY.md      ← THIS FILE
└── project files/
    ├── apk/                    ← DELIVERABLES (v1.0, v1.1 …)
    └── downloadchan/           ← the app source (@WORKING dir)
        ├── VERSION             currently 1.2 (next build)
        ├── env.sh · build-apk.sh
        ├── www/                ← the UI to edit
        └── android/            ← Capacitor Android + native plugin
```
Build working dir: `~/Downloads/download-chan/"project files"/downloadchan`.

## 3. Build workflow (mandatory)
```bash
cd ~/Downloads/download-chan/"project files"/downloadchan
source env.sh        # JDK 17 + ANDROID_HOME=~/Android/Sdk
./build-apk.sh       # VERSION -> inject versionCode/Name -> cap sync -> gradlew
                     #   assembleDebug -> copy APK -> bump VERSION
```
- Output: `project files/apk/Download-Chan-v<ver>.apk`.
- **JDK 17 required** (system default is newer → Gradle 8.2 fails).
- First build is slow (downloads yt-dlp/python/ffmpeg natives); incremental ~11–60s.
- ABI filters = `arm64-v8a`, `armeabi-v7a` (set in `android/app/build.gradle`).

## 4. App architecture
### Web layer (`www/`)
- `index.html` — 4-tab shell (Download / Library / Browse / Settings).
- `css/style.css` — dark theme, pink/cyan, bottom tab bar, safe-area aware.
- `js/native.js` — `window.DC` wrapper over the Capacitor `Downloader` plugin.
- `js/app.js` — all UI logic: tabs, settings (localStorage `dc_settings`),
  download queue (one at a time), progress rendering, library list, sites,
  share handling. Auto-starts a download when a link is shared.

### Native layer (`android/app/src/main/java/com/downloadchan/app/`)
- `MainActivity.java` — registers `DownloaderPlugin`; receives `ACTION_SEND`
  (text/plain) + `ACTION_VIEW`, extracts the URL, calls `setPendingShare` and
  `emitShareToJs`.
- `DownloaderPlugin.java` — `@CapacitorPlugin(name="Downloader")`. Uses
  `YoutubeDL.getInstance().init(ctx)` and `execute(request, processId, Function3)`.
  - Progress callback type from Java: `kotlin.jvm.functions.Function3<Float,Long,String,Unit>`.
  - Output dir: `getExternalFilesDir(DIRECTORY_DOWNLOADS)/Download-Chan`.
  - Output template: `%(title).100s [%(id)s].%(ext)s`.
  - Formats: video `bv*+ba/b` (+ `--merge-output-format mp4`), audio
    `bestaudio/best -x --audio-format mp3`.
  - Returns the newest file in the dir as the result.
- Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`; `largeHeap=true`;
  **`extractNativeLibs="true"`** (REQUIRED by youtubedl-android — see gotchas);
  share intent-filter; FileProvider `${applicationId}.fileprovider`.
- `build.gradle`: `ndk { abiFilters 'arm64-v8a','armeabi-v7a' }` and
  `packagingOptions { jniLibs { useLegacyPackaging = true } }` (both required).
- `res/values/colors.xml` added (was missing in the KanaPet template).
- **Icons:** cuter chibi green-haired girl (twin tails, hair flower, big sparkly
  eyes) authored as SVG then rasterized with `rsvg-convert` + ImageMagick into
  every mipmap density (square + round + adaptive foreground). Adaptive background
  is a gradient VectorDrawable. Source art is versioned at
  `project details/icon/{art.svg,bg.svg,gen.sh}` — regenerate with
  `cd "project details/icon" && bash gen.sh`.
- **Splashes:** gradient + centered logo (all `drawable*/splash.png`).

## 5. Deliverables
| Version | What | Size |
|---|---|---|
| v1.0 | Initial app (all 4 ABIs) — broken init | 214 MB |
| v1.1 | + ABI filter — broken init | 109 MB |
| v1.2 | Fix init (extractNativeLibs), green-hair icon + splash | 108 MB |
| v1.3 | YouTube 403 hardening + retry, mp4/m4a prefer, persistent error box, cuter chibi icon | 108 MB |
| v1.4 | Online resolver fallback (Auto: local yt-dlp → gendownload.com), Settings toggle | 108 MB |
| v1.5 | **yt-dlp engine auto-update (boot + Settings button), login-wall error hint** ← LATEST | 108 MB |

Debug-signed (debug keystore). Install via `adb install -r <apk>`.
(v1.0/v1.1/v1.2 removed — superseded.)

## 6. Gotchas
- **Facebook login-wall (v1.5):** FB `watch/?v=` videos usually need an authenticated
  session. Bundled yt-dlp (2025.11.12) AND latest (2026.08.19) both give
  `[facebook] ...: Cannot parse data`; gendownload returns **422**. Real fix =
  **cookies/login** (not implemented). Engine auto-update added anyway (bundle was ~10mo
  stale): `updateEngine` → `YoutubeDL.UpdateChannel.STABLE.INSTANCE`.
- **Online fallback (v1.4):** `DownloaderPlugin.downloadRemote()` POSTs `{url}` to
  `settings.resolver + /api/extract` (default `https://gendownload.com`), which
  returns `{title, formats:[{label,type,ext,url}]}`; the app downloads
  `formats[i].url` directly. Generic/pluggable, but the JSON schema is
  gendownload-specific — a **cobalt** backend needs a different adapter (cobalt
  returns `status: tunnel|redirect|picker`, not `formats[]`). Test a resolver with:
  `curl -sX POST <res>/api/extract -H 'Content-Type: application/json' -d '{"url":"<video>"}'`.
- **"failed to initialize" = native libs not extracted.** youtubedl-android reads
  its `.so` from `nativeLibraryDir`; AGP 8 defaults `extractNativeLibs=false`.
  Keep BOTH `android:extractNativeLibs="true"` in the manifest AND
  `packagingOptions { jniLibs { useLegacyPackaging = true } }` in build.gradle.
- **YouTube "fails after a while" = media-download 403 (anti-bot/PO token), NOT an
  outdated extractor.** Bundled yt-dlp is current. Extraction succeeds, then the
  stream URL is rejected. Mitigations in-app: `player_client=default`, prefer
  mp4/m4a, retry with `--force-ipv4` + `android` client. Hard IP flags need
  cookies/VPN. Reproduce locally with the bundled binary
  (`python3 <unzipped res/raw/ytdlp> ...`) — it runs on the host too.
- Different YouTube `player_client` values are NOT equivalent: `default`/`android`
  give downloadable URLs; `android_vr`/`tv`/`web_safari`/`mweb`/`ios` 403 or have no
  formats. Don't set `player_client` blind.
- `git clone github.com` / `git ls-remote` HANG on this machine (use raw /
  codeload). `git push` may be slow — allow a long timeout.
- GitHub token in keyring was **invalid** (needs `gh auth login` before push).
- Paths have a space (`project files`) — always quote.
- fuzzy: the KanaPet template had no `colors.xml` yet built; we added one.
- Native plugin is registered manually (`registerPlugin(DownloaderPlugin.class)`)
  because it lives in the app module, not as an npm package.

## 7. Typical next moves
- Confirm v1.5 on the device: engine auto-updates on first launch (watch Settings →
  Engine), and Online fallback catches YouTube 403s.
- **Cookies support** is the highest-value next feature: import a `cookies.txt` (SAF) or
  in-app WebView login → pass `--cookies` to yt-dlp. Fixes login-walled FB/IG.
- If the free resolver is unreliable/rate-limited, swap `settings.resolver` to a
  self-hosted **cobalt** instance (needs a small adapter for cobalt's schema).
- Smaller release APK (keystore + `assembleRelease`, `minifyEnabled`), or arm64-only.
- Download thumbnail/title preview (`getInfo` already implemented, UI not wired).
- Multiple concurrent downloads / background service + notification.
- Custom cookies support for Instagram/Facebook gated posts.
- In-app WebView "Browse" tab instead of launching the system browser.
- iOS target.
