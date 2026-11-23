# BridgeLink v0.13.0: deep code audit

Date: 15 Nov 2026. Source: `bridgelink-main` zip (v0.13.0, versionCode 12).
Scope: `android/`, `macos/`, `relay/`, `.github/workflows/`, `docs/`, `scripts/check-*.py`.

## How this audit was done (and what was not done)

| Check | Result |
|---|---|
| Android `./gradlew test lint assembleDebug` (JDK 17, SDK 35, on Linux) | **BUILD SUCCESSFUL** in 2m31s. 28 unit tests, run for debug and release (56 runs), **0 failures**. Lint: **0 errors, 50 warnings** (mostly old dependency versions, `commit()` vs `apply()`, default locale). Debug APK built (20 MB). |
| Android emulator / instrumented tests | **Not run** (no KVM emulator here). Read by hand instead. |
| Mac Swift build / `swift test` | **Not possible on Linux.** Every Swift file was reviewed by reading only. |
| `node --check relay/worker.js` | Syntax OK. Not deployed. Not load-tested. |
| `python3 scripts/check-matrix.py` / `check-versions.py` | Both pass ("matrix ok 25 features", "versions ok 0.13.0 (12)"). Note: check-matrix only checks that a test **name** appears in the code, not that it tests the feature. |
| Protocol cross-check | Every message type sent by each side was compared with what the other side handles (table at the end). |

Nothing here was run on a real phone or a real Mac. "Works in code" below means the code path looks complete when read, not that it was tested on a device.

**Counts:** Critical 7 · High 16 · Medium 27 · Low 15 (65 items in total).

---

## CRITICAL (breaks the app, or is a real security hole)

**C1. The phone never reconnects after a clean disconnect.** If the Mac quits, restarts, updates, sleeps, or drops a quiet phone after 45 s, the socket closes cleanly. `linkState.phase` stays `PAIRED`, no retry is scheduled, and the network callback skips reconnecting because the phase is still `PAIRED`. After that the phone stays "Disconnected" until the user taps Connect.
- `android/.../LinkService.java:438-447` (read loop ends with no exception), `:472-492` (finally: no retry branch), `:1414` (network callback returns when phase == PAIRED), `LinkState.java:18-23`.
- Fix: in `finally`, when the attempt ended without a fatal error and a saved token exists, call `linkState.retryable("Link closed")` and schedule `connect()` with backoff. Reset the phase to IDLE or BACKOFF on any close. Add a unit test for "EOF after paired leads to reconnect scheduled".

**C2. Any web page or app can replace the pinned Mac key, and the phone then sends its saved token to that fake Mac.** `bridgelink://pair` is a BROWSABLE deep link. `applyLink()` overwrites `fp` (the pinned fingerprint) and connects at once with no confirmation. The saved `token` stays, so `HelloPlan` sees "pinned == seen" and sends the token to the attacker's certificate.
- `android/app/src/main/AndroidManifest.xml:55-60`, `PhoneWork.kt:51-56` (`openLink`), `PhoneWork.kt:70-93` (line 86 `putString("fp", …)`), `HelloPlan.java:29-33`.
- Fix: never overwrite an existing `fp` from a link without a confirm dialog that shows the Mac name and the new fingerprint. Always delete `token` when `fp` changes. Store token **per Mac fingerprint**. Do not auto-connect from a deep link. Show "Pair with this Mac?" first.

**C3. The Mac opens every received file automatically (remote code risk).** `finishNow` calls `NSWorkspace.shared.open(dest)` for every file. A `.command`, `.terminal`, `.app` (inside a zip), `.pkg` or `.webloc` is opened with no warning. Also, `ShareActivity` is exported, so **any app on the phone** can start it with an explicit intent and push a file to the Mac with no user action.
- `macos/Sources/BridgeLink/MacFiles.swift:205`, `AndroidManifest.xml:62-81` (ShareActivity exported), `ShareActivity.java:17-50`.
- Fix: never auto-open. Show a banner "photo.jpg saved. Open / Show in Finder". Only auto-open the scan PDF when the user turned that on, and only `.pdf` and `.jpg`. Set the quarantine attribute (`com.apple.quarantine`) on received files, or add `LSFileQuarantineEnabled`. In ShareActivity, show a confirm screen ("Send 3 files to Mac?") before sending.

