#!/bin/bash
# API 34 emulator: release APK installs, then the pairing instrumentation test runs.
set -euo pipefail
cd "$(dirname "$0")"
chmod +x gradlew

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  echo "Android SDK is not set"
  exit 1
fi
echo "sdk.dir=$SDK" > local.properties

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

KEYSTORE="${RUNNER_TEMP:-/tmp}/ci-emulator.keystore"
keytool -genkeypair -keystore "$KEYSTORE" -alias bridgelink -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android -dname "CN=BridgeLink CI"

./gradlew assembleRelease --no-daemon \
  -PRELEASE_STORE_FILE="$KEYSTORE" \
  -PRELEASE_STORE_PASSWORD=android \
  -PRELEASE_KEY_ALIAS=bridgelink \
  -PRELEASE_KEY_PASSWORD=android

adb wait-for-device
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n app.bridgelink.android/.MainActivity
adb uninstall app.bridgelink.android

prepare_log() {
  adb logcat -G 32M || true
  adb logcat -c || true
}

run_tests() {
  prepare_log
  ./gradlew connectedDebugAndroidTest --no-daemon
}

pull_shots() {
  mkdir -p ../docs/screenshots
  echo "---- phone screenshots ----"
  local names="home send notifications messages files remote mirror settings"
  for name in $names; do
    adb pull "/data/local/tmp/android-${name}.png" "../docs/screenshots/android-${name}.png" \
      && echo "tmp $name" || echo "tmp missing $name"
  done
  find app/build/outputs -name 'android-*.png' -print || true
  find app/build/outputs -name 'android-*.png' -exec cp -n {} ../docs/screenshots/ \; || true
  adb shell ls -la /sdcard/Android/media/app.bridgelink.android /sdcard/Android/media/app.bridgelink.android/additional_test_output /data/local/tmp || true
  adb pull /data/local/tmp/blshot-log.txt /tmp/blshot-log.txt || true
  if [ ! -s /tmp/blshot-log.txt ]; then
    adb logcat -d -s BLSHOT:I > /tmp/blshot-log.txt || true
  fi
  echo "logcat lines $(wc -l < /tmp/blshot-log.txt)"
  python3 - << 'PY'
import base64, pathlib, re, sys
log = pathlib.Path("/tmp/blshot-log.txt").read_text(errors="replace")
parts = {}
ends = {}
for line in log.splitlines():
    if "BLSHOT" not in line:
        continue
    end = re.search(r"(android-[a-z]+)#END#(\d+)", line)
    if end:
        ends[end.group(1)] = int(end.group(2))
        continue
    chunk = re.search(r"(android-[a-z]+)#(\d+)#([A-Za-z0-9+/=]+)", line)
    if chunk:
        parts.setdefault(chunk.group(1), {})[int(chunk.group(2))] = chunk.group(3)
out = pathlib.Path("../docs/screenshots")
wrote = []
for name, expected in sorted(ends.items()):
    chunks = parts.get(name, {})
    if any(i not in chunks for i in range(expected)):
        print("missing chunks", name, "have", len(chunks), "want", expected)
        continue
    png = base64.b64decode("".join(chunks[i] for i in range(expected)))
    dest = out / f"{name}.png"
    if dest.exists() and dest.stat().st_size > 1000 and open(dest, "rb").read(8).startswith(b"\x89PNG"):
        wrote.append(name + " (kept)")
        continue
    if png.startswith(b"\x89PNG") and len(png) > 1000:
        dest.write_bytes(png)
        wrote.append(name)
print("from log:", ", ".join(wrote) or "none")
PY
  local count=0
  for name in $names; do
    local f="../docs/screenshots/android-${name}.png"
    if [ -f "$f" ] && [ "$(wc -c < "$f")" -gt 1000 ]; then
      count=$((count + 1))
    else
      echo "bad or missing $f"
      rm -f "$f"
    fi
  done
  echo "phone screenshots: $count"
  if [ "$count" -ne 8 ]; then
    echo "Need 8 phone screenshots"
    return 1
  fi
}

if ! run_tests; then
  echo "::warning::Emulator pairing test failed once. Retrying."
  adb logcat -d -t 250 || true
  if ! run_tests; then
    echo "::error::Emulator pairing test failed twice."
    adb logcat -d -t 400 || true
    pull_shots
    exit 1
  fi
fi
pull_shots
