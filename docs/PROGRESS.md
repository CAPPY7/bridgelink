# Progress

v0.13.0 is the Stage 6 release.

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in v0.11.0. Media control works both ways. The Mac can lock when the phone leaves, and it never unlocks. The Mac has one inbox. Clipboard history keeps 100 items and skips secrets. A phone scan is saved on the Mac and opened.
- Stage 5 medium features are in v0.12.0. Texts, a shared folder, automations, and the phone trackpad, keyboard, and slides.
- Stage 6 hard features are in v0.13.0. Screen mirror, control while mirroring, the phone camera inside BridgeLink, and an away relay that only forwards locked frames.

## Next exact step
Start Stage 7 (version 1.0.0). Polish every feature, check the docs, and ship the final sideload build.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and Accessibility on the Mac so the lock shortcut works. The Mac is never unlocked by the phone.
- The trackpad also needs Accessibility on the Mac. BridgeLink shows the steps.
- Shared folder files over 32 MB are skipped on purpose.
- Screen mirror needs the phone to allow screen share each time Android asks. Control needs the accessibility service, including Allow restricted settings on Android 13 and newer.
- The phone camera inside BridgeLink is the free webcam. A system camera for Zoom needs a paid Apple Developer ID. That extension is in the repo and stays off.
- Away from home needs the relay address on both devices, and one Wi-Fi pairing first so they share a key. The relay cannot read the frames.
- Do not create a new Android signing key. Do not print secrets.