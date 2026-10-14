# KanaPet — APK Build Instructions

**Tech stack used:** single-page HTML/CSS/JS app (vanilla, no build step) wrapped with
**Capacitor 6** for Android. Voice uses the Web Speech API (`SpeechRecognition`,
`lang='ja-JP'`) with a self-rated-button fallback. All data is hard-coded in `www/js/data.js`
(208 characters: 46+46 basic gojūon, dakuten, handakuten, yōon).

## Project layout
```
kanapet/
├── package.json            # Capacitor dependencies
├── capacitor.config.json   # appId com.kanapet.app, webDir = www
├── BUILD.md                # this file
└── www/                    # the whole app (opens directly in any browser)
    ├── index.html
    ├── css/style.css
    └── js/ data.js · pet.js · app.js
```

## Prerequisites (one-time install)
1. **Node.js 18+** and npm (https://nodejs.org).
2. **Android Studio** (installs the Android SDK + JDK 17) — https://developer.android.com/studio.
   Set `ANDROID_HOME` to your SDK folder (e.g. `~/Android/Sdk` on Linux,
   `%LOCALAPPDATA%\Android\Sdk` on Windows).
3. Accept SDK licenses: `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses`.

## Exact build commands
```bash
cd kanapet

# 1. Install Capacitor
npm install

# 2. Add the Android platform (creates ./android/)
npx cap add android

# 3. Copy www/ into the Android project + update plugins
npx cap sync

# 4a. Build a debug APK (fast, for testing on a phone):
cd android && ./gradlew assembleDebug
#     output: android/app/build/outputs/apk/debug/app-debug.apk

# 4b. Build a release APK (for distribution; needs a signing key):
cd android && ./gradlew assembleRelease
#     output: android/app/build/outputs/apk/release/app-release-unsigned.apk
```

## ✅ PREFERRED: versioned build script (use this every time)
```bash
cd kanapet
./build-apk.sh          # builds current VERSION, names APK KanaPet-v<version>.apk, bumps VERSION
./build-apk.sh 2.0      # optional: force a specific version
```
This is the standard way to build. It:
1. reads `VERSION` (last version, e.g. `1.7`),
2. writes that `versionName` + a matching `versionCode` into `android/app/build.gradle`,
3. runs `npx cap sync android` + `./gradlew assembleDebug`,
4. copies the result to **`project files/apk/KanaPet-v<version>.apk`**,
5. bumps the last number in `VERSION` (1.7 → 1.8) for the next build.

So every change we make ships as a new, clearly-named APK and versions never collide.
To open in Android Studio instead of the command line: `npx cap open android`,
then **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Signing a release APK (required for Play Store / install on some devices)
```bash
# create a key once
keytool -genkey -v -keystore kanapet.keystore -alias kanapet -keyalg RSA -keysize 2048 -validity 10000
# sign
$ANDROID_HOME/build-tools/<version>/apksigner sign --ks kanapet.keystore \
  --out kanapet-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## Microphone permission (RECORD_AUDIO)
After `npx cap add android`, edit `android/app/src/main/AndroidManifest.xml` and add
this line inside `<manifest>` (above `<application>`):
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
Capacitor shows a runtime permission prompt the first time the mic button is tapped.
If the user denies it (or speech recognition is unavailable/offline), the app
**automatically falls back to "Got it / Missed" self-rated buttons** — it is always usable.

## 🎤 Voice recognition (implemented, offline-capable)
Inside the APK the app uses the native plugin
`@capacitor-community/speech-recognition` (v6). `www/js/app.js` `startListening()`:
- uses the plugin's `partialResults` + `listeningState` events (NOT the old
  `speechRecognitionResult`/`speechRecognitionError` events — those don't exist in v6),
- auto-restarts listening if Android ends on silence (so the mic does not cut off
  after ~2s), with a 15s hard cap,
- asks for `preferOffline: true`,
- on a correct answer the next card **auto-advances**.

### Offline Japanese setup (one-time, on the phone)
Android's recognizer is offline only if the on-device language pack is installed:
**Settings → Google → Settings for Google apps → Search, Assistant & Voice → Voice →
Offline speech recognition → Japanese → Download.**
`preferOffline` makes the app use that on-device model; without the pack it falls
back to the network, and if neither works the Got-it/Missed buttons are always there.

### Offline native patch
`patches/SpeechRecognition.java` adds `RecognizerIntent.EXTRA_PREFER_OFFLINE` to the
plugin. `build-apk.sh` copies it into `node_modules/...` before every build, so the
patch survives `npm install`. If you upgrade the plugin, re-check/rebuild the patch.

## Back button
With `@capacitor/app` installed, add to `app.js` `init()`:
```js
// TODO (native): CapacitorApp.addListener('backButton', ({canGoBack}) => { if(!canGoBack) App.exitApp(); });
```
Without it, the WebView default back behavior applies; the app never crashes on back
because all state is in memory + persisted to `localStorage`.

## Offline-first
Everything (progress, errors, streak, XP, settings) persists in `localStorage`.
No network calls except optional Google Fonts (system Noto CJK fallback on Android)
and voice recognition (which gracefully degrades).
