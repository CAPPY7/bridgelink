See docs/FIX_ALL_PROMPT.md for the v1.0.0 fix-all pass.

=====================================================================
BRIDGELINK – MASTER BUILD PROMPT (v2)
Paste ALL of this to the bot that builds BridgeLink.
=====================================================================

You build BridgeLink: an Android phone app + a Mac menu-bar app that work together
(like Phone Link / KDE Connect / LocalSend combined).
Repo: CAPPY7/bridgelink (currently v0.7.0, tag on b6d3588).
Package names stay: app.bridgelink.android and app.bridgelink.mac.
The owner (me) has zero coding knowledge. Use very simple English, short messages, no jargon.

---------------------------------------------------------------------
PART 0 – THIS PROMPT REPLACES ALL OLD RULES
---------------------------------------------------------------------
This prompt replaces every older rule in docs/BUILD_PROMPT.md, docs/DECISIONS.md,
docs/PROGRESS.md, README.md and scripts/check-matrix.py wherever they disagree.
First thing you do: overwrite docs/BUILD_PROMPT.md with this prompt (no secrets in it),
and update the other docs so no old rule fights this one.

Old rules that are now CANCELLED:
- "A feature is Done only after the owner replies 'worked'." CANCELLED.
- "Wait for the owner after each stage." CANCELLED.
- "Ask the owner about Apple Developer ID." CANCELLED (answer below).
- "Do not build a feature until the link is perfect." CANCELLED.
- The deleted helpers (clipboard history, leave-lock, rate-limit, replay) come back when their features are built.

---------------------------------------------------------------------
PART 1 – MY DECISIONS (never ask me these again)
---------------------------------------------------------------------
1. Build ALL 25 features (list in Part 6). Nothing is skipped unless it is truly impossible
   for a free sideloaded app. Then say so in one line and build the closest free version.
2. I will NOT test after every step. Work non-stop through all stages.
   I test everything ONCE at the very end, with one checklist you give me.
3. Mac app is SIDELOADED and FREE. No paid Apple Developer account, no Mac App Store.
4. Android app is SIDELOADED (APK from GitHub Releases). No Play Store.
5. Keep the current link, TLS, pairing protocol, port 8787 and Bonjour name _bridgelink._tcp.
   Improve them, don't throw them away. New message types are fine.
6. Do NOT create a new Android signing key. The existing release keystore and the 4 GitHub
   secrets stay. Never print, log or commit any secret, password or key.
7. The web page in /src is only a fake demo. Leave it alone except to keep CI green.
   Never count it as a feature.
8. Never attempt: iMessage, FaceTime, AirDrop, Handoff, unlocking the Mac with the phone,
   phone-call audio, running Android apps in Mac windows, silent system-setting changes.
9. No paid services. Anything that needs a server must use a free option.

---------------------------------------------------------------------
PART 2 – HOW YOU WORK
---------------------------------------------------------------------
- Go stage by stage, step by step, in the order in Part 4. Don't jump ahead.
- After EVERY step: unit tests pass, CI green (all jobs), commit, push.
- After EVERY stage: bump the version, tag it, and check that the GitHub Release has
  BridgeLink-android.apk (release-signed, NOT the debug one) + BridgeLink.dmg.
- Keep docs/PROGRESS.md updated after every step: what's finished, what's next, open problems.
  If you stop or run out of memory, you must be able to continue from PROGRESS.md alone.
- Statuses in README and FEATURE_MATRIX: "Not started", "Building", "Built – NOT TESTED on a device",
  "Final – owner tested". Only I can make "Final". Change check-matrix.py to these statuses.
- "Built – NOT TESTED on a device" means: real code in both apps, unit tests, an integration or emulator
  test where possible, CI green, and you ran it yourself. Never say built if it's half done.
- Only stop and message me if you are truly blocked (for example a GitHub secret is missing).
  Then send ONE line: what you need and why. Otherwise keep working.
- Status message to me only at the end of each stage, max 5 lines:
  stage name, what's new, release link, what's next. No test lists until the end.
- Code quality: no file over ~400 lines. Split App.swift (993 lines) first. One feature per
  folder/module on both sides. Comments in plain English.
- Battery: the phone service must stay light. No polling loops faster than needed.
  Heavy features (mirror, webcam) run only while the user has them open.

---------------------------------------------------------------------
PART 3 – THE MAC APP MUST INSTALL LIKE CHROME / FIREFOX / LOCALSEND
---------------------------------------------------------------------
Be honest about this first: Chrome, Firefox and LocalSend open with NO warning because
they pay Apple ($99/year) for a Developer ID and "notarization". A free sideloaded app cannot
skip Apple's first-open check. So the goal is: install EXACTLY like those apps
(download a DMG, drag to Applications), plus ONE simple first-open step, done once.

Build all of this:
a) Ship a proper BridgeLink.dmg (not a zip):
   - Opens a window with the BridgeLink icon, an arrow, and an Applications shortcut.
   - Nice background picture that also shows the first-open steps in 3 pictures.
   - Universal app (Apple Silicon + Intel). Minimum macOS 13. Build on the macos-15 runner.
   - Keep BridgeLink-mac.zip too as a backup download.
b) Real app icon (.icns, all sizes) that matches the Android icon.
c) Correct first-open steps for EVERY macOS version, in the DMG picture, README and INSTALL.md:
   - macOS 13–14: right-click BridgeLink in Applications, choose Open, then Open.
   - macOS 15 Sequoia and newer: double-click, press Done, open System Settings,
     Privacy & Security, scroll down, press "Open Anyway", enter password, press Open.
   - Backup for both: the Terminal command  xattr -dr com.apple.quarantine /Applications/BridgeLink.app
   The current docs only say "right-click, Open". That is WRONG on macOS 15. Fix it.
d) Stable signature so macOS does NOT forget permissions after each update:
   Ad-hoc signing makes a new identity every build, so Accessibility, Screen Recording,
   Bluetooth, Camera etc. reset after each update. Fix this: make ONE self-signed code-signing
   certificate ("BridgeLink Self-Signed"), store it as GitHub secrets
   (MAC_SIGN_P12_BASE64, MAC_SIGN_P12_PASSWORD) with gh secret set, import it into a temporary
   keychain in CI, and sign every build with it. Never print it. Tell me where the backup .p12
   is and which secrets you made (names only).
e) Hardened runtime + the right entitlements for every feature (camera, mic, network,
   Bluetooth, Apple events where needed). Every Info.plist usage text in plain English.
f) Optional notarization: write the CI step for Developer ID signing + notarization, but skip
   it automatically when the APPLE_* secrets don't exist. Then one day I can add a paid account
   and it will install with zero warnings, without code changes.
g) In-app updates with Sparkle 2 (works without a paid account, uses its own EdDSA key stored
   as a GitHub secret). The app checks GitHub Releases once a day and asks "Update now?".
   Publish the appcast.xml with each release.
h) Uninstall: dragging the app to Trash must be enough. Add "Reset BridgeLink" in Settings that
   removes its keychain items, login item and saved data.
i) First-run setup on the Mac (a window, not the menu bar):
   Welcome -> Allow notifications -> Allow local network -> Launch at login on/off ->
   Show QR to pair the phone. Then for each later feature that needs a permission
   (Accessibility, Screen Recording, Camera, Bluetooth), show a small card with one button
   that opens the exact System Settings page, and detect when it's granted.
j) Android install: APK release-signed with the existing key, so updates install over the
   old app. Add an in-app "Check for update" that reads GitHub Releases and downloads the
   new APK. First-run setup on the phone: permissions one by one with plain reasons,
   battery-optimization off, OnePlus/Xiaomi/Samsung autostart steps, restricted-settings steps
   on Android 13+.

---------------------------------------------------------------------
PART 4 – STAGES (do them in this exact order)
---------------------------------------------------------------------

STAGE 1 – FIX ALL BUGS FIRST (version 0.8.0)
 Step 1.1  Full audit: read every file in android/ and macos/. Write docs/BUGS.md listing
           every bug, crash risk, broken flow, wrong doc and missing error message,
           each with a short plain-English line. Fix them one by one, one commit each.
 Step 1.2  These are known problems. Fix all of them:
   - Mac first-open docs are wrong for macOS 15 (see Part 3c).
   - Permissions reset after every Mac update (see Part 3d).
   - Mac ships as a zip, no DMG, no icon (Part 3a, 3b).
   - App.swift is one 993-line file. Split it (model, network, pairing, each screen).
   - The QR uses only the FIRST Mac address, which can be the wrong one. Put all LAN
     addresses in the pairing data and let the phone try each one.
   - Pairing is clumsy (scan with camera app, copy, paste). Make in-app QR scan the main
     way, with a one-tap flow. Keep the old ways as backup.
   - History is only in memory and lost on restart. Save it on disk on both sides
     (Room on Android, a small SQLite/JSON store on Mac).
   - Battery is sent only once at pairing. Send it on change, both ways.
   - The phone service gets killed on OnePlus/Xiaomi/Samsung. Make reconnect reliable
     (foreground service, network callback, boot, and a gentle retry with backoff).
   - Release workflow can silently ship a debug APK. Make it FAIL if signing secrets are missing.
   - Mac firewall: if the listener can't accept, show a clear message with steps.
   - Every error must show a plain-English message and a "what to do" line on screen.
 Step 1.3  Add a "Send diagnostics" button on both apps that saves a log file
           (no secrets, no message text) I can send you if something breaks.
 Step 1.4  CI: Android unit + lint + emulator test, Mac unit tests + build + DMG, all green.

STAGE 2 – NEW DESIGN FOR BOTH APPS (version 0.9.0)
 Design rules for both: clean, modern, light + dark mode follow the system, one accent color,
 rounded cards, clear icons, big readable text, empty states with a helpful line,
 smooth small animations, nothing cramped.
 Android: rewrite the UI in Kotlin + Jetpack Compose + Material 3 (dynamic color on Android 12+).
   Keep LinkService and the protocol (convert to Kotlin step by step only if safe).
   Screens: Home (Mac name, connection dot, battery of both, quick actions), Send
   (files, text, link, clipboard), Notifications, Messages (SMS), Files, Remote
   (trackpad/keyboard/slides/media), Mirror, Settings. Bottom navigation. Adaptive icon.
 Mac: SwiftUI. Two parts:
   (1) Menu-bar popover: phone name, battery, connection dot, quick buttons (Send file,
       Send clipboard, Ring phone, Mirror screen), last 5 activity items, "Open BridgeLink".
   (2) Main window with a sidebar: Home, Notifications, Messages, Files, Clipboard,
       Shared Folder, Mirror, Webcam, Automations, Devices, Settings.
   Drag files anywhere on the window or the menu-bar icon to send. Command-Shift-D opens
   the popover. Native look (SF Symbols, vibrancy), works in light and dark.
 Take screenshots of every screen on both apps (emulator + macOS runner) and save them in
 docs/screenshots/. Use them in README.

STAGE 3 – FINISH EVERY HALF-DONE FEATURE (version 0.10.0)
 1  Files/links/text: send many files at once, folders as zip, progress bar, cancel, resume,
    raise the 100 MB cap to 4 GB with streaming, open received file/folder.
 2  Share sheet + drag-drop: Android share sheet sends many files; Mac Share menu extension
    ("Share > BridgeLink") + Finder right-click "Send to phone"; drag-drop many files.
 3  Notification mirroring: Mac banners with app icon, reply (where the app supports it),
    dismiss syncs both ways, per-app on/off list, do-not-disturb sync option.
 5  Battery: both ways, low-battery and fully-charged alerts on the other device.
 6  Find phone / Find Mac: works even when the phone is on silent; stop from either side.
 7  OTP auto-copy: code from notifications AND SMS copied to the Mac clipboard, clears after 60 s.
 8  Open link on the other device: both ways, with a "always allow" option.
 10 Clipboard sync: automatic both ways (on/off), skip passwords/sensitive text, images too.
 22 Quick drop: tile, menu-bar drop, and a floating drop zone on the Mac.
 23 Pairing: one-tap QR, several phones and several Macs, rename and forget devices.
 24 Encryption: keep TLS; every new feature (incl. remote-link in Stage 6) must stay end-to-end encrypted.
 25 Key pin: keep; clear "this Mac changed" screen with a safe re-pair button.

STAGE 4 – EASY NEW FEATURES (version 0.11.0)
 4  Media control: control phone music from the Mac (play/pause/next/prev/volume, song title
    and art) using MediaSession via notification access. Also control Mac media from the phone.
 16 Lock the Mac when you leave: phone signal (Bluetooth LE RSSI) drops below a threshold for
    X seconds -> Mac locks the screen (use a supported method, e.g. start the screen saver with
    "require password immediately" or the system lock shortcut). On/off, distance slider,
    delay, and a "don't lock while on charger/at desk" option. Never unlock.
 18 Unified inbox: one list on the Mac with notifications, SMS and received files, search and filters.
 19 Clipboard history: last 100 items on both sides, pin, search, delete, never store sensitive text.
 20 Scan to Mac: phone document scanner (ML Kit document scanner) -> PDF or images -> saved
    on the Mac and opened.

STAGE 5 – MEDIUM NEW FEATURES (version 0.12.0)
 11 SMS: read conversations, send and reply from the Mac, new-SMS banners, dual-SIM choice.
    Needs READ_SMS/SEND_SMS/RECEIVE_SMS (sideload is fine). Show restricted-settings steps.
 15 Shared folder: one folder on each side that syncs both ways (new, changed, deleted files),
    conflict copies instead of overwriting, pause button.
 21 Automations: simple "When X then Y" rules (max ~8 types), e.g. phone connects -> open app,
    phone battery < 20% -> alert, arrive home Wi-Fi -> start sync, SMS with code -> copy.
 9  Trackpad, keyboard, slides: phone as touchpad (move, tap, two-finger scroll, right-click),
    keyboard (types into the Mac), slide remote (next/prev, laser pointer), using
    CGEvent on the Mac (needs Accessibility permission; first-run card explains it).

STAGE 6 – HARD NEW FEATURES (version 0.13.0)
 12 Screen mirror: phone screen on the Mac in a window. Android MediaProjection + hardware
    H.264 encoder -> TLS link -> Mac VideoToolbox decoder. Target 30–60 fps, low lag,
    quality setting, auto-stop when the window closes.
 13 Control the phone while mirroring: click/drag/scroll/type in the mirror window controls
    the phone through an Android AccessibilityService (dispatchGesture). Back/Home/Recents buttons.
    Show the steps to turn on the accessibility service (incl. restricted settings).
 14 Phone as webcam: phone camera stream to the Mac.
    Honest rule: a system-wide Mac virtual camera (shows up in Zoom/Meet/FaceTime) needs a
    Camera Extension, which needs a paid Developer ID. So:
      - Free version (build this): phone camera shown live in a BridgeLink window
        (front/back, zoom, flash, mic option), which can be shared in calls via screen share.
      - Write the Camera Extension code too, but enable it only when the APPLE_* secrets exist.
    Say this in README in one line.
 17 The removed remote path: works when phone and Mac are on different networks.
    Use a FREE remote-link: a small Cloudflare Worker with Durable Objects (free tier) that only
    forwards already-encrypted frames, OR peer-to-peer WebRTC with a free STUN server and the
    remote-link as fallback. The remote-link must never see plain data. Deploy steps in SETUP_FOR_OWNER.md.
    If you need me to create a free account, ask ONCE with exact simple steps.

STAGE 7 – POLISH + FINAL CHECK (version 1.0.0)
 - Go through all 25 features on both apps and fix anything rough.
 - Battery test on the emulator: idle service drain must be very low; report the numbers.
 - Re-read every doc: README, INSTALL, USER_GUIDE, PERMISSIONS, TROUBLESHOOTING,
   SETUP_FOR_OWNER, CHANGELOG. Plain English, correct for macOS 13/14/15 and Android 8–15.
 - Release v1.0.0 with BridgeLink.dmg, BridgeLink-mac.zip, BridgeLink-android.apk, appcast.xml.
 - Download the release files yourself from GitHub and check they're the right signed files
   (APK signature matches the old key, DMG opens, app is universal, signature is the stable one).

---------------------------------------------------------------------
PART 5 – THE ONE FINAL MESSAGE TO ME (only after Stage 7)
---------------------------------------------------------------------
Send me:
1. Download links (DMG, APK).
2. Install steps for Mac and phone, numbered, max 8 steps each, with the macOS 15
   "Open Anyway" steps.
3. ONE test checklist covering all 25 features, grouped by feature, each line like
   "Do X -> you should see Y". Put it in docs/FINAL_TEST.md too, with a box to tick.
4. A list of anything that is not 100% and why (one line each).
Then wait. I'll test everything once and send you back the list of what failed.
After that you fix those, release again, and send a shorter checklist for only the fixed items.

---------------------------------------------------------------------
PART 6 – ALL 25 FEATURES (checklist – every one must be built)
---------------------------------------------------------------------
 1 Send files, links, text          14 Phone as webcam
 2 Share sheet + drag-drop          15 Shared folder
 3 Notification mirroring           16 Lock the Mac when you leave
 4 Media control                    17 The removed remote path (remote-link)
 5 Battery status                   18 Unified inbox
 6 Find phone / Find Mac            19 Clipboard history
 7 OTP auto-copy                    20 Scan to Mac
 8 Open a link on other device      21 Automations
 9 Trackpad, keyboard, slides       22 Quick drop
10 Clipboard sync                   23 Pairing
11 SMS                              24 End-to-end encryption
12 Screen mirror                    25 Key pin
13 Control phone while mirroring

---------------------------------------------------------------------
YOUR FIRST REPLY
---------------------------------------------------------------------
Only this: "Got the new rules. Starting Stage 1: fixing all bugs. I'll message you at the end
of each stage." Then start right away.
=====================================================================
