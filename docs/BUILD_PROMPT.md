# Build prompt summary

This is the short form of the owner's finishing prompt. It has no secrets.

The product is the Android app and the Mac menu-bar app in this repo. The browser page is a labeled simulation.

Work in order. After a stage that changes behavior: tests, green CI, commit, push, and a release tag. The README status table uses only Done, Partial, Experimental, or Not started. Do not mark a feature Done without code, CI, and a test.

Do not attempt iMessage, FaceTime, AirDrop, Handoff, unlocking the Mac, silent system-setting changes, Android apps in Mac windows, or phone-call audio.

Local link: TLS, frames of 1 byte type plus a 4 byte big-endian length, JSON envelope version 1, port 8787, Bonjour `_bridgelink._tcp`. Package names `app.bridgelink.android` and `app.bridgelink.mac`.

Stages:

0. Confirm the release key, docs, and CI.
1. v0.4.0 security and link reliability. Pairing proof is HMAC-SHA256 of the certificate fingerprint keyed by the PIN. Never send the raw PIN. Pending connections are capped and timed out. Reconnect stops on fatal errors. More than one phone can stay paired.
2. v0.5.0 design system and app shells.
3. v0.6.0 through v1.0.0 add the remaining features only when they are real.

Android UI moves to Kotlin and Compose when a screen is rewritten. The Mac app stays SwiftUI. Distribution is GitHub Releases.
