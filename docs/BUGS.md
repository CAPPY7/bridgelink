# Bugs found in the Stage 1 audit

Each line is one problem. Fixed ones say Fixed and the idea of the change.

1. Mac install docs only said right-click Open. That is wrong on macOS 15. Fixed: docs now give the macOS 13–14 steps and the macOS 15 Open Anyway steps, plus the Terminal backup.
2. The Mac app was ad-hoc signed, so each update looked like a new app and macOS forgot permissions. The build signs with one certificate named BridgeLink Self-Signed when two GitHub secrets exist. Storing those secrets failed with 403, so today's builds stay ad-hoc until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD are added. The backup is not in the repo.
3. The Mac download was only a zip, with no app icon and no disk image. Fixed: the build makes BridgeLink.dmg and an icon, and still keeps the zip.
4. App.swift was one huge file. Fixed: the menu window, the connection, and the app entry are separate files.
5. The QR code contained only the first Mac address, which can be the wrong one. Fixed: every local address is in the pairing link, and the phone tries each one.
6. Pairing asked you to paste a link before it offered the scanner. Fixed: Scan the Mac QR is the first button. Paste and Find stay as backups.
7. The activity list lived in memory and disappeared on restart. Fixed: the phone stores it in a Room database, and the Mac stores it in a JSON file.
8. Phone battery was sent once, when pairing finished. The Mac never sent its battery. Fixed: each side sends battery when the level or the charging state changes.
9. If Android restarted the phone service, the new start had no command, so it did not reconnect. Fixed: a restart uses the saved Mac. Replacing the app also reconnects.
10. The release workflow could upload a debug APK when signing secrets were missing. Fixed: the release fails instead of publishing that APK.
11. If the Mac could not listen, the error did not mention the firewall. Fixed: the screen tells you to allow BridgeLink in System Settings, Network, Firewall.
12. Several errors named the failure and did not say what to do. Fixed: each link error includes a next step, and the raw exception text stays off the screen.
13. There was no way to save a safe log. Fixed: both apps have Send diagnostics. The file has version, connection, and port. It has no keys and no message text.
14. LinkService.java is still longer than 400 lines. Left open on purpose this round so the connection fixes stay in one place. Split it in the next pass before adding features.
15. The Mac app still cannot skip Apple's first-open warning. That is not a bug. A free sideload cannot be notarized. The disk image explains the one extra step.
