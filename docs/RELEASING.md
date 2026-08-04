# Releasing S.Ai

Pushing a tag like `v0.1.0` triggers `.github/workflows/release.yml`, which
builds the APK and attaches it to a GitHub release named after the tag —
e.g. `S.Ai-v0.1.0.apk`. Anyone can download that file directly from the
release page, no GitHub login required.

If the four signing secrets below are **not** configured, the workflow
still succeeds — it falls back to a debug-signed APK so a tag always
produces something installable. But a debug-signed APK cannot be upgraded
in place by a future signed release (Android refuses to install an update
signed with a different key over an existing install), so set up signing
before you actually rely on this for your own phone.

## 1. Generate a keystore

Do this once, on your own machine (not in CI):

```sh
keytool -genkeypair -v \
  -keystore sai-release.keystore \
  -alias sai \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a keystore password, a key password, and identity
details (name/org — anything is fine here). **Remember both passwords.**

## 2. Back up the keystore

Copy `sai-release.keystore` somewhere durable outside this repo — a
password manager, encrypted cloud storage, wherever you keep secrets.

**If you lose this file, you can never publish an update that installs
over an existing copy of the app.** Every future release must be signed
with the same key. There is no recovery path; a lost keystore means
existing installs are stuck forever and the only way forward is shipping
under a new applicationId as a fresh app.

Never commit the keystore file to the repo (`.gitignore` already excludes
`*.keystore` and `*.jks`).

## 3. Base64-encode it

```sh
# macOS
base64 -i sai-release.keystore | pbcopy

# Linux
base64 -w0 sai-release.keystore

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sai-release.keystore")) | Set-Clipboard
```

## 4. Add repo secrets

In the repo on GitHub: **Settings → Secrets and variables → Actions → New
repository secret**. Add all four:

| Secret name         | Value                                      |
|----------------------|---------------------------------------------|
| `KEYSTORE_BASE64`    | Output from step 3                          |
| `KEYSTORE_PASSWORD`  | The keystore password from step 1           |
| `KEY_ALIAS`          | `sai` (or whatever alias you used)          |
| `KEY_PASSWORD`       | The key password from step 1                |

## 5. Cut a release

```sh
git tag v0.1.0
git push origin v0.1.0
```

Watch the **Actions** tab for the `Release` workflow, then grab the APK
from the repo's **Releases** page.
