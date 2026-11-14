# Testing

Unit tests run on every push and again before a release is published.

| Check | Where | What it proves |
|---|---|---|
| `./gradlew test` | CI `android` job, release workflow | Pairing proof, commit-then-reveal, sealed token, Mac list, frames |
| `bash macos/build.sh` | CI `mac` job, release workflow | Swift codec, identity, universal binary |
| `connectedDebugAndroidTest` | CI `emulator` job, API 34 | The installed app pairs with a fake Mac by QR, sends a note, forgets so the token is gone, then pairs again by match code |
| Release APK `adb install` | Same emulator job, before the test | The signed release APK installs and its main screen opens |

The emulator job retries the instrumentation test once. A second failure fails the build.

CI cannot see a real phone, a real Mac, Play Services scan, or the system share sheet. Those stay on the manual list in the release notes.