**C4. "Away from home" cannot work as written.** Five separate breaks:
1. `relay/wrangler.toml:11` uses `new_classes`. On a **free** Cloudflare plan, Durable Objects need `new_sqlite_classes`, so `wrangler deploy` fails on a free account.
2. Mac replies go to a fake socket: `acceptRelay` passes `live ?? NWConnection(host:"127.0.0.1", port: 9)`, and `send(on: connection)` writes to that unstarted connection. So `pong`, `file_have`, `folder_want`, `folder_part`, `media_now` and `error` replies are lost. `MacSix.swift:49-52`, `LinkExtras.swift:15-24`.
3. Over the relay the phone never starts its heartbeat (it only starts on `paired`). The Mac's silence clock then drops the relay "connection" after 45 s. `LinkService.java:189-192`, `LinkListen.swift:116-136`.
4. Replay counters are never reset. Mac `seen` (`MacSix.swift:178,247`) and phone `seen` (`RelayDoor.java:39,163`) live for the whole app run. When the other side restarts, its counter starts at 1 again and **every frame is dropped** until this side also restarts.
5. No automatic switch. The phone only tries the relay when a retry is NOT scheduled (`LinkService.java:481-487`, which almost never happens). Once the relay is up, `paired=true` stops Wi-Fi from ever being tried again (`:1402`). The Mac never stops `away` when Wi-Fi returns.
- Fix: use `new_sqlite_classes` and WebSocket hibernation. Route relay replies through `away` (pass `nil` connection when not on the local link). Start the heartbeat on `relayUp`. Use a per-session random 64-bit session id plus counter, and reset `seen` on every new session. Add an auto-switch state machine (Wi-Fi first, relay after 2 failed Wi-Fi tries, back to Wi-Fi when the network changes).

**C5. A forgotten phone (or anyone with the relay secret) keeps full control through the relay forever.** The relay path skips hello/token checks: `acceptRelay` calls `handle(..., pending: false)` with `liveID`, so every command is accepted. The relay secret is one per Mac, sent to every phone (`MacSix.swift:8-17`), never rotated on forget, and stored in plain UserDefaults and plain SharedPreferences (`PhoneSix.java:98-104`, not in the sealed `Prefs` token slot).
- Fix: run a short authenticated hello inside the sealed relay channel (the device token, checked against the DeviceBook). Use one relay secret **per paired phone**. Rotate it on forget. Keep it in the Keychain on the Mac and in the sealed prefs on Android.

**C6. The feature tests do not test the features.** The "integration" tests listed in `FEATURE_MATRIX.md` for features 1–22 only re-check constants and helper functions on the emulator, for example `assertTrue(StageSix.CONTROL_STEPS.contains("Accessibility"))`. No test sends a single real message between the two apps except pairing and one note. `check-matrix.py` only checks that the test name exists somewhere. So "Built – NOT TESTED on a device" is not backed for 22 of the 25 features.
- `androidTest/.../StageSixDeviceTest.java:12-24` (same pattern in StageThree, Four and Five), `ProtocolTest.java:284-309` and `:627-725`, `scripts/check-matrix.py:52-56`, `docs/FEATURE_MATRIX.md`.
- Fix: add a loopback integration test (a Java fake Mac using `fake-mac.p12`) that drives the real `LinkService` through each message type and checks the reply. Add Swift tests that feed real phone JSON fixtures into `LinkModel.handle`. Make check-matrix require a test that contains the feature's message type.

**C7. Screen mirror and webcam only start when the phone app is open on screen.** `mirror_start` and `cam_start` only set a static `want` string (never read) and publish the status text "Allow screen share". The activity reacts by **matching that exact status string** in its broadcast receiver, which exists only between onStart and onStop. If the phone is locked or BridgeLink is in the background, nothing happens and the Mac waits forever. The Mac's quality, facing, zoom, flash and mic choices are ignored, and the phone uses its own UI values.
- `PhoneSix.java:25,57-79`, `SixWork.kt:100-108` (`onAsk` string match), `MainActivity.kt:72-87`, `SixWork.kt:29-33,58-66`.
- Fix: when `mirror_start` or `cam_start` arrives, post a high-priority notification "Mac wants to see your screen. Tap to allow" with a full-screen/PendingIntent to a small `ConsentActivity` that launches the MediaProjection prompt, passing the Mac's quality, facing, zoom, flash and mic as extras. Remove the string matching and the unused `want`.

