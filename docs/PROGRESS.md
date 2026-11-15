# Progress

v0.11.0 is the Stage 4 release. The code CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43696134332

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in v0.11.0. Media control works both ways. The Mac can lock when the phone leaves, and it never unlocks. The Mac has one inbox. Clipboard history keeps 100 items and skips secrets. A phone scan is saved on the Mac and opened.

## Next exact step
Start Stage 5 (version 0.12.0). Medium features, in the order in docs/BUILD_PROMPT.md Part 4, Stage 5: SMS, shared folder, automations, then the phone as a trackpad and keyboard.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and Accessibility on the Mac so the lock shortcut works. The Mac is never unlocked by the phone.
- Screen mirror, shared folder, webcam, and automations are still later stages.
- Do not create a new Android signing key. Do not print secrets.
