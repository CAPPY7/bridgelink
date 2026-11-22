# Decisions

The current rules are docs/BUILD_PROMPT.md (master prompt v2). If an older paragraph below disagrees with that file, the prompt wins.

The Mac app is sideloaded. There is no paid Apple Developer account. A self-signed certificate keeps the same signature across updates when the Mac signing secrets are set. Permissions can still be forgotten if a build is ad-hoc signed. The menu window explains the first open.

A pairing link never connects by itself. A different Mac key drops the active token before any hello, and each Mac's token is sealed under its own fingerprint.

Received files are not opened by themselves. A scan PDF or JPG still opens when Open scans automatically is on, because that is the scan the user just made. Other files get a notification with Open and Show in Finder. macOS quarantine is set so a script still asks before it runs.

The away relay uses Cloudflare's free Durable Object database. The relay can check the join ticket. It cannot read the frames, because each direction has its own key.

The Mac tests must not call a semaphore just to see that the send window is full. That call traps on the current Mac runner. A lock is enough for the check. A real 7th chunk still waits.

The pair question stays on the phone screen. The emulator test reads that screen and taps Pair. A link still does not connect by itself.

The away relay writes its idle clock at most once every 30 seconds, so a busy room does not rewrite storage on every frame. A room still closes after 10 minutes with no frames.

The relay room tests run with the web checks. A wake-up is a new socket that only has the saved note. It must still forward a frame.

Password-manager clips are never synced or stored. On Android 13 and newer that is the sensitive clip flag. On the Mac that is the concealed and transient pasteboard types.

The phone starts the link after a boot or an app update only. Other broadcasts are ignored.

The Mac signing script changes the default keychain only when CI is true. A home computer keeps its own default keychain. codesign is pointed at the BridgeLink keychain with --keychain, that keychain is added to the search list for the sign, and the old search list is put back after.

The Android key-making script is deleted. A new key would not match the existing signing secrets. Do not add the script back.

Java key files ending in .jks are ignored, the same as .keystore files. Do not commit a signing key.

The away sign-in retry is one saved wait. A new wait replaces that same wait. The sign-in bytes are written on the send thread, never on the main thread.

Clipboard history is off until the user turns it on. Clear history deletes it. Copies older than 24 hours are dropped. The file is sealed with the same keystore key as the pairing token. If that key cannot be used, the copies stay in memory and are not written as plain text.

Home Wi-Fi automations are removed. Android 10 and newer do not tell an app the Wi-Fi name unless it asks for location, and this app does not use location for that. Old Home Wi-Fi rules are ignored.

Precise location stays only through Android 11. Leave-lock Bluetooth on those phones needs it. Android 12 and newer use the Bluetooth scan permission and do not get location.

The Mac Bluetooth beacon runs only while leave-lock is on. Turning leave-lock off stops the beacon.

The Android release signer is checked against the public fingerprint in android/release-cert.sha256. That line is not a secret. A different signer stops the release.

Switching to a saved Mac uses that Mac's own token. The handshake does not change the token while it is running. A certificate that is not a saved Mac does not erase the saved token. Forget removes that one Mac from the token book and leaves the others.

The screen, the camera, and the mic each keep at most 3 whole frames. A full lane drops the next whole frame, not one 24 KB piece of it. Only the screen asks for a new key frame.

Looking for the Mac on Wi-Fi releases the multicast lock when the search stops or fails. The next search can start. The saved Mac is not forgotten.

A shared-folder piece is decoded, written, and checked off the Mac main thread, one piece at a time. The status line is still shown on the main thread.

Activity history is saved on one background thread. The phone screen updates after that write. The database does not allow a query on the main thread.

A file send shows "Sending — N%" at most once a second. The finished line, 100%, is always shown.

Seek uses milliseconds into the song. Volume is still 0 to 100. A seek time is not treated as a volume.

Each phone notification keeps its own Mac banner, named by the phone's key. Dismissing it on the phone removes that banner. Reply is on the banner when the phone says a reply is possible. The in-app reply box still answers the newest one.

A notification is sent again only when its title or text changes. A progress bar is sent the first time, then skipped until the text changes without a progress bar.