---

## HIGH

**H1. The mirror encoder can crash the app on stop.** `stop()` releases `codec` on the main thread while the `drain` thread is still calling `getOutputBuffer` and `releaseOutputBuffer`, which are not in a try block. That throws an IllegalStateException on a background thread and kills the app. `MirrorCast.java:79-105` and `:107-134`. Fix: set `running=false`, `join()` the drain thread (with a timeout) before `codec.stop()`/`release()`, and wrap all codec calls in try.

**H2. Video and camera frames have no backpressure, so memory grows without limit.** `LinkService.sendRaw` submits every frame to the unbounded `Outbox` queue. On slow Wi-Fi or the relay the queue grows and lag and memory rise until the app dies. `LinkService.java:1012-1022`, `MirrorCast.java:147-155`, `CamCast.java:143-166`. Fix: a small bounded queue for media (for example 3 frames). Drop non-key frames when it is full, and ask for a key frame when frames were dropped.

**H3. A new watchdog thread is started for every frame written.** Mirror at 30 fps × several chunks means hundreds of threads per second, which wastes CPU and battery. `FrameChannel.java:101,125-140`. Fix: one long-lived watchdog that checks `writeStartedNanos`, or socket `setSoTimeout` plus a scheduled executor.

**H4. Mac file sending loads the whole file into memory.** `hop()` waits only until the main thread has *queued* a Task, not until `NWConnection.send` finishes, so a 4 GB file gets buffered in RAM. The single `files` queue is also blocked for the whole send, so files coming in from the phone stall. `MacFiles.swift:46-131,134-141`, `App.swift:300-327`. Fix: send each chunk with a completion handler and wait for `.contentProcessed` (keep 4–8 chunks in flight). Use separate queues for sending and receiving.

**H5. The Mac main thread is blocked by AppleScript.** `MacMedia.now()` runs 2–3 `osascript` processes with `waitUntilExit()` on the MainActor every 16 s (`pollMacMedia`) and on every media command. `MacMedia.swift:42-47,103-118`, `MacStage.swift:84-88`, `EasyMac.swift:91-104,136-147`. Fix: run them on a background queue with a timeout, and use MediaRemote-free fallbacks. Poll only while the phone's media screen is open.

**H6. All incoming data is processed on the Mac main thread.** Every frame (H.264 decode, JPEG decode, base64 file chunks, shared-folder hashing) runs in `Task { @MainActor }`. The UI stutters during mirror or transfers. `LinkListen.swift:186-222`, `MacSix.swift:59-82`. Fix: parse on the network queue. Decode video and camera on a dedicated queue. Only publish UI state on the main thread.

**H7. Turning on the phone trackpad without Accessibility creates a stack of modal alerts.** Every `pointer` message calls `explainPointer()`, which calls `NSAlert.runModal()`. `MacFive.swift:590,598-605`. Fix: show the alert once per session and ignore pointer events until trusted, with a non-modal banner.

**H8. Toggling notification mirroring on the Mac wipes other settings.** `setMirror` writes `flags = "notify=1,otp=1"`, which erases `folder`, `input` and `auto`. `App.swift:226-229`. Fix: use `StageRules.setFlag` for `notify` and `otp` only.

**H9. Leave-lock usually cannot deliver its lock.** The phone sends `lock` over the Wi-Fi link, but when you walk away the Wi-Fi link is usually gone too (`send()` refuses when not paired). The Mac has no detection of its own. `LeaveWatch.java:141-160`, `LinkService.java:211-216,731-736`. Fix: the Mac should also lock by itself when it loses the paired phone's link **and** the phone's BLE presence for the set delay (CoreBluetooth central scan for a phone beacon, or link loss plus a timer). Keep "never unlock".

**H10. Lock silently changes Mac system settings.** `MacLock.lock()` writes `com.apple.screensaver askForPassword` with `defaults write`, which goes against master prompt Part 1.8 ("no silent system-setting changes"). It also needs Accessibility plus System Events automation for the Ctrl-Cmd-Q keystroke. `MacMedia.swift:136-148`. Fix: drop the `defaults write` calls. Lock with `SACLockScreenImmediate` (login framework) or `pmset displaysleepnow`, and show a one-time card if "require password immediately" is off.

