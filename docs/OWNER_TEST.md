# Owner test

Nothing here was tested on a device by the bot.

Use a real Android phone and a real Mac on the same Wi-Fi. Use the APK from the latest green CI run, not a GitHub release. Use the Mac disk image from that same run if CI built one. If CI did not upload a disk image, build is not your job: say so in the notes.

After each section, write Pass, Fail, or a note.

## 0. Install

1. Download the APK from the green CI artifacts. Tap it and allow install from that source.
   You should see: BridgeLink opens. You should not see a Play Store page.
2. On the Mac, open the disk image. Drag BridgeLink to Applications. Right-click the app and choose Open, then Open.
   You should see: the setup window. macOS 15 may also ask for Open Anyway in Privacy and Security.
3. Leave both apps open.
   You should see: the phone says it is not connected. The Mac menu bar shows BridgeLink.

Pass / Fail / Notes:

## 1. Setup guide

1. On a fresh phone install, read the first setup page. Tap Skip.
   You should see: the next page. Nothing is asked until you tap Allow.
2. Tap Allow on notifications, or Skip if this phone does not show that page.
   You should see: Android's permission question, only after the tap.
3. Finish or skip until the last page. Tap I'm ready.
   You should see: "You can change these any time in Settings > Permissions."
4. Open Settings. Read Permissions.
   You should see: each item says On, Off, or Not needed on this phone.
5. Tap Show setup again.
   You should see: the first setup page returns.

Pass / Fail / Notes:

## 2. Pairing

1. On the Mac, read the QR and the PIN.
   You should see: a QR code and a short PIN.
2. On the phone, tap Scan the Mac QR and scan it.
   You should see: both screens show the same match code.
3. Tap Codes match on both.
   You should see: both say they are paired.
4. On the phone, tap Forget this Mac. Pair again with the QR.
   You should see: the old pair is gone, and the new pair works.

Pass / Fail / Notes:

## 3. Kept features

### 3.1 Files, links, and text

1. Send a short note from the phone.
   You should see: the note on the Mac.
2. Send an https link from the phone.
   You should see: the Mac asks before it opens, unless Always open links is on.
3. Send a small file from the phone.
   You should see: the file in Downloads/BridgeLink on the Mac.

Pass / Fail / Notes:

### 3.2 Share sheet and drag-drop

1. Share a file to BridgeLink from another phone app and tap Send.
   You should see: the file arrive on the Mac.
2. Drop a file on the Mac menu-bar icon.
   You should see: the file arrive on the phone.

Pass / Fail / Notes:

### 3.3 Notification mirroring

1. Turn notification mirroring on and allow notification access.
   You should see: a phone notification appear as a Mac banner.

Pass / Fail / Notes:

### 3.4 Media control

1. Play music on the phone. Use the Mac menu buttons.
   You should see: play, pause, or next change the phone.

Pass / Fail / Notes:

### 3.5 Battery

1. Look at the Mac menu bar panel.
   You should see: the phone battery percent, and whether it is charging.

Pass / Fail / Notes:

### 3.6 Find phone and Mac

1. On the Mac, tap Ring phone. Then Stop ring.
   You should see: the phone rings, then stops.
2. On the phone, tap Find my Mac, then stop it.
   You should see: the Mac makes a sound, then stops.

Pass / Fail / Notes:

### 3.7 OTP

1. Turn on code copy. Receive a text with a one-time code.
   You should see: the Mac copies the code. It should clear after about a minute.

Pass / Fail / Notes:

### 3.8 Open a link

1. Send an https link from the Mac to the phone.
   You should see: the phone asks before it opens the link.

Pass / Fail / Notes:

### 3.9 Trackpad and keyboard

1. Turn the phone trackpad on. Move a finger on the trackpad page.
   You should see: the Mac pointer move, after Accessibility is allowed.

Pass / Fail / Notes:

### 3.10 Clipboard

1. Turn clipboard sync on. Copy plain text on the phone.
   You should see: the same text on the Mac. A password should not be sent.

Pass / Fail / Notes:

### 3.11 Texts

1. Turn texts on and allow SMS.
   You should see: recent texts on the Mac. A reply should send from the phone.

Pass / Fail / Notes:

### 3.12 Screen mirror

1. On the Mac, start the mirror. Allow the screen on the phone.
   You should see: the phone screen in the Mac window.
2. Close the Mac window.
   You should see: the mirror stop.

Pass / Fail / Notes:

### 3.13 Control the phone

1. Turn on the accessibility service. Tap inside the mirror window.
   You should see: the phone react. The Mac should not unlock the phone.

Pass / Fail / Notes:

### 3.14 Webcam

1. Start the webcam in BridgeLink.
   You should see: the phone camera inside the BridgeLink window. It should not appear as a Zoom camera.

Pass / Fail / Notes:

### 3.15 Shared folder

1. Turn the shared folder on. Put a small file in it.
   You should see: the file show up on the other side.

Pass / Fail / Notes:

### 3.16 Leave-lock

1. After a fresh install, do not tap the leave-lock switch.
   You should see: it is off.
2. Turn it on. Walk away until the signal is weak, then wait.
   You should see: the Mac lock. Coming back should not unlock it.

Pass / Fail / Notes:

### 3.19 Clipboard history

