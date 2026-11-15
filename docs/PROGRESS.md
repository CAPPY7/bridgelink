# Progress

v0.9.0 is tagged on ae6339b. CI was green: https://github.com/CAPPY7/bridgelink/actions/runs/43687137407

Release: https://github.com/CAPPY7/bridgelink/releases/tag/v0.9.0

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0. The phone screen is Compose. The Mac app has a menu panel and a sidebar window. Pictures are in the README. The link code is unchanged.

## Next exact step
Start Stage 3 (version 0.10.0). Finish the half-done features in the order in docs/BUILD_PROMPT.md Part 4, Stage 3.

## Open problems
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Sparkle, the first-run wizard, and the in-app updater are not built yet.
- Messages, remote, screen mirror, shared folder, webcam, and automations are empty screens on purpose. Those features are later stages.
- Do not create a new Android signing key. Do not print secrets.