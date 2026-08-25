"""Authenticated, least-privilege bridge from Wisteria to Vertex AI Memory Bank."""

from __future__ import annotations

import hashlib
import hmac
import os
import re
from functools import lru_cache
from typing import Annotated, Any

import firebase_admin
from fastapi import Depends, FastAPI, Header, HTTPException, status
from firebase_admin import app_check, auth
from google.api_core.exceptions import AlreadyExists, NotFound
from google.cloud import aiplatform_v1beta1
from pydantic import BaseModel, Field, field_validator

ALLOWED_CATEGORIES = {
    "CONVERSATION_CONTEXT",
    "CONVERSATION_PREFERENCE",
    "CONVERSATION_ROUTINE",
    "CONVERSATION_SUPPORT",
}
REJECTED_TERMS = {
    "password", "passcode", "pin number", "social security", "credit card",
    "api key", "access token", "private key", "recovery code",
    "ignore previous", "ignore all", "system prompt", "developer message",
    "jailbreak", "do anything now", "reveal your prompt",
}
EMAIL = re.compile(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.IGNORECASE)
PHONE = re.compile(r"(?<!\d)(?:\+?1[-. ]?)?\(?\d{3}\)?[-. ]?\d{3}[-. ]?\d{4}(?!\d)")


class MemoryInput(BaseModel):
    fact: str = Field(min_length=12, max_length=240)
    category: str

    @field_validator("fact")
    @classmethod
    def safe_fact(cls, value: str) -> str:
        value = " ".join(value.split())
        lower = value.lower()
        if any(term in lower for term in REJECTED_TERMS):
            raise ValueError("This note cannot be saved")
        if EMAIL.search(value) or PHONE.search(value):
            raise ValueError("Contact details cannot be saved")
        return value

    @field_validator("category")
    @classmethod
    def known_category(cls, value: str) -> str:
        if value not in ALLOWED_CATEGORIES:
            raise ValueError("Unknown memory category")
        return value


class Caller(BaseModel):
    uid: str


class Settings(BaseModel):
    parent: str
    location: str
    scope_hmac_secret: str

    @classmethod
    def from_environment(cls) -> "Settings":
        parent = os.environ.get("MEMORY_BANK_PARENT", "").rstrip("/")
        location = os.environ.get("MEMORY_BANK_LOCATION", "us-central1")
        secret = os.environ.get("MEMORY_SCOPE_HMAC_SECRET", "")
        if "/reasoningEngines/" not in parent:
            raise RuntimeError("MEMORY_BANK_PARENT must be a reasoning engine resource name")
        if len(secret.encode()) < 32:
            raise RuntimeError("MEMORY_SCOPE_HMAC_SECRET must contain at least 32 bytes")
        return cls(parent=parent, location=location, scope_hmac_secret=secret)


@lru_cache
def settings() -> Settings:
    return Settings.from_environment()


@lru_cache
def memory_client() -> aiplatform_v1beta1.MemoryBankServiceClient:
    endpoint = f"{settings().location}-aiplatform.googleapis.com"
    return aiplatform_v1beta1.MemoryBankServiceClient(
        client_options={"api_endpoint": endpoint}
    )


def firebase_app() -> firebase_admin.App:
    try:
        return firebase_admin.get_app()
    except ValueError:
        return firebase_admin.initialize_app()


def require_caller(
    authorization: Annotated[str | None, Header()] = None,
    x_firebase_appcheck: Annotated[str | None, Header()] = None,
) -> Caller:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing Firebase ID token")
    if not x_firebase_appcheck:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing App Check token")
    firebase_app()
    try:
        checked_app = app_check.verify_token(x_firebase_appcheck)
        checked_user = auth.verify_id_token(authorization.removeprefix("Bearer ").strip())
    except Exception as exc:  # Firebase deliberately does not expose token details.
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid app or user token") from exc
    uid = checked_user.get("uid") or checked_user.get("sub")
    if not uid or not checked_app:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid app or user token")
    return Caller(uid=uid)


