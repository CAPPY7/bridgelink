=====================================================================
BRIDGELINK – FIX-ALL PROMPT (v0.13.0 -> v1.0.0)
Paste ALL of this to the bot that builds BridgeLink.
This REPLACES the old "Stage 7" prompt. Everything from Stage 7 is inside this one.
=====================================================================

Your first reply must be exactly this line:
Starting the fix-all pass.
Then start right away. Do not wait for me.

---------------------------------------------------------------------
PART 0 – RULES (all master prompt rules still apply)
---------------------------------------------------------------------
This prompt EXTENDS the master prompt in docs/BUILD_PROMPT.md. Where they disagree,
this prompt wins. Save this whole prompt as docs/FIX_ALL_PROMPT.md (no secrets in it)
and add one line at the top of docs/BUILD_PROMPT.md: "See docs/FIX_ALL_PROMPT.md for the
v1.0.0 fix-all pass."

My rules (never ask me about these):
1. I have zero coding knowledge. Talk to me in very simple English, short messages.
2. I will NOT test anything until the very end. Work non-stop through every step.
3. Do not ask me to decide anything. Pick the safest free option and write why in
   docs/DECISIONS.md (one line).
4. Never print, log, or commit any secret, password, or key. Not in chat, not in CI logs.
5. NEVER create a new Android signing key. The existing keystore and the 4 GitHub secrets
   stay. Do not run scripts/generate-android-keystore.sh.
6. Keep the link protocol, TLS, port 8787, and the Bonjour name _bridgelink._tcp.
   New message types are fine. Old ones must keep working.
7. The Mac app stays FREE and SIDELOADED (a DMG you drag to Applications). No paid Apple
   account. No App Store. The Android app stays a sideloaded APK.
8. No paid services. The relay must run on the FREE Cloudflare plan.
9. ONE commit per fix. After EVERY commit: unit tests pass, lint passes, and all CI jobs
   (android, mac, web, emulator) are green. If a job goes red, fix it before the next item.
10. After EVERY fix, update docs/BUGS.md (one line: the problem and "Fixed: the idea of
    the change") and docs/PROGRESS.md (done, next, open problems). If you stop or run out
    of memory, you must be able to continue from PROGRESS.md alone.
11. A feature can only be "Built – self-tested" if a test really sends that feature's
    messages between a phone and a Mac (see Step 2). Otherwise it is "Building".
    Only I can set "Final – owner tested".
12. Things that only I can do (adding Mac signing secrets, deploying the relay) are made
    into ready-to-run scripts plus simple steps. DO NOT wait for me to do them. Keep
    working. They go in the final message.
13. Only message me if you are truly blocked. Then send ONE line: what you need and why.
    At the end of each step group (Steps 1–9), send at most 5 lines: what got fixed,
    the CI link, and what's next. No test lists until the very end.

The full audit with every problem and its file and line is in AUDIT.md. Copy it into
the repo as docs/AUDIT_v0.13.md in your first commit. Each item has an id (C1, H4,
M12, L3 …). Use the id in every commit message, for example: "fix(C1): phone reconnects
after the Mac closes the link".

---------------------------------------------------------------------
STEP 1 – CRITICAL FIXES (do these first, in this order)
---------------------------------------------------------------------
1.1 (C1) Phone never reconnects after a clean disconnect.
    File: android/app/src/main/java/app/bridgelink/android/LinkService.java
    (runConnect finally block around lines 472-492, network callback around line 1414)
    and protocol/LinkState.java.
    - When the read loop ends with no fatal error and a token is saved, schedule a
      reconnect with the existing backoff (Backoff.java). Never leave the phase at PAIRED
      after the socket closed.
    - The network callback must reconnect when the phone is not linked, whatever the
      old phase was.
    - Add a unit test: "link closes cleanly after paired -> reconnect is scheduled".

1.2 (C2) A bridgelink:// link can replace the pinned Mac key and leak the token.
    Files: PhoneWork.kt (openLink, applyLink lines 51-93), HelloPlan.java,
    AndroidManifest.xml (deep link), Prefs.java.
    - A deep link must NEVER auto-connect. Show a dialog first: "Pair with <Mac name>?"
      with the short fingerprint.
    - If a Mac is already pinned with a different fingerprint, say "This is a different
      Mac" and only continue if the user taps "Pair new Mac".
    - Whenever the fingerprint changes, delete the old token first.
    - Store the token per Mac fingerprint (see 4.10).
    - Add a unit test: a link with a new fp never results in a hello that carries the old
      token.

1.3 (C3) The Mac opens every received file automatically, and any phone app can push
    files with no user action.
    Files: macos/Sources/BridgeLink/MacFiles.swift (line 205), ShareActivity.java,
    AndroidManifest.xml.
    - Remove the automatic NSWorkspace.open. Show a notification "photo.jpg saved" with
      "Open" and "Show in Finder" buttons.
    - Only a scan (.pdf or .jpg from Scan to Mac) may open automatically, and only if the
      "Open scans automatically" setting is on (default on).
    - Mark received files as downloaded from outside (quarantine attribute) so macOS warns
      before running scripts or apps.
    - ShareActivity: show a small confirm screen "Send 3 files to the Mac?" with Send and
      Cancel before anything is sent.

1.4 (C4 + C5) Make "Away from home" (the relay) really work, and make it safe.
    Files: relay/worker.js, relay/wrangler.toml, macos/Sources/BridgeLink/MacSix.swift,
    LinkExtras.swift, LinkListen.swift, android .../RelayDoor.java, PhoneSix.java,
    LinkService.java, protocol/Features.java, protocol/StageSix.java and the matching
    Swift files in BridgeLinkCore.
    a) wrangler.toml: use `new_sqlite_classes = ["Room"]` so it deploys on the FREE plan.
       Use the WebSocket Hibernation API (state.acceptWebSocket) so idle rooms cost nothing.
    b) worker.js: check the hello ticket (HMAC with a timestamp, max 2 minutes old). Do not
       let a new socket replace a live slot without a valid fresh ticket. Limit to 2 sockets
       per room, at most 200 messages per second, at most 512 KB per message, and close idle
       rooms after 10 minutes.
    c) Mac: when a message came over the relay, send replies over the relay. Never create
       the fake NWConnection(host: "127.0.0.1", port: 9) in acceptRelay.
    d) Start the phone heartbeat (ping every 20 s) when the relay is up, so the Mac does not
       drop it after 45 s.
    e) Replay counters: each new relay session starts with a random session id. Reset the
       "seen" counter for each new session on both sides. A restart on one side must not
       break the other.
    f) Use two keys made with HKDF: one for phone->Mac and one for Mac->phone, so a relay
       cannot bounce frames back.
    g) Inside the locked relay channel, the phone must first send its device token. The Mac
       checks it against the paired device list before it accepts anything else. A
       forgotten phone gets refused.
    h) Use one relay secret PER paired phone. Make a new one when a phone is forgotten.
       Store it in the Mac Keychain and in the phone's sealed preferences (not plain text).
    i) Automatic switching: Wi-Fi first. After 2 failed Wi-Fi tries, use the relay. When
       the network changes or the Mac is found on Wi-Fi again, go back to Wi-Fi and close
       the relay. Both apps show "On Wi-Fi" or "Away (relay)".
    j) After pairing on Wi-Fi, the Mac sends the relay address to the phone automatically
       (relay_set), so I paste the address only once, on the Mac. Move the relay setting on
       the phone from the Mirror screen to Settings.
    k) Keep relay frames at 512 KB or less (split bigger ones).
    l) Add a test: a fake relay in a unit test forwards frames; restart one side; messages
       still flow; a reflected frame is rejected; a forgotten phone is refused.

1.5 (C7) Screen mirror and webcam must start even when the phone app is not open.
    Files: PhoneSix.java, SixWork.kt, MainActivity.kt, MirrorService.java, CamService.java.
    - When mirror_start or cam_start arrives, show a high-priority notification:
      "Your Mac wants to see your screen. Tap to allow." Tapping it opens a small new
      ConsentActivity that shows Android's screen-share prompt right away (one tap).
    - Pass the Mac's quality, facing, zoom, flash, and mic choices through. Use them.
    - Remove the status-text matching in SixWork.onAsk and the unused static `want`.
    - On screen, explain in one line why Android asks every time (Android rule for privacy).

1.6 (C6) Real tests instead of fake ones. Do this now, because every later step needs it.
    - Android: add a loopback integration test (JVM unit test or androidTest) with a fake
      Mac that uses android/app/src/androidTest/assets/fake-mac.p12. It must drive the real
      LinkService code and send and receive EVERY message type in docs/PROTOCOL.md, checking
      the reply or effect.
    - Mac: add Swift tests that feed real phone JSON fixtures (docs/fixtures/*.json, one per
      message type) into LinkModel.handle and check the result. Use the same fixtures in the
      Android tests.
    - Change scripts/check-matrix.py: a feature is only "Built – self-tested" if a test
      named in its row actually contains that feature's message type.
    - Delete or rewrite StageThree/Four/Five/SixDeviceTest so they test behavior, not
      text constants.
    - Write docs/MANUAL_TESTS.md that defines M1–M25 (one line each).
    - Until a feature passes its real test, set it back to "Building" in README.md and
      docs/FEATURE_MATRIX.md. Be honest.

---------------------------------------------------------------------
STEP 2 – HIGH FIXES (crashes, freezes, memory)
---------------------------------------------------------------------
2.1 (H1) MirrorCast.java: stop the drain thread and wait for it (join with a timeout)
    BEFORE codec.stop()/release(). Wrap every codec call in try. No crash when the Mac
    closes the mirror.
2.2 (H2) Video and camera frames: use a small bounded queue (3 frames). When full, drop
    frames that are not key frames, and ask the encoder for a key frame. No more
    unlimited Outbox growth from LinkService.sendRaw.
2.3 (H3) FrameChannel.java: one long-lived watchdog for the whole channel, not a new
    thread per write.
2.4 (H4) MacFiles.swift: send each chunk and wait until NWConnection says it was sent
    (keep 4–8 chunks in flight). Use separate queues for sending and receiving. A 4 GB
    file must not fill the Mac's memory.
2.5 (H5) MacMedia.swift: run osascript on a background queue with a 2 second timeout.
    Never block the main thread. Poll the Mac's "now playing" only while the phone shows
    the media screen.
2.6 (H6) Mac: parse frames on the network queue. Decode video and camera on their own
    queue. Only update the screen on the main thread (LinkListen.swift, MacSix.swift,
    MacPicture.swift).
2.7 (H7) MacFive.swift: show the Accessibility alert once per session, not for every
    pointer move. Ignore pointer events until Accessibility is allowed, and show a calm
    banner with an "Open Settings" button.
2.8 (H8) App.swift setMirror: change only the notify and otp flags (StageRules.setFlag).
    Never overwrite the whole flags string.
2.9 (H9 + H10) Leave-lock:
    - The Mac must also lock by itself when the paired phone's link AND its Bluetooth
      signal are gone for the chosen delay. The Mac still NEVER unlocks.
    - Remove the `defaults write com.apple.screensaver` calls in MacLock (no silent system
      changes). Lock with the system lock screen call, and show a one-time card if
      "Require password immediately" is off.
    - Start the Mac Bluetooth beacon only when leave-lock is on (L8).
2.10 (H11) Mac Share menu: either build a real share extension (proper extension entry
    point, sandboxed, hand files to the app with a bridgelink-mac:// URL), or remove it and
    say clearly that Finder "Services > Send to phone", dragging to the menu-bar icon, and
    the drop zone are the ways. Pick the one that works for a free app and write why in
    DECISIONS.md.
2.11 (H12) Two phones on one Mac must not kick each other off. Support several live
    phones at once (one session per device), or refuse the second one with a clear message
    and no reconnect loop. Prefer several at once.
2.12 (H13) Several Macs on one phone: store host list, port, fingerprint, sealed token,
    deviceId, and name PER Mac. Add a "Switch Mac" list on the phone. Forget removes only
    that Mac.
2.13 (H14) Automations on the phone must really do things: copy the code to the
    clipboard, show a real notification for alert and note, ring the Mac for "ring". Hide
    "open app" on the phone (Mac only). Let the user type the home Wi-Fi name. Ask for
    location "while using the app" only when a Wi-Fi rule exists, because Android hides the
    Wi-Fi name without it. If the name is still unknown, don't fire.
2.14 (H15) Release workflow (.github/workflows/release.yml):
    - Run scripts/check-versions.py and fail if the tag is not v + versionName.
    - Only publish when the CI and emulator jobs passed on that commit.
    - Run `apksigner verify --print-certs` and fail if the certificate SHA-256 is not the
      known old one (store the fingerprint, NOT the key, in docs/SIGNING.md).
    - If MAC_SIGN_P12_BASE64 exists, set REQUIRE_MAC_SIGN=1 and fail if the app is ad-hoc.
    - Make SHA256SUMS.txt and appcast.xml and attach them.
    - Staple the DMG (not the .app) in the optional notarize step (L11).
2.15 (C6 follow-up) After Step 2, re-run every real integration test. Every one must pass.

---------------------------------------------------------------------
STEP 3 – MEDIUM FIXES (features that only half work)
---------------------------------------------------------------------
3.1 (M1, M2) Reconnect finds the Mac again after its IP changes: after 2 failed tries,
    browse _bridgelink._tcp for the pinned fingerprint and update the address. Never
    replace the saved address list with one address.
3.2 (M3) LinkListen.swift: keep waiting connections in a dictionary. A new one must not
    orphan the old one.
3.3 (M4) Find my Mac: the phone gets a "Stop Mac ringing" button that sends find_stop.
    The Mac plays a loud bundled sound for 30 s (not just a beep), and has a Stop button.
3.4 (M5, M7) Notifications on the Mac: use the phone's notification key as the banner id.
    Remove the banner when the phone sends notify_dismiss. Add a Reply box on the banner
    when the phone says reply is allowed. Keep a list so any recent notification can be
    replied to or dismissed. Ask for notification permission in the Mac first-run window.
3.5 (M6) NotifyMirror.java: do not send the same notification again when only its
    progress or time changed (dedupe by key + title + text).
3.6 (M8) Mac Focus -> phone Do Not Disturb: add NSFocusStatusUsageDescription to
    macos/Info.plist and ask for permission, or remove that direction and say so.
3.7 (M9) Phone clipboard: say on screen that Android only lets BridgeLink read the
    clipboard while it is open. Add a "Send clipboard" Quick Settings tile and a button
    on the BridgeLink notification.
3.8 (M10) Never sync or store password-manager copies: check ClipDescription
    EXTRA_IS_SENSITIVE on Android, and org.nspasteboard.ConcealedType / TransientType on
    the Mac, before the word check.
3.9 (M11–M15) Shared folder:
    - Watch for changes (FSEvents on the Mac; FileObserver + 60 s wait on the phone,
      only while linked).
    - One scan per index with a saved size/mtime/sha table. Re-hash only changed files.
      Never read whole files into memory on the Mac main thread.
    - Phone folder: let the user pick a normal folder (Android folder picker), default
      Documents/BridgeLink Shared, so it is visible in file managers.
    - Refuse any incoming path part that starts with "." (protects .seen and .incoming).
    - Raise the 32 MB limit to 4 GB using the same chunked, resumable streaming as file
      send. If something truly can't, write one line why in DECISIONS.md.
3.10 (M16, M17) Mirror picture: match the phone's real screen shape (scale the long edge
    to the quality), handle rotation, repeat SPS/PPS before every key frame. TouchGate:
    use the real full screen size (WindowMetrics) so taps land in the right place.
3.11 (M18) Webcam picture: rotate by the camera sensor angle. Mirror the front camera
    like a selfie.
3.12 (M19) Mac trackpad drag: press on start, move while dragging, release on end.
3.13 (M20) Phone can send a folder: add a folder picker that zips on the fly. Or remove
    the "A folder is sent as a zip" text on the phone. Prefer adding it.
3.14 (M21, M22) Share sheet: hand the files to the service with read permission
    (ClipData + FLAG_GRANT_READ_URI_PERMISSION). Big incoming files go straight into a
    pending Downloads item, not the cache folder.
3.15 (M23–M25) Relay hardening is done in 1.4. Double-check it here with tests.
3.16 (M26) DMG: mount without -nobrowse (or use a pre-made layout) so the background
    picture with the 3 first-open pictures, the arrow, and the Applications shortcut
    really show. Check the DMG window in the CI log (screenshot it as an artifact).
3.17 (M27) Mac keychain prompt after updates: fixed by stable signing (Step 7). Until
    then, the first-run window explains it in one line.
3.18 Scanner without Play services: the photo fallback must still make a PDF (use
    PdfDocument on the phone). Stage 7 item.

---------------------------------------------------------------------
STEP 4 – LOW FIXES AND CODE CLEANUP
---------------------------------------------------------------------
4.1 (L1) Split LinkService.java (1,495 lines) into small parts under ~400 lines each:
    LinkConnection (socket, TLS, reconnect), Pairing (hello, SAS, token), and one handler
    per feature (Files, Clipboard, Notifications, Find, Media, Battery, Sms,
    SharedFolder, Remote, Mirror, Webcam, Relay, Automations). No behavior change. All
    tests stay green.
4.2 Rename every stage-named file to a feature name, on BOTH apps:
    StageFour/StageFive/StageSix (Java and Swift), PhoneFive, PhoneSix, FiveWork, SixWork,
    EasyWork, MacFive, MacSix, EasyMac, MacStage, SixPages, FivePages, EasyPages, SixPage
    -> Media, Sms, SharedFolder, Remote, Mirror, Webcam, Relay, Automations, LeaveLock,
    Inbox, ClipHistory, and so on. One feature per folder or module on each side.
4.3 (L2) One golden JSON fixture per message type in docs/fixtures/, used by both test
    suites.
4.4 (L3) Media "seek" seeks to the given position, or remove it.
4.5 (L4) The same OTP is copied once, not twice.
4.6 (L5) Put the alarm-volume change in startRing inside try.
4.7 (L6) Rate-limit status and progress notifications to once per second. No message
    per dropped video frame.
4.8 (L7) touch_gate.xml: ask only for the events really used.
4.9 (L9) Phone: links and files from the Mac show a notification with "Open" instead
    of opening by themselves (keep the "always open links" setting).
4.10 (L10) sign.sh: never change the default keychain outside CI.
4.11 (L12) Rename scripts/generate-android-keystore.sh to
    scripts/DO-NOT-RUN-new-android-key.sh with a big warning at the top, or delete it.
4.12 (L15) Update the old libraries (Compose BOM, Room, activity-compose, core,
    document scanner stable). Fix the lint warnings. Keep lint at 0 errors.

---------------------------------------------------------------------
STEP 5 – DESIGN POLISH (both apps look plain)
---------------------------------------------------------------------
5.1 Mac: real buttons with SF Symbol icons and an accent color instead of small grey
    pills. Bigger cards, more space, one clear main action per card. The Mac card on a
    desktop says "Desktop Mac" (no battery). A clear green or grey dot for connected or
    not, with a Reconnect button. Empty states with one helpful line.
5.2 Android: icons on quick actions. The status card shows the Mac name and a clear
    "Connect" button. No duplicate text ("Not connected / Not connected"). Bottom bar:
    5 tabs + "More" (no sideways scrolling that hides Remote, Mirror, and Settings) (L13).
5.3 Same accent color and same app icon on both apps. Light and dark both checked.
5.4 Retake every screenshot in docs/screenshots. Screenshots made from staged sample
    data must say "Sample data" in the README caption.

---------------------------------------------------------------------
STEP 6 – MISSING PIECES FROM THE MASTER PROMPT (H16)
---------------------------------------------------------------------
6.1 Sparkle 2 in-app updates for the Mac (free, uses its own EdDSA key). The app checks
    GitHub Releases once a day and asks "Update now?". Publish appcast.xml with every
    release. The EdDSA private key is a GitHub secret (SPARKLE_ED_PRIVATE_KEY) created by
    the owner's one-time script (Step 7). If that secret is missing, the release still
    works and says "Updates: check the website" (no crash). Never print the key.
6.2 Mac first-run window (a real window, not the menu): Welcome -> Allow notifications
    -> Allow local network -> Launch at login on/off -> QR to pair the phone. Then a
    permission card for each feature that needs one (Accessibility, Camera, Microphone,
    Bluetooth, Automation for Music/Spotify). Each card has one button that opens the exact
    System Settings page and turns green when allowed. It can be opened again from
    Settings.
6.3 Phone first-run setup: each permission one by one with one plain reason
    (notifications, nearby devices/Bluetooth, SMS, notification access, accessibility,
    camera, microphone). Then battery optimization off, then OnePlus / Xiaomi / Samsung
    autostart steps, then "Allow restricted settings" steps on Android 13+. It can be
    opened again from Settings.
6.4 Android "Check for update" in Settings: reads GitHub Releases
    (CAPPY7/bridgelink), downloads BridgeLink-android.apk, checks its SHA-256 against
    SHA256SUMS.txt, then opens the installer. Add REQUEST_INSTALL_PACKAGES and explain the
    "Install unknown apps" switch in one line.
6.5 Mac Settings -> "Reset BridgeLink": removes its Keychain items, login item, saved
    data, paired devices, and relay secrets, after a confirm dialog.

---------------------------------------------------------------------
STEP 7 – THINGS ONLY THE OWNER CAN DO (prepare them, don't wait for me)
---------------------------------------------------------------------
7.1 scripts/mac-signing-setup.sh – I run it ONCE on my Mac with one command. It must:
    - Check for Homebrew `gh`. If missing, say how to install it in one line. If
      `gh auth status` fails, run `gh auth login` for me.
    - Create a code-signing certificate named "BridgeLink Self-Signed" (10 years) in my
      login keychain.
    - Export a backup to ~/Documents/BridgeLink-signing-backup.p12 with a random
      password. Save the password ONLY in the macOS Keychain (item
      "BridgeLink signing backup"). Never print it.
    - Set the GitHub secrets MAC_SIGN_P12_BASE64 and MAC_SIGN_P12_PASSWORD with
      `gh secret set --repo CAPPY7/bridgelink` (read from a pipe, never echo).
    - Create the Sparkle EdDSA key pair, set SPARKLE_ED_PRIVATE_KEY the same way, and put
      the PUBLIC key into the repo (macos/Info.plist SUPublicEDKey) by opening a small PR
      or printing ONLY the public key.
    - Print only: "Done. Secrets set: MAC_SIGN_P12_BASE64, MAC_SIGN_P12_PASSWORD,
      SPARKLE_ED_PRIVATE_KEY. Backup: ~/Documents/BridgeLink-signing-backup.p12".
    - Running it twice must not break anything (it asks before replacing).
    The one command I copy:
    curl -fsSL https://raw.githubusercontent.com/CAPPY7/bridgelink/main/scripts/mac-signing-setup.sh | bash
    (Also allow: download the file, then `bash mac-signing-setup.sh`.)
7.2 scripts/relay-setup.sh – I run it once, on Mac or any computer with Node:
    - Steps in plain words: make a free Cloudflare account, then the script runs
      `npx wrangler login`, then `npx wrangler deploy` from the relay folder.
    - At the end, print ONLY the wss:// address. Tell me: "Paste this into BridgeLink on
      the Mac: Settings -> Away from home." The Mac sends it to the phone by itself.
7.3 Write both in docs/SETUP_FOR_OWNER.md in numbered simple steps (max 8 steps each).
7.4 After I add the secrets later, the next release must use them automatically (CI
    rule from 2.14). No code change needed then.

---------------------------------------------------------------------
STEP 8 – DOCS: MAKE EVERY DOC TRUE AND SIMPLE
---------------------------------------------------------------------
Re-read and fix: README.md, docs/INSTALL.md, USER_GUIDE.md, PERMISSIONS.md,
TROUBLESHOOTING.md, SETUP_FOR_OWNER.md, SERVICES.md, PROTOCOL.md, TESTING.md,
ARCHITECTURE.md, DECISIONS.md, CHANGELOG.md, FEATURE_MATRIX.md, BUGS.md, PROGRESS.md.
Known wrong today:
- SERVICES.md says the relay is not built.
- SETUP_FOR_OWNER.md, INSTALL.md, and TROUBLESHOOTING.md talk about a
  "BridgeLink-android-debug.apk". The release now fails instead.
- SETUP_FOR_OWNER.md relay steps fail on the free plan.
- PROTOCOL.md is stale ("Types in 0.4.0", "no in-app camera scanner") and is missing
  notify_reply, notify_dismiss, dnd, media, media_now, lock, lock_setup, forget, folder,
  find_mac, clipboard_image, and the label, deviceId, and art keys. Document EVERY message
  type and key, plus the relay session/auth.
- TESTING.md mentions a manual list "in the release notes" that does not exist.
- README claims that are not true yet (find stop from both sides, several phones and
  Macs, dismiss both ways, folder from phone). Make them true or change the words.
- PERMISSIONS.md: list every Android and Mac permission and why, matching the
  manifest and Info.plist exactly.

---------------------------------------------------------------------
STEP 9 – FINAL CHECK AND RELEASE v1.0.0
---------------------------------------------------------------------
9.1 Go through all 25 features on both apps one last time in code and with the real
    integration tests. Fix anything rough.
9.2 Battery test on the emulator: run the phone service linked and idle for 30 minutes
    with `dumpsys batterystats`. Report the wakeups and the estimated drain per hour in
    PROGRESS.md. No polling faster than needed. Mirror and webcam only run while their
    window is open.
9.3 Set versionName 1.0.0, versionCode 13, and Mac CFBundleShortVersionString 1.0.0 /
    CFBundleVersion 13 in all places (scripts/check-versions.py must pass).
9.4 Tag v1.0.0. The release must contain: BridgeLink.dmg, BridgeLink-mac.zip,
    BridgeLink-android.apk, appcast.xml, SHA256SUMS.txt.
9.5 Download the release files yourself and check:
    - The APK is signed with the OLD key (the certificate SHA-256 matches docs/SIGNING.md),
      so it updates over the installed app.
    - The DMG opens and shows the background picture with the first-open steps.
    - The Mac app is universal (lipo -info shows arm64 and x86_64).
    - If the Mac signing secrets exist: the signature is the stable one. If not: say
      "ad-hoc until the owner runs the signing script".
9.6 Write docs/FINAL_TEST.md: ONE checklist covering all 25 features, grouped by feature,
    each line "Do X -> you should see Y", each with a [ ] box to tick. Plus install
    checks and the update check.

---------------------------------------------------------------------
STEP 10 – THE ONE FINAL MESSAGE TO ME
---------------------------------------------------------------------
Only after Step 9. Send me:
1. Download links (DMG, APK).
2. Mac install steps, numbered, max 8. Include the macOS 15 Sequoia steps: double-click,
   press Done, open System Settings -> Privacy & Security, scroll down, press
   "Open Anyway", enter password, press Open. Also macOS 13–14: right-click -> Open ->
   Open. Backup: xattr -dr com.apple.quarantine /Applications/BridgeLink.app
3. Phone install steps, numbered, max 8 (allow "Install unknown apps", restricted
   settings on Android 13+, battery optimization off).
4. The commands I must run once, each in its own copy box:
   - Mac signing + Sparkle key: the one command from 7.1.
   - Relay: the one command from 7.2, then where to paste the wss:// address.
5. The link to docs/FINAL_TEST.md (all 25 features, tick boxes).
6. A list of anything that is not 100% and why, one line each (for example: "Webcam
   shows in a BridgeLink window only. Zoom can't see it without a paid Apple account.").
Then wait. I test everything once and send you what failed. Then you fix those, release
again, and send a short checklist for only the fixed items.

Your first reply, again: "Starting the fix-all pass." Then start with Step 1.1.
=====================================================================
