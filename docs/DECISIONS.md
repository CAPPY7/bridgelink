# Decisions

## One desk instead of two native installers

The preview can run a web app. It cannot compile Kotlin, sign an APK, or build a macOS `.app`. BridgeLink is therefore the working product: phone UI and Mac UI sharing one protocol, one paired link, and one history.

## Protocol

JSON envelopes, message ids, chunked file reads, and AES-GCM for the relay path. See `src/lib/bridgelink/protocol.ts`. Local traffic stays in the desk. The relay path seals the envelope before the log records only the sealed length.

## Pairing

A six-digit PIN expires in two minutes. After a match, each side pins a public-key fingerprint. A changed fingerprint blocks sending until you trust or reject it.

## What stays partial

Screen mirror is a live view of the phone UI, not an H.264 hardware encode. Control uses the accessibility path, not wireless ADB. The camera is a preview window, not a virtual camera. Shared folders can be browsed and copied, not mounted in Finder. Away-from-home is a path you choose (local, saved host, encrypted relay), not a hosted relay or Firebase project. Scan cleans contrast and does not OCR.

## Not attempted

iMessage, FaceTime, Apple AirDrop or Handoff, unlocking the Mac, silent system setting changes, running Android apps on the Mac, and phone-call audio.
