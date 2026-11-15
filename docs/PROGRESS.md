# Progress

v0.7.0 is the bug-fix release. It is not tagged until CI on that commit is green.

## Done in code
- Pairing with commit-then-reveal, emulator test, signed v0.4.0 and v0.6.0.
- Files both ways, resume, links, share sheet, Quick Settings tile, menu-bar drop, find, clipboard echo, battery, notification and OTP hooks.
- v0.7.0: connection threads, non-blocking close, typed link errors, background-start queue, notification privacy, restricted-settings help, dead helper removal, feature matrix gate.

## Next exact step
Wait for CI on the v0.7.0 commit. If it is green, tag v0.7.0. Then start Stage 2: Kotlin Compose shell, light and dark theme, adaptive icon, Mac icon. Do not mark any feature Done until the owner replies "worked".

## Open problems
- Apple Developer ID is not decided. The Mac app stays ad-hoc signed until the owner answers. Permissions can reset after each update.
- Theme is still hard-coded dark on Android. No adaptive icon. No Mac app icon. That is Stage 2.
- Features 4, 9, 11-21 are not in the apps.
- The owner has not run the manual tests yet, so nothing is Done.
- Do not create a new signing key. Do not print secrets. The release keystore stays outside the repo, in the owner's own backup of bridgelink-release.keystore.
