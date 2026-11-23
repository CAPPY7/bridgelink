# Setup for the owner

You only need this if you want the release files. The apps already build. Signing secrets stay in GitHub. Do not print them. Do not commit a keystore.

## 1. Android signing secrets

These four must already exist. Do not create a new Android key.

| Secret name | What it is |
|---|---|
| ANDROID_KEYSTORE_BASE64 | The existing release keystore, base64 |
| ANDROID_KEY_ALIAS | The alias inside that keystore |
| ANDROID_KEYSTORE_PASSWORD | The store password |
| ANDROID_KEY_PASSWORD | The key password |

If any of them is missing, the Release workflow stops. It does not upload a debug APK.

## 2. Mac signing secrets

| Secret name | What it is |
|---|---|
| MAC_SIGN_P12_BASE64 | One self-signed certificate, used for every build |
| MAC_SIGN_P12_PASSWORD | The password for that certificate |

Without them, the Mac app is ad-hoc signed and macOS can forget Accessibility and Bluetooth after an update. Notarization runs only when APPLE_ID, APPLE_APP_PASSWORD, and APPLE_TEAM_ID are all set. They are optional.

## 3. Publish 1.0.0

Push a tag `v1.0.0`, or run the Release workflow and type that tag. The release gets four files:

- BridgeLink-android.apk
- BridgeLink.dmg
- BridgeLink-mac.zip
- appcast.xml

Check for update reads appcast.xml from the latest release. The Sparkle app framework is not linked. The button opens the release page instead of installing by itself. A Sparkle EdDSA key was not created here.

## 4. Remote path

Removed. Do not deploy a worker. The phone and the Mac link only on the same Wi-Fi.
