#!/bin/bash
# A disk image with the app and an Applications shortcut.
set -euo pipefail
cd "$(dirname "$0")"
STAGE=build/dmg-root
rm -rf "$STAGE" BridgeLink.dmg build/rw.dmg
mkdir -p "$STAGE/.background"
cp -R BridgeLink.app "$STAGE/"
ln -s /Applications "$STAGE/Applications"
if [ -f dmg-background.png ]; then
  cp dmg-background.png "$STAGE/.background/background.png"
fi
hdiutil create -size 120m -fs HFS+ -volname BridgeLink build/rw.dmg >/dev/null
mkdir -p build/mnt
hdiutil attach build/rw.dmg -mountpoint build/mnt >/dev/null
cp -R "$STAGE/." build/mnt/
sync
python3 - << 'PY' || echo "Finder layout skipped. The disk image still has the app and Applications."
import subprocess, sys
script = r'''tell application "Finder"
  tell disk "BridgeLink"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {200, 120, 860, 560}
    set viewOptions to the icon view options of container window
    set arrangement of viewOptions to not arranged
    set icon size of viewOptions to 96
    set background picture of viewOptions to file ".background:background.png"
    set position of item "BridgeLink.app" of container window to {180, 200}
    set position of item "Applications" of container window to {500, 200}
    close
    open
    update without registering applications
    delay 1
  end tell
end tell'''
try:
    subprocess.run(["osascript", "-e", script], timeout=25, check=False)
except subprocess.TimeoutExpired:
    sys.exit(1)
PY
hdiutil detach build/mnt >/dev/null
hdiutil convert build/rw.dmg -format UDZO -o BridgeLink.dmg >/dev/null
rm -f build/rw.dmg
echo "Built BridgeLink.dmg"
