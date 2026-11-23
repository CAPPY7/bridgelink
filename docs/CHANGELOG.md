# Changelog

## Phase 2 (not released)

- Removed the away path, the combined list, and when-this-then-that rules.
- Leave-lock stays off until you turn it on.
- Added call alerts with Decline, transfer history, phone photos, a sharper mirror, a menu-bar status panel, pause phone notifications, text from a photo, and Finder Send to phone for files and text.
- The phone setup walks through each permission. Skip is on every page.
- Nothing here was tried on a real phone or Mac. CI runs unit tests and the emulator pairing test.

## Unreleased

- The phone link file is split. Connecting stays in one place. Clipboard, alerts, files, ringing, and battery stay in another. Incoming messages stay in a third. Nothing about how the link works was changed. NOT TESTED on a device.
- The unified inbox is removed. Notifications, texts, and files stay in their own places. The saved inbox file is deleted the next time the Mac app opens. NOT TESTED on a device.
- The away relay is removed. The phone and the Mac link only on Wi-Fi. An old relay message is ignored. NOT TESTED on a device.

## 1.0.1

- Away from home keeps its room after a sleep, closes only after 10 quiet minutes, and says "Away link down" if it drops. It says paired only after the Mac accepts the phone.
- A file from the Mac is not opened by itself. You get Open and Show in Downloads. An install file has no Open button.
- Password-manager clipboard items are not copied or stored.
- The phone reconnects only after a boot or an app update.
- This build was not tried on a real phone or Mac yet.

## 1.0.0

- First open on the Mac is a short setup window: notifications, local network, launch at login, then the pairing QR.
- First open on the phone asks you to allow notifications, turn off battery limits, and read the restricted-settings note. Tap I'm ready when that is done.
- Check for update reads the release list. If a newer version is there, it opens the download page. Reset BridgeLink forgets the paired devices, the saved history, and the Mac key. Quit and open the Mac app again after a reset, then pair.
- The release includes appcast.xml next to the disk image, the zip, and the signed APK.

## 0.13.0

- The Mac can show the phone screen in a window. The phone encodes H.264. The Mac decodes it. Low, medium, and high set the size and the frame rate. Closing the window stops the picture.
- A click, drag, or scroll in that window taps the phone. Back, Home, and Recents are buttons. Typing needs a text field on the phone. Turn on BridgeLink under Accessibility. Android 13 and newer may need Allow restricted settings first. The Mac never unlocks the phone.
- The phone camera can show live inside BridgeLink, front or back, with zoom, flash, and the mic. Share that window in a call. A camera that appears in Zoom, Meet, or FaceTime needs a paid Apple Developer ID, so that extension stays off.
- Away from home can use a free relay after you pair once on the same Wi-Fi. The relay only forwards locked frames. It cannot read them. The address starts with wss://. Setup steps are in Setup for the owner.

## 0.12.0

- Texts can be read and sent from the Mac. A new text shows a banner. If the phone has two SIMs, you can pick one. Allow SMS on the phone. A sideloaded app may need Allow restricted settings first.
- One folder on the phone and BridgeLink Shared on the Mac stay in sync. New, changed, and deleted files are copied. If both sides changed a file, the original stays and the other copy is saved with BridgeLink conflict in the name. Sync can be paused. Files over 32 MB are skipped.
- Automations are simple when-this-then-that rules, up to eight. Examples: the phone connects, the battery is under 20%, the phone joins home Wi-Fi, or a text has a code.
- The phone can be a trackpad, keyboard, and slide remote. Drag moves the pointer, two fingers scroll, and a button right-clicks. Slide next and previous press the arrow keys. The laser is a red dot on the Mac. The Mac needs Accessibility the first time.

## 0.11.0

- The phone can play, pause, skip, and set the volume of music on the Mac. The Mac can do the same for music on the phone, including the song name and a small picture when notification access is on.
- If you turn it on, the phone watches the Mac over Bluetooth. When the signal stays weak, the Mac locks. It never unlocks. Charging the phone, or "I am at my desk", skips the lock.
- The Mac has one inbox for notifications, text messages, and received files. You can search it and filter it. Passwords and codes are hidden.
- Both apps keep the last 100 clipboard copies. You can pin, search, and delete them. Passwords, card numbers, and codes are not stored.
- Scan a document on the phone. It is saved on the Mac and opened. If the document scanner is missing, pick a photo instead.

## 0.10.0

- Files can be up to 4 GB. They still stream in small pieces, can resume, and can be cancelled. A folder is sent as a zip. Several files can be sent together. A received file opens when it is saved.
- The Android share sheet sends every file you picked, not only the first. On the Mac, drop files on the window, the menu-bar icon, or the drop zone. Finder has Services, Send to phone. Share has BridgeLink.
- Notification banners can be dismissed both ways. If the app allows a reply, the Mac can send it. An app can be blocked. Do Not Disturb can stay quiet on the other side.
- A low battery and a full battery are announced on the other device.
- Find my phone rings on the alarm stream, so the mute switch does not hide it. Either side can stop it.
- A code in a notification or an SMS is copied on the Mac and cleared after 60 seconds. The code is not written into the activity list.
- Links open both ways. You can choose to open them right away.
- Clipboard sync can stay on. Passwords and card numbers are skipped. Pictures on the clipboard are sent when they are small enough.
- Saved Macs and phones can be renamed. If the Mac key changed, the phone shows a button to forget it and scan again.
- The link stays TLS. New messages use the same encrypted connection.

## 0.9.0

- The phone uses a new screen: Home, Send, Notifications, Messages, Files, Remote, Mirror, and Settings.
- The Mac app has a menu-bar panel and a window with a sidebar. Drop a file on either one.
- Light and dark follow the system. Features that are not built yet say so, instead of pretending to work.

## 0.8.0

- The Mac app is a disk image with an icon. The first-open steps are correct for macOS 13, 14, and 15.
- Builds are signed with one self-signed certificate when the Mac signing secrets are set, so macOS can keep permissions across updates.
- The QR code lists every local Mac address. The phone tries each one.
- Scan the Mac QR is the first pairing button.
- Activity history is saved on the phone and the Mac.
- Battery is sent both ways when it changes.
- If Android restarts the link, the phone reconnects to the saved Mac.
- A release with missing Android signing secrets fails instead of shipping a debug APK.
- Link errors say what to do next. The Mac firewall has its own message.
- Both apps can save a diagnostics file with no keys and no message text.

## 0.7.0

- A stalled file send can no longer freeze the phone. Socket open, close, read, and write run on connection threads. Closing the link does not wait on a blocked write. A write that makes no progress for 20 seconds closes the socket.
- Each connection attempt has its own thread. A new connect does not wait behind the old read loop.
- If Android refuses to start the link service from the background, the message is kept and a notification says to open BridgeLink. It does not crash.
- Link failures are chosen by type (key changed, wrong code, rejected phone, version, timeout, refused, network). The screen does not match words inside an error.
- Notification text is shown on the Mac only when the Mac is unlocked and Show notification text is on. Otherwise the Mac shows the app name. The text is not written into the activity log.
- Android 13 and newer shows the restricted-settings steps before notification access, and detects when that setting is still blocked.
- Clipboard history, leave-lock, rate-limit, and replay helpers that were not used by either app were removed.
- The Mac app stays sideloaded and ad-hoc signed. No Apple Developer ID.

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
