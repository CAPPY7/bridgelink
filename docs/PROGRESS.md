# Progress

Stage 3 is in the working tree as 0.10.0. It is not tagged yet.

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are implemented for 0.10.0: 4 GB streamed files, many files, folder zip, progress, cancel, open after save, share sheet, Mac drop zone, Finder service, Share extension, notification reply and dismiss, per-app block, Do Not Disturb option, battery alerts, find on the alarm stream, OTP from SMS, always-open links, clipboard text and images, rename, and the key-changed re-pair button.

## Next exact step
Run CI. If it is green, tag v0.10.0 and confirm the release has the APK and the DMG. Then start Stage 4.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can be hidden by macOS until the app is opened once. Finder Services, Send to phone, is the backup.
- Messages, remote, screen mirror, shared folder, webcam, and automations are still later stages.
- Do not create a new Android signing key. Do not print secrets.
