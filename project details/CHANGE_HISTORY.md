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

---

## Session 3 — Downloads failing mid-way + cuter icon (v1.3)

### Prompt (verbatim)
> issu the link work and add download but its some how cant start after some time
> it says failed change the app icon cuter anc choby anime stle one

### Diagnosis (reproduced with the bundled yt-dlp on the same network)
- Bundled yt-dlp is recent (`2025.11.12`), so it's NOT an outdated-extractor issue.
- Format extraction/metadata SUCCEEDS ("the link work, add download"), but the
  **actual media download returns `HTTP Error 403: Forbidden`** right after
  "Sleeping 5.00 seconds as required by the site..." → matches "after some time it
  says failed". This is YouTube's anti-bot / PO-token enforcement.
- Different player clients behave very differently: `default` + `android` give
  downloadable URLs; `android_vr`, `tv`, `web_safari`, `mweb`, `ios` returned 403 or
  "format not available".

### Fixes
- `DownloaderPlugin.java` hardened:
  - format selector now prefers **H.264 mp4 video + m4a audio** (universally
    playable, remuxes cleanly, avoids SABR/av01 pitfalls), with fallbacks.
  - forces `--extractor-args youtube:player_client=default` (the client that exposes
    a directly downloadable URL).
  - `--retries 10`, `--fragment-retries 10`, `--socket-timeout 30`.
  - **auto-retry** on failure with `--force-ipv4` + `youtube:player_client=android,default`.
- UI now shows a **persistent, copyable "Last error"** box (was a 2.6s toast), so
  the exact yt-dlp error is readable/reportable.

### Icon (cuter chibi anime style)
- Redesigned `project details/icon/art.svg`: bigger head (chibi), large sparkling
  eyes with double highlights, **twin tails** with star ties, a pink **hair flower**,
  blush, open smile with tongue, tiny body hugging the download sign.
- Regenerated all mipmap densities (square + round + adaptive foreground).

### Deliverables
- `project files/apk/Download-Chan-v1.3.apk` (108 MB) ← LATEST
- Removed v1.2 (superseded).

### Notes
- If a specific YouTube link still 403s, it's an IP-level bot flag; retries/VPN or
  cookies are the only remaining levers. Report the full "Last error" text.

---

## Session 4 — Online resolver fallback (v1.4)

### Prompt (verbatim)
> i have a idea use exsting websites who let past link and download videso and add
> their api or what need to fix the issu whats your plan tell me first

### Decisions (confirmed with user)
- Approach: let a **server-side resolver** handle the anti-bot problem; the phone
  downloads a direct file URL.
- Backend: **free public API** (tested `gendownload.com` — works, no key).
- Fallback mode: **Auto** — try bundled yt-dlp first, then the online resolver.

### What was built
- Verified `POST https://gendownload.com/api/extract {url}` returns
  `{ title, thumbnail, formats:[{label,type,ext,url}] }` and the returned stream URL
  serves a real `video/mp4`/`audio/mp3` (`Content-Disposition: attachment`). No API key.
- `DownloaderPlugin.java`: new **`downloadRemote`** method — POSTs to the resolver,
  picks the format by mode/quality, then downloads the direct URL with a plain
  `HttpURLConnection` stream (Content-Length progress), saving into the same folder.
  Resolver URL is configurable; defaults to gendownload.com.
- `www/js/native.js`: exposes `downloadRemote`.
- `www/js/app.js`: **Auto fallback** — `runJobLocal` → on failure → `runJobRemote`
  (toast + card shows "Trying online…"); both messages shown if both fail.
- Settings UI: **Online fallback** toggle + **Online resolver** URL field.

### Deliverables
- `project files/apk/Download-Chan-v1.4.apk` (108 MB) ← LATEST
- Removed v1.3.

### Notes / caveats
- The shared link leaves the phone to the resolver (privacy) and the service may
  rate-limit or vanish — that's why local yt-dlp stays as the first attempt.
- Resolver is pluggable: pointing it at a self-hosted cobalt instance later is a
  Settings change (cobalt's response schema differs, so it'd need an adapter).
- Legal: personal use; respect each site's terms.

---

## Session 5 — Facebook "Cannot parse data" diagnosis + engine auto-update (v1.5)

### Prompt (verbatim)
> chack download folder issu.txt

### The reported error (`~/Downloads/issu.txt`)
```
YoutubeDLException: WARNING: Your yt-dlp version (2025.11.12) is older than 90 days!
ERROR: [facebook] 1413081230459468: Cannot parse data; please report this issue ...
```

### Root-cause findings (verified)
- The failing link is **Facebook**, not YouTube. Facebook now **login-walls** most
  `watch/?v=` videos, so:
  - bundled yt-dlp `2025.11.12` → `Cannot parse data`;
  - **latest** yt-dlp `2026.08.19` → **also `Cannot parse data`** (so not just stale);
  - online resolver (gendownload) → **HTTP 422** "Could not fetch this link" after ~50s.
- Conclusion: **this specific video needs an authenticated session** — not an app bug.
  A version bump alone does NOT fix it; proper fix = **cookies/login support**.
- But the bundled yt-dlp IS ~10 months stale (2025.11.12 vs 2026.08.19) → worth updating.

### What was built
- `DownloaderPlugin.java`: new **`updateEngine`** method — calls
  `YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE.INSTANCE)`
  (Kotlin sealed class → `.INSTANCE`), returns `{updated, status}`.
- `www/js/native.js`: exposes `updateEngine`.
- `www/js/app.js`: **auto-update on boot** (max once / 24h via `dc_engine_update` in
  localStorage) + a **Settings → "Update download engine"** button; engine status shown
  in Settings ("up to date" / "just updated").
- Better error hint: if the error matches `cannot parse data|not available|login|sign
  in|private`, append a hint pointing at Online fallback / public links.

### Deliverables
- `project files/apk/Download-Chan-v1.5.apk` ← LATEST (removed v1.3/v1.4).

### Next (optional)
- **Cookies support** (import a `cookies.txt` or in-app WebView login → `--cookies`) is
  the real fix for login-walled Facebook/Instagram. Not yet implemented.
