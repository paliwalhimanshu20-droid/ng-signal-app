"""
jarvis.ai_coordination.model_registry

Sprint-5 Part 3 — Model Registry.

Do NOT register real APIs. This registry holds ProviderMetadata only
(see models.ProviderMetadata's docstring for the structural guarantee
that this is impossible to misuse as a live client). The three example
providers pre-registered below (OpenAI, Anthropic, Google) are exactly
that — examples, matching Part 3's own "Example providers" list — plus
one deliberately-named "Future Provider" entry demonstrating that a
not-yet-real provider is a completely ordinary registry entry, not a
special case.

Registered fresh per ModelRegistry instance (not a module-level
singleton) — mirrors jarvis.registry.AgentRegistry's own Sprint-0
precedent of per-instance state, so tests never leak registrations
across each other.
"""

from __future__ import annotations

from jarvis.ai_coordination.models import Capability, ProviderAvailability, ProviderHealth, ProviderMetadata


class ModelRegistryError(Exception):
    """Raised for any invalid Model Registry operation (duplicate provider_id, unknown provider_id)."""


def _seed_providers() -> tuple[ProviderMetadata, ...]:
    """
    Illustrative metadata only — priorities, costs, and latencies below
    are placeholder relative values for demonstrating deterministic
    selection (Part 4), not real figures for any actual service.
    """
    return (
        ProviderMetadata(
            provider_id="provider-openai-example",
            provider_name="OpenAI",
            supported_capabilities=(
                Capability.ARCHITECTURE,
                Capability.IMPLEMENTATION,
                Capability.DEBUGGING,
                Capability.TESTING,
                Capability.DOCUMENTATION,
            ),
            priority=1,
            estimated_cost=3.0,
            estimated_latency_ms=1200,
            availability=ProviderAvailability.AVAILABLE,
            version="example-1.0",
            health=ProviderHealth.HEALTHY,
        ),
        ProviderMetadata(
            provider_id="provider-anthropic-example",
            provider_name="Anthropic",
            supported_capabilities=(
                Capability.ARCHITECTURE,
                Capability.IMPLEMENTATION,
                Capability.REVIEW,
                Capability.PLANNING,
                Capability.RESEARCH,
                Capability.DOCUMENTATION,
            ),
            priority=1,
            estimated_cost=3.5,
            estimated_latency_ms=1100,
            availability=ProviderAvailability.AVAILABLE,
            version="example-1.0",
            health=ProviderHealth.HEALTHY,
        ),
        ProviderMetadata(
            provider_id="provider-google-example",
            provider_name="Google",
            supported_capabilities=(
                Capability.RESEARCH,
                Capability.TRANSLATION,
                Capability.OPTIMIZATION,
                Capability.TESTING,
            ),
            priority=2,
            estimated_cost=2.5,
            estimated_latency_ms=900,
            availability=ProviderAvailability.AVAILABLE,
            version="example-1.0",
            health=ProviderHealth.HEALTHY,
        ),
        ProviderMetadata(
            provider_id="provider-future-example",
            provider_name="Future Provider",
            supported_capabilities=tuple(Capability),
            priority=3,
            estimated_cost=1.0,
            estimated_latency_ms=500,
            availability=ProviderAvailability.UNAVAILABLE,
            version="example-0.0-unreleased",
            health=ProviderHealth.UNHEALTHY,
        ),
    )


class ModelRegistry:
    def __init__(self, seed_examples: bool = True) -> None:
        self._providers: dict[str, ProviderMetadata] = {}
        if seed_examples:
            for provider in _seed_providers():
                self.register(provider)

    def register(self, provider: ProviderMetadata) -> None:
        if provider.provider_id in self._providers:
            raise ModelRegistryError(f"Provider '{provider.provider_id}' is already registered.")
        self._providers[provider.provider_id] = provider

    def update(self, provider: ProviderMetadata) -> None:
        """Replace an existing provider's metadata wholesale (e.g. a health/availability refresh) — never a partial mutation, keeping ProviderMetadata frozen and each update an explicit, auditable replacement."""
        if provider.provider_id not in self._providers:
            raise ModelRegistryError(f"Cannot update unknown provider '{provider.provider_id}'.")
        self._providers[provider.provider_id] = provider

    def get(self, provider_id: str) -> ProviderMetadata:
        try:
            return self._providers[provider_id]
        except KeyError as exc:
            raise ModelRegistryError(f"No provider registered with id '{provider_id}'.") from exc

    def list_all(self) -> tuple[ProviderMetadata, ...]:
        return tuple(self._providers.values())

    def find_by_capability(self, capability: Capability) -> tuple[ProviderMetadata, ...]:
        return tuple(p for p in self._providers.values() if capability in p.supported_capabilities)

    def is_healthy(self) -> bool:
        return isinstance(self._providers, dict)

    def __len__(self) -> int:
        return len(self._providers)
