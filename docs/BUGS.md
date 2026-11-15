# Bugs found in the Stage 1 audit

Each line is one problem. Fixed ones say Fixed and the idea of the change.

1. Mac install docs only said right-click Open. That is wrong on macOS 15. Fixed: docs now give the macOS 13–14 steps and the macOS 15 Open Anyway steps, plus the Terminal backup.
2. The Mac app was ad-hoc signed, so each update looked like a new app and macOS forgot permissions. The build signs with one certificate named BridgeLink Self-Signed when two GitHub secrets exist. Storing those secrets failed with 403, so today's builds stay ad-hoc until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD are added. The backup is not in the repo.
3. The Mac download was only a zip, with no app icon and no disk image. Fixed: the build makes BridgeLink.dmg and an icon, and still keeps the zip.
4. App.swift was one huge file. Fixed: the menu window, the connection, and the app entry are separate files.
5. The QR code contained only the first Mac address, which can be the wrong one. Fixed: every local address is in the pairing link, and the phone tries each one.
6. Pairing asked you to paste a link before it offered the scanner. Fixed: Scan the Mac QR is the first button. Paste and Find stay as backups.
7. The activity list lived in memory and disappeared on restart. Fixed: the phone stores it in a Room database, and the Mac stores it in a JSON file.
8. Phone battery was sent once, when pairing finished. The Mac never sent its battery. Fixed: each side sends battery when the level or the charging state changes.
9. If Android restarted the phone service, the new start had no command, so it did not reconnect. Fixed: a restart uses the saved Mac. Replacing the app also reconnects.
10. The release workflow could upload a debug APK when signing secrets were missing. Fixed: the release fails instead of publishing that APK.
11. If the Mac could not listen, the error did not mention the firewall. Fixed: the screen tells you to allow BridgeLink in System Settings, Network, Firewall.
12. Several errors named the failure and did not say what to do. Fixed: each link error includes a next step, and the raw exception text stays off the screen.
13. There was no way to save a safe log. Fixed: both apps have Send diagnostics. The file has version, connection, and port. It has no keys and no message text.
14. LinkService.java is still longer than 400 lines. Left open on purpose this round so the connection fixes stay in one place. Split it in the next pass before adding features.
15. The Mac app still cannot skip Apple's first-open warning. That is not a bug. A free sideload cannot be notarized. The disk image explains the one extra step.

## v0.13 audit

C1. The phone stayed Disconnected after the Mac closed the link with no error, until you tapped Connect. Fixed: a clean close with a saved token schedules the same backoff reconnect, and a bad key or a bad code does not.
C2. A web page could open a pairing link, replace the saved Mac key, and the phone would send the old token to that fake Mac. Fixed: the link asks "Pair with this Mac?" first, a different key says "This is a different Mac", and the old token is removed before any hello.
C3. The Mac opened every received file, and any phone app could push a file with no tap. Fixed: files stay closed until you press Open or Show in Finder. Only a scan can open by itself, and only if Open scans automatically is on. The share sheet asks Send or Cancel first.
C4. Away from home could not switch back to Wi-Fi, and a quiet relay was dropped. Fixed: a returning network still starts a Wi-Fi try while the away link is up. A real Wi-Fi pair closes the relay.
C5. A forgotten phone kept the away-from-home secret and could still send commands. Fixed: each phone has its own secret in the Mac keychain. Forgetting that phone deletes only its secret.
C6. Finished features were marked self-tested even when the test only checked a sentence. Partly fixed: every feature message now has to appear in a test, and a unit test sends each message through the envelope and reads it back. The emulator still only pairs and sends one note, not every feature.
C7. The Mac could only start the screen or the camera while the phone app was open, and it ignored the Mac's quality and camera choices. Fixed: a high-priority "Tap to allow" notice opens the allow screen with the Mac's choices, even if BridgeLink is in the background.
H1. Stopping the screen mirror could release the encoder while the drain thread was still using it, which crashed the phone. Fixed: the drain thread is waited on first, and every encoder call is inside a try so a late call cannot kill the app.
H2. Video and camera frames piled up with no limit on a slow link. Fixed: only 3 picture frames wait. Extra non-key frames are dropped, and the phone asks the encoder for a new key frame.
H3. Every frame started a new watchdog thread. Fixed: one deadline thread watches the current write for the life of the connection.
H4. The Mac queued a whole file in memory before the network finished each chunk. Fixed: at most 6 chunks are in flight, and the next chunk waits until a send finishes. Incoming files use a separate queue.
H5. Asking Music or Spotify froze the Mac, and it asked even when nobody was looking. Fixed: those scripts run in the background, stop after 3 seconds, and run only while the phone's Remote page is open.
H6. Every incoming frame, including video, was handled on the Mac's main thread, so the window stuttered. Fixed: frames are split on the network thread. Pictures decode on their own queue. Only text and the window update run on the main thread.
H7. Turning the phone trackpad on without Accessibility stacked a blocking alert for every move. Fixed: one quiet note per session, and pointer moves are ignored until Accessibility is on.
H8. Turning notification mirroring on replaced every other switch. Fixed: only the notify and otp switches change. Folder, trackpad, and automations stay as they were.
H9. The phone could not lock the Mac after you walked away, because the Wi-Fi link was already gone. Fixed: the Mac itself locks after the link has been gone for the delay you set. At the desk skips it. The Mac still never unlocks.
