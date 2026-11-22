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
H10. Locking the Mac silently changed the password setting. Fixed: BridgeLink only sleeps the display. It does not write system settings. If a password is not required right away, it says so once.
H11. The Mac share extension was a library, not a real extension, so it could not load. Fixed: it is not shipped. Send files with Finder Services, a Dock drop, or the drop zone.
H12. Two phones on one Mac kept kicking each other off. Fixed: the Mac keeps the phone that is already connected and tells the other one to wait 5 minutes.
H13. Pairing a second Mac forgot the first Mac's key. Fixed: each Mac keeps its own key. Switching to a saved name uses that key. A Mac you already paired is not treated as a changed key.
H14. Phone automations only wrote a status line, and Home Wi-Fi fired for every network. Fixed: a code is copied, an alert is shown, and ring asks the Mac to ring. Open app still runs on the Mac. Home Wi-Fi fires only for the name you typed.
H15. A release could ship an ad-hoc Mac app and skip version checks. Fixed: the release checks the version and the tag, checks the APK signature, writes SHA256SUMS, and refuses an ad-hoc Mac app when the Mac signing secrets are set.
H16. Partly fixed. First-run setup, Check for update, and Reset BridgeLink are in both apps. Check for update opens the release page. The Sparkle framework is not linked, because that needs an EdDSA secret that is not here. It does not install the update by itself.
M1. The phone kept dialing an old Mac address. Fixed: after two misses it looks for BridgeLink on the Wi-Fi and accepts only the Mac whose key matches the one you saved.
M2. Tapping Connect replaced the list of Mac addresses with one address. Fixed: the typed address is added, and the other saved addresses stay.
M3. A second phone that was still pairing silently dropped the first waiting connection. Fixed: each waiting connection stays until it pairs or times out.
M4. The phone could not stop the Mac ringing, and the Mac only beeped. Fixed: Stop Mac ringing sends find-stop. The Mac plays a loud system sound for 30 seconds.
N1. The Mac app did not compile. Fixed: the picture decoder can see the shared protocol, the listener no longer unwraps itself twice, and the read buffer is not captured as a non-sendable value. The test can build an incoming frame. The send-window check no longer calls the semaphore, which stopped the Mac tests with signal 5. NOT TESTED on a device. Swift was not run here.
N2. The emulator pair test never tapped Pair, so it never saw "Paired with the Mac". Fixed: the test waits until the pair screen is up, then taps Pair. The pair question is on the screen. It is not a service status line. NOT TESTED on a device. The emulator only runs in CI.
N3. A sleeping relay forgot which side was which, and forgot the room key. Fixed: the role stays on the socket, and the room key stays in storage. NOT TESTED on a device. A local relay test covers a wake-up. The worker is not deployed.
N4. The away relay closed because the connection was old, even when frames were still moving. A drop also left the apps looking paired. Fixed: it closes after 10 minutes of silence. Both apps say "Away link down" and try again after a wait. NOT TESTED on a device.
N5. Each Wi-Fi retry tore down a working away relay and started it again. Fixed: if the away relay is already up, it is left alone. NOT TESTED on a device.
N6. The phone sent the away sign-in once, so a late Mac never accepted it. Fixed: the phone keeps sending until the Mac answers relay_ok, and sends again when the Mac joins. Forgotten stops the away link. Old message types still work. NOT TESTED on a device.
N7. A late Mac reply on the away relay was thrown away. Fixed: while Wi-Fi is down, replies still go out on the away relay. NOT TESTED on a device.
N12. The apps said paired as soon as the away relay connected, before the Mac checked the phone. Fixed: paired shows only after the Mac accepts the sign-in, and a drop clears it. NOT TESTED on a device.
N17. The away relay counted both directions together and never forgot old counts. Fixed: each direction has its own count, and counts older than a few seconds are dropped. NOT TESTED on a device. A local relay test covers it. The worker is not deployed.
X6. A file from the Mac opened by itself. Partly fixed earlier: the phone showed a notice. The tap did not open the file until the activity button. See the later X6 line.
V1. The away relay rewrote its idle clock on every frame. Fixed: the clock moves at most once every 30 seconds. A quiet room still closes after 10 minutes. NOT TESTED on a device. A local relay test covers the clock.
V2. The relay room tests were not part of the checks. Fixed: they run with the other web tests, and a wake-up uses a new socket that only has the saved note, then a frame is forwarded. NOT TESTED on a device.
M10. Password-manager clipboard items could be copied and stored. Fixed: Android skips clips marked sensitive on Android 13 and newer. The Mac skips concealed and transient pasteboard types. Those clips are not sent and not stored. NOT TESTED on a device.
X8. The phone could start the link for any broadcast the boot receiver saw. Fixed: it starts only after the phone boots or the app is updated. NOT TESTED on a device.
L10. Signing the Mac app changed the default keychain even on a home computer. Partly fixed earlier: the default changes only on CI. The certificate was still invisible on a home Mac until W4. See the later L10 line.
L12. A script in the repo could make a new Android signing key. Fixed: that script is deleted, and the docs no longer tell anyone to run it. Do not add it back.
X9. A Java key file could be committed by accident. Fixed: .jks files are ignored, the same way .keystore files already were.
W2. The away sign-in retry ran on the phone's main thread, so the send was dropped, and cancelling it never found the old wait. Fixed: one saved retry posts the sign-in, and the actual write runs on the send thread. NOT TESTED on a device.
N6. Partly fixed before. The phone now keeps one sign-in until the Mac answers, including when the phone joins first. A test covers relay_ok and forgotten both stopping that retry. NOT TESTED on a device.
W1. File and link notice buttons used a broadcast, which Android 12 and newer blocks, so Open did nothing. Fixed: Open and Show in Downloads are real activity buttons. The file grant sits on the view itself. NOT TESTED on a device.
X6. A file from the Mac is not opened by itself. Tapping the notice or Open starts the real view screen for that file. An install file still has no Open button. NOT TESTED on a device.
L9. A link from the Mac opened itself whenever the screen was on. Fixed: it waits for Open unless Always open links is on. NOT TESTED on a device.
W6. Open was offered for some Android install files, not only .apk. Fixed: .apks, .xapk, .aab, and the Android package type have no Open button. NOT TESTED on a device.
W7. Every file notice used the same number, so a second file hid the first. Fixed: each file gets its own notice number. NOT TESTED on a device.
W3. The away relay looked for a closed flag that Cloudflare sockets do not have, so it kept the room key after the last socket closed. Fixed: a socket just closed, or one whose ready state is closed, is not counted. NOT TESTED on a device. The worker is not deployed.
W4. On a home Mac, signing could not see the BridgeLink certificate because the tool was not pointed at that keychain. Fixed: codesign gets --keychain, the keychain is on the search list only while signing, and the old list is restored. The default keychain still changes only on CI. NOT TESTED on a device.
L10. The Mac signing script used to change the home default keychain. Fixed: it changes that default only on CI, and codesign is pointed at the BridgeLink keychain instead. NOT TESTED on a device.
W5. The away-relay send rule lived only in a test. Fixed: the phone uses that same rule, so a late reply still goes out on the away relay when it should. NOT TESTED on a device.
W8. The progress note said v1.0.0 was released. Fixed: it now says there is no v1.0.0 release. The published builds are v0.12.0 and v1.0.1.
X7. Every phone copy was saved as plain text, even when clipboard history was off. Fixed: history stays off until you turn it on, Clear history wipes it, copies older than 24 hours are dropped, and the file is sealed with the keystore key. If the keystore cannot be used, the copies stay in memory only. NOT TESTED on a device.
N11. Home Wi-Fi automations cannot see the network name on Android 10 and newer. Fixed: that trigger is removed. Saved rules that used it are ignored. NOT TESTED on a device.
N11b. Precise location was declared for every Android version. Fixed: it is only declared through Android 11, where leave-lock Bluetooth still needs it. Android 12 and newer do not get it. NOT TESTED on a device.
L7. The accessibility service asked for click and focus events it never reads. Fixed: it only keeps gesture and window content, which the mirror taps use. NOT TESTED on a device.
L8. The Mac started its Bluetooth beacon even when leave-lock was off. Fixed: the beacon starts only while leave-lock is on, and it stops when leave-lock is off. NOT TESTED on a device.
H15. A release could ship an Android app signed by a different key. Fixed: the release compares the APK signer with the public fingerprint saved in the repo and stops if they differ. NOT TESTED on a device.
X10. A release rebuilt and published without waiting for the checks. Fixed: the release waits until the checks for that same commit are green. NOT TESTED on a device.
X11. A hand-started release could build the wrong commit, and the release page was not tied to that commit. Fixed: it checks out the typed tag, and the release points at that commit. NOT TESTED on a device.
X12. GitHub Actions were named by a moving tag. Fixed: each one is pinned to the commit that tag pointed at, and the tag name is written beside it. NOT TESTED on a device.
X13. Every release job could write to the repository. Fixed: only the publish job can write. The others can only read. NOT TESTED on a device.
L11. When a paid Apple account exists, the release stapled the app instead of the disk image. Fixed: it staples the disk image. It still skips that step when there is no paid account. NOT TESTED on a device.
N15. The update file was a static page with size 0. Fixed: the release writes it with the real disk-image size and the date. It still has no signature, because Sparkle is not linked. NOT TESTED on a device.
X14. The Gradle download was not checked. Fixed: the wrapper uses the official Gradle 8.11.1 checksum.
C6. The feature check only looked for a word in some test. Fixed: every message type has to be in the test that builds the message and reads it back. The emulator still only pairs and sends one note.
N8. Switching Macs rewrote the token during the handshake, then treated that as a changed key and deleted it. Forget also left the Mac in the token book. Fixed: a saved Mac is chosen before hello, the handshake does not rewrite the token, a mismatch does not delete a saved token, and forget removes that Mac. NOT TESTED on a device.
N9. The picture queue dropped 24 KB pieces, so a frame arrived broken. Fixed: a full queue drops a whole frame. The screen, the camera, and the mic do not share one queue. NOT TESTED on a device.
N10. A search for the Mac stayed running in the app, so the next search never started, and the multicast lock could stay held. Fixed: the search is cleared when it stops, and the lock is released. NOT TESTED on a device.
H6. A shared-folder piece was decoded, written, and hashed on the Mac main thread. Fixed: that work runs on a folder queue, then the status line returns to the main thread. NOT TESTED on a device.
X1. Activity history was written on the phone's main thread. Fixed: reads and writes run on one history thread, and the database refuses main-thread queries. NOT TESTED on a device.
L6. "Sending — N%" was posted on every file piece. Fixed: that line shows at most once a second, and 100% still shows. A line in the same second is skipped. NOT TESTED on a device.
L3. Seek always jumped to the start of the song. Fixed: it uses the time that was sent, in milliseconds. NOT TESTED on a device.
M5. Only the newest phone notification could be answered or dismissed on the Mac. Fixed: the phone's key is the Mac banner id, dismiss removes that banner, and a replyable banner has its own Reply action. NOT TESTED on a device.
M6. Every notification update was sent, including progress bars. Fixed: the same key with the same title and text is skipped, and later progress-bar updates are skipped. NOT TESTED on a device.
M8. Mac Focus was read with no permission, so it could not work. Fixed: the app asks, and the screen says a free sideload cannot send Mac Focus to the phone. Phone Do Not Disturb still quiets Mac banners. NOT TESTED on a device.
M14. A shared path could name a hidden file such as .seen. Fixed: any path part that starts with a dot is refused. NOT TESTED on a device.
M9. The phone could send the clipboard only while the app was on screen. Fixed: a Quick Settings tile and a notification button open a short screen, read the clipboard there, and send it. The Send page says so. NOT TESTED on a device.
M19. A drag clicked down and up on every move. Fixed: the button stays down until the drag ends. NOT TESTED on a device.

























