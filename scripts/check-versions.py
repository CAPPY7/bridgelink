#!/usr/bin/env python3
"""Fail if the Android, Mac, and package versions drift apart."""
import re
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
gradle = (root / "android/app/build.gradle").read_text()
plist = (root / "macos/Info.plist").read_text()
package = (root / "package.json").read_text()
about = (root / "macos/Sources/BridgeLink/App.swift").read_text()

name = re.search(r'versionName "([^"]+)"', gradle).group(1)
code = re.search(r'versionCode (\d+)', gradle).group(1)
short = re.search(r"CFBundleShortVersionString</key>\s*<string>([^<]+)</string>", plist).group(1)
bundle = re.search(r"CFBundleVersion</key>\s*<string>([^<]+)</string>", plist).group(1)
pkg = re.search(r'"version": "([^"]+)"', package).group(1)

errors = []
if not (name == short == pkg):
    errors.append(f"version name mismatch android={name} mac={short} package={pkg}")
if code != bundle:
    errors.append(f"version code mismatch android={code} mac={bundle}")
if f"BridgeLink {name}" not in about:
    errors.append(f"Mac About sheet does not say BridgeLink {name}")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"versions ok {name} ({code})")
