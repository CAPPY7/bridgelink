# Contributing

The installable apps are `android/` and `macos/`. Do not treat the browser demo as the product.

- Do not mark a feature done in the README unless the code is wired up and a test or a manual test covers it.
- Keep `versionName`, `CFBundleShortVersionString`, `package.json`, and the Mac About line on the same number. `python3 scripts/check-versions.py` checks this.
- Protocol changes need a fixture in `docs/fixtures` and a passing test on both sides.
- Do not commit a keystore, `local.properties`, or `macos/.build`.
- The Mac package is built on the macos-15 GitHub runner. A green Actions run is required before a tag.
