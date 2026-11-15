# Changelog

## 0.7.0

- A stalled file send can no longer freeze the phone. Socket open, close, read, and write run on connection threads. Closing the link does not wait on a blocked write. A write that makes no progress for 20 seconds closes the socket.
- Each connection attempt has its own thread. A new connect does not wait behind the old read loop.
- If Android refuses to start the link service from the background, the message is kept and a notification says to open BridgeLink. It does not crash.
- Link failures are chosen by type (key changed, wrong code, rejected phone, version, timeout, refused, network). The screen does not match words inside an error.
- Notification text is shown on the Mac only when the Mac is unlocked and Show notification text is on. Otherwise the Mac shows the app name. The text is not written into the activity log.
- Android 13 and newer shows the restricted-settings steps before notification access, and detects when that setting is still blocked.
- Clipboard history, leave-lock, rate-limit, and replay helpers that were not used by either app were removed.
- The Mac build is still ad-hoc signed. After an update, macOS can forget permissions. The app says so.

## 0.6.0

- Files go both ways. The name is sanitized, the size is capped at 100 MB, and the SHA-256 is checked before the file is kept. The phone saves into Downloads/BridgeLink. The Mac saves into ~/Downloads/BridgeLink.
- A link is sent only if it is http or https. The Mac asks before opening it. The phone opens it when the screen is on.
- The Android share sheet can send text, a link, or one file. A Quick Settings tile opens the app. A file dropped on the Mac menu window is sent.
- A dropped file transfer resumes from the bytes the other side already stored. The menu-bar icon accepts a dropped file. Command-Shift-D opens the panel. The phone can ring the Mac, and the Mac can stop the phone ring. A clipboard just received is not sent straight back.
- The phone can send its battery level after pairing. Notification mirroring and OTP copy exist and stay off until you turn them on. The Mac shows the app name, not the notification text, in the panel.
- v0.4.0 can still pair. These messages are new types on the same link.

## 0.4.0

- A typed PIN is no longer a proof. Without the QR fingerprint, both screens show a match code. The nonces are committed with SHA-256 before they are revealed, so a relay cannot grind them. A bad reveal or three wrong confirms drops the link on both sides.
- Opening a pairing link connects immediately. The fingerprint is saved before the handshake, so the QR proof is not skipped.
- Forget runs even while the phone is blocked waiting for the Mac. It clears the token.
- An API 34 emulator test pairs by QR, sends a note, forgets, and pairs again with the match code.
- A wrong PIN, a changed key, or a rejected token stops reconnecting and keeps the reason on screen.
- Forget on either side tells the other and drops that device only.
- The Mac caps waiting connections, refreshes Wi-Fi addresses, skips VPN and bridge addresses, and a new PIN does not sign out phones that are already paired. The PIN countdown restarts the code after two minutes.
- The pairing token on the phone is sealed with the Android Keystore. The keystore generates the IV. It is not stored in a plain file.
- The phone can scan the Mac QR in the app, or the camera app can open the pairing link.
- Payload values may be numbers and booleans, not only strings.
- v0.3.0 cannot talk to 0.4.0.

## 0.3.0

- Phone and Mac speak length-prefixed frames and TLS. The phone pins the Mac certificate.
- Socket writes on Android run off the main thread.
- The foreground service declares the network permissions Android 14 requires.
- Pairing uses a one-time PIN, a QR link, Wi-Fi discovery, and a saved token. A failed new connection does not drop the paired phone.
- The Mac app can quit, show About, toggle launch at login, and choose what to do with the phone clipboard.
- The phone reconnects after a network change and after boot when a token is saved. Ring uses maximum alarm volume and stops after 2 minutes.
- GitHub Actions runs tests on every push. The release workflow creates the release. A missing signing secret produces a clearly named debug APK. The Mac build fails if either CPU slice is missing.

## 0.2.0

- First installable Android APK and universal Mac zip. Cleartext notes, clipboard, ring, and a PIN. Replaced by 0.3.0. The two versions cannot talk to each other.

## 0.1.0

- Source only. No installable apps.
