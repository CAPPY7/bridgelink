# Decisions

## Installable apps, v0.2.0

The release now has two real apps:

- Android: a Java app, debug-signed APK, foreground service, port 8787.
- Mac: a SwiftUI menu-bar app. It is compiled on a GitHub macOS runner because this environment is Linux and cannot produce a Mach-O Mac binary.

They speak newline-delimited JSON over the local network. Features in this pair: pair with a PIN, notes, clipboard, and ring. TLS pinning, file transfer, notification mirroring, and screen mirror stay in the browser desk. The Mac app is ad-hoc signed. There is no Apple Developer certificate and no Play Store listing.

## One desk instead of two native installers

The preview can run a web app. The browser desk is still the place the full feature list is demonstrated. See the feature guide inside the desk.

## Protocol

The desk uses JSON envelopes, message ids, chunked file reads, and AES-GCM for the relay path. See `src/lib/bridgelink/protocol.ts`. The installable apps use a smaller line protocol: `v`, `type`, `from`, `body`, plus `pin` and `name` on hello.

## Pairing

The Mac shows a six-digit PIN. The phone must send that PIN. A wrong PIN is rejected.

## What stays partial

Screen mirror in the desk is a live view of the phone UI, not an H.264 hardware encode. The installable apps do not mirror the screen. The camera in the desk is a preview window, not a virtual camera. Shared folders can be browsed and copied, not mounted in Finder.

## Not attempted

iMessage, FaceTime, Apple AirDrop or Handoff, unlocking the Mac, silent system setting changes, running Android apps on the Mac, and phone-call audio.
