# Permissions

Each permission stays off until a feature needs it. BridgeLink does not change a system setting silently.

## Android 8–15

| Permission | Why | When |
|---|---|---|
| Notifications | Connection status, notes, the ring notification | First launch, Android 13 and later |
| Internet, network state, Wi-Fi state | Connect to the Mac | Install time, no prompt |
| Foreground service | Keep the link while the app is closed | While the service runs |
| Receive boot completed | Reconnect after a reboot if a token is saved | Install time |
| Ignore battery optimizations | Stop the vendor from killing the service | When you tap Keep me connected |
| Nearby Bluetooth | Leave-lock watches the Mac signal | When you turn leave-lock on |
| Precise location | Only Android 11 and older. Bluetooth scan on those phones needs it. Android 12 and newer do not get this permission | When you turn leave-lock on, on an old phone |
| SMS and phone state | Read and send texts, and tell two SIMs apart. Phone state also lets call alerts know the phone is ringing | When you turn texts on, or when you turn call alerts on |
| Answer phone calls | Decline a call from the Mac when the call notification has no Decline button | When you turn call alerts on, Android 9 and newer |
| Photos | Show recent photos on the Mac. Android 13 and newer use photo access. Older phones use storage up to Android 12. Android 14 can be only the photos you pick | When you turn phone photos on, or open the Mac Photos page |
| Do Not Disturb access | Let the Mac pause phone notifications, and report the real state back | When you tap the pause switch or Sync Do Not Disturb. Already declared. If it is off, the phone shows the Settings step |
| Notification access | Mirror alerts, call banners, reply, and phone media controls | When you turn notification mirroring on, or when you turn call alerts on |
| Accessibility | Clicks in the Mac mirror window | When you turn phone control on. Android 13+ may need Allow restricted settings |
| Screen share | The Mac mirror window | Every time you start a mirror. Android asks each time |
| Full screen intent | Show the screen-share or camera question over the lock screen | Android 14 and newer can turn this off. The notification still waits if it is off |
| Camera and microphone | The camera inside BridgeLink, and a scan | When you open the camera or scan |
| Files you pick | Send a file, or choose the shared folder | When you pick them. The choice is remembered. There is no broad storage permission |

The document scanner uses Play services. If that is missing, pick a photo instead. The in-app QR scanner also uses Play services. Paste the link if it is missing.

## Mac, macOS 13, 14, and 15

| Item | Why | How |
|---|---|---|
| Local network | Talk to the phone | First connection |
| Notifications | Notes from the phone | Setup window, or the button in the menu |
| Login item | Launch at login | Setup window or Settings. Put the app in Applications |
| Accessibility | Trackpad, keyboard, slides, and the leave-lock shortcut | Settings explains the page. The phone never unlocks the Mac |
| Bluetooth | Leave-lock | When you turn leave-lock on |
| Camera and microphone | Only if a future system camera is enabled | Not used by the free webcam window |

The owner sideloads this app. There is no paid Apple Developer certificate. macOS 15 needs Open Anyway once. Later updates keep permissions only when the build is signed with the same self-signed certificate. Ad-hoc builds can forget them.
