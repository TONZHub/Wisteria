<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/d1ab5902-f70d-4c2f-a2c3-2c92b4e813be

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com), add an Android app with package name `com.example.wisteria`, enable **Firestore** and **Anonymous** sign-in under Authentication, then download the generated `google-services.json` and place it at `app/google-services.json` (it's gitignored — each developer supplies their own). Without this file, Firestore sync fails gracefully at runtime rather than crashing, but check-ins won't persist to the cloud.
6. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
7. Run the app on an emulator or physical device
8. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
