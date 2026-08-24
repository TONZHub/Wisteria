# Wisteria — under-four-minute demo plan

Target length: **3:20–3:40**. Begin already signed in on the Check-In screen. Record short clips, but preserve the first complete agent action as one uninterrupted live take so the proof of action is undeniable.

## Before recording

- Use a connected build that successfully reaches Google ADK Kotlin, Gemini 3.5 Flash, and Firestore.
- Use a dedicated demo Google account with no personal email, notification, or Cloud data visible.
- If a clean install is needed, clear Wisteria’s local data **before** registering the final App Check debug token. After registration, rehearse without clearing storage; another reset can create a new token and break the connected take.
- Confirm microphone recognition, text-to-speech, speaker volume, and screen-recording audio.
- Set Android display size and font size to defaults and enable Do Not Disturb for unrelated notifications.
- Open the relevant Firestore document and Firebase AI Logic or Agent Platform evidence in a desktop browser before recording the Cloud clip.
- Hide billing details, tokens, debug secrets, OAuth client IDs, and unrelated browser tabs.
- Prepare short on-screen labels; do not type or wait through loading on camera.
- If Android Builder is operating the phone, use [`ANDROID_BUILDER_DEMO_RUNBOOK.md`](ANDROID_BUILDER_DEMO_RUNBOOK.md). The entrant still supplies the two real voice turns; the operator must stop if any live receipt is missing.

## Shot list and narration

### 0:00–0:12 — Start with the agent working

**Picture:** Wisteria is already open on Check-In. Tap the microphone and say: **“I feel off.”**

**Narration:**

> Some days, a full sentence is too much. Wisteria turns one ordinary phrase into a useful daily signal.

Do not place a title card before this action.

### 0:12–0:38 — Continuous proof of action

Keep this section as one uncut take. Let Wisteria finish the turn and briefly hold on:

- the spoken companion reply;
- the Google ADK Kotlin and Gemini runtime receipt;
- the local `RecordSingleInputCheckInTool` receipt;
- the saved **off** texture.

**Narration:**

> A local router classified the turn before the model saw it. Google ADK keeps the conversation state, Gemini 3.5 Flash shapes the wording, and a separate local policy authorizes exactly one write.

### 0:38–1:05 — Show that it is a session, not a form

**Picture:** Tap **Call**. Say: **“Yes, give me one idea.”** Show that the response remains conversational and does not create a second check-in. Briefly toggle hands-free mode, then end the call.

**Narration:**

> Wisteria remembers what this conversation is about. A follow-up stays a follow-up, so voice mode feels natural without quietly duplicating the record.

On-screen label: **Session-aware • duplicate-safe**

### 1:05–1:38 — Show action with clear permission

**Picture:** Open Insights. Show the single saved check-in and optional ideas. Mark one idea complete. Briefly reveal the action receipt or architecture screen.

**Narration:**

> The model can shape language, but it cannot change settings, contact anyone, or invent a reason for how I feel. Unknown tools are denied, and every allowed action leaves a visible receipt.

On-screen label: **The model suggests. Local policy decides.**

### 1:38–2:08 — Show learning from a timeline

**Picture:** Open **See How It Works**, load the ten clearly labeled demo days, and run Night Shift. Show the heavy-to-off result, sample size, confidence, and tool trace.

**Narration:**

> With a history, Night Shift can synthesize recurring heavy-to-off stretches. It shows its samples and uncertainty, calls the result a gentle heads-up, and never assigns a body phase, condition, or cause.

On-screen label: **User-triggered pattern learning • never a certainty**

### 2:08–2:30 — Show private context without exposing it

**Picture:** Briefly show the Health Connect access card and the architecture privacy boundary. Do not show real health values.

**Narration:**

> Health Connect is optional and granular. Raw records stay on-device; Wisteria reduces granted signals to a tone-only hint that the reply must never mention aloud.

### 2:30–2:54 — Show explicit Cloud action

**Picture:** Return to Insights, show the connected Google account state, and tap **Sync Firestore**. Hold on the successful sync receipt and document path.

**Narration:**

> Cloud sync is separate from the check-in. It happens only after Google Sign-In and this explicit tap.

### 2:54–3:14 — Prove Google Cloud is running

**Picture:** Cut to the Firebase or Google Cloud console. Show the newly updated `users/{uid}/daily_timeline/{date}` document, then briefly show Firebase AI Logic or Agent Platform request evidence for the connected run.

**Narration:**

> Here is the live Firestore update, and here is the Gemini request running through Firebase AI Logic on Google Cloud.

On-screen label: **Cloud Firestore • Firebase AI Logic • Gemini 3.5 Flash**

### 3:14–3:36 — End on the architecture and value

**Picture:** Show `docs/wisteria-architecture.png`, then return to Wisteria’s Check-In screen.

**Narration:**

> Wisteria is a Collaborative Partner that listens, remembers the turn, takes useful action, and learns a rhythm—while the person stays in control. Three seconds in; something useful back.

End card, no longer than two seconds:

> **Wisteria** — A three-second daily-rhythm agent

## Edit rules

- Keep the 0:12–0:38 proof-of-action segment continuous and unsped.
- Use jump cuts for navigation, loading, and switching between phone and Cloud Console.
- Keep phone footage large enough to read receipts; crop dead space rather than shrinking the interface.
- Use captions throughout. Keep on-screen labels to one short claim at a time.
- Do not claim that the public judge APK has a connected backend unless that exact build does.
- Do not call an edited sequence “unedited.” Describe the first action accurately as a continuous live run.
- Keep the final upload public on YouTube or Vimeo and verify it in a signed-out browser.

## Must-capture evidence

- [ ] Wisteria completes a real turn through Google ADK Kotlin and Gemini 3.5 Flash.
- [ ] The runtime receipt is readable.
- [ ] One local tool write succeeds and no duplicate write occurs.
- [ ] Night Shift produces a trace from labeled demo history.
- [ ] The explicit Firestore sync succeeds.
- [ ] The new Firestore document is visible in the console.
- [ ] Firebase AI Logic or Agent Platform evidence is visible.
- [ ] No personal account data, secrets, tokens, or billing information appears.
- [ ] Total runtime is under four minutes.