**H11. The Mac share extension cannot load.** It is built with `swiftc -emit-library` (a dylib, not an extension executable with `NSExtensionMain`), it is not sandboxed (app extensions must be), and it writes into `~/Library/Application Support` (not allowed from a sandbox). `macos/build.sh:36-41`, `macos/share/Info.plist`, `share/ShareExt.swift:40-48`. Fix: either build a proper appex (`-parse-as-library -e _NSExtensionMain`, app-sandbox entitlement, an App Group or `NSWorkspace.open` with a custom URL scheme to hand paths to the app), or remove it and keep Finder Services, Dock drop and the drop zone. Say so honestly.

**H12. Two paired phones on one Mac knock each other off.** `promote()` cancels the current `live` connection whenever another paired phone connects, then that phone reconnects and kicks the other: an endless loop that drains battery. `LinkSession.swift:262-277`. Fix: either keep a map of live sessions (one per deviceId), or refuse the second phone with a clear "Another phone is connected" error and no auto-retry for 5 minutes.

**H13. Several Macs on one phone do not really work.** There is one `token` and one `fp` key. Pairing a second Mac overwrites the first. `MacList` keeps names and hosts but no credentials, and switching Macs gives "key changed" and deletes the token. `LinkService.java:518-529`, `PhoneWork.kt:80-87`, `MacList.java`. Fix: store `{fp, token, hosts, port, name, deviceId}` per Mac (token sealed). Add a "Switch Mac" list.

**H14. Automations on the phone mostly do nothing.** `copy_code`, `alert`, `note`, `ring` and `open_app` only write a status line. Nothing is copied, no alert is shown, nothing rings. `PhoneFive.java:160-171`. `home_wifi` fires for **any** Wi-Fi because the wanted name is `""` (`PhoneFive.java:434`, `StageFive.homeWifi` returns true for empty), and the SSID cannot be read on Android 10+ without location permission (`ACCESS_FINE_LOCATION maxSdkVersion=30` in the manifest at `:28`), so it is `<unknown ssid>`. Fix: implement real phone actions (copy, notification, ring the Mac with `find_mac`) or hide phone-side actions that only run on the Mac. Let the user set the home Wi-Fi name. Ask for location (while in use) only if a Wi-Fi rule exists, or use the Mac's SSID instead.

**H15. Release can publish without Mac signing, and nothing checks the APK key.** `release.yml` never sets `REQUIRE_MAC_SIGN=1`, so an ad-hoc Mac app ships quietly (`sign.sh:38-42`). The release does not run `check-versions.py`, does not check that the tag matches `versionName`, does not verify the APK certificate (`apksigner verify --print-certs`) against the known fingerprint, does not depend on CI or emulator success, and makes no SHA256SUMS or appcast. `.github/workflows/release.yml`. Fix: add those checks. Fail when the Mac secrets exist but the signature is ad-hoc.

**H16. Required items from the master prompt are still missing.** Sparkle 2 updater + appcast, the Mac first-run window and permission cards, the phone first-run setup, "Check for update" on Android, and "Reset BridgeLink" on the Mac. `docs/PROGRESS.md:19`, `docs/BUILD_PROMPT.md` Part 3 g–j. Fix: build them (see the fix prompt, Step 6).

---

## MEDIUM

**M1. Mac IP changes break the reconnect.** The phone reconnects only to the saved IP. Bonjour browsing runs only when the user taps "Find Mac on Wi-Fi". `LinkService.java:496-507`, `PhoneWork.kt:96-109`. Fix: on 2 failed tries, browse `_bridgelink._tcp` for the pinned fingerprint and update the host.

**M2. A manual Connect overwrites the multi-address list** (`hosts` becomes a single host). `PhoneWork.kt:58-67`. Fix: merge, don't replace.

**M3. A second waiting connection silently orphans the first.** `accept()` overwrites `pendingID` and `pending`. The first connection's receive loop stops without cancelling, which leaks it and can break a real pairing during a scan. `LinkListen.swift:138-160,186-192`. Fix: track pending connections in a dictionary keyed by id.

**M4. The phone cannot stop the Mac's "Find my Mac".** Android never sends `find_stop`. "Stop ringing" only stops the phone's own ring. The README says "either side can stop it". `PhoneWork.kt:254-258`, `SettingsPage.kt:66`. Also the Mac "ring" is only `NSSound.beep()`, which is silent if muted (`LinkExtras.swift:89-98`). Fix: add a "Stop Mac ringing" path that sends `find_stop`. Play a bundled loud sound at full volume for 30 s.

**M5. Notification reply and dismiss only work for the newest notification.** The Mac keeps a single `lastNotifyKey`. A phone-side dismiss only writes a log line and does not remove the Mac banner. `LinkSession.swift:143-144,192-193`, `MacStage.swift:48-63`. Fix: use the phone key as the UNNotification identifier. Remove it on `notify_dismiss`. Add a Reply text action on the banner (`UNTextInputNotificationAction`).

**M6. Notifications are mirrored again on every update.** The `duplicate` argument is hard-coded `false`, so a chat or progress notification that updates is sent many times. `NotifyMirror.java:42`. Fix: dedupe by key plus hash of title/text, and ignore progress-only updates.

**M7. Mac notification permission is never requested on first launch.** It is only requested from a button, so banners fail silently. `App.swift:248-265`. Fix: ask in the first-run window.

**M8. Mac → phone Do Not Disturb sync cannot work.** `INFocusStatusCenter` is read with no authorization request and no `NSFocusStatusUsageDescription`, so `isFocused` is nil. `MacStage.swift:91-98`, `macos/Info.plist`. Fix: add the usage string and request authorization, or drop the feature with a clear note.

**M9. Phone → Mac clipboard auto-sync does not run in the background.** Android 10+ blocks clipboard reads by background apps, so the listener only fires while BridgeLink is on screen. Phone clip history is also only filled then. `LinkService.java:1093-1102`. Fix: say so on screen. Offer a "Send clipboard" tile, a notification action, and a share target for text.

**M10. Password-manager copies are not detected.** Android ignores `ClipDescription.EXTRA_IS_SENSITIVE`, and the Mac ignores `org.nspasteboard.ConcealedType` / `TransientType`. Only a word and number heuristic is used. `LinkService.java:1115-1123`, `MacStage.swift:73-83`, `Features.java:76-95`. Fix: check those flags first and never sync or store such items.

**M11. Shared folder is not watched.** Changes sync only on pair, on toggle or on manual sync, with no FileObserver or FSEvents. `PhoneFive.java:321-345`, `MacFive.swift:387-401`. Fix: FSEvents on the Mac, and a FileObserver plus 60 s debounce on the phone (only while the link is up).

**M12. Shared-folder scanning is very slow and wasteful.** On Android, `onIndex` calls `find()`, which re-hashes the whole folder **for every remote entry** (O(n²) hashing) (`PhoneFive.java:270-297,498-503`). On the Mac, `walk()` reads every whole file into memory on the main thread (`MacFive.swift:268-287`). Fix: one scan per index, a cached `{path: size, mtime, sha}` table, and re-hash only when size or mtime changed.

**M13. The phone's shared folder is in a hidden place.** It sits in `Android/data/app.bridgelink.android/files/Shared`, which most file managers cannot open on Android 11+. `PhoneFive.java:50-52`. Fix: let the user pick a folder with `ACTION_OPEN_DOCUMENT_TREE` (SAF), or use `Documents/BridgeLink Shared` through MediaStore.

**M14. The Mac can overwrite the phone's `.seen` index.** Incoming shared paths starting with `.` are allowed. `FolderFiles.java:42-101`, `Features.java:245-259`. Fix: reject any path segment that starts with `.`.

**M15. The 32 MB shared-folder cap** is the open Stage 7 item. `StageFive.java:16`, `FolderFiles.java:111`, `MacFive.swift:235`. Fix: stream with the same chunked, resumable path as file send (4 GB).

**M16. The mirror picture is squashed.** The virtual display is a fixed 1280×720 (landscape) for a portrait phone, so the picture is distorted and tap positions are off. The SPS/PPS are sent once only, so a lost first frame means a black window. `MirrorCast.java:41-60`, `:136-145`. Fix: size the display to the phone's real aspect ratio (scaled to the quality's long edge), handle rotation, and repeat the parameter sets before every key frame (`KEY_PREPEND_HEADER_TO_SYNC_FRAMES`).

