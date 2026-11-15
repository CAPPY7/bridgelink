# Install the apps

0.3.0 is the first pair that uses TLS, a saved pairing, and a real QR link. It is still not the full 25-feature desk. Read the status table in the README before expecting a feature.

These are not Play Store or Mac App Store builds. The Mac app is sideloaded and ad-hoc signed. No Apple Developer account is required. The Android APK is signed with the project key when the GitHub secrets are set. If the release file is named `BridgeLink-android-debug.apk`, it will not update an older install. Uninstall first, then install it.

## Android

1. On the phone, open the latest GitHub release and download the APK.
2. Allow install from that source if Android asks.
3. Open BridgeLink and allow notifications.
4. Tap **Keep me connected** and turn off battery optimization for BridgeLink. On Xiaomi, Oppo, Vivo, Samsung, and Huawei you also need the vendor "autostart" setting. See `docs/TROUBLESHOOTING.md`.

## Mac

1. Download `BridgeLink.dmg` from the latest GitHub release. `BridgeLink-mac.zip` is the backup if the disk image will not open.
2. Open the disk image. Drag BridgeLink onto the Applications folder.
3. Eject the disk image.
4. First open, macOS 13 or 14: in Applications, right-click BridgeLink, choose Open, then Open again.
5. First open, macOS 15 or newer: double-click BridgeLink, press Done on the warning, open System Settings, Privacy & Security, scroll down, press Open Anyway, enter your password, then press Open.
6. If macOS still refuses, open Terminal and run `xattr -dr com.apple.quarantine /Applications/BridgeLink.app`, then open the app again.
7. A link icon appears in the menu bar. Click it. Quit is in that window. Launch at login is the toggle in the same window.

## Pair

1. Phone and Mac join the same Wi-Fi. VPN clients often hide local addresses. Turn the VPN off for the first pair.
2. On the Mac, read the QR with the phone's normal camera app and copy the link. Paste it into **pairing link** on the phone and tap **Use pairing link**. That connects. The PIN expires in 2 minutes. Tap **New PIN** on the Mac if it does.
3. Or tap **Find Mac on Wi-Fi**, type the 6-digit PIN, and tap **Connect**.
4. After this, the phone reconnects with the saved token. You do not enter the PIN again unless you tap **Forget** or **New PIN**.
5. Send a note either way. Send clipboard, or ring the phone. Ring uses the alarm volume at maximum for up to 2 minutes.

If the Mac shows more than one address, the QR uses the first one. If connect fails, paste the other address into the host field.
