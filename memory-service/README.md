# Wisteria Backend Bridge

This Cloud Run service is the only component allowed to call Vertex AI Memory Bank or Inworld TTS. The Android app sends a Firebase ID token and Firebase App Check token with each request. The service verifies both before accessing either paid backend.

For memory, the bridge creates a pseudonymous per-user scope with HMAC-SHA256, re-applies Wisteria's memory allowlist, and stores only the bounded fact—not the chat transcript. Recall uses a fixed server-side query, so the current chat turn also stays on-device.

For speech, the bridge sends only the agent response text to Inworld, returns synthesized WAV audio to Android, and keeps the Inworld credential out of the APK. Android device TTS remains available as a fallback if the provider cannot be reached.

## Required configuration

- `MEMORY_BANK_PARENT`: `projects/PROJECT/locations/LOCATION/reasoningEngines/ENGINE_ID`
- `MEMORY_BANK_LOCATION`: for example, `us-central1`
- `MEMORY_SCOPE_HMAC_SECRET`: at least 32 random bytes, supplied from Secret Manager
- `INWORLD_API_KEY`: Inworld Basic-auth API credential, supplied from Secret Manager
- `INWORLD_VOICE_ID`: the Wisteria voice ID

Give the Cloud Run service account `roles/aiplatform.memoryUser` on the reasoning engine. It does not need project Owner, Editor, or service-account-key permissions. Do not make application routes unauthenticated at the app layer; Firebase tokens are still verified on every request.

Build and deploy from this directory, then set `MEMORY_SERVICE_URL` to the HTTPS Cloud Run URL when building Wisteria. If it is absent, local memory continues to work and voice falls back to Android TTS.

```bash
gcloud run deploy wisteria-memory \
  --source . \
  --region us-central1 \
  --service-account wisteria-memory@PROJECT_ID.iam.gserviceaccount.com \
  --set-env-vars MEMORY_BANK_PARENT=projects/PROJECT_ID/locations/us-central1/reasoningEngines/ENGINE_ID,MEMORY_BANK_LOCATION=us-central1,INWORLD_VOICE_ID=YOUR_WISTERIA_VOICE_ID \
  --set-secrets MEMORY_SCOPE_HMAC_SECRET=wisteria-memory-scope-hmac:latest,INWORLD_API_KEY=wisteria-inworld-api-key:latest \
  --allow-unauthenticated
```

`--allow-unauthenticated` permits the Android HTTP request to reach the service; it does not bypass application authentication. The bridge rejects calls unless both a valid Firebase ID token and App Check token are present.

After deployment, verify the public service boundary with `GET /ready`. Cloud Run reserves/intercepts `/healthz` on some frontends, so Wisteria deliberately avoids that path.
