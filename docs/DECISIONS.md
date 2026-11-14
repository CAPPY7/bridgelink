# Decisions

## v0.4.0 pairing proof

The first hello sends `proof` only when the QR link already contained the certificate fingerprint and the live certificate matches it. A typed address does not send a PIN proof. Both screens show a 6-digit match code from the certificate the phone saw. The user confirms on both. A relay sees a different code and is not given a token. This replaced the v0.3.0 hello, so 0.3.0 and 0.4.0 cannot pair. The Mac stores SHA-256 hashes of device tokens. The PIN on the Mac refreshes itself after 120 seconds.

Android reconnect is a small state machine: fatal errors do not retry. Forget on the phone sends a `forget` message, then drops the token. The UI is still the existing Java form. Kotlin and Compose wait until the shell is replaced, because rewriting it in the same release as the pairing change would hide the security fix.

The phone seals the pairing token with AES-256-GCM. The key is created in the Android Keystore and is not exported. The cipher generates the IV. A caller-supplied IV is rejected by the keystore, and that failure used to drop the token while the screen still said paired. If the keystore cannot be opened, or the seal does not read back, the token is not written to a file and the screen says so. `androidx.security:security-crypto` is no longer a dependency. Host, port, and certificate fingerprint are not the token; they stay in a private preferences file so the phone can still show the saved Mac.

The Mac identity is still created with openssl because Swift has no small X.509 writer here. The certificate lasts 10 years. A new identity uses a random PKCS#12 password stored in the keychain, not a password in the source. Identities created by 0.3.0 still open with the old fixed password. After the first import the identity is labeled and later launches load that label, so the keychain does not gain another copy. A unit test checks that loading twice keeps the same fingerprint and the same identity count.

The in-app QR scanner uses Google’s code scanner (Play services). It does not add a CAMERA permission. Phones without Play services can still paste the link or use the camera app, which opens `bridgelink://`. Opening that link connects at once. The fingerprint is committed to preferences before the service starts, because an unflushed write would make the phone treat a QR pair as a match-code pair.

The read loop owns the single network thread. Forget and match-code confirm run on their own threads so they can close that socket. CI installs a release APK on an API 34 emulator, then runs `PairingEmulatorTest`: QR pair, one note, forget (the token is gone), then a second pair by match code. That job is required. It is not a substitute for a test on a real phone.

SetTextI18n and UnusedAttribute stay disabled. Status lines are built in Java. HardcodedText and ContentDescription are on.

## v0.6.0 files and links

A dropped transfer keeps the same file id. The receiver answers `file_have` with the bytes already stored, and the sender continues from there. The menu-bar icon is an AppKit status item so it can accept a file drop. Command-Shift-D opens that panel. Flash during Find is off unless the ring message asks for it, because the camera permission is separate.

The share sheet copies a shared file into the app cache first, because the temporary URI grant ends when the share activity closes. The Quick Settings tile only opens the app. It does not read the clipboard in the background.

## v0.3.0 is the native foundation, not the full desk

The product is the Android app and the Mac menu-bar app. The page under `src/` is a labeled simulation for the browser preview. Its feature table is not the status of the installable apps. The README table is.

## Java on Android, for now

The phone app stays Java. A Kotlin and Jetpack Compose rewrite would throw away a working service, and this pass had to keep the GitHub build green. Compose is still the intended UI once the link itself is solid. Recorded here so it is a choice, not an accident.

## TLS is server authentication plus a pin, not mutual TLS

The Mac certificate is made with the system `openssl` and stored as a PKCS#12 blob in the login keychain. Swift's certificate package kept its DER helper internal, so the app does not depend on it. The phone pins the SHA-256 of that certificate. The phone does not present its own certificate in 0.3.0. A changed Mac key fails the handshake with a visible error. Forget, then pair again, to trust a new key.

## Pairing

The Mac shows a real QR (Core Image) whose text is `bridgelink://pair?v=1&host&port&fp&pin`. The phone can scan that QR in the app, paste the link, or open it from the camera app. It can also discover `_bridgelink._tcp` and type the PIN. The PIN is single-use, lasts 2 minutes, locks for 60 seconds after 5 failures, and is not saved on the phone. After pairing, a random token is saved. An unauthenticated connection does not replace the paired phone.

## Secrets on the phone

The token is sealed with a Keystore AES-GCM key. The fingerprint and the list of Mac names are not the token. If the keystore fails, the app refuses to store the token and shows a warning. The PIN is not stored.

## History

The phone and the Mac keep a short in-memory log. There is no Room or SQLite history yet. That is why clipboard history and the unified inbox are "Not started".

## What 0.3.0 does not do

No file transfer, notification mirroring, SMS, screen mirror, webcam, shared folder, relay, or FCM. Those stay "Not started" until the code exists. The Mac app is ad-hoc signed. Hardened runtime is not enabled: there is no Developer ID, and the keychain identity is more reliable without it. Release APKs are signed with a stable key only after the four GitHub secrets exist. Otherwise the release is clearly named `BridgeLink-android-debug.apk`.

## Web demo stays

The preview host requires the existing web shell, including its auth provider and platform branding. The public CI job typechecks that shell and runs the demo protocol test. It does not run the sandbox skill tests, because those read files that are not part of this repository.

## Not attempted

iMessage, FaceTime, AirDrop, Handoff, unlocking the Mac, silent system setting changes, running Android apps on the Mac, and phone-call audio.
