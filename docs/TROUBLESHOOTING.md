# Troubleshooting

## The phone cannot find the Mac

- Both devices must be on the same Wi-Fi, not a guest network.
- Turn off any VPN on both sides and try again.
- The Mac firewall must allow incoming connections for BridgeLink. System Settings, Network, Firewall.
- If discovery fails, type the address shown under the QR. Prefer an address that starts with 192.168 or 10.
- The PIN lasts 2 minutes. Tap New PIN and paste the new link.

## It paired, then the phone went quiet

- Android killed the service. Tap Keep me connected. Then, in the phone's settings, allow autostart and unrestricted battery for BridgeLink.
- Xiaomi and Poco: Security app, Autostart, and Battery saver, No restrictions.
- Oppo, Realme, and OnePlus: App battery management, allow background.
- Vivo: Background power consumption, high.
- Samsung: Sleeping apps, remove BridgeLink.
- Huawei: App launch, manage manually, allow all three toggles.
- After a reboot the phone reconnects only if a token was saved.

## "This Mac key changed"

The Mac certificate is not the one the phone pinned. This happens if the Mac identity was deleted from the keychain, or you replaced the Mac. Tap Forget this Mac, then pair again with the new QR. Do that only if you expect the change.

## The APK will not install over the old one

The file is `BridgeLink-android-debug.apk`, or it was signed with a different key. Uninstall the old app, then install. A release signed with the saved keystore updates in place. See `docs/SETUP_FOR_OWNER.md`.

## The Mac app will not open

Right-click, Open, Open. If macOS still refuses, run `xattr -dr com.apple.quarantine` on the app in Terminal, then open it again. The app is not notarized.

## Notifications do not appear on the Mac

Click Notifications in the BridgeLink window. If you previously denied them, the button opens System Settings.

## Wrong PIN five times

Pairing locks for 60 seconds. Wait, then use the PIN that is on screen. New PIN starts a new code and the phone must pair again.
