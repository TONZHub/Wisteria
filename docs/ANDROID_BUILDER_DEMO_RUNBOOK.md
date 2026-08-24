# Android Builder — Wisteria demo operator

This runbook lets Android Builder operate the connected phone while the entrant supplies only the two live voice turns. It is a real connected demonstration, not a simulated product path.

## What remains human

- Say **“I feel off”** when Android Builder opens the first microphone turn.
- Say **“Yes, give me one idea”** when Android Builder opens the in-app call.
- Approve an Android system permission or screen-recording dialog if Android Builder cannot interact with it.
- Record the short Firebase / Google Cloud console clip separately.

Everything else—navigation, pauses, verification, and capture—is Android Builder's job.

## Prepare once

1. Install the connected Wisteria build on the phone.
2. Register that build's Firebase App Check debug token.
3. Sign in with the dedicated demo Google account and leave it signed in.
4. Grant microphone permission and complete one private rehearsal so Android speech recognition and text-to-speech are warm.
5. Verify that a Wisteria turn visibly reports Google ADK Kotlin and Gemini 3.5 Flash rather than the local fallback.
6. Enable Do Not Disturb and close every unrelated app.
7. Do **not** clear app storage after registering App Check; that can create a new debug token.
8. Start the final take on **Check-In** with no private information visible.

## Copy this instruction into Android Builder

```text
You are the phone operator for Wisteria's Devpost demo. Operate only the connected Android phone. Do not change source code, seed an agent reply, type either spoken check-in, expose account details, or claim success without reading the visible receipt.

MODE: REHEARSAL

Your goals are to produce one clean, truthful phone capture and to stop safely if any required live proof is missing.

Rules:
1. Never clear Wisteria's app storage, sign out, change permissions, change alarm settings, or open another account.
2. Never type or inject “I feel off” or “Yes, give me one idea.” Ask me to speak those phrases at the marked moments.
3. Do not continue past a failed checkpoint. Stop recording, preserve the screen, and report the exact visible failure in plain language.
4. Wait for animations, speech, and loading to finish before the next tap. Do not tap the same control twice.
5. Keep the first check-in—from microphone tap through visible receipts—in one continuous, unsped capture.
6. Do not show notifications, tokens, OAuth identifiers, billing, or unrelated account data.

Run:
A. Confirm Wisteria is open on Check-In and the screen contains no personal information.
B. Start the phone screen recording if device control permits it. If Android requires human approval, pause and ask me once.
C. Tap the microphone and say: “Please say: I feel off.” Wait for my voice and let Wisteria finish listening, thinking, and speaking.
D. CHECKPOINT 1: Verify all of these are visible before continuing:
   - the recognized turn is “I feel off”;
   - the saved texture is off;
   - exactly one local RecordSingleInputCheckInTool action succeeded;
   - the runtime receipt names Google ADK Kotlin and Gemini 3.5 Flash;
   - the receipt does not say local fallback.
   If any item is absent, stop and report which one.
E. Hold the completed receipt on screen for three seconds.
F. Tap Call. When listening begins, say: “Please say: Yes, give me one idea.” Wait for my voice and Wisteria's complete spoken response.
G. CHECKPOINT 2: Verify the follow-up stayed conversational and did not create a second check-in. Toggle hands-free off, toggle it back on, then end the call. Stop if a duplicate write appears.
H. Open Insights. Show the single saved check-in and optional low-effort ideas. Mark one optional idea complete. Hold for two seconds.
I. Open See How It Works. Tap Load 10 clearly labeled demo days once. Verify the app says the samples stay local.
J. Return to Insights and run Night Shift once. Wait for completion. Show the result, sample count, confidence, and tool trace. Stop if no pattern result or trace appears.
K. Show the Health Connect access card or privacy explanation without opening any raw health values.
L. In Insights, tap Sync Daily Timeline once.
M. CHECKPOINT 3: Verify a successful Firestore receipt and a path shaped like users/{uid}/daily_timeline/{date}. Keep any email address out of the final crop. Stop on a timeout, fallback, sign-in request, or sync error.
N. Hold the success receipt for three seconds, return to Check-In, and stop the phone recording if device control permits it.
O. Report: duration, whether all three checkpoints passed, the visible runtime name, whether exactly one live check-in was written, and the Firestore document path with the uid redacted.

In REHEARSAL mode, do every step but treat the recording as disposable. After a completely successful rehearsal, wait for me to replace MODE: REHEARSAL with MODE: FINAL TAKE. In FINAL TAKE, follow the same run exactly and make no exploratory taps.
```

## Final-take acceptance

Do not use the recording unless all of these are true:

- The first live action is continuous and readable.
- The visible runtime receipt says Google ADK Kotlin and Gemini 3.5 Flash.
- Exactly one real check-in is saved.
- The voice follow-up does not create a duplicate.
- Night Shift shows its samples, confidence, and trace.
- Firestore sync visibly succeeds.
- No personal information or secret appears.

The phone recording is the live product evidence. Add the prepared Cloud Console clip, architecture image, captions, and narration during editing according to `DEMO_SCRIPT.md`.
