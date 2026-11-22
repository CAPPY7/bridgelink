#!/usr/bin/env python3
"""Write appcast.xml with the real disk-image size and the current date.

Sparkle is not linked, so this file has no signature. Check for update opens the release page.
"""
import datetime
import plistlib
import sys
from pathlib import Path

if len(sys.argv) < 3:
    print("usage: make-appcast.py <dmg> <appcast.xml> [tag]", file=sys.stderr)
    sys.exit(2)

root = Path(__file__).resolve().parents[1]
dmg = Path(sys.argv[1])
out = Path(sys.argv[2])
if not dmg.is_file():
    print("disk image is missing", file=sys.stderr)
    sys.exit(1)
plist = plistlib.loads((root / "macos/Info.plist").read_bytes())
short = str(plist["CFBundleShortVersionString"])
build = str(plist["CFBundleVersion"])
tag = sys.argv[3] if len(sys.argv) > 3 else "v" + short
if not tag.startswith("v"):
    print("tag must start with v", file=sys.stderr)
    sys.exit(1)
size = dmg.stat().st_size
if size <= 0:
    print("disk image is empty", file=sys.stderr)
    sys.exit(1)
when = datetime.datetime.now(datetime.timezone.utc).strftime("%a, %d %b %Y %H:%M:%S +0000")
url = f"https://github.com/CAPPY7/bridgelink/releases/download/{tag}/BridgeLink.dmg"
text = f"""<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
  <channel>
    <title>BridgeLink</title>
    <link>https://github.com/CAPPY7/bridgelink/releases</link>
    <description>BridgeLink updates. This file has no signature because the Sparkle framework is not linked.</description>
    <item>
      <title>Version {short}</title>
      <sparkle:version>{build}</sparkle:version>
      <sparkle:shortVersionString>{short}</sparkle:shortVersionString>
      <sparkle:minimumSystemVersion>13.0</sparkle:minimumSystemVersion>
      <pubDate>{when}</pubDate>
      <enclosure
        url="{url}"
        sparkle:version="{build}"
        sparkle:shortVersionString="{short}"
        length="{size}"
        type="application/octet-stream" />
    </item>
  </channel>
</rss>
"""
out.write_text(text)
print(f"appcast {short} ({build}) length {size}")
