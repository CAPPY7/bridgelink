# Final test — BridgeLink 1.0.0

Tick a box only when that line happened. Send back the lines that failed, not the ones that worked.

Install first: `docs/INSTALL.md`. macOS 15 uses Open Anyway. The phone APK must be `BridgeLink-android.apk`, not a debug file.

## Setup

- [ ] Open the Mac app the first time → a setup window appears, not only the menu bar.
- [ ] Tap through notifications, local network, launch at login, and Done → the main window opens.
- [ ] Open the phone the first time → Home explains battery limits. Tap Keep me connected → Android's battery screen opens. Tap I'm ready → Home shows the link, not the setup list.

## The 25 features

- [ ] 1. Send a note phone to Mac, and Mac to phone → the other side shows the same words.
- [ ] 1. Send a file larger than a photo, then cancel it, then send it again → you see progress, the cancel stops it, and the second send finishes. A received file opens from the list.
- [ ] 2. Share two files from another Android app to BridgeLink → both arrive on the Mac.
- [ ] 2. Drop two files on the Mac window → both arrive on the phone. Finder, Services, Send to phone does the same for one file.
- [ ] 3. Raise a notification on the phone → a Mac banner shows the app. Dismiss it on the Mac → it clears on the phone. Reply only if that app allows a reply.
- [ ] 4. Play a song on the phone → the Mac shows the title. Pause on the Mac → the phone pauses. From the phone, pause music that is playing on the Mac.
- [ ] 5. Let the phone battery change → the Mac shows the new number. At a low battery, the other side says so.
- [ ] 6. Tap Find phone → the phone rings even if the ringer is off. Stop it from the Mac → it stops.
- [ ] 7. Receive a text that is only a short code → the code lands on the Mac clipboard and is not written into the activity list.
- [ ] 8. Send an https link either way → the other device offers to open it. With Always open links on, it opens without asking.
- [ ] 9. On the phone, drag in the trackpad → the Mac pointer moves. Two fingers scroll. Type a letter → it appears in the focused Mac app. Next and Previous move a slide. The first time, the Mac asks for Accessibility.
- [ ] 10. Copy ordinary text on the phone with clipboard sync on → the Mac clipboard changes. Copy the word password → it is not synced.
- [ ] 11. Open Messages on the Mac → you see a phone text. Send a reply → the phone sends it. If the phone has two SIMs, you can pick one.
- [ ] 12. Start mirror → the Mac window shows the phone screen after you allow the capture. Close the window → the capture stops.
- [ ] 13. Click inside the mirror → the phone acts on that tap, after Accessibility is on. Back, Home, and Recents work. The Mac does not unlock the phone.
- [ ] 14. Start the camera → the Mac shows the live camera inside BridgeLink. Switch front and back. It does not appear as a camera in Zoom.
- [ ] 15. Put a small file in the shared folder on one side → it shows up on the other. Pause sync → a new file stays put. A file over 32 MB is skipped.
- [ ] 16. Turn on leave-lock, then walk the phone away → the Mac locks after the delay. Bring the phone back → the Mac stays locked.
- [ ] 17. Removed. Away and a remote address do not appear in either app.
- [x] 18. Removed. There is no combined inbox. Alerts, texts, and files each have their own place.
- [ ] 19. Copy several things → history shows them, up to 100. A password is not stored. Pin one, delete one.
- [ ] 20. Scan a page on the phone → a file is saved on the Mac and opened. If the scanner is missing, pick a photo instead and that arrives too.
- [ ] 21. Add one rule, phone connects then alert → the next connect shows that alert. An eighth rule is the last one accepted.
- [ ] 22. Drop a file on the menu-bar icon → the phone receives it.
- [ ] 23. Forget the phone, then scan the QR again → you are paired without reinstalling. Rename the phone → the Mac shows the new name.
- [ ] 24. Pair, then look at a note on the wire only if you are debugging. Local traffic uses the pinned Mac certificate.
- [ ] 25. Delete is not required. If the Mac key changes, the phone says so and sending stops → Forget this Mac, then scan again, and sending works.

## Update and reset

- [ ] Settings, Check for update, while you are on 1.0.0 and nothing newer is published → the screen says this is the latest.
- [ ] When a newer tag exists → the release page opens. The app does not install it by itself.
- [ ] Reset BridgeLink on the Mac, quit, open it again → setup can show again, the old phone is not still paired, and the phone asks you to pair because the key changed.

## Not in this test

iMessage, FaceTime, AirDrop, Handoff, unlocking the Mac, phone-call audio, and running Android apps in a Mac window are not part of BridgeLink.
