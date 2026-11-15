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
mkdir -p BridgeLink.app/Contents/MacOS BridgeLink.app/Contents/Resources
cp build/BridgeLink BridgeLink.app/Contents/MacOS/BridgeLink
cp Info.plist BridgeLink.app/Contents/Info.plist
chmod +x BridgeLink.app/Contents/MacOS/BridgeLink
if [ -f AppIcon.png ]; then
  ICONSET=build/AppIcon.iconset
  rm -rf "$ICONSET"
  mkdir -p "$ICONSET"
  for size in 16 32 64 128 256 512; do
    sips -z "$size" "$size" AppIcon.png --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  done
  cp "$ICONSET/icon_32x32.png" "$ICONSET/icon_16x16@2x.png"
  cp "$ICONSET/icon_64x64.png" "$ICONSET/icon_32x32@2x.png"
  cp "$ICONSET/icon_256x256.png" "$ICONSET/icon_128x128@2x.png"
  cp "$ICONSET/icon_512x512.png" "$ICONSET/icon_256x256@2x.png"
  sips -z 1024 1024 AppIcon.png --out "$ICONSET/icon_512x512@2x.png" >/dev/null
  iconutil -c icns "$ICONSET" -o build/AppIcon.icns
  cp build/AppIcon.icns BridgeLink.app/Contents/Resources/AppIcon.icns
fi
bash "$(dirname "$0")/sign.sh" BridgeLink.app
ditto -c -k --keepParent BridgeLink.app BridgeLink-mac.zip
bash "$(dirname "$0")/make-dmg.sh"
echo "Built universal BridgeLink-mac.zip and BridgeLink.dmg"
