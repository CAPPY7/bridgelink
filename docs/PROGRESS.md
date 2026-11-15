# Progress

v0.8.0 is tagged on 1151c98. CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43681134725

Release: https://github.com/CAPPY7/bridgelink/releases/tag/v0.8.0

## Done in code
- Stage 1 bug fixes: macOS 15 open steps, disk image, icon, history on disk, battery both ways, every Mac address in the QR, scan button first, diagnostics, plain errors, firewall text, release fails without the Android key.
- The Mac app is still ad-hoc signed. GitHub returned 403 when storing MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD.

## Next exact step
Stage 2. New design. Android UI in Kotlin and Jetpack Compose. Mac menu popover plus a sidebar window. Light and dark. Do not mark features Final.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until those two secrets exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- Do not create a new Android signing key. Do not print secrets.
