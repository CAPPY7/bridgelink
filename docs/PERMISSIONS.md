# Permissions

## Android

| Permission | Why | When |
|---|---|---|
| Notifications | Connection status, notes, the ring notification | First launch, Android 13 and later |
| Internet, network state, Wi-Fi state | Connect to the Mac | Always, no prompt |
| Change network state, change Wi-Fi multicast state | Required for the connected-device foreground service on Android 14, and for finding the Mac | Install time |
| Foreground service, connected device | Keep the link alive while the app is closed | While the service runs |
| Receive boot completed | Reconnect after a reboot if a token is saved | Install time |
| Vibrate, wake lock | Ring the phone if no alarm sound is available | During a ring |
| Ignore battery optimizations | Stop the vendor from killing the service | When you tap Keep me connected |
| None for ordinary files | The file picker and the share sheet hand over a file. The shared folder uses the app's own folder | When you send a file or turn the shared folder on |
| Receive, read, and send SMS | Show texts on the Mac, send a reply, and copy a code from a text | When you turn texts on |
| Phone state | Tell two SIMs apart. If you say no, texts use the default SIM | When you turn texts on |
| Nearby Bluetooth, location | Leave-lock measures the Mac signal. Location is only for older Android Bluetooth scans | When you turn leave-lock on |
| Camera | Reserved for a future in-app QR scanner. Not requested in 0.3.0 | Not yet |

Not requested, because the feature is not built: contacts, accessibility on the phone, media projection.

## Mac

| Item | Why | How |
|---|---|---|
| Local network | Talk to the phone. The system shows this because the app advertises Bonjour | First connection. The reason string is in Info.plist |
| Notifications | Notes from the phone | Button in the menu window |
| Login item | Launch at login | Toggle in the menu window. Put the app in Applications |
| Accessibility | The phone trackpad, keyboard, and slides. Also the leave-lock shortcut | Button in Settings. System Settings, Privacy and Security, Accessibility |

The owner sideloads this app. There is no Apple Developer certificate and no notarization, on purpose.
