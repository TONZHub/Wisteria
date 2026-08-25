# Wisteria — final submission sweep

This is the submission-freeze checklist. From this point forward, prefer documentation and submission-material fixes over product changes. Do not change authentication, signing, memory behavior, or the demo path unless a reproducible blocker appears.

## Verified in the final sweep

- [x] Android CI passes on `main` with the connected Firebase configuration.
- [x] CI verifies that the `com.zoeb.wisteria` OAuth client contains the exact SHA-1 used to sign the debug APK before building.
- [x] Google Sign-In succeeds in the CI-built APK on a real Android device.
- [x] The APK artifact is flattened to a single `Wisteria-debug.apk` file.
- [x] Memory-bridge tests pass.
- [x] `app/google-services.json`, keystores, local properties, and `.env` files are ignored by Git.
- [x] Repository search found no committed Google API key prefix, client secret, private-key marker, or password placeholder requiring removal.
- [x] Empty duplicate root `Procfile` placeholders were removed.
- [x] Devpost positioning now keeps PMDD + perimenopause as the initial audience while explaining the broader calendar-agnostic value for irregular cycles, including PCOS, without making a diagnostic claim.

## Manual submission items

- [ ] Fill the remaining `TODO` fields in `DEVPOST_SUBMISSION.md`: public APK release URL, public demo video URL, and team status.
- [ ] Choose the project license deliberately.
- [ ] Add at least two real screenshots from the connected build.
- [ ] Upload `wisteria-architecture.png` to Devpost.
- [ ] Record the demo using `DEMO_SCRIPT.md` and verify the final video while signed out.
- [ ] Show one uninterrupted live agent action plus visible Google Cloud evidence.
- [ ] Run the final connected APK on the real phone once after merging this PR.
- [ ] Confirm Google Sign-In, one check-in, conversational follow-up, Night Shift, and explicit Firestore sync.
- [ ] Create the permanent judge APK/release URL if it has not already been created.
- [ ] Submit before the hackathon deadline and then freeze the submitted repo/build/video through judging.

## Positioning guardrail

Wisteria is not a PMDD, perimenopause, or PCOS diagnostic tool. Its core promise is simpler: it remains useful when a person's cycle does not match a predictable calendar and when completing a detailed tracker is too much work. Conditions can explain who may benefit from that design; they should not be presented as conditions Wisteria identifies or treats.
