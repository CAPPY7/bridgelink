# Progress

v1.0.0 is the Stage 7 release.

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in v0.11.0. Media control works both ways. The Mac can lock when the phone leaves, and it never unlocks. The Mac has one inbox. Clipboard history keeps 100 items and skips secrets. A phone scan is saved on the Mac and opened.
- Stage 5 medium features are in v0.12.0. Texts, a shared folder, automations, and the phone trackpad, keyboard, and slides.
- Stage 6 hard features are in v0.13.0. Screen mirror, control while mirroring, the phone camera inside BridgeLink, and an away relay that only forwards locked frames.
- Stage 7 polish is in v1.0.0. First-run setup on both apps, Check for update, Reset BridgeLink, and the docs for macOS 13–15 and Android 8–15.

## Next exact step
Fix-all pass, Step 1. C1 and C2 are done. Next is C3: the Mac must not open every received file by itself.

## Open problems
- The v0.13 audit is in docs/AUDIT_v0.13.md. Items not listed under Done below are still open.
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Check for update reads the Sparkle appcast and opens the release page. The Sparkle framework is not linked, because that needs an EdDSA secret that is not in this environment. It does not install the update by itself.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and Accessibility on the Mac so the lock shortcut works. The Mac is never unlocked by the phone.
- The trackpad also needs Accessibility on the Mac. BridgeLink shows the steps.
- Shared folder files over 32 MB are skipped on purpose.
- Screen mirror needs the phone to allow screen share each time Android asks. Control needs the accessibility service, including Allow restricted settings on Android 13 and newer.
- The phone camera inside BridgeLink is the free webcam. A system camera for Zoom needs a paid Apple Developer ID. That extension is in the repo and stays off.
- Away from home is not reliable yet (see C4 and C5 in the audit).
- The signed downloads are produced by the Release workflow. This environment cannot sign them or upload them.
- Do not create a new Android signing key. Do not print secrets.

## Fix-all done
- C1. Phone reconnects by itself after a clean disconnect. A bad key or a bad code does not.
- C2. A pairing link asks before it connects, and a different Mac does not receive the old token.

## Battery
The idle service does not poll in a tight loop. A connected phone sends one small heartbeat every 20 seconds. Battery is sent only when the level or the charger changes. The 40 millisecond wait exists only while a file send is asking how many bytes the other side already has, and it stops after 800 milliseconds. Screen mirror and the camera run only while those screens are open. This environment has no Android emulator, so a batterystats number was not measured. Do not treat a guessed milliamp figure as a result.