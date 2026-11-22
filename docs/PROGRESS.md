# Progress

v1.0.1 is the published fix build. There is no v1.0.0 release. The previous release was v0.12.0. Not every audit item is done. Nothing in 1.0.1 has been tried on a real phone or Mac.

## Done in code
- Stage 1 bug fixes are in v0.8.0.
- Stage 2 design is in v0.9.0.
- Stage 3 half-done features are in v0.10.0.
- Stage 4 easy features are in v0.11.0. Media control works both ways. The Mac can lock when the phone leaves, and it never unlocks. The Mac has one inbox. Clipboard history keeps 100 items and skips secrets. A phone scan is saved on the Mac and opened.
- Stage 5 medium features are in v0.12.0. Texts, a shared folder, automations, and the phone trackpad, keyboard, and slides.
- Stage 6 hard features are in v0.13.0. Screen mirror, control while mirroring, the phone camera inside BridgeLink, and an away relay that only forwards locked frames.
- Stage 7 polish is in the 1.0.1 code. First-run setup on both apps, Check for update, Reset BridgeLink, and the docs for macOS 13–15 and Android 8–15. It was never published as v1.0.0.

## Next exact step
Last fix pass. NB1 and NB2 are done. Next is NB8: a drag must send drag_end when the finger lifts, and the Mac must let the mouse button up.

## Open problems
- The v0.13 audit is in docs/AUDIT_v0.13.md. Items not listed under Done below are still open.
- LinkService.java is still longer than 400 lines.
- Mac permissions can still reset after an update until MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD exist.
- Check for update reads the Sparkle appcast and opens the release page. The Sparkle framework is not linked, because that needs an EdDSA secret that is not in this environment. It does not install the update by itself.
- A free share extension can stay hidden until the app is opened once. Finder Services, Send to phone, is the backup.
- The document scanner needs Google Play services. If it is missing, the phone asks you to pick a photo instead.
- Leave-lock needs Bluetooth on the phone and Accessibility on the Mac so the lock shortcut works. The Mac is never unlocked by the phone.
- The trackpad also needs Accessibility on the Mac. BridgeLink shows the steps.
- Shared folder files over 32 MB are skipped on purpose.
- Screen mirror needs the phone to allow screen share each time Android asks. Control needs the accessibility service, including Allow restricted settings on Android 13 and newer.
- The phone camera inside BridgeLink is the free webcam. A system camera for Zoom needs a paid Apple Developer ID. That extension is in the repo and stays off.
- Away from home returns to Wi-Fi and each phone has its own secret. The relay worker still has to be deployed by the owner. The relay cannot read the frames.
- The signed downloads are produced by the Release workflow. This environment cannot sign them or upload them.
- Do not create a new Android signing key. Do not print secrets.

## Fix-all done
- C1. Phone reconnects by itself after a clean disconnect. A bad key or a bad code does not.
- C2. A pairing link asks before it connects, and a different Mac does not receive the old token.
- C3. Received files are not opened by themselves. The share sheet asks before it sends.
- C4. Away from home goes back to Wi-Fi when the network returns, and the heartbeat stays up on the relay.
- C5. Each phone has its own away secret. Forgetting that phone deletes the secret.
- C6 partly. Every feature message is named in a test and round-trips. The emulator still only sends one real note.
- N8. Switching to a saved Mac uses that Mac's token. The handshake does not swap the token in the middle, and a mismatch does not erase a saved token. Forget removes that Mac from the token book.
- N9. Picture send drops a whole frame, not a 24 KB piece. The screen, the camera, and the mic each have their own queue.
- N10. Looking for the Mac clears itself when the search stops, and the multicast lock is released even if the search fails.
- H6. A shared-folder piece is decoded, written, and hashed off the Mac main thread. The status line still updates on the main thread.
- X1. Activity history is read and written off the phone's main thread. The database no longer allows main-thread queries.
- L6. The "Sending — N%" line is shown at most once a second. 100% is always shown.
- L3. Seek uses the real time in the song. It no longer jumps to the start.
- M5. Each phone notification uses its own Mac banner. Dismiss removes that banner. A banner that can be answered has its own Reply action.
- M6. The same notification is not sent again. A progress-bar update is sent once, then later progress updates are skipped.
- M8. Mac Focus is asked for, and the reason is in the app. A free sideload cannot send Mac Focus to the phone. Phone Do Not Disturb still quiets Mac banners.
- M14. A shared-folder path that starts with a dot is refused, including .seen.
- M9. The clipboard can be sent from a Quick Settings tile or the BridgeLink notification. Both open a short screen, because Android 10 and newer will not share the clipboard in the background.
- M19. A drag keeps the button down until the drag ends. It does not click on every move.
- M26. The disk image is mounted where the layout step can see it.
- N14. Switching to a saved Mac adds that address. It does not throw away the others.
- L5. Changing the ring volume cannot stop the ring if the phone refuses it.
- M20. The send pages no longer say a folder can be sent. They send files.
- L4. The same code is sent once. A different code, or the same code after a minute, is sent again.
- M17. Mirror taps use the real screen size, including the system bars.
- NB1. The clipboard tile waits until the short screen is in front, then reads once.
- NB2. Send clipboard uses the same sensitive-clipboard check as that short screen.
- C7. The Mac can ask to see the screen or the camera even when the phone app is in the background.
- H1. Stopping the screen mirror waits for the drain thread before it releases the encoder.
- H2. Picture frames stay in a queue of 3. Extra non-key frames are dropped.
- H3. One write deadline thread serves every frame on a connection.
- H4. The Mac keeps at most 6 file chunks in flight and does not block incoming files on that send.
- H5. Music and Spotify scripts run off the main thread, and only while the phone's Remote page is open.
- H6. Picture frames decode off the Mac main thread. Text still updates the window.
- H7. The trackpad permission note shows once, then pointer moves wait quietly.
- H8. Notification mirroring changes only the notify and otp switches.
- H9. The Mac locks itself after the phone link has been gone for the delay. It never unlocks.
- H10. Locking sleeps the display and does not change Mac system settings.
- H11. The broken share extension is not shipped. Finder Services, Dock drop, and the drop zone remain.
- H12. A second phone is told to wait. It does not disconnect the phone that is already paired.
- H13. Each saved Mac keeps its own key. Switch to this saved Mac uses that key.
- H14. Phone automations copy, alert, or ask the Mac to ring. Home Wi-Fi needs the network name.
- H15. A release checks the version, the tag, and the APK signature, and writes SHA256SUMS.
- H16 partly. First-run, Check for update, and Reset are in. Sparkle is not linked, so the update opens the release page.
- M1. After two misses the phone looks for the saved Mac on Wi-Fi.
- M2. Connect adds an address. It does not throw away the others.
- M3. A second waiting connection does not drop the first one.
- M4. Stop Mac ringing tells the Mac. The Mac plays a loud sound for 30 seconds.
- N1. The Mac app compiles again. Picture code sees the protocol. The listener does not unwrap itself twice. The send-window check does not trap the tests.
- N2. The emulator pair test waits for the pair screen, then taps Pair.
- N3. A sleeping relay still knows the phone or the Mac, and it still knows the room key.
- N4. The away relay closes after 10 minutes of silence. A drop says "Away link down" and both apps try again.
- N5. A Wi-Fi retry does not restart an away relay that is already up.
- N6. The phone keeps signing in on the away relay until the Mac answers, and stops if the Mac forgot it.
- N7. A late reply still goes out on the away relay when Wi-Fi is down.
- N12. Away from home says paired only after the Mac accepts the phone, and a drop clears it.
- N17. Each away-relay direction has its own rate limit, and old counts are dropped.
- X6. A file from the Mac is not opened by itself. An apk has no Open button. A link waits when the screen is off.
- V1. The away relay writes its idle clock at most once every 30 seconds.
- V2. The relay room tests run with the web checks. A wake-up uses a new socket and still forwards a frame.
- M10. Password-manager clipboard items are not sent or stored.
- X8. The phone reconnects after boot or an app update, and ignores other broadcasts.
- L10. The Mac signing script changes the default keychain only on CI.
- L12. The Android key-making script is deleted.
- X9. Java key files ending in .jks are ignored.
- W2. The away sign-in uses one saved retry, and the write runs on the send thread, not the main thread.
- N6. Away sign-in keeps going until the Mac answers. relay_ok and forgotten both stop it. A unit test covers phone-first, then Mac.
- W1. Open and Show in Downloads on a file notice start an activity. The old broadcast receiver is gone.
- X6. Tapping the file notice opens that file. An install file still has no Open button.
- L9. A link from the Mac waits for Open unless Always open links is on.
- W6. Install files (.apk, .apks, .xapk, .aab, and the Android package type) have no Open button.
- W7. Each file from the Mac gets its own notice, so a second file does not hide the first.
- W3. The away relay drops the room key when the last socket is closed, even without a closed flag.
- W4. Mac signing points codesign at the BridgeLink keychain and restores the old search list.
- L10. The home Mac default keychain stays alone. Signing points at the BridgeLink keychain instead.
- W5. The phone uses the away-relay send rule. It is not only a test.
- W8. The progress note no longer says v1.0.0 was released. The releases are v0.12.0 and v1.0.1.
- X7. Clipboard history is off until it is turned on. Clear history wipes it. Copies older than 24 hours are dropped. The file is sealed.
- N11. The Home Wi-Fi automation is removed. It could not see the network name.
- N11b. Precise location is only for Android 11 and older, for leave-lock Bluetooth. Newer phones do not get it.
- L7. The accessibility service no longer asks for click and focus events. It keeps gestures and window content for mirror taps.
- L8. The Mac Bluetooth beacon starts only when leave-lock is on.
- H15. A release stops if the Android app signer does not match the saved public fingerprint.
- X10. A release waits until the checks for that same commit are green.
- X11. A hand-started release checks out the typed tag, and the release page points at that commit.
- X12. Every GitHub Action in the checks and the release is pinned to a full commit, with the tag beside it.
- X13. Only the publish job can write to the repository.
- L11. A paid Apple account staples the disk image, not the app. No paid account still skips that step.
- N15. The release writes appcast.xml with the real disk-image size and the date. There is still no Sparkle signature.
- X14. The Gradle download is checked against the official 8.11.1 checksum.
- C6. Partly. Every message type is in the round-trip test. The emulator still only pairs and sends one note.

## Battery
The idle service does not poll in a tight loop. A connected phone sends one small heartbeat every 20 seconds. Battery is sent only when the level or the charger changes. The 40 millisecond wait exists only while a file send is asking how many bytes the other side already has, and it stops after 800 milliseconds. Screen mirror and the camera run only while those screens are open. This environment has no Android emulator, so a batterystats number was not measured. Do not treat a guessed milliamp figure as a result.