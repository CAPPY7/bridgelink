# Progress

v0.8.0 is tagged on 1151c98. CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43681134725

Release: https://github.com/CAPPY7/bridgelink/releases/tag/v0.8.0

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 screen work is in progress on top of that. The phone screen is Compose. The Mac app has a menu panel and a sidebar window. The link code is unchanged.
- Screenshots are drawn by the Mac build and by the emulator test, then copied into docs/screenshots.

## Next exact step
Finish Stage 2. Keep CI green, put the screenshots in the repo, tag v0.9.0.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- Messages, remote, screen mirror, shared folder, webcam, and automations are empty screens on purpose. Those features are later stages.
- Do not create a new Android signing key. Do not print secrets.
