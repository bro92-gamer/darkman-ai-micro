#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_DIR="$ROOT/android-source"
cd "$ANDROID_DIR"
[ -x ./gradlew ] || chmod +x ./gradlew
./gradlew --no-daemon clean assembleRelease
APK=$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | head -n 1)
if [ -z "$APK" ] || [ ! -f "$APK" ]; then echo "Build did not produce a release APK" >&2; exit 1; fi
BYTES=$(wc -c < "$APK" | tr -d ' ')
MAX=$((25 * 1024 * 1024))
printf 'APK: %s\nSize: %s bytes\n' "$ANDROID_DIR/$APK" "$BYTES"
if [ "$BYTES" -ge "$MAX" ]; then echo "APK exceeds the 25 MiB target" >&2; exit 2; fi
if command -v unzip >/dev/null 2>&1; then
  NATIVE=$(unzip -l "$APK" | grep -E 'lib/[^/]+/' || true)
  if [ -n "$NATIVE" ] && ! printf '%s\n' "$NATIVE" | grep -q 'lib/armeabi-v7a/'; then echo "Unexpected non-ARM native payload found" >&2; exit 3; fi
  if [ -z "$NATIVE" ]; then echo "No native libraries: pure Kotlin/Java APK is ABI-neutral and runs on ARMv7."; fi
fi
echo "Darkman-AI release build passed the size and ABI checks."
