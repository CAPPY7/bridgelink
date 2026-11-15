# Progress

v0.7.0 is tagged on b6d3588. CI was green, including the emulator: https://github.com/CAPPY7/bridgelink/actions/runs/43677930845

## Done in code
- Pairing with commit-then-reveal, emulator test, signed v0.4.0, v0.6.0, and v0.7.0.
- Files both ways, resume, links, share sheet, Quick Settings tile, menu-bar drop, find, clipboard echo, battery, notification and OTP hooks.
- v0.7.0: connection threads, close off the main thread, 20 second write deadline, typed link errors, background-start queue, notification privacy, restricted-settings help, dead helper removal, feature matrix gate.

## Next exact step
Stage 2. Rebuild the Android UI in Kotlin and Jetpack Compose with Material 3, light and dark mode, an adaptive icon, and a Mac icon. Keep the service and protocol. Do not mark any feature Done until the owner replies "worked".

## Open problems
- The Mac app is sideloaded. No Apple Developer ID. Permissions can reset after each update.
- Theme is still hard-coded dark on Android. No adaptive icon. No Mac app icon. That is Stage 2.
- Features 4, 9, 11-21 are not in the apps.
- The owner has not run the manual tests yet, so nothing is Done.
- Do not create a new signing key. Do not print secrets. The release keystore stays outside the repo, in the owner's own backup of bridgelink-release.keystore.
