#!/usr/bin/env python3
"""The Mac build must not ship a share dylib pretending to be an extension."""
from pathlib import Path

text = Path("macos/build.sh").read_text()
if "emit-library" in text or "Share.appex" in text:
    raise SystemExit("share extension must not be bundled as a dylib")
if Path("macos/share").exists():
    raise SystemExit("macos/share should be deleted")
print("share build ok")
