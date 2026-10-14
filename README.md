# Download-Chan

A share-to-download Android app. Share (or paste) a link from **YouTube, TikTok,
Instagram, Facebook, Douyin, Reels, X/Twitter, Vimeo, Reddit** and more — the app
saves the video (or extracts MP3 audio) to your device.

Built with **Capacitor 6** (HTML/CSS/JS UI) + a native Java layer that bundles
**yt-dlp** via [youtubedl-android](https://github.com/yausername/youtubedl-android),
so extraction runs entirely offline with no server.

## The 4 tabs

| Tab | What it does |
|---|---|
| **Download** | Main screen. Auto-fills from a shared link and starts. Video / Audio(MP3) toggle + quality picker. Live progress, queue, cancel. |
| **Library** | Every saved file — open, share, or delete. |
| **Browse** | Quick-launch buttons for supported sites, plus a "send this link to Download" box. |
| **Settings** | Default mode/quality, save folder, engine status, version, source link. |

## How it works

1. In any app, tap **Share → Download-Chan**. Android delivers the link via an
   `ACTION_SEND` intent (`MainActivity` → `DownloaderPlugin`).
2. The URL is handed to the web UI, which calls the native `Downloader` plugin.
3. `DownloaderPlugin` runs bundled yt-dlp + ffmpeg and streams progress events
   back to JS. Finished files land in the app's private Downloads folder.

## Build

```bash
cd "project files/downloadchan"
source env.sh          # JDK 17 + Android SDK
./build-apk.sh         # reads VERSION, cap sync, gradlew assembleDebug, copies APK, bumps VERSION
```

Output: `project files/apk/Download-Chan-v<version>.apk`.
Requires **JDK 17** (Gradle 8.2). ABI filters limit the build to `arm64-v8a` +
`armeabi-v7a`.

## Layout

```
project files/downloadchan/
├── www/                    ← the UI (edit here)
│   ├── index.html
│   ├── css/style.css
│   └── js/{app,native}.js
├── android/                ← Capacitor Android project + native plugin
│   └── app/src/main/java/com/downloadchan/app/
│       ├── MainActivity.java       (share-intent receiver)
│       └── DownloaderPlugin.java   (yt-dlp bridge)
├── env.sh · build-apk.sh · VERSION
```

Regenerate web assets into the APK with `npx cap sync android` (done automatically
by `build-apk.sh`).

## Notes / limitations

- Download only content you own or have permission to save. Respect each site's
  terms and local copyright law.
- Facebook/Instagram sometimes require login cookies; those private/age-gated
  posts may fail.
- Debug builds are signed with the debug keystore. For an installable release,
  create a keystore and a release variant.
