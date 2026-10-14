#!/usr/bin/env bash
# Download-Chan — versioned APK builder.
#
# Usage:
#   ./build-apk.sh            -> build the version in VERSION, name APK Download-Chan-v<version>.apk,
#                                then bump VERSION (patch) for next time.
#   ./build-apk.sh 2.0        -> build explicitly as v2.0 and set VERSION to 2.1 next.
#
# Output APK folder:  ../apk/Download-Chan-v<version>.apk   (i.e. "project files/apk/")
set -euo pipefail

cd "$(dirname "$0")"
APP_NAME="Download-Chan"
PROJ="$PWD"
OUT_DIR="$(cd .. && pwd)/apk"

# ---- version handling ----
VER_FILE="$PROJ/VERSION"
[ -f "$VER_FILE" ] || echo "1.0" > "$VER_FILE"
CUR="$(tr -d '[:space:]' < "$VER_FILE")"

if [ "$#" -ge 1 ]; then
  VER="$1"
else
  VER="$CUR"
fi

# next version: bump the LAST number (1.0 -> 1.1, 1.7.3 -> 1.7.4)
IFS='.' read -ra PARTS <<< "$VER"
LAST_IDX=$(( ${#PARTS[@]} - 1 ))
PARTS[$LAST_IDX]=$(( PARTS[$LAST_IDX] + 1 ))
NEXT="$(IFS=.; echo "${PARTS[*]}")"

# versionCode = major*10000 + minor*100 + patch  (Android requires a monotonically rising int)
IFS='.' read -r MA MI PA <<< "$VER"
MA="${MA:-0}"; MI="${MI:-0}"; PA="${PA:-0}"
CODE=$(( MA * 10000 + MI * 100 + PA ))

echo "==> Building $APP_NAME v$VER (versionCode $CODE)"

# ---- inject version into the Android project ----
GRADLE="$PROJ/android/app/build.gradle"
sed -i -E "s/versionCode [0-9]+/versionCode $CODE/"                  "$GRADLE"
sed -i -E "s/versionName \"[^\"]*\"/versionName \"$VER\"/"           "$GRADLE"

# ---- build env + build ----
# shellcheck disable=SC1091
source ./env.sh >/dev/null 2>&1 || true
npx cap sync android
( cd android && ./gradlew assembleDebug )

# ---- copy + rename the APK ----
mkdir -p "$OUT_DIR"
SRC="$PROJ/android/app/build/outputs/apk/debug/app-debug.apk"
DEST="$OUT_DIR/$APP_NAME-v$VER.apk"
cp -f "$SRC" "$DEST"

# ---- remember next version ----
echo "$NEXT" > "$VER_FILE"

echo ""
echo "==> Done!"
echo "    APK : $DEST"
echo "    Next build will be v$NEXT (edit VERSION to change)."
