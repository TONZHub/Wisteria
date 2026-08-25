# Google Play release

Google Play receives a signed Android App Bundle (`.aab`). The upload keystore and
its passwords must never be committed to the repository.

## One-time setup

1. In Android Studio, choose **Build → Generate Signed Bundle / APK → Android App
   Bundle → Create new**.
2. Save the upload keystore somewhere backed up outside this repository. Use
   `upload` as the key alias (or retain the alias you choose).
3. Enable Play App Signing when the first bundle is added in Play Console.
4. Add these GitHub Actions repository secrets:

   | Secret | Value |
   | --- | --- |
   | `GOOGLE_SERVICES_JSON_B64` | Base64 of the production `app/google-services.json` |
   | `ANDROID_UPLOAD_KEYSTORE_B64` | Base64 of the upload `.jks` file |
   | `ANDROID_UPLOAD_STORE_PASSWORD` | Keystore password |
   | `ANDROID_UPLOAD_KEY_PASSWORD` | Upload-key password |
   | `ANDROID_UPLOAD_KEY_ALIAS` | Upload-key alias |

On Windows PowerShell, encode a file without line wrapping with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\file"))
```

## Build the bundle

Run **Actions → Build Google Play bundle → Run workflow** from `main`. The
workflow tests the app, builds the connected release variant, verifies its
signature, and publishes `wisteria-google-play-v1` as a private workflow
artifact. Download `app-release.aab` from that artifact and upload it to the
appropriate Play Console testing track.

Before a later release, increment `versionCode` in `app/build.gradle.kts` and
update `versionName` when appropriate. Google Play rejects an uploaded bundle
whose version code has already been used.