**M17. Remote taps use the wrong screen size.** `displayMetrics` leaves out the system bars on many phones, so taps land offset. `TouchGate.java:20-24`. Fix: use `WindowManager.getCurrentWindowMetrics().getBounds()` (or `getRealMetrics` on API < 30).

**M18. The camera picture is sideways or mirrored.** Sensor orientation is ignored. `CamCast.java:113-166`. Fix: rotate using `SENSOR_ORIENTATION` and the facing.

**M19. Mac trackpad drag is wrong.** Each move posts mouse-down, drag and mouse-up, so dragging becomes many tiny drags. `MacFive.swift:60-67`. Fix: keep a drag state (down on start, dragged events, up on end).

**M20. The phone cannot send a folder.** It has no tree picker, but the Send page says "A folder is sent as a zip". `MainActivity.kt` (OpenMultipleDocuments only), `FileBridge.java:254-259`, `SendPage.kt:19`. Fix: add an `OpenDocumentTree` picker that zips on the fly, or fix the text.

**M21. Share-sheet file access can be lost.** `ShareActivity` finishes right away, and the service reads the content URIs later. The temporary grant can be revoked, which breaks big shares. `ShareActivity.java:38-49`. Fix: pass the URIs with `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` on the service intent, or keep the activity until streaming starts.

**M22. Incoming 4 GB files are written to `cacheDir`.** Android may clear that, and it needs double space. `FileBridge.java:64-70`. Fix: write the partial straight into a pending MediaStore Downloads item.

**M23. The relay hello ticket is never checked by the relay.** Anyone who knows the room id can take the "mac" or "phone" slot and knock the real device off. `relay/worker.js:18-31`. Fix: an HMAC ticket with a timestamp that the relay can check against a room key, and refusing to replace a live slot without a fresh ticket. Also add size and rate limits and an idle timeout.

**M24. Both directions use the same relay key,** so a relay can reflect the phone's own frames back to it. `Features.java:270-273`, `MacSix.swift:192`. Fix: derive two keys (`mac->phone`, `phone->mac`) with HKDF info strings.

**M25. Big frames are dropped silently by the relay.** A full 1 MiB frame plus 28 bytes of seal is over the worker's limit (`size > 1048576`). `worker.js:37-38`. Fix: keep relay frames ≤ 512 KB (split), and report drops.

**M26. Mac DMG layout never applies.** The image is mounted with `-nobrowse`, so Finder cannot `tell disk "BridgeLink"`, and the background and arrow are always skipped. `make-dmg.sh:15-36`. Fix: mount without `-nobrowse`, or build the DMG with `create-dmg` / a pre-made `.DS_Store`.

