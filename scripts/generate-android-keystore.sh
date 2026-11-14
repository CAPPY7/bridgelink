#!/bin/bash
# Creates a release keystore. Do not commit the keystore or the passwords.
# Then store them as GitHub secrets. See docs/SETUP_FOR_OWNER.md.
set -euo pipefail
out="${1:-bridgelink-release.keystore}"
alias="${2:-bridgelink}"
store_pass="${3:-$(openssl rand -base64 24)}"
key_pass="${store_pass}"
keytool -genkeypair -v \
  -keystore "$out" \
  -alias "$alias" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$store_pass" -keypass "$key_pass" \
  -dname "CN=BridgeLink, O=CAPPY7"
echo "Wrote $out"
echo "ANDROID_KEY_ALIAS=$alias"
echo "ANDROID_KEYSTORE_PASSWORD=$store_pass"
echo "ANDROID_KEY_PASSWORD=$key_pass"
echo "Base64 for ANDROID_KEYSTORE_BASE64:"
base64 -w0 "$out" 2>/dev/null || base64 "$out" | tr -d '\n'
echo
