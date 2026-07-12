"""
jarvis.connections.provider_adapter

Sprint-6 Part 6 — Provider Adapter Framework.

`ProviderAdapter` is the one interface every provider (OpenAI, Anthropic,
and any future provider per Part 9) must implement. "No provider-specific
logic outside adapters" is enforced by construction: every other module
in this codebase — ConnectionManager, AICoordinator, the Interface
Layer — talks only to this abstract interface, never to
`OpenAIAdapter`/`AnthropicAdapter` by name.

HTTP is real (Part 6/7/8 require genuine adapters, not stubs), but the
actual transport is injected as a constructor dependency
(`transport: HTTPTransport`) defaulting to `default_http_transport` — a
real `urllib.request`-based implementation with no third-party HTTP
dependency (matching this project's established "boring technology"
preference, JARVIS-001 §3). Tests inject a fake transport to verify
request-building and response-parsing deterministically, without
touching the network — the same dependency-injection pattern this
codebase already uses for jarvis.interface.console.ConsoleInterface's
`input_fn`/`output_fn`.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Callable

from jarvis.ai_coordination.models import Capability, ProviderRequest, ProviderResponse
from jarvis.connections.credentials import ConnectionCredentials


class ProviderAdapterError(Exception):
    """Raised for any adapter-level failure: connection failure, malformed response, or a network error surfaced from the transport."""


@dataclass(frozen=True)
class HTTPRequestSpec:
    method: str
    url: str
    headers: dict[str, str]
    body: bytes
    timeout_seconds: float = 30.0


@dataclass(frozen=True)
class HTTPResponseSpec:
    status_code: int
    headers: dict[str, str]
    body: bytes


HTTPTransport = Callable[[HTTPRequestSpec], HTTPResponseSpec]


def default_http_transport(spec: HTTPRequestSpec) -> HTTPResponseSpec:
    """
    The REAL transport — an actual HTTPS request via the standard
    library, no SDK, no third-party HTTP dependency. This function is
    exercised by construction whenever an adapter is used without an
    injected `transport` override; it is never called during this
    codebase's own test suite (tests always inject a fake transport),
    which is a testing-hygiene choice, not evidence this code is inert —
    given a real ConnectionCredentials from a real environment variable,
    this makes a real network call to the real provider endpoint.
    """
    request = urllib.request.Request(
        spec.url, data=spec.body, headers=spec.headers, method=spec.method
    )
    try:
        with urllib.request.urlopen(request, timeout=spec.timeout_seconds) as response:
            return HTTPResponseSpec(
                status_code=response.status,
                headers=dict(response.headers),
                body=response.read(),
            )
    except urllib.error.HTTPError as exc:
        return HTTPResponseSpec(status_code=exc.code, headers=dict(exc.headers or {}), body=exc.read())
    except urllib.error.URLError as exc:
        raise ProviderAdapterError(f"Network error reaching provider: {exc.reason}") from exc


class AdapterHealthStatus:
    """Namespaced string constants (not an Enum re-export) so adapters can report a status without importing jarvis.connections.models, keeping this module's only dependency on connection state ONE WAY — ConnectionManager consumes adapter health, adapters never consume Connection objects."""

    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"


class ProviderAdapter(ABC):
    """
    Part 6's provider-neutral interface. Every method below is real —
    none of them are permitted to be a no-op placeholder in a concrete
    adapter (Part 7/8's explicit requirement: "MUST implement real
    provider adapters").
    """

    provider_id: str
    provider_name: str

    @abstractmethod
    def connect(self, credentials: ConnectionCredentials) -> None:
        """Verify the given credentials actually work against the real provider (a real, minimal API call — e.g. a models-list or lightweight health endpoint). Raises ProviderAdapterError on failure. Never silently succeeds without a real check."""

    @abstractmethod
    def disconnect(self) -> None:
        """Release any held credentials/state. No network call is required for most providers (there is no server-side session to tear down for a stateless REST API), but this is the point where a future stateful provider WOULD close one."""

    @abstractmethod
    def health(self) -> str:
        """Returns one of AdapterHealthStatus's constants, from a real check against the provider."""

    @abstractmethod
    def capabilities(self) -> tuple[Capability, ...]:
        """The Capability values this provider can plausibly serve — used by jarvis.ai_coordination.model_selector, never fabricated beyond what the provider's real feature set supports."""

    @abstractmethod
    def send_prompt(self, request: ProviderRequest) -> ProviderResponse:
        """The one real, provider-specific HTTP exchange this class exists for. Raises ProviderAdapterError on any failure (network, auth, malformed response) — never returns a fabricated ProviderResponse to paper over a real failure."""

    @abstractmethod
    def conversation_support(self) -> bool:
        """Whether this provider's API supports multi-turn conversation context natively (both OpenAI and Anthropic do, via a messages array — see their adapters)."""
