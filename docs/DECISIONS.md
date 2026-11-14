# Decisions

## v0.3.0 is the native foundation, not the full desk

The product is the Android app and the Mac menu-bar app. The page under `src/` is a labeled simulation for the browser preview. Its feature table is not the status of the installable apps. The README table is.

## Java on Android, for now

The phone app stays Java. A Kotlin and Jetpack Compose rewrite would throw away a working service, and this pass had to keep the GitHub build green. Compose is still the intended UI once the link itself is solid. Recorded here so it is a choice, not an accident.

## TLS is server authentication plus a pin, not mutual TLS

The Mac certificate is made with the system `openssl` and stored as a PKCS#12 blob in the login keychain. Swift's certificate package kept its DER helper internal, so the app does not depend on it. The phone pins the SHA-256 of that certificate. The phone does not present its own certificate in 0.3.0. A changed Mac key fails the handshake with a visible error. Forget, then pair again, to trust a new key.

## Pairing

The Mac shows a real QR (Core Image) whose text is `bridgelink://pair?v=1&host&port&fp&pin`. The phone pastes that link. It can also discover `_bridgelink._tcp` and type the PIN. There is no in-app camera scanner yet, so scanning is "open the camera app, copy the link, paste it". The PIN is single-use, lasts 2 minutes, locks for 60 seconds after 5 failures, and is not saved on the phone. After pairing, a random token is saved. An unauthenticated connection does not replace the paired phone.

## Secrets on the phone

The token and fingerprint go through EncryptedSharedPreferences, which uses the Android Keystore. If that fails to open, the app falls back to a normal private preferences file and still runs. The PIN is not stored.

## History

The phone and the Mac keep a short in-memory log. There is no Room or SQLite history yet. That is why clipboard history and the unified inbox are "Not started".

## What 0.3.0 does not do

No file transfer, notification mirroring, SMS, screen mirror, webcam, shared folder, relay, or FCM. Those stay "Not started" until the code exists. The Mac app is ad-hoc signed. Hardened runtime is not enabled: there is no Developer ID, and the keychain identity is more reliable without it. Release APKs are signed with a stable key only after the four GitHub secrets exist. Otherwise the release is clearly named `BridgeLink-android-debug.apk`.

## Web demo stays

The preview host requires the existing web shell, including its auth provider and platform branding. The public CI job typechecks that shell and runs the demo protocol test. It does not run the sandbox skill tests, because those read files that are not part of this repository.

## Not attempted

iMessage, FaceTime, AirDrop, Handoff, unlocking the Mac, silent system setting changes, running Android apps on the Mac, and phone-call audio.
