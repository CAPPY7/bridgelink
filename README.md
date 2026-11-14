# BridgeLink

A continuity link for an Android phone and a Mac.

The installable pair is on the [v0.2.0 release](https://github.com/CAPPY7/bridgelink/releases/tag/v0.2.0):

- `BridgeLink-android.apk` installs on the phone
- `BridgeLink-mac.zip` is the Mac menu-bar app

What that pair does: pair on the same Wi-Fi with a 6-digit PIN, send notes, send the clipboard, and ring the phone. Install steps are in [docs/INSTALL.md](docs/INSTALL.md).

The desk in this repo is the larger preview of the 25-feature list. It runs in the browser. It is not an installable Android or Mac binary.

## Build

Android, on a machine with the Android SDK:

```bash
cd android && ./gradlew assembleDebug
```

Mac, on macOS 13 or later:

```bash
bash macos/build.sh
```