Mac Focus is not sent to the phone on a free sideload. The app asks, then says a paid Apple account is required for that direction. Phone Do Not Disturb still quiets Mac banners. That direction does not need Mac Focus.

A shared-folder name that starts with a dot is refused. That keeps .seen and other hidden files out of the folder.

Android 10 and newer share the clipboard only with the app that is on screen. Send clipboard, the Quick Settings tile, and the notification button each open a short screen, read the clipboard there, and send that text. They do not read it from the background.

A drag holds the mouse button down until a move that is not a drag. It does not click on every tiny move.

The disk image is mounted in view while it is built, so the icon layout can be applied. It is still a free sideload.

Switching to a saved Mac adds that Mac's address to the list. The other addresses stay.

If the phone refuses a ring-volume change, the ring still starts. The volume is put back the same way when the ring stops.






Open and Show in Downloads start the real view screen. They are not broadcasts. Android 12 and newer blocks a broadcast that then opens a screen.



A sleeping relay keeps the role on the socket and the room key in storage. Nothing important is only in memory. The free Cloudflare plan already supports that.

A quiet away relay closes after 10 minutes without a frame, not because the connection is old. A drop shows "Away link down" and rejoins with the same wait used for Wi-Fi. A relay that never came up does not loop by itself.

A Wi-Fi retry leaves a working away relay alone. Starting the relay again while it is up does nothing.

The phone repeats the away sign-in every 2 seconds until the Mac answers relay_ok. A ping from the Mac means it just joined, so the phone signs in again. Forgotten stops the away link and does not delete the Wi-Fi key.

A late reply uses the away relay when Wi-Fi is down, or when that reply belongs to a relay message. The phone uses the same rule as the Mac. It is not sent to a fake local socket.

Away from home says paired only after the Mac checks the phone. Until then the phone says it is still connecting. A drop clears that.

The free relay counts phone-to-Mac and Mac-to-phone separately, 200 messages a second each. Counts older than a few seconds are dropped so the map does not grow.

A file from the Mac is never opened by itself. The notice has Open and Show in Downloads. Install files have no Open button: .apk, .apks, .xapk, .aab, and the Android package type. A link waits for Open unless Always open links is on. The screen being on is not enough.

## v0.7.0 link threads and signing

Socket work no longer shares one thread with the read loop, and close does not hold the write lock. A 20 second write deadline closes a socket the other side stopped reading. Background callers that are not allowed to start a foreground service queue the message and show "Open BridgeLink to reconnect".

Link errors use a kind. Fatal kinds do not retry. The wording of an exception is not the rule.

Notification text crosses the link only so the Mac can show it when asked. The Mac shows the full text only if the screen is unlocked and Show notification text is on. The activity list stores "Notification from the phone" and never the text.

The Mac app is sideloaded with one self-signed certificate when CI has the Mac signing secrets. There is no Apple Developer ID. Each ad-hoc build can drop Accessibility, Screen Recording, and Bluetooth. The menu window tells the owner how to open the app the first time.

Clipboard history, leave-lock, rate limit, and replay classes were deleted because nothing in the apps called them. They come back only when those features are wired in.

## v0.4.0 pairing proof

The first hello sends `proof` only when the QR link already contained the certificate fingerprint and the live certificate matches it. A typed address does not send a PIN proof. Both screens show a 6-digit match code from the certificate the phone saw. The user confirms on both. A relay sees a different code and is not given a token. This replaced the v0.3.0 hello, so 0.3.0 and 0.4.0 cannot pair. The Mac stores SHA-256 hashes of device tokens. The PIN on the Mac refreshes itself after 120 seconds.

Android reconnect is a small state machine: fatal errors do not retry. Forget on the phone sends a `forget` message, then drops the token. The screen is Kotlin and Jetpack Compose. LinkService and the protocol stay Java.

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

## Phone screen is Compose. The link stays Java.

The phone screen is Kotlin and Jetpack Compose with Material 3. On Android 12 and newer it uses the system colors. LinkService, pairing, and the protocol stay Java so the socket code is not rewritten in the same pass as the screen. The Mac app is a menu-bar panel plus a sidebar window.

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
