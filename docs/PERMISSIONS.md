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
| None for files | The file picker and the share sheet hand over one file. The app does not ask for storage access | When you send or receive a file |
| Camera | Reserved for a future in-app QR scanner. Not requested in 0.3.0 | Not yet |

Not requested, because the feature is not built: SMS, contacts, notification listener, accessibility, media projection, nearby Bluetooth, location.

## Mac

| Item | Why | How |
|---|---|---|
| Local network | Talk to the phone. The system shows this because the app advertises Bonjour | First connection. The reason string is in Info.plist |
| Notifications | Notes from the phone | Button in the menu window |
| Login item | Launch at login | Toggle in the menu window. Put the app in Applications |
| Accessibility, screen recording, camera, microphone | Not used in 0.3.0 | Not requested |

There is no Apple Developer certificate, so the app is ad-hoc signed and not notarized.
