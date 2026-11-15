# Progress

v0.10.0 is tagged on this commit. CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43691415465

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0. Files go up to 4 GB, with many files, folder zip, progress, cancel, and open after save. Share, drop, notifications, battery alerts, find, codes, links, and clipboard are in both apps.

## Next exact step
Start Stage 4 (version 0.11.0). Easy new features, in the order in docs/BUILD_PROMPT.md Part 4, Stage 4: media control, lock the Mac when you leave, unified inbox, clipboard history, scan to Mac.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- Messages, remote, screen mirror, shared folder, webcam, and automations are still later stages.
- Do not create a new Android signing key. Do not print secrets.