1. Turn clipboard history on. Copy two plain notes. Search them.
   You should see: both notes. A password should not be stored.

Pass / Fail / Notes:

### 3.20 Scan to Mac

1. Scan a page, or pick a photo if the scanner is missing.
   You should see: the scan saved on the Mac.

Pass / Fail / Notes:

### 3.22 Quick drop

1. Use the phone Quick Settings tile, or drop a file on the Mac icon.
   You should see: the file go to the other device.

Pass / Fail / Notes:

### 3.23 Pairing extras

1. Pair, forget, and pair a second time. If you have two Macs, switch.
   You should see: each Mac keeps its own trust. Forget drops only that one.

Pass / Fail / Notes:

### 3.24 Encrypted local link

1. Pair on Wi-Fi. Turn the Mac Wi-Fi off.
   You should see: the link drop. It should not switch to a remote server.

Pass / Fail / Notes:

### 3.25 Key pin

1. Pair once. Quit the Mac app and open it again. Connect from the phone.
   You should see: it connects without a new PIN. If you delete the Mac app and install a new copy, the phone should refuse the new key until you forget and scan again.

Pass / Fail / Notes:

## 4. New features

### 4.1 Call alerts

1. Turn call alerts on. Allow phone state, and Decline if Android asks.
   You should see: the call-alerts switch stay on. Android asks only after that tap.
2. Call this phone from another phone.
   You should see: a Mac banner that says Call from the name or number, or Incoming call.
3. Tap Decline on the Mac.
   You should see: the call end on the phone, or a plain sentence if the phone refused.
4. Let a call end by itself.
   You should see: the Mac banner go away.

Pass / Fail / Notes:

### 4.2 Transfer history

1. Send a file, a link, and a note. Open History. Search for the file name.
   You should see: the row. Resend sends it again. Open opens a file, a link, or copies text. Delete removes that row. Clear history empties the list.

Pass / Fail / Notes:

### 4.3 Phone photos

1. Turn phone photos on. On Android 14, choose Selected photos if Android offers it.
   You should see: only those photos, plus Choose more photos.
2. On the Mac, open Photos. Tap Load more if it is there.
   You should see: newest photos first, about 30 at a time.
3. Select two and tap Save.
   You should see: the files in Downloads/BridgeLink.
4. Turn phone photos off.
   You should see: the Mac stop listing photos.

Pass / Fail / Notes:

### 4.4 Mirror quality

1. Start a mirror. Rotate the phone.
   You should see: the Mac window change shape to match the phone.
2. Hold the phone still for a few seconds.
   You should see: the picture stay up without the phone getting hot from a tight loop. This is a look, not a lab measurement.

Pass / Fail / Notes:

### 4.5 Menu-bar status

1. Open the Mac menu bar panel while paired.
   You should see: battery, signal bars, and the latest notification title.
2. There is no home-screen widget. That is expected on a free sideload.
   You should see: no widget to add.

Pass / Fail / Notes:

### 4.6 Pause phone notifications

1. In the Mac menu, turn on Pause phone notifications before allowing Do Not Disturb access.
   You should see: "Allow Do Not Disturb access on the phone."
2. Allow it on the phone. Turn the Mac switch on, then off.
   You should see: the phone's Do Not Disturb follow the switch. The switch should match the phone, not stay on if the phone refused.

Pass / Fail / Notes:

### 4.7 Text from a photo

1. Turn on airplane mode. On the phone, tap Text from photo and pick a picture with words.
   You should see: the words, with no network needed.
2. If the words look like a password, read the warning. Tap Send to Mac clipboard, or Cancel.
   You should see: the words on the Mac clipboard only if you sent them.

Pass / Fail / Notes:

### 4.8 Finder Send to phone

1. Put BridgeLink in Applications. Open it once.
   You should see: the menu-bar icon, and a setup page that says Send to phone.
2. In Finder, right-click a file. Choose Quick Actions or Services, then Send to phone.
   You should see: the file on the phone.
3. If the item is missing, open Settings, Keyboard Shortcuts, and turn Send to phone on.
   You should see: the same steps work on macOS 13, 14, and 15.

Pass / Fail / Notes:

## 5. Errors

1. Turn Wi-Fi off on the Mac. Try to connect from the phone.
   You should see: a plain sentence about Wi-Fi and what to do next. You should not see a stack trace or a Java class name.

Pass / Fail / Notes:

## 6. Removed features

1. Look through the phone and the Mac.
   You should see: no away page, no combined list, and no when-this-then-that rules.

Pass / Fail / Notes:

## 7. Battery

1. Leave the phone and the Mac paired for one hour with the phone screen off. Do not mirror, and do not turn on leave-lock or call alerts unless you want to measure those too.
   You should see: both apps stay linked while the phone screen is off.
2. Open Android Settings, Battery, BridgeLink.
   You should see: the battery number Android shows. Write that number in the notes.

Pass / Fail / Notes:

## 8. What to send back

1. Send the failed step numbers.
   You should see: a short note with only those step numbers. If nothing failed, the note can say none.
2. On the phone, tap Send diagnostics. Send that file.
   You should see: a text file with no messages, no photos, and no keys.
3. Do not send screenshots of passwords, texts, or photos of people.
   You should see: nothing private in what you send back.

Pass / Fail / Notes:
