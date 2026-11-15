# Setup for the owner

You only need this if you want release APKs to update in place. A debug APK installs, but the next debug build from GitHub will not replace it.

## 1. Create a keystore on your computer

From the repo:

```bash
bash scripts/generate-android-keystore.sh bridgelink-release.keystore bridgelink
```

The script prints four values. Keep them. Do not commit the `.keystore` file. Anyone with admin access to the repository can instead run the same script and store the four values as Actions secrets with `gh secret set`. Rotating the key means a new keystore and a new install, because Android will not update across different signatures.

## 2. Add four GitHub secrets

Open the repository on GitHub, then Settings, then Secrets and variables, then Actions, then New repository secret.

| Secret name | What to paste |
|---|---|
| ANDROID_KEYSTORE_BASE64 | The long base64 line the script printed |
| ANDROID_KEY_ALIAS | `bridgelink`, unless you chose another alias |
| ANDROID_KEYSTORE_PASSWORD | The store password the script printed |
| ANDROID_KEY_PASSWORD | The key password the script printed |

## 3. Publish

Push a tag such as `v0.3.0`, or run the Release workflow and type that tag. If any secret is missing, the workflow attaches `BridgeLink-android-debug.apk` and says so in the log. It does not pretend that file can update an older install.

## 4. Away-from-home relay (free, optional)

The phone and the Mac still pair on the same Wi-Fi first. That gives them a key. After that, a relay can carry the locked frames when they are not on the same network. The relay cannot read the frames.

1. Create a free Cloudflare account.
2. Install Wrangler: `npm install -g wrangler`, then `wrangler login`.
3. From the `relay` folder, run `wrangler deploy`.
4. Copy the `wss://` address it prints. It looks like `wss://bridgelink-relay.your-name.workers.dev`.
5. On the Mac, open Settings, paste that address, and tap Save relay.
6. On the phone, open Mirror, paste the same address, and tap Save relay.
7. When Wi-Fi to the Mac fails, tap Use away relay on both. Pair again on Wi-Fi if it says the key is missing.

Do not put the pairing key in the worker. The worker in `relay/worker.js` only copies bytes from one side to the other.

## 5. System webcam

The free app shows the phone camera inside a BridgeLink window. A camera that shows up in Zoom, Meet, or FaceTime needs a paid Apple Developer ID. The source is `macos/CameraExtension`. It is not built unless `APPLE_TEAM_ID` is set, and it still cannot be installed system-wide without that paid account.
