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
