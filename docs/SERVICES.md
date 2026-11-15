# Services

| Service | Used for | Account |
|---|---|---|
| GitHub Actions | Android tests and debug APK, Mac tests and universal app, web typecheck and tests, GitHub Releases | The CAPPY7 repository |
| GitHub Releases | The APK, the Mac disk image, the Mac zip, and appcast.xml | Same repository |
| Cloudflare Workers | Optional away-from-home relay. It only forwards locked frames. | A free Cloudflare account, if you turn the relay on |

Not used: Firebase, FCM, a database, or a paid Apple Developer account. The phone and the Mac still work on the same Wi-Fi with no account. The relay is optional and cannot read the frames.
