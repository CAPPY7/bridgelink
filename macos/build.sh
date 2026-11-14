#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="$(xcrun --show-sdk-path)"
rm -rf build BridgeLink.app
mkdir -p build

compile() {
  local target="$1"
  local output="$2"
  swiftc -sdk "$SDK" -target "$target" -O -parse-as-library \
    -framework SwiftUI -framework AppKit -framework Network \
    -o "$output" Sources/BridgeLink.swift
}

compile arm64-apple-macos13.0 build/BridgeLink-arm64
if compile x86_64-apple-macos13.0 build/BridgeLink-x64; then
  lipo -create -output build/BridgeLink build/BridgeLink-arm64 build/BridgeLink-x64
else
  cp build/BridgeLink-arm64 build/BridgeLink
fi

mkdir -p BridgeLink.app/Contents/MacOS
cp build/BridgeLink BridgeLink.app/Contents/MacOS/BridgeLink
cp Info.plist BridgeLink.app/Contents/Info.plist
chmod +x BridgeLink.app/Contents/MacOS/BridgeLink
codesign --force --deep --sign - BridgeLink.app
ditto -c -k --keepParent BridgeLink.app BridgeLink-mac.zip
echo "Built BridgeLink-mac.zip"
