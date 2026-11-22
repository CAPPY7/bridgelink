#!/bin/bash
# Sign BridgeLink.app. Uses the stable certificate when the two Mac secrets exist.
set -euo pipefail
APP="${1:-BridgeLink.app}"
cd "$(dirname "$0")"
if [ -n "${MAC_SIGN_P12_BASE64:-}" ] && [ -n "${MAC_SIGN_P12_PASSWORD:-}" ]; then
  WORK="${RUNNER_TEMP:-/tmp}/bridgelink-sign"
  mkdir -p "$WORK"
  KC="$WORK/bridgelink.keychain-db"
  PASS="$(openssl rand -base64 24 | tr -d '\n')"
  security delete-keychain "$KC" >/dev/null 2>&1 || true
  security create-keychain -p "$PASS" "$KC"
  if [ "${CI:-}" = "true" ]; then
    security default-keychain -s "$KC"
  fi
  security unlock-keychain -p "$PASS" "$KC"
  security set-keychain-settings -t 3600 "$KC"
  echo "$MAC_SIGN_P12_BASE64" | base64 -d > "$WORK/sign.p12"
  security import "$WORK/sign.p12" -k "$KC" -P "$MAC_SIGN_P12_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security >/dev/null
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$PASS" "$KC" >/dev/null
  IDENTITY="$(security find-identity -v -p codesigning "$KC" | awk -F '"' '/BridgeLink/{print $2; exit}')"
  if [ -z "$IDENTITY" ]; then
    echo "The Mac signing certificate did not import."
    exit 1
  fi
  if [ -d "$APP/Contents/PlugIns" ]; then
    find "$APP/Contents/PlugIns" -name '*.appex' -print0 | while IFS= read -r -d '' plugin; do
      codesign --force --sign "$IDENTITY" --options runtime --timestamp=none "$plugin"
    done
  fi
  codesign --force --sign "$IDENTITY" --options runtime --timestamp=none --entitlements entitlements.plist "$APP"
  codesign --verify --verbose=2 "$APP"
  rm -f "$WORK/sign.p12"
  echo "Signed with the stable BridgeLink certificate."
else
  if [ "${REQUIRE_MAC_SIGN:-}" = "1" ]; then
    echo "Mac signing secrets MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD are missing."
    exit 1
  fi
  if [ -d "$APP/Contents/PlugIns" ]; then
    find "$APP/Contents/PlugIns" -name '*.appex' -print0 | while IFS= read -r -d '' plugin; do
      codesign --force --sign - "$plugin"
    done
  fi
  codesign --force --sign - "$APP"
  echo "Ad-hoc signature. Permissions can reset after an update."
fi
