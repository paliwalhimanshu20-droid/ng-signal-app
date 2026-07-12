"""
jarvis.ai_coordination.model_selector

Sprint-5 Part 4 — Model Selector.

Input: Capability, Context, Risk, Priority. Output: the best available
ProviderMetadata, or None if nothing qualifies. Selection is a pure,
deterministic sort over registered providers — same input always
produces the same output, and there is no random tie-break anywhere in
this module (Engineering Requirement: "Selection must be deterministic.
Never random.").

Risk's effect on selection: at HIGH/CRITICAL risk, only providers
currently AVAILABLE are eligible — a DEGRADED provider is an acceptable
fallback for low-stakes work but not for something already flagged as
high-consequence upstream (jarvis.intelligence.decision_engine). This is
the same "risk narrows what's acceptable" pattern already established
there, applied to provider selection instead of task approval.

Priority's effect: HIGH TaskPriority breaks ties in favor of lower
estimated_latency_ms over lower estimated_cost; NORMAL/LOW priority
breaks ties in favor of lower estimated_cost over latency. Context is
accepted as a Part 4 input for future extension (e.g. a future sprint
excluding a provider the owner has disabled) but this sprint has no
context-driven exclusion rule to apply yet — its presence never changes
today's outcome and that is stated explicitly, not left implicit.
"""

from __future__ import annotations

from typing import Any, Optional

from jarvis.ai_coordination.model_registry import ModelRegistry
from jarvis.ai_coordination.models import Capability, ProviderAvailability, ProviderMetadata, RiskLevel, TaskPriority

_ELEVATED_RISK = frozenset({RiskLevel.HIGH, RiskLevel.CRITICAL})


class ModelSelector:
    def __init__(self, model_registry: ModelRegistry) -> None:
        self._registry = model_registry

    def select(
        self,
        capability: Capability,
        risk: RiskLevel,
        priority: TaskPriority,
        context: Optional[dict[str, Any]] = None,
    ) -> Optional[ProviderMetadata]:
        candidates = [p for p in self._registry.find_by_capability(capability) if self._eligible(p, risk)]
        if not candidates:
            return None

        sort_key = self._sort_key_for(priority)
        candidates.sort(key=sort_key)
        return candidates[0]

    @staticmethod
    def _eligible(provider: ProviderMetadata, risk: RiskLevel) -> bool:
        if provider.availability is ProviderAvailability.UNAVAILABLE:
            return False
        if risk in _ELEVATED_RISK and provider.availability is not ProviderAvailability.AVAILABLE:
            return False
        return True

    @staticmethod
    def _sort_key_for(priority: TaskPriority):
        if priority is TaskPriority.HIGH:
            return lambda p: (p.priority, p.estimated_latency_ms, p.estimated_cost, p.provider_id)
        return lambda p: (p.priority, p.estimated_cost, p.estimated_latency_ms, p.provider_id)

    def is_healthy(self) -> bool:
        return self._registry.is_healthy()