def user_scope(caller: Caller) -> dict[str, str]:
    digest = hmac.new(
        settings().scope_hmac_secret.encode(), caller.uid.encode(), hashlib.sha256
    ).hexdigest()
    return {"app_name": "wisteria", "user_id": digest}


def memory_id(memory: MemoryInput) -> str:
    digest = hashlib.sha256(f"{memory.category}:{memory.fact}".encode()).hexdigest()
    return f"w-{digest[:40]}"


def memory_name(memory: MemoryInput) -> str:
    return f"{settings().parent}/memories/{memory_id(memory)}"


app = FastAPI(title="Wisteria Memory Bridge", docs_url=None, redoc_url=None)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/memories")
def remember(
    item: MemoryInput,
    caller: Annotated[Caller, Depends(require_caller)],
) -> dict[str, str | bool]:
    memory = aiplatform_v1beta1.Memory(fact=item.fact, scope=user_scope(caller))
    try:
        operation = memory_client().create_memory(
            parent=settings().parent,
            memory=memory,
            memory_id=memory_id(item),
        )
        operation.result(timeout=20)
        existed = False
    except AlreadyExists:
        existed = True
    return {"id": memory_id(item), "remembered": True, "alreadyExisted": existed}


@app.post("/v1/memories:search")
def search(
    caller: Annotated[Caller, Depends(require_caller)],
) -> dict[str, list[dict[str, str]]]:
    request = aiplatform_v1beta1.RetrieveMemoriesRequest(
        parent=settings().parent,
        scope=user_scope(caller),
        similarity_search_params=aiplatform_v1beta1.SimilaritySearchParams(
            # Never forward the current chat turn. The bank is intentionally
            # bounded, so a fixed query is enough to restore its user context.
            search_query="recent context preferences routines and support"
        ),
    )
    response = memory_client().retrieve_memories(request=request)
    rows: list[dict[str, str]] = []
    for retrieved in response.retrieved_memories:
        memory: Any = getattr(retrieved, "memory", retrieved)
        fact = str(getattr(memory, "fact", "")).strip()
        if not fact:
            continue
        name = str(getattr(memory, "name", ""))
        rows.append({"id": name.rsplit("/", 1)[-1], "fact": fact[:240]})
    return {"memories": rows[:12]}


@app.post("/v1/memories:forgetAll")
def forget_all(
    caller: Annotated[Caller, Depends(require_caller)],
) -> dict[str, int | bool]:
    deleted = 0
    expected_scope = user_scope(caller)
    # Retrieval is scope-bound. Repeating handles server-side result limits
    # without ever listing or exposing another user's memories.
    for _ in range(12):
        request = aiplatform_v1beta1.RetrieveMemoriesRequest(
            parent=settings().parent,
            scope=expected_scope,
            similarity_search_params=aiplatform_v1beta1.SimilaritySearchParams(
                search_query="recent context preferences routines and support"
            ),
        )
        response = memory_client().retrieve_memories(request=request)
        batch = []
        for retrieved in response.retrieved_memories:
            memory: Any = getattr(retrieved, "memory", retrieved)
            if dict(getattr(memory, "scope", {})) != expected_scope:
                continue
            name = str(getattr(memory, "name", ""))
            if name:
                batch.append(name)
        if not batch:
            break
        for name in batch:
            memory_client().delete_memory(name=name).result(timeout=20)
            deleted += 1
    return {"forgotten": True, "deleted": deleted}


@app.post("/v1/memories:forget")
def forget(
    item: MemoryInput,
    caller: Annotated[Caller, Depends(require_caller)],
) -> dict[str, bool]:
    name = memory_name(item)
    try:
        existing = memory_client().get_memory(name=name)
    except NotFound:
        return {"forgotten": True}
    if dict(existing.scope) != user_scope(caller):
        # Do not reveal whether a memory exists in another scope.
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Memory not found")
    memory_client().delete_memory(name=name).result(timeout=20)
    return {"forgotten": True}
