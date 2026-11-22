#!/usr/bin/env bash
# Build 小窗 / FloatWin without Gradle.
# Requires: JDK 17, Android SDK (build-tools + platform android-36).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build"
DIST="$ROOT/dist"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/local/lib/android/sdk"; do
    if [ -d "$candidate" ]; then SDK="$candidate"; break; fi
  done
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "error: Android SDK not found, set ANDROID_SDK_ROOT" >&2
  exit 1
fi

pick_max() {
  python3 - "$1" "$2" <<'PY'
import os, re, sys
root, suffix = sys.argv[1], sys.argv[2]
items = []
for name in os.listdir(root):
    path = os.path.join(root, name)
    if os.path.isdir(path) and os.path.exists(os.path.join(path, suffix)):
        items.append(path)
if not items:
    sys.exit("nothing found under " + root)
def key(p):
    parts = [int(x) for x in re.findall(r"\d+", os.path.basename(p))]
    return parts or [0]
print(max(items, key=key))
PY
}

BT="$(pick_max "$SDK/build-tools" aapt2)"
PLATFORM_DIR="$(pick_max "$SDK/platforms" android.jar)"
PLATFORM="$PLATFORM_DIR/android.jar"

AAPT2="$BT/aapt2"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
D8="$BT/d8"
JAVAC="${JAVAC:-javac}"
KEYTOOL="${KEYTOOL:-keytool}"

KS="$ROOT/debug.keystore"
APK="$DIST/floatwin.apk"

# Keep in sync with build.ps1
VERSION_CODE=3
VERSION_NAME=1.1.1

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

echo "==> compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> link resources"
"$AAPT2" link \
  -o "$BUILD/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  --min-sdk-version 29 \
  --target-sdk-version 36 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "==> compile java"
SOURCES=$(find "$APP/java" "$BUILD/gen" -name '*.java')
# shellcheck disable=SC2086
"$JAVAC" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d "$BUILD/classes" $SOURCES

echo "==> dex"
CLASSES=$(find "$BUILD/classes" -name '*.class')
# shellcheck disable=SC2086
"$D8" --release --lib "$PLATFORM" --min-api 29 --output "$BUILD/dex" $CLASSES

echo "==> package classes.dex"
if command -v zip >/dev/null 2>&1; then
  (cd "$BUILD/dex" && zip -q -j "$BUILD/app-unsigned.apk" classes.dex)
else
  python3 - "$BUILD/app-unsigned.apk" "$BUILD/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY
fi

echo "==> zipalign"
"$ZIPALIGN" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

if [ ! -f "$KS" ]; then
  echo "==> generate debug keystore"
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android \
    -dname 'CN=Android Debug,O=Android,C=US' \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi

echo "==> sign"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$APK" "$BUILD/app-aligned.apk"

echo "==> verify"
"$APKSIGNER" verify --print-certs "$APK" | head -n 3

echo "built: $APK ($(du -h "$APK" | cut -f1))"
