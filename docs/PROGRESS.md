# Progress

v0.12.0 is the Stage 5 release. The code CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43704475077

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in v0.11.0. Media control works both ways. The Mac can lock when the phone leaves, and it never unlocks. The Mac has one inbox. Clipboard history keeps 100 items and skips secrets. A phone scan is saved on the Mac and opened.
- Stage 5 medium features are in v0.12.0. Texts, a shared folder, automations, and the phone trackpad, keyboard, and slides.

## Next exact step
Start Stage 6 (version 0.13.0). Hard features, in the order in docs/BUILD_PROMPT.md Part 4, Stage 6: screen mirror, control the phone while mirroring, the phone as a webcam, then away from home.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and Accessibility on the Mac so the lock shortcut works. The Mac is never unlocked by the phone.
- The trackpad also needs Accessibility on the Mac. BridgeLink shows the steps.
- Shared folder files over 32 MB are skipped on purpose.
- Screen mirror, webcam, and away-from-home are still later stages.
- Do not create a new Android signing key. Do not print secrets.