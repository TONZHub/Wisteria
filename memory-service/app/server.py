"""Application entrypoint including the memory and voice bridge routes."""

from .main import app
from .tts import router as tts_router

app.include_router(tts_router)
