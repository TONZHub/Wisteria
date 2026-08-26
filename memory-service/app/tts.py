"""Authenticated server-side proxy for Inworld text-to-speech."""

from __future__ import annotations

import base64
import json
import os
import urllib.error
import urllib.request
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import Response
from pydantic import BaseModel, Field, field_validator

from .main import Caller, require_caller

INWORLD_TTS_URL = "https://api.inworld.ai/tts/v1/voice"


class SpeechInput(BaseModel):
    text: str = Field(min_length=1, max_length=2000)

    @field_validator("text")
    @classmethod
    def normalized_text(cls, value: str) -> str:
        value = " ".join(value.split())
        if not value:
            raise ValueError("Speech text cannot be empty")
        return value


router = APIRouter()


@router.post("/v1/tts")
def synthesize_speech(
    item: SpeechInput,
    caller: Annotated[Caller, Depends(require_caller)],
) -> Response:
    # `caller` is deliberately required even though the user id is not sent to Inworld.
    # Auth + App Check keep this paid endpoint from becoming an unauthenticated relay.
    del caller

    api_key = os.environ.get("INWORLD_API_KEY", "").strip()
    voice_id = os.environ.get("INWORLD_VOICE_ID", "").strip()
    if not api_key or not voice_id:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            "Wisteria voice is not configured",
        )

    payload = json.dumps(
        {
            "text": item.text,
            "voiceId": voice_id,
            "modelId": "inworld-tts-2",
            "audioConfig": {
                "audioEncoding": "LINEAR16",
                "sampleRateHertz": 22050,
            },
            "deliveryMode": "BALANCED",
            "applyTextNormalization": "ON",
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        INWORLD_TTS_URL,
        data=payload,
        method="POST",
        headers={
            "Authorization": f"Basic {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=50) as upstream:
            response_body = upstream.read()
    except urllib.error.HTTPError as exc:
        # Never forward provider response bodies because they may contain diagnostics
        # that are useful to the server but should not leak through the client API.
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            f"Voice provider returned HTTP {exc.code}",
        ) from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            "Voice provider is temporarily unavailable",
        ) from exc

    try:
        decoded = json.loads(response_body)
        audio_content = decoded["audioContent"]
        audio = base64.b64decode(audio_content, validate=True)
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            "Voice provider returned invalid audio",
        ) from exc

    if not audio:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY,
            "Voice provider returned empty audio",
        )

    return Response(
        content=audio,
        media_type="audio/wav",
        headers={"Cache-Control": "no-store"},
    )
