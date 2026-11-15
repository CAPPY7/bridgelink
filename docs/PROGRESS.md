# Progress

Stage 4 code is in this commit. Version is still 0.10.0 until CI is green, then tag v0.11.0.

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in the working tree: media control both ways, lock the Mac when the phone leaves, a Mac inbox, clipboard history, and scan to Mac.

## Next exact step
Wait until CI is green. Then bump to 0.11.0, tag v0.11.0, and check the release has the signed APK and the DMG.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and a Mac that is allowed to lock the screen. The Mac is never unlocked by the phone.
- Messages, remote trackpad, screen mirror, shared folder, webcam, and automations are still later stages.
- Do not create a new Android signing key. Do not print secrets.
