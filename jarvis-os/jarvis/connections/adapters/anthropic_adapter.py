"""
jarvis.connections.adapters.anthropic_adapter

Sprint-6 Part 8 — Anthropic Adapter.

Real integration against Anthropic's Messages API
(`POST /v1/messages`) — real endpoint, real request shape, real response
parsing. Same honesty notes as jarvis.connections.adapters.openai_adapter
apply here: `recommended_action` and `confidence` are both derived
structurally from `stop_reason`, never from parsing the response prose,
since Anthropic's API (like OpenAI's) reports no categorical action or
confidence score of its own.

Anthropic has no lightweight "list models" endpoint used purely for a
connectivity check the way OpenAI's adapter uses GET /v1/models; this
adapter's connect() instead sends a minimal (max_tokens=1) real message,
which is the smallest real, billable-but-negligible call that genuinely
verifies the key authenticates against the real Messages endpoint.
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

API_BASE_URL = "https://api.anthropic.com/v1"
ANTHROPIC_VERSION = "2023-06-01"
DEFAULT_MODEL = "claude-3-5-haiku-latest"

_STOP_REASON_COMPLETENESS = {
    "end_turn": 1.0,
    "stop_sequence": 1.0,
    "max_tokens": 0.6,
}

_CAPABILITIES: tuple[Capability, ...] = (
    Capability.ARCHITECTURE,
    Capability.IMPLEMENTATION,
    Capability.REVIEW,
    Capability.PLANNING,
    Capability.RESEARCH,
    Capability.DOCUMENTATION,
)


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class AnthropicAdapter(ProviderAdapter):
    provider_id = "provider-anthropic"
    provider_name = "Anthropic"

    def __init__(self, model: str = DEFAULT_MODEL, transport=default_http_transport) -> None:
        self._model = model
        self._transport = transport
        self._credentials: Optional[ConnectionCredentials] = None
        self._last_health = AdapterHealthStatus.UNHEALTHY

    def connect(self, credentials: ConnectionCredentials) -> None:
        spec = HTTPRequestSpec(
            method="POST",
            url=f"{API_BASE_URL}/messages",
            headers=self._headers(credentials),
            body=json.dumps(
                {"model": self._model, "max_tokens": 1, "messages": [{"role": "user", "content": "ping"}]}
            ).encode("utf-8"),
        )
        response = self._transport(spec)
        if response.status_code >= 400:
            raise ProviderAdapterError(
                f"Anthropic connect() failed: HTTP {response.status_code}. "
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
            method="POST",
            url=f"{API_BASE_URL}/messages",
            headers=self._headers(self._credentials),
            body=json.dumps(
                {"model": self._model, "max_tokens": 1, "messages": [{"role": "user", "content": "ping"}]}
            ).encode("utf-8"),
        )
        try:
            response = self._transport(spec)
        except ProviderAdapterError:
            self._last_health = AdapterHealthStatus.UNHEALTHY
            return self._last_health

        if response.status_code < 400:
            self._last_health = AdapterHealthStatus.HEALTHY
        elif response.status_code in (429, 529):
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
            raise ProviderAdapterError("AnthropicAdapter.send_prompt() called before connect().")

        payload = {
            "model": self._model,
            "max_tokens": 1024,
            "messages": [{"role": "user", "content": request.prompt_summary}],
        }
        if request.constraints:
            payload["system"] = "Constraints: " + " | ".join(request.constraints)

        spec = HTTPRequestSpec(
            method="POST",
            url=f"{API_BASE_URL}/messages",
            headers=self._headers(self._credentials, json_body=True),
            body=json.dumps(payload).encode("utf-8"),
        )
        response = self._transport(spec)
        if response.status_code >= 400:
            raise ProviderAdapterError(
                f"Anthropic send_prompt() failed: HTTP {response.status_code}: "
                f"{response.body.decode('utf-8', errors='replace')[:500]}"
            )

        try:
            data = json.loads(response.body)
            content = data["content"][0]["text"]
            stop_reason = data.get("stop_reason", "end_turn")
        except (json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
            raise ProviderAdapterError(f"Anthropic response was not in the expected shape: {exc}") from exc

        completeness = _STOP_REASON_COMPLETENESS.get(stop_reason, 0.5)
        confidence = completeness  # see module docstring's honesty note

        return ProviderResponse(
            response_id=_new_id("resp"),
            provider_id=self.provider_id,
            request_id=request.request_id,
            recommended_action=_recommended_action_for(stop_reason),
            content=content,
            confidence=confidence,
            completeness=completeness,
            structured_fields={
                "model": self._model,
                "stop_reason": stop_reason,
                "confidence_basis": "structural (stop_reason only) — not a provider-reported certainty score",
            },
            created_at=_utc_now_iso(),
        )

    def _headers(self, credentials: ConnectionCredentials, json_body: bool = True) -> dict[str, str]:
        headers = {
            "x-api-key": credentials.api_key,
            "anthropic-version": ANTHROPIC_VERSION,
        }
        if json_body:
            headers["Content-Type"] = "application/json"
        return headers


def _recommended_action_for(stop_reason: str) -> str:
    """Deterministic, structural-only mapping — see module docstring's honesty note."""
    if stop_reason in ("end_turn", "stop_sequence"):
        return "proceed"
    return "retry"
