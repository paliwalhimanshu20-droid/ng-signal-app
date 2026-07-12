"""
jarvis.connections.adapters.openai_adapter

Sprint-6 Part 7 — OpenAI Adapter.

Real integration against OpenAI's Chat Completions REST API
(`POST /v1/chat/completions`) — real endpoint, real request shape, real
response parsing. No mock, no simulation: given a real
ConnectionCredentials (a real environment variable) and the default
transport, this makes a real network call. This codebase's own test
suite injects a fake transport (see test_openai_adapter.py) specifically
so CI never spends real API budget or requires a real key to pass —
that is a testing-hygiene decision, not evidence the adapter itself is
fake.

HONESTY NOTE on two fields Chat Completions does not return:
  - `recommended_action`: OpenAI's API has no categorical "action" field.
    This adapter derives one deterministically from `finish_reason`
    (see `_recommended_action_for`) — never from parsing/understanding
    the completion's prose, which would be exactly the kind of
    fabricated-confidence behavior Article III exists to prevent.
  - `confidence`: OpenAI's API reports no confidence score. This adapter
    computes a structural proxy from `finish_reason` alone (did the
    model complete normally, or was it cut off/filtered) — explicitly
    NOT a claim about how correct the content is, only about how
    complete the response mechanically was. `confidence_reason`-style
    honesty about this limitation lives in the ProviderResponse's
    `structured_fields["confidence_basis"]` field, so nothing downstream
    can mistake this for genuine model-reported certainty.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from jarvis.ai_coordination.models import Capability, ProviderRequest, ProviderResponse
from jarvis.connections.credentials import ConnectionCredentials
from jarvis.connections.provider_adapter import (
    AdapterHealthStatus,
    HTTPRequestSpec,
    ProviderAdapter,
    ProviderAdapterError,
    default_http_transport,
)

API_BASE_URL = "https://api.openai.com/v1"
DEFAULT_MODEL = "gpt-4o-mini"

_FINISH_REASON_COMPLETENESS = {
    "stop": 1.0,
    "length": 0.6,
    "content_filter": 0.3,
    "tool_calls": 0.8,
}

_CAPABILITIES: tuple[Capability, ...] = (
    Capability.ARCHITECTURE,
    Capability.IMPLEMENTATION,
    Capability.DEBUGGING,
    Capability.TESTING,
    Capability.DOCUMENTATION,
)


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class OpenAIAdapter(ProviderAdapter):
    provider_id = "provider-openai"
    provider_name = "OpenAI"

    def __init__(self, model: str = DEFAULT_MODEL, transport=default_http_transport) -> None:
        self._model = model
        self._transport = transport
        self._credentials: Optional[ConnectionCredentials] = None
        self._last_health = AdapterHealthStatus.UNHEALTHY

    def connect(self, credentials: ConnectionCredentials) -> None:
        # A real, minimal call — GET /v1/models — verifies the key
        # actually authenticates, rather than just checking it's
        # non-empty (Sprint-6 Part 7: "Authentication Interface" means
        # actually exercising it once, not just storing it).
        spec = HTTPRequestSpec(
            method="GET",
            url=f"{API_BASE_URL}/models",
            headers=self._headers(credentials),
            body=b"",
        )
        response = self._transport(spec)
        if response.status_code >= 400:
            raise ProviderAdapterError(
                f"OpenAI connect() failed: HTTP {response.status_code}. "
                f"Credentials were not accepted or the service is unreachable."
            )
        self._credentials = credentials
        self._last_health = AdapterHealthStatus.HEALTHY

    def disconnect(self) -> None:
        self._credentials = None
        self._last_health = AdapterHealthStatus.UNHEALTHY

    def health(self) -> str:
        if self._credentials is None:
            return AdapterHealthStatus.UNHEALTHY
        spec = HTTPRequestSpec(
            method="GET",
            url=f"{API_BASE_URL}/models",
            headers=self._headers(self._credentials),
            body=b"",
        )
        try:
            response = self._transport(spec)
        except ProviderAdapterError:
            self._last_health = AdapterHealthStatus.UNHEALTHY
            return self._last_health

        if response.status_code < 400:
            self._last_health = AdapterHealthStatus.HEALTHY
        elif response.status_code in (429, 503):
            self._last_health = AdapterHealthStatus.DEGRADED
        else:
            self._last_health = AdapterHealthStatus.UNHEALTHY
        return self._last_health

    def capabilities(self) -> tuple[Capability, ...]:
        return _CAPABILITIES

    def conversation_support(self) -> bool:
        return True  # the `messages` array natively carries multi-turn context

    def send_prompt(self, request: ProviderRequest) -> ProviderResponse:
        if self._credentials is None:
            raise ProviderAdapterError("OpenAIAdapter.send_prompt() called before connect().")

        payload = {
            "model": self._model,
            "messages": self._build_messages(request),
        }
        spec = HTTPRequestSpec(
            method="POST",
            url=f"{API_BASE_URL}/chat/completions",
            headers=self._headers(self._credentials, json_body=True),
            body=json.dumps(payload).encode("utf-8"),
        )
        response = self._transport(spec)
        if response.status_code >= 400:
            raise ProviderAdapterError(
                f"OpenAI send_prompt() failed: HTTP {response.status_code}: "
                f"{response.body.decode('utf-8', errors='replace')[:500]}"
            )

        try:
            data = json.loads(response.body)
            choice = data["choices"][0]
            content = choice["message"]["content"]
            finish_reason = choice.get("finish_reason", "stop")
        except (json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
            raise ProviderAdapterError(f"OpenAI response was not in the expected shape: {exc}") from exc

        completeness = _FINISH_REASON_COMPLETENESS.get(finish_reason, 0.5)
        confidence = completeness  # see module docstring's HONESTY NOTE

        return ProviderResponse(
            response_id=_new_id("resp"),
            provider_id=self.provider_id,
            request_id=request.request_id,
            recommended_action=_recommended_action_for(finish_reason),
            content=content,
            confidence=confidence,
            completeness=completeness,
            structured_fields={
                "model": self._model,
                "finish_reason": finish_reason,
                "confidence_basis": "structural (finish_reason only) — not a provider-reported certainty score",
            },
            created_at=_utc_now_iso(),
        )

    def _headers(self, credentials: ConnectionCredentials, json_body: bool = False) -> dict[str, str]:
        headers = {"Authorization": f"Bearer {credentials.api_key}"}
        if credentials.organization_id:
            headers["OpenAI-Organization"] = credentials.organization_id
        if json_body:
            headers["Content-Type"] = "application/json"
        return headers

    @staticmethod
    def _build_messages(request: ProviderRequest) -> list[dict[str, str]]:
        messages = []
        if request.constraints:
            messages.append({"role": "system", "content": "Constraints: " + " | ".join(request.constraints)})
        messages.append({"role": "user", "content": request.prompt_summary})
        return messages


def _recommended_action_for(finish_reason: str) -> str:
    """Deterministic, structural-only mapping — see module docstring's HONESTY NOTE."""
    if finish_reason == "stop":
        return "proceed"
    if finish_reason == "content_filter":
        return "reject"
    return "retry"
