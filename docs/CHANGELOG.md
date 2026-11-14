# Changelog

## 0.3.0

- Phone and Mac speak length-prefixed frames and TLS. The phone pins the Mac certificate.
- Socket writes on Android run off the main thread.
- The foreground service declares the network permissions Android 14 requires.
- Pairing uses a one-time PIN, a QR link, Wi-Fi discovery, and a saved token. A failed new connection does not drop the paired phone.
- The Mac app can quit, show About, toggle launch at login, and choose what to do with the phone clipboard.
- The phone reconnects after a network change and after boot when a token is saved. Ring uses maximum alarm volume and stops after 2 minutes.
- GitHub Actions runs tests on every push. The release workflow creates the release. A missing signing secret produces a clearly named debug APK. The Mac build fails if either CPU slice is missing.

## 0.2.0

- First installable Android APK and universal Mac zip. Cleartext notes, clipboard, ring, and a PIN. Replaced by 0.3.0. The two versions cannot talk to each other.

## 0.1.0

- Source only. No installable apps.
