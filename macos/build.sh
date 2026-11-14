#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
swift test
rm -rf build BridgeLink.app
mkdir -p build
swift build -c release --triple arm64-apple-macosx13.0 --product BridgeLink
swift build -c release --triple x86_64-apple-macosx13.0 --product BridgeLink
ARM="$(find .build -type f -path '*arm64*release/BridgeLink' ! -path '*.dSYM*' | head -1)"
X64="$(find .build -type f -path '*x86_64*release/BridgeLink' ! -path '*.dSYM*' | head -1)"
if [ -z "$ARM" ] || [ -z "$X64" ]; then
  echo "Universal build failed. Refusing to ship an arm64-only app."
  find .build -type f -name BridgeLink -print
  exit 1
fi
lipo -create -output build/BridgeLink "$ARM" "$X64"
mkdir -p BridgeLink.app/Contents/MacOS
cp build/BridgeLink BridgeLink.app/Contents/MacOS/BridgeLink
cp Info.plist BridgeLink.app/Contents/Info.plist
chmod +x BridgeLink.app/Contents/MacOS/BridgeLink
codesign --force --sign - BridgeLink.app
ditto -c -k --keepParent BridgeLink.app BridgeLink-mac.zip
echo "Built universal BridgeLink-mac.zip"
