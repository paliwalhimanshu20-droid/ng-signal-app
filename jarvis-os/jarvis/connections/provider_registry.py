"""
jarvis.connections.provider_registry

Sprint-6 Part 9 — Future Provider Framework.

A plugin registry mapping provider_id -> a zero-argument factory
producing a fresh ProviderAdapter instance. Adding a new provider (Google,
a local LLM, Azure, OpenRouter, or anything else) requires exactly one
call to `register()` — no change to ConnectionManager, AICoordinator,
Memory, or Intelligence, per Part 9's explicit requirement. This registry
IS that requirement's proof: it is the only place provider_id strings are
mapped to concrete adapter classes anywhere in this codebase.
"""

from __future__ import annotations

from typing import Callable, Optional

from jarvis.connections.adapters.anthropic_adapter import AnthropicAdapter
from jarvis.connections.adapters.openai_adapter import OpenAIAdapter
from jarvis.connections.provider_adapter import ProviderAdapter

AdapterFactory = Callable[[], ProviderAdapter]


class ProviderRegistryError(Exception):
    """Raised for any invalid Provider Registry operation (duplicate provider_id, unknown provider_id)."""


class ProviderRegistry:
    def __init__(self, seed_defaults: bool = True) -> None:
        self._factories: dict[str, AdapterFactory] = {}
        if seed_defaults:
            self.register("provider-openai", OpenAIAdapter)
            self.register("provider-anthropic", AnthropicAdapter)

    def register(self, provider_id: str, factory: AdapterFactory) -> None:
        if provider_id in self._factories:
            raise ProviderRegistryError(f"Provider adapter '{provider_id}' is already registered.")
        self._factories[provider_id] = factory

    def create(self, provider_id: str) -> ProviderAdapter:
        try:
            factory = self._factories[provider_id]
        except KeyError as exc:
            raise ProviderRegistryError(f"No adapter registered for provider '{provider_id}'.") from exc
        return factory()

    def is_registered(self, provider_id: str) -> bool:
        return provider_id in self._factories

    def list_provider_ids(self) -> tuple[str, ...]:
        return tuple(self._factories.keys())

    def is_healthy(self) -> bool:
        return isinstance(self._factories, dict) and len(self._factories) > 0
