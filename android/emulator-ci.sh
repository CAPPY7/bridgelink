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

run_tests() {
  ./gradlew connectedDebugAndroidTest --no-daemon
}

pull_shots() {
  mkdir -p ../docs/screenshots
  adb shell run-as app.bridgelink.android ls files/shots > /tmp/bl-shot-names.txt 2>/dev/null || true
  while IFS= read -r name; do
    name=$(printf '%s' "$name" | tr -d '\r')
    if [ -z "$name" ]; then
      continue
    fi
    adb exec-out run-as app.bridgelink.android cat "files/shots/$name" > "../docs/screenshots/$name" || true
  done < /tmp/bl-shot-names.txt
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
