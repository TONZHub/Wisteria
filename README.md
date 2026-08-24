# Wisteria

> A 3-second check-in for days when a full sentence is too much.

[![Android CI](https://github.com/TONZHub/Wisteria/actions/workflows/android-ci.yml/badge.svg)](https://github.com/TONZHub/Wisteria/actions/workflows/android-ci.yml)

Wisteria is a local-first Android prototype that turns a number, emoji, or everyday word into a gentle daily record. It uses four intentionally plain textures—**bright, steady, heavy, and off**—and never assigns a body phase or explains why someone feels a certain way.

## 60-second judge demo

1. Open **Check-In**, tap the microphone, and say “I feel off.” Wisteria captions the turn, runs the same agent tools as text mode, and speaks its reply.
2. Tap **Call** to show full-screen voice mode. Toggle hands-free off and back on, then end the call.
3. Open **Insights** to see today's texture and toggle one optional idea as done.
4. Tap the info icon, choose **See How It Works**, then tap **Load 10 clearly labeled demo days**.
5. Wisteria runs Night Shift on-device and shows the heavy-to-off pattern it found, the number of samples used, and a trace of each step.
6. If Firebase is configured, sign in with Google and explicitly tap **Sync Firestore**.

The demo-data button is explicit: every sample starts with `Demo:`, stays local, and is never synced automatically.

## What works today

- One-tap or one-word check-ins with a deterministic local fallback.
- Tap-to-speak check-ins with live partial captions and a spoken Wisteria response.
- Full-screen in-app calls with push-to-talk, turn-based hands-free mode, mute, speaker, captions, and hang up controls.
- Local Room storage; Android backup is disabled for the app.
- Everyday textures kept separate: bright, steady, heavy, off, or unlabeled.
- User-triggered Night Shift learning from real heavy-to-off stretches in local history—no fixed schedule.
- Sample-based confidence that grows only with saved history and observed transitions.
- Optional companion wording through Firebase AI Logic and `gemini-3.5-flash`.
- Optional, button-triggered Firestore sync under the signed-in Google account's Firebase user ID.
- Firebase App Check: debug provider for debug builds and Play Integrity for release builds.
- A test-gated GitHub Actions build that publishes the debug APK and unit-test report.

## Honest boundaries

Wisteria does **not** assign a body phase, label a condition, identify a cause, change alerts or tasks, place telephone calls, contact anyone, run overnight, or deploy a Cloud Run worker. “Call” means a full-screen conversation inside the Android app. The tool interface is project-owned; this repository does not claim a Google ADK integration.

Night Shift runs only when the person taps its button. Firebase AI Logic shapes the short companion reply; local rules own storage, texture selection, and care ideas so the core check-in still works offline or without Firebase configuration. Hands-free voice mode uses bounded listen–think–speak turns rather than an always-open microphone.

## Architecture

| Part | Current implementation |
| --- | --- |
| UI | Jetpack Compose |
| Daily record | Room, on-device |
| Texture selection | Deterministic Kotlin rules using the submitted number or words |
| Pattern learning | `NightShiftAnalyzer`, on-device and user-triggered |
| Companion wording | Firebase AI Logic (`gemini-3.5-flash`), with local fallback |
| Voice input | Android `SpeechRecognizer`; on-device recognition is preferred when available |
| Voice output | Android device text-to-speech with utterance lifecycle callbacks |
| Call mode | Full-screen, turn-based in-app voice session over the existing agent loop |
| Optional sync | Cloud Firestore at `users/{uid}/daily_timeline/{date}` |
| Identity | Firebase Authentication with Google Sign-In; the resolved UID is used after sign-in |
| Request protection | Firebase App Check |

## Run locally

### Local-only demo

The app builds and its core demo works without Firebase configuration:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, then use **Load 10 clearly labeled demo days** for the full judge flow. The first microphone or call action requests `RECORD_AUDIO`; voice features also require an Android speech-recognition service and text-to-speech engine.

### Connected Firebase demo

1. Create a Firebase Android app with package name `com.zoeb.wisteria`.
2. Place the downloaded configuration at `app/google-services.json` (it is gitignored).
3. Enable Google as a Firebase Authentication sign-in provider, Cloud Firestore, and Firebase AI Logic.
4. Add the app's SHA-1 in Firebase project settings and confirm the Credential Manager `serverClientId` matches the project's Web OAuth client ID.
5. Deploy the included `firestore.rules`.
6. Register the App Check debug token printed by a debug build. Configure Play Integrity for release builds.
7. Sign in with Google, then explicitly tap **Sync Firestore** when you want to copy the current entry.

No developer Gemini API key belongs in this Android project or APK.

For connected GitHub Actions artifacts, add the repository secret `GOOGLE_SERVICES_JSON_B64` containing a base64-encoded `google-services.json`. Public CI still builds safely without the secret and uses the local companion response.

## Verification

Every pull request runs:

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

The tests cover everyday-language selection, unknown input, optional care ideas, prompt de-duplication, local-only check-ins, explicit Firestore sync, demo-data labeling, heavy-to-off learning, confidence limits, on-device Night Shift execution, and voice-session UI state.

## Privacy notes

- Check-ins start on-device and are not cloud-synced during a normal check-in.
- Wisteria does not retain raw microphone audio. The configured Android speech service produces a transcript, which follows the same storage and optional AI path as typed text.
- Voice mode stops recognition while the agent reasons or speaks; hands-free mode opens a new finite listening turn only after speech playback ends.
- Firestore sync requires an explicit button tap.
- App data is excluded from Android backup.
- The repository contains no Firebase configuration, developer API key, or personal planning artifact in its current tree.

The public Git history predates this cleanup. Maintainers should review and rewrite that history separately before sharing the repository more broadly; history rewriting is intentionally not performed by this feature branch.

## Before final submission

- Record a 60–90 second video using the judge flow above.
- Add two real app screenshots from the connected demo build.
- Configure `GOOGLE_SERVICES_JSON_B64` and register the App Check debug token for the downloadable connected APK.
- Choose and add a project license.
