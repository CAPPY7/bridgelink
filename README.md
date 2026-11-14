# BridgeLink

Installable link between an Android phone and a Mac menu-bar app.

Downloads: https://github.com/CAPPY7/bridgelink/releases/latest

The page at the root of this repo is a **simulated demo**. It does not talk to the phone or the Mac. The apps in `/android` and `/macos` are the product.

## Install

See [docs/INSTALL.md](docs/INSTALL.md) and [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Status of the 25 features

Honest status of the **installable apps** in 0.3.0. The demo is not counted.

| # | Feature | Status |
|---|---|---|
| 1 | Send files, links, and text | Partial. Text notes only |
| 2 | Share sheet and drag-drop | Not started |
| 3 | Notification mirroring | Not started |
| 4 | Media control | Not started |
| 5 | Battery status | Not started |
| 6 | Find phone | Partial. Rings at alarm max volume, stops after 2 minutes |
| 7 | OTP auto-copy | Not started |
| 8 | Open a link on the other device | Not started |
| 9 | Trackpad, keyboard, slides | Not started |
| 10 | Clipboard sync | Partial. Manual send. Mac can auto-apply, ask, or ignore. Sensitive text waits |
| 11 | SMS | Not started |
| 12 | Screen mirror | Not started |
| 13 | Control the phone while mirroring | Not started |
| 14 | Phone as webcam | Not started |
| 15 | Shared folder | Not started |
| 16 | Lock the Mac when you leave | Not started |
| 17 | Away from home | Not started |
| 18 | Unified inbox | Not started |
| 19 | Clipboard history | Not started |
| 20 | Scan to Mac | Not started |
| 21 | Automations | Not started |
| 22 | Quick drop | Not started |
| 23 | Pairing | Partial. QR link, pasted link, Wi-Fi discovery, one-time PIN, saved token. No in-app camera scanner |
| 24 | End-to-end encryption | Partial. TLS on the local link. No relay |
| 25 | Key pin | Partial. Phone pins the Mac certificate after the first pair |

## Build

```bash
cd android && ./gradlew test assembleDebug
bash macos/build.sh
```

The Mac build needs macOS 15 and produces a universal app, or it fails. It does not silently drop the Intel slice.
