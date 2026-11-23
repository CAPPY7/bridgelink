# BridgeLink

Installable link between an Android phone and a Mac menu-bar app. Version 1.0.

Downloads, after the release workflow runs: https://github.com/CAPPY7/bridgelink/releases/latest

The page at the root of this repo is a **simulated demo**. It does not talk to the phone or the Mac. The apps in `/android` and `/macos` are the product. In the demo, walk through Set up BridgeLink, then Check for update and Reset BridgeLink in Settings.


## Screens

Light and dark follow the system. A screen that is not built yet says so.

### Phone

<img src="docs/screenshots/android-home.png" alt="Phone home" width="180">
<img src="docs/screenshots/android-send.png" alt="Phone send" width="180">
<img src="docs/screenshots/android-notifications.png" alt="Phone notifications" width="180">
<img src="docs/screenshots/android-messages.png" alt="Phone messages" width="180">
<img src="docs/screenshots/android-files.png" alt="Phone files" width="180">
<img src="docs/screenshots/android-remote.png" alt="Phone remote" width="180">
<img src="docs/screenshots/android-mirror.png" alt="Phone mirror" width="180">
<img src="docs/screenshots/android-settings.png" alt="Phone settings" width="180">

### Mac menu bar

<img src="docs/screenshots/mac-popover.png" alt="Mac menu bar" width="280">
<img src="docs/screenshots/mac-popover-dark.png" alt="Mac menu bar in dark mode" width="280">

### Mac window

<img src="docs/screenshots/mac-home.png" alt="Mac home" width="320">
<img src="docs/screenshots/mac-home-dark.png" alt="Mac home in dark mode" width="320">
<img src="docs/screenshots/mac-notifications.png" alt="Mac notifications" width="320">
<img src="docs/screenshots/mac-messages.png" alt="Mac messages" width="320">
<img src="docs/screenshots/mac-files.png" alt="Mac files" width="320">
<img src="docs/screenshots/mac-clipboard.png" alt="Mac clipboard" width="320">
<img src="docs/screenshots/mac-shared.png" alt="Mac shared folder" width="320">
<img src="docs/screenshots/mac-mirror.png" alt="Mac mirror" width="320">
<img src="docs/screenshots/mac-webcam.png" alt="Mac webcam" width="320">
<img src="docs/screenshots/mac-automations.png" alt="Mac automations" width="320">
<img src="docs/screenshots/mac-devices.png" alt="Mac devices" width="320">
<img src="docs/screenshots/mac-settings.png" alt="Mac settings" width="320">

## Install

See [docs/INSTALL.md](docs/INSTALL.md) and [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Status of the 25 features

Honest status of the **installable apps**. The demo is not counted. Status words are Not started, Building, Built – NOT TESTED on a device, or Final – owner tested. See [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md).

What was tested: CI unit tests and the emulator pairing test. Nothing in this build was tried on a real phone or Mac.

| # | Feature | Status |
|---|---|---|
| 1 | Send files, links, and text | Built – NOT TESTED on a device. Files, folders as zip, up to 4 GB, progress, cancel, and resume |
| 2 | Share sheet and drag-drop | Built – NOT TESTED on a device. Android share sends many files. Mac drop, drop zone, Finder Services, and Share |
| 3 | Notification mirroring | Built – NOT TESTED on a device. Banners, reply when the app allows it, dismiss both ways, per-app block, Do Not Disturb option |
| 4 | Media control | Built – NOT TESTED on a device |
| 5 | Battery status | Built – NOT TESTED on a device. Both ways, plus low and full alerts |
| 6 | Find phone | Built – NOT TESTED on a device. Rings on the alarm stream so silent mode does not hide it. Either side can stop it |
| 7 | OTP auto-copy | Built – NOT TESTED on a device. Codes from notifications and SMS, cleared on the Mac after 60 seconds |
| 8 | Open a link on the other device | Built – NOT TESTED on a device. Both ways, with an always-open option |
| 9 | Trackpad, keyboard, slides | Built – NOT TESTED on a device. Drag, click, two-finger scroll, right-click, typing, slides, and a laser dot. The Mac needs Accessibility |
| 10 | Clipboard sync | Built – NOT TESTED on a device. On or off, skips sensitive text, sends small pictures |
| 11 | SMS | Built – NOT TESTED on a device. Read, send, banners, and a SIM choice. Allow SMS on the phone |
| 12 | Screen mirror | Built – NOT TESTED on a device. Phone screen in a Mac window. Closing the window stops it. Quality is low, medium, or high |
| 13 | Control the phone while mirroring | Built – NOT TESTED on a device. Click, drag, scroll, and type through the phone accessibility service. Back, Home, and Recents. The Mac never unlocks the phone |
| 14 | Phone as webcam | Built – NOT TESTED on a device. Live camera inside BridgeLink, with front or back, zoom, flash, and mic. A Zoom or FaceTime camera needs a paid Apple Developer ID, so that extension stays off |
| 15 | Shared folder | Built – NOT TESTED on a device. Both ways, conflict copies, pause. You can choose the phone folder. Files over 32 MB use the normal file send and land in the shared folder |
| 16 | Lock the Mac when you leave | Built – NOT TESTED on a device |
| 17 | Remote link | Removed. The phone and the Mac stay on the same Wi-Fi |
| 18 | Unified inbox | Removed. Notifications, texts, and files stay in their own places |
| 19 | Clipboard history | Built – NOT TESTED on a device |
| 20 | Scan to Mac | Built – NOT TESTED on a device |
| 21 | Automations | Built – NOT TESTED on a device. Up to eight when-this-then-that rules |
| 22 | Quick drop | Built – NOT TESTED on a device. Quick Settings tile, menu-bar drop, and a floating drop zone |
| 23 | Pairing | Built – NOT TESTED on a device. QR, several phones and Macs, rename, and forget |
| 24 | Encrypted local link | Built – NOT TESTED on a device. TLS on the local link, with the Mac key pinned |
| 25 | Key pin | Built – NOT TESTED on a device. If the Mac key changed, forget it and scan again |

## Build

```bash
cd android && ./gradlew test assembleDebug
bash macos/build.sh
```

The Mac build needs macOS 15 and produces a universal app, or it fails. It does not silently drop the Intel slice.
