# Progress

v0.8.0 is tagged on 1151c98. CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43681134725

Release: https://github.com/CAPPY7/bridgelink/releases/tag/v0.8.0

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 screen work is in the tree at version 0.9.0. The phone screen is Compose. The Mac app has a menu panel and a sidebar window. The link code is unchanged.
- The emulator test writes each phone screen to the log so CI can save it. The Mac build draws each Mac screen.

## Next exact step
Wait until CI is green, commit the pictures into docs/screenshots, show them in the README, then tag v0.9.0.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- Messages, remote, screen mirror, shared folder, webcam, and automations are empty screens on purpose. Those features are later stages.
- Do not create a new Android signing key. Do not print secrets.