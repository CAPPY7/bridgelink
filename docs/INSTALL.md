# Install the apps

1.1.0 is the latest build: the desk, plus call alerts, transfer history, phone photos, a sharper mirror, a menu-bar status panel, pause phone notifications, text from a photo, and Finder Send to phone. The away path, the combined list, and when-this-then-that rules are gone. These are not Play Store or Mac App Store builds. The Mac app is sideloaded. No paid Apple account is required. The Android APK is signed with the project key when the GitHub secrets are set. The release workflow refuses to publish a debug APK. Nothing in 1.1.0 was tried on a real phone or Mac.

## Android

1. On the phone, open the latest GitHub release and download `BridgeLink-android.apk`.
2. Allow install from that source if Android asks.
3. Open BridgeLink. Allow notifications.
4. Tap **Keep me connected** and turn off battery optimization. On Xiaomi, Oppo, Vivo, Samsung, OnePlus, and Huawei, also allow autostart. See Troubleshooting.
5. Tap **I'm ready**.

Android 8 through 15 can install the APK. Android 13 and newer may also need Allow restricted settings before texts, notification access, or screen control. Settings explains that.

## Mac

1. Download `BridgeLink.dmg`. `BridgeLink-mac.zip` is the backup if the disk image will not open.
2. Open the disk image. Drag BridgeLink onto Applications.
3. Eject the disk image.
4. First open, macOS 13 or 14: in Applications, right-click BridgeLink, choose Open, then Open again.
5. First open, macOS 15 or newer: double-click BridgeLink, press Done on the warning, open System Settings, Privacy and Security, scroll down, press Open Anyway, enter your password, then press Open.
6. If macOS still refuses, open Terminal and run `xattr -dr com.apple.quarantine /Applications/BridgeLink.app`, then open the app again.
7. The setup window appears. Allow notifications if you want them. Launch at login is optional. Then the menu-bar icon is ready.

macOS 13 is the oldest version. macOS 14 uses the same first-open step as macOS 13. macOS 15 uses Open Anyway.

## Pair

1. Phone and Mac join the same Wi-Fi. Turn a VPN off for the first pair.
2. On the phone, tap **Scan the Mac QR**. That is the main way. Paste and Find on Wi-Fi stay as backups.
3. The QR lists every local Mac address. The phone tries each one.
4. If you did not scan, both screens show a match code. Confirm it on both. A wrong code drops the attempt.
5. After this, the phone reconnects by itself. You do not enter a code again unless you forget the device or the Mac key changed.
