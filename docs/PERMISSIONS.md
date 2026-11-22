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
| SMS and phone state | Read and send texts, and tell two SIMs apart | When you turn texts on |
| Notification access | Mirror alerts, reply, and phone media controls | When you turn notification mirroring on |
| Accessibility | Clicks in the Mac mirror window | When you turn phone control on. Android 13+ may need Allow restricted settings |
| Screen share | The Mac mirror window | Every time you start a mirror. Android asks each time |
| Full screen intent | Show the screen-share or camera question over the lock screen | Android 14 and newer can turn this off. The notification still waits if it is off |
| Camera and microphone | The camera inside BridgeLink, and a scan | When you open the camera or scan |
| Files you pick | Send a file or use the shared folder | When you pick them. The shared folder uses the app's own folder |

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
