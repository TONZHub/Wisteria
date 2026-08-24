# Wisteria — Devpost submission pack

This file is the copy-and-check source for the All Things Agentic Hackathon submission. Replace every `TODO` before the deadline and keep the submitted repository, video, and downloadable build unchanged through judging.

## Submission facts

| Field | Answer |
| --- | --- |
| Project | Wisteria |
| Tagline | A three-second daily-rhythm agent for days when a full sentence is too much. |
| Category | **Collaborative Partner** |
| Development start date | **August 22, 2026** — independently evidenced by [pull request #1](https://github.com/TONZHub/Wisteria/pull/1) |
| Repository | <https://github.com/TONZHub/Wisteria> |
| Hosted/test build | TODO: add the public GitHub Release APK URL |
| Architecture upload | [`docs/wisteria-architecture.png`](wisteria-architecture.png) |
| Public demo video | TODO: add the public YouTube or Vimeo URL |
| Team | TODO: confirm every human teammate is added and has accepted; if solo, no invitations are required |
| Startup Excellence | Select only if entering for an incorporated organization with its corporate email |

## Short description

Some days, the hardest part of self-reflection is finding a full sentence. Wisteria accepts a number, a tap, or an everyday phrase such as “I feel off,” turns it into one gentle daily record, and stays present for a follow-up without accidentally logging the conversation twice. Over time, its user-triggered Night Shift notices recurring heavy-to-off stretches and prepares a plain-language brief with visible confidence and evidence.

Wisteria can act without taking control away from the person. Google ADK Kotlin keeps the conversation together, and Gemini 3.5 Flash helps Wisteria respond through Firebase AI Logic. Small local rules decide whether anything may be saved. The model cannot change alerts, contact anyone, infer a condition, or silently sync data.

## Inspiration

Most trackers ask for the most effort on the days when the person has the least to spare. Wisteria began with a smaller question: what if being understood could start with three seconds and one ordinary word?

The answer became a companion that meets the person where they are, remembers only what it has permission to keep, and turns a series of tiny signals into something useful without pretending to know why the person feels that way.

## What it does

- Accepts one-tap, number, text, and voice check-ins using the everyday textures **bright**, **steady**, **heavy**, and **off**.
- Keeps tap-to-speak and full-screen in-app calls in one session-aware agent loop.
- Separates a new check-in from a conversational follow-up, request for an idea, pattern question, reminder request, or conversation ending.
- Records at most one copy of an explicit check-in and displays a receipt for every authorized tool action.
- Offers optional, low-effort ideas after a low check-in without presenting them as instructions.
- Learns recurring heavy-to-off stretches from saved local history when the person asks Night Shift to run.
- Supports a user-scheduled Check-In Alarm with start, snooze, dismiss, reboot recovery, and graceful notification fallbacks.
- Reads only individually granted Health Connect signals and reduces them on-device to a tone-only hint; raw values and inferred labels never enter the model prompt.
- Uses Google Sign-In for optional, explicit synchronization to Cloud Firestore.
- Continues to provide a deterministic local companion response if ADK, Firebase, or the network is unavailable.

## How the agent works

1. The person speaks, taps, or types a tiny signal.
2. A deterministic local router combines that signal with bounded conversation state and assigns a final intent.
3. Google ADK Kotlin maintains the in-memory dialogue session. Its `LlmAgent` uses Gemini 3.5 Flash through Firebase AI Logic for concise companion wording.
4. A separate local policy authorizes or denies every tool. Unknown tools and unclear writes are denied by default.
5. An explicit check-in may write one local Room record and, for a low check-in, queue optional ideas once.
6. Night Shift can synthesize the local timeline into a pattern brief when the person requests it.
7. Firestore receives the current entry only after Google Sign-In and a separate tap on **Sync Firestore**.

This separation is the core design choice: Gemini helps Wisteria choose its words, while small local rules decide what Wisteria may do.

## Google technology used

### Required model

- **Gemini 3.5 Flash** (`gemini-3.5-flash`)
- Accessed through Firebase AI Logic using the **Agent Platform Gemini API** backend

### Required agent framework

- **Google ADK Kotlin 0.8.0**
- `LlmAgent`, `InMemoryRunner`, and `InMemorySessionService`
- One model call maximum per turn, with a fresh session at explicit conversation boundaries

### Required Google Cloud service

- **Cloud Firestore** at `users/{uid}/daily_timeline/{date}`
- Firebase Authentication with Google Sign-In
- Firebase AI Logic backed by Agent Platform
- Firebase App Check using the debug provider for development and Play Integrity for release builds

### Other Google and Android technology

- Android Credential Manager and Google ID
- Health Connect
- Jetpack Compose, Room, SpeechRecognizer, and Android text-to-speech

## Data sources

- The person’s own tap, number, typed phrase, or voice transcript.
- Locally saved Wisteria check-ins in Room.
- Optional Health Connect sleep, step, and timing records, only when individually granted. Raw records are reduced on-device and are not transmitted to the model.
- Optional Cloud Firestore copies created by an explicit sync action.
- Ten clearly labeled synthetic demo days, loaded only when the demo button is pressed and never synced automatically.

Wisteria uses no scraped dataset and makes no claim to identify a cause or condition.

## What we learned

- A short ordinary phrase can be a more usable signal than a detailed form when someone has very little energy.
- Conversation state matters: “yes, give me one idea” should continue the current check-in, not create a duplicate record.
- Useful personalization does not require revealing raw private context. Health Connect can influence how gently the agent speaks without exposing the underlying value or supposed reason.
- Agentic systems are more trustworthy when generation and authority are separate. ADK and Gemini handle the conversation; a small, testable local policy owns every write.
- Visible receipts and deterministic fallbacks make an agent easier to understand, demonstrate, and trust when a cloud dependency fails.
- A pattern should show its sample size and uncertainty. Wisteria calls a pattern a gentle heads-up, never a certainty.

## New-project and third-party disclosure

Development of Wisteria began on **August 22, 2026**, during the hackathon submission period. No Wisteria application code existed before August 3, 2026, and no pre-existing application code was incorporated into the submission.

The project uses standard development tools, open-source libraries, and platform SDKs, including Kotlin, AndroidX, Jetpack Compose, Room, Google ADK Kotlin, Firebase Android SDKs, Kotlin coroutines, Coil, JUnit, Robolectric, and Roborazzi, under their respective licenses. AI coding assistants were used for scaffolding, implementation support, debugging, review, testing, and documentation. The entrant supplied the product concept, interaction design, constraints, creative direction, and final decisions.

Official Google and Health Connect service marks are used only to identify their corresponding integrations. No third-party dataset, proprietary application code, or paid template is included.

## Judge testing instructions

### Fastest path: downloadable APK

1. Download the APK from the public release URL in **Hosted/test build** above.
2. Install it on Android 8.0 or newer. Android may ask permission to install an app from the browser or file manager.
3. Open **Check-In** and approve microphone access when first using voice.
4. Say “I feel off.” Confirm the reply, the single saved check-in receipt, and the absence of duplicate writes.
5. Tap **Call**, say “yes, give me one idea,” and confirm the follow-up stays in the same session.
6. Open **Insights**, then **See How It Works**, and load the ten clearly labeled demo days.
7. Run Night Shift and inspect its sample size, pattern result, confidence, and tool trace.

The public judge APK may use Wisteria’s deterministic local fallback. The connected ADK, Gemini, and Firestore execution is shown live in the public demo video and is reproducible with the setup below.

### Build from source

Requirements: JDK 17 and an Android SDK containing API 36.1.

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0+ device or emulator. Voice requires an Android speech-recognition service and text-to-speech engine.

### Reproduce the connected Google Cloud path

1. Create a Firebase Android app with package name `com.zoeb.wisteria`.
2. Place its downloaded configuration at `app/google-services.json`.
3. In Firebase, enable Google Authentication, Cloud Firestore, and Firebase AI Logic with the Agent Platform backend.
4. Add the signing certificate SHA-1 and set the app’s Credential Manager `serverClientId` to the matching Web OAuth client ID.
5. Deploy `firestore.rules`.
6. Register the App Check debug token printed by the debug build, or use the appropriate attestation provider for the installed build.
7. Build and install the app, sign in with Google, complete a check-in, then tap **Sync Firestore**.
8. Confirm the document at `users/{uid}/daily_timeline/{date}` in the Firebase or Google Cloud console.

No developer Gemini API key belongs in the APK or repository.

## Final submission checklist

- [ ] Select **Collaborative Partner** and no second core category.
- [ ] Confirm every human teammate is added and has accepted, or confirm the entry is solo.
- [ ] Add the public GitHub Release APK URL.
- [ ] Upload `docs/wisteria-architecture.png`.
- [ ] Add two or more real connected-build screenshots.
- [ ] Record the demo using [`docs/DEMO_SCRIPT.md`](DEMO_SCRIPT.md).
- [ ] Make the video publicly visible on YouTube or Vimeo and add its URL.
- [ ] Show an uninterrupted agent action and visible Google Cloud proof in the video.
- [ ] Confirm the repository remains public; otherwise add `testing@devpost.com` and `cloudhackathons@google.com`.
- [ ] Copy the features, technologies, data sources, learnings, and disclosures from this file into Devpost.
- [ ] Choose a project license deliberately; do not add one by accident.
- [ ] Remove every `TODO` from this file or replace it with final submission information.
- [ ] Submit before **August 31, 2026 at 5:00 PM Pacific / 8:00 PM Eastern**.
- [ ] Leave the submitted repository, build, video, and hosted materials unchanged through judging; continue later work in a separate fork if needed.

## Optional bonus work — only after the core submission is complete

- Publish a public build write-up that explicitly says it was created for entry into the All Things Agentic Hackathon.
- Publish a Wisteria post on LinkedIn or X with `#AllThingsAgenticHackathon`.
- Do not add another model merely for a checkbox; integrate Gemma, Veo, or Lyria only if it creates a real, demonstrable improvement.
