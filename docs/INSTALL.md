# Install the apps

v0.2.0 is the first installable pair. It is not the full 25-feature desk. The phone app and the Mac menu-bar app can pair on the same Wi-Fi, send notes, sync the clipboard, and ring the phone.

They are not Play Store or Mac App Store builds. The APK is debug-signed. The Mac app is ad-hoc signed, with no Apple Developer account.

## Android

1. On the phone, download `BridgeLink-android.apk` from the GitHub release.
2. Allow install from this source if Android asks.
3. Open BridgeLink and allow notifications.

## Mac

1. Download `BridgeLink-mac.zip` and unzip it.
2. Move `BridgeLink.app` to Applications.
3. First launch: right-click the app, choose Open, then Open again. macOS blocks unsigned apps on the first double-click.
4. A link icon appears in the menu bar. Click it. It shows the Mac's address and a 6-digit PIN, and listens on port 8787.

## Pair

1. Phone and Mac join the same Wi-Fi.
2. On the phone, enter the address and PIN shown on the Mac, then Connect.
3. The Mac menu should say it is paired. Send a note either way.
4. Send clipboard copies the other device's clipboard when that app is allowed to read it. On Android 10 and later the phone only reads the clipboard while BridgeLink is open.
5. Ring phone uses the alarm stream even if the ringer is silent. Stop it from the phone notification or the Stop ringing button.

If the Mac shows more than one address, try the 192.168 or 10. address. A wrong PIN is rejected and the Mac stays unpaired.