**M27. Mac keychain prompts after each update while ad-hoc signed.** The TLS identity in the login keychain is tied to the code signature. `Identity.swift`. Fix: stable signing (owner's one-time script), plus a clear first-run note.

---

## LOW

**L1. `LinkService.java` is 1,495 lines.** It also has stage-named files (`StageFour/Five/Six`, `PhoneFive/Six`, `FiveWork`, `SixWork`, `EasyWork`, `MacFive`, `MacSix`, `EasyMac`, `SixPages`, `FivePages`, `EasyPages`, `MacStage`). Fix: split by feature (Stage 7 item 4).

**L2. Rules are duplicated by hand** in `Features.java` / `Features.swift` and `StageX.java` / `StageX.swift`, with no shared fixtures beyond `text.json`. Fix: golden JSON fixtures for every message type, used by both test suites.

**L3. "Seek" always jumps to the start of the track.** `MediaControl.java:61`.

**L4. OTP can be copied twice** (from the SMS receiver and from the SMS app's notification). `OtpSms.java:36-43`, `LinkService.java:810-818`.

**L5. `startRing` sets the alarm volume outside a try block.** It can throw on some DND states. `LinkService.java:1250-1254`.

**L6. A progress notification is updated for every chunk** ("Sending x — 37%"), and each failed video frame posts "The picture could not be sent". `LinkService.java:864-866,1019`. Fix: rate-limit to once per second.

**L7. The accessibility config asks for click and focus events it never uses.** That wakes the service needlessly. `res/xml/touch_gate.xml`.

**L8. The Mac BLE beacon starts for everyone on launch** (even with leave-lock off), which triggers a Bluetooth prompt. `EasyMac.swift:44-49`.

**L9. The phone opens Mac links and received files automatically when the screen is on.** Better to show a notification with an "Open" button. `LinkService.java:966-988,649-651`.

**L10. `sign.sh` changes the default keychain** (`security default-keychain -s`). That is fine on CI, but harmful if run on the owner's Mac. `sign.sh:15`.

**L11. Release notarization staples the `.app` after the DMG is built** (it should staple the DMG). `release.yml` mac step.

**L12. The Android key-making script was removed.** Do not add it back. A new key would not match the owner's existing signing secrets.

**L13. The Android bottom bar scrolls sideways,** so Remote, Mirror and Settings are hidden off-screen. The status card shows "Not connected / Not connected". `BridgeTheme.kt:122`, `docs/screenshots/android-home.png`.

**L14. The Mac UI is plain** (small grey pills, "Mac: No battery" on desktops). `docs/screenshots/mac-home.png`, `DeskPages.swift`.

**L15. 50 lint warnings and old dependencies** (Compose BOM 2024.12, Room 2.6.1, document-scanner beta).

---

## Docs that are wrong or contradict the code

| Doc | Problem |
|---|---|
| `docs/SERVICES.md:8` | Says the relay "is not built. Local Wi-Fi is the only path". The code has a relay. |
| `docs/SETUP_FOR_OWNER.md:28`, `INSTALL.md:5`, `TROUBLESHOOTING.md:27` | Say a missing secret attaches `BridgeLink-android-debug.apk`. The release workflow now fails instead. |
| `docs/SETUP_FOR_OWNER.md:34-36` | Relay steps would fail on a free plan (`new_classes`). |
| `docs/PROTOCOL.md` | Stale ("Types in 0.4.0", "no in-app camera scanner in 0.3.0"). Missing: `notify_reply`, `notify_dismiss`, `dnd`, `media`, `media_now`, `lock`, `lock_setup`, `forget`, `folder`, `find_mac`, `clipboard_image`, the `label` and `deviceId` keys. |
| `docs/FEATURE_MATRIX.md` | Manual ids M1–M25 are not defined anywhere. The integration tests listed don't exercise the features (see C6). |
| `docs/TESTING.md` | Says manual items are "on the manual list in the release notes". The release uses `--generate-notes`, so no such list exists. |
| `README.md` | Claims "Either side can stop it" (find), "several phones and Macs", "dismiss both ways", "A folder is sent as a zip" (phone), "Away from home: Built" and "All 25 Built – NOT TESTED on a device". None of these is fully true (see above). Screenshots show staged data from `ShotBook.swift` / `DesignShotTest.kt` and are not labelled as staged. |
| `docs/PROGRESS.md` | Does not list any of the Critical or High items above. |

---

## Protocol cross-check (sent vs handled)

| Type | Sent by | Handled by other side? | Note |
|---|---|---|---|
| hello, sas_reveal, sas_confirm | Phone | Mac yes | OK |
| sas, paired, error | Mac | Phone yes | OK |
| ping / pong | Phone / Mac | yes | Not over the relay (C4) |
| text, link, clipboard, clipboard_image | both | both | OK |
| battery (`label`) | both | both | `label` key not in PROTOCOL.md |
| ring, find_stop | Mac | Phone yes | — |
| find_mac | Phone | Mac yes | **Phone never sends `find_stop`** (M4) |
| notify, otp, notify_dismiss | Phone | Mac yes | dismiss only logged on the Mac (M5) |
| notify_reply, notify_dismiss, dnd | Mac | Phone yes | single key only (M5) |
| media, media_now, lock_setup | both | both | OK |
| lock | Phone | Mac yes | Rarely deliverable (H9) |
| file_begin/chunk/end/cancel/have | both | both | OK. No backpressure on the Mac (H4) |
| forget | both | both | OK |
| sms_list, sms_new | Phone | Mac yes | OK |
| sms_send, sms_ask | Mac | Phone yes | OK |
| folder_index/want/part/delete/pause, folder | both | both | OK |
| auto_rules, input | both | both | OK |
| pointer, wifi | Phone | Mac yes | OK |
| mirror_start, cam_start, control, relay_key | Mac | Phone yes | start only if the app is open (C7) |
| mirror_stop, cam_stop, relay_set | both | both | OK |
| relay_use | Phone (internal) | — | local command only, never on the wire. OK |
| Binary type 2 (video, JPEG, audio) | Phone | Mac yes | Phone ignores binary (fine, the Mac never sends it) |

No type is sent that the other side lacks a handler for, apart from the gaps noted. Payload keys match on both sides. The `label`, `deviceId` and `art` keys are used but not documented in PROTOCOL.md.

---

## Per-feature verdict (25 features)

Legend: **Works** = the code path looks complete both sides when read (not device-tested) · **Partial** = works only in some cases or one side · **Broken** = cannot work as written · **Missing** = no code.

| # | Feature | Verdict | One-line reason |
|---|---|---|---|
| 1 | Send files, links, text | Partial | Works both ways, but the phone can't send folders, the Mac buffers whole files in RAM, and the Mac auto-opens received files (C3, H4, M20) |
| 2 | Share sheet + drag-drop | Partial | Android share and Mac drop/Services OK. The Mac Share extension can't load (H11). URI grant risk (M21) |
| 3 | Notification mirroring | Partial | Banners work. Reply/dismiss only for the newest one, phone dismiss doesn't clear the Mac banner, duplicates (M5, M6, M7) |
| 4 | Media control | Works | Both ways in code. Mac AppleScript blocks the UI (H5). Seek goes to 0 (L3) |
| 5 | Battery status | Works | Sent on change both ways with alerts |
| 6 | Find phone / Find Mac | Partial | Phone rings well. The Mac only beeps, and the phone can't stop it (M4) |
| 7 | OTP auto-copy | Works | SMS and notification codes, cleared after 60 s. May copy twice (L4) |
| 8 | Open link on other device | Works | Both ways, http/https only |
| 9 | Trackpad, keyboard, slides | Partial | Move, click, keys, slides OK. Drag broken (M19). Modal alert storm without Accessibility (H7) |
| 10 | Clipboard sync | Partial | Mac→phone OK. Phone→Mac only while the app is on screen (M9). Password flags ignored (M10) |
| 11 | SMS | Works | Read, send, new-text banners, SIM choice (needs SMS permission) |
| 12 | Screen mirror | Partial | Only starts with the phone app open (C7). Squashed picture, crash on stop, no backpressure (H1, H2, M16) |
| 13 | Control phone while mirroring | Partial | Depends on 12. Taps offset (M16, M17) |
| 14 | Phone as webcam | Partial | Same start problem (C7). Mac choices ignored. Sideways picture (M18). Window only (no Zoom camera, free-account limit) |
| 15 | Shared folder | Partial | Syncs only on pair/toggle, 32 MB cap, O(n²) hashing, hidden phone folder, flags wiped by H8 |
| 16 | Lock Mac when you leave | Partial | Works only while still on the same Wi-Fi. Mac has no own detection. Changes system defaults (H9, H10) |
| 17 | Away from home | **Broken** | Free-plan deploy fails, Mac replies lost, 45 s drop, counters break after restart, no auto-switch (C4) |
| 18 | Unified inbox | Works | Mac stores notifications, texts and files with search and filter |
| 19 | Clipboard history | Works | Mac 100 items, skips sensitive-looking text. Phone history only while in foreground |
| 20 | Scan to Mac | Works | PDF via Play services, or a photo as JPG (not PDF) fallback |
| 21 | Automations | Partial | Mac actions work. Phone actions are status text only. Home-Wi-Fi fires for any Wi-Fi (H14) |
| 22 | Quick drop | Works | QS tile opens the app. Mac menu-bar drop and floating drop zone |
| 23 | Pairing | Partial | QR and match code work for one phone and one Mac. Multi-Mac and multi-phone broken (H12, H13). Deep-link hijack (C2) |
| 24 | End-to-end encryption | Partial | Local TLS with pinning OK. Relay: same key both ways, unchecked ticket, secret never rotated (C5, M23, M24) |
| 25 | Key pin | Partial | Pin enforced, but a deep link can silently replace it (C2) |

**Summary:** 9 Work in code (4, 5, 7, 8, 11, 18, 19, 20, 22) · 15 Partial · 1 Broken (17) · 0 fully missing features. Missing **non-feature** items: Sparkle updater, Mac first-run, phone first-run, Android "Check for update", Mac "Reset BridgeLink".
