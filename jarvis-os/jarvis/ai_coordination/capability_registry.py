"""
jarvis.ai_coordination.capability_registry

Sprint-5 Part 2 — Capability Registry.

Holds a short, human-readable description for each Capability value —
the registry's job is to make capabilities inspectable and self-
documenting (a health dashboard or a future prompt-building step can ask
"what does REVIEW mean here" without that knowledge being hardcoded
wherever it's needed), not to define new capabilities at runtime (that
still requires an enum addition — see models.Capability's docstring for
why that split is deliberate).
"""

from __future__ import annotations

from jarvis.ai_coordination.models import Capability

CAPABILITY_DESCRIPTIONS: dict[Capability, str] = {
    Capability.ARCHITECTURE: "Structural / system design reasoning.",
    Capability.IMPLEMENTATION: "Writing or modifying working code.",
    Capability.RESEARCH: "Comparing options and gathering evidence toward a recommendation.",
    Capability.PLANNING: "Breaking a goal into ordered, dependency-aware steps.",
    Capability.REVIEW: "Evaluating existing work against requirements or quality standards.",
    Capability.TESTING: "Designing or evaluating verification strategy.",
    Capability.DOCUMENTATION: "Producing explanatory or reference material.",
    Capability.DEBUGGING: "Diagnosing the cause of an observed defect.",
    Capability.OPTIMIZATION: "Improving performance, cost, or efficiency of existing work.",
    Capability.TRANSLATION: "Converting content between languages or representations.",
}


class CapabilityRegistry:
    def is_supported(self, capability: Capability) -> bool:
        return capability in CAPABILITY_DESCRIPTIONS

    def describe(self, capability: Capability) -> str:
        return CAPABILITY_DESCRIPTIONS[capability]

    def list_all(self) -> tuple[Capability, ...]:
        return tuple(CAPABILITY_DESCRIPTIONS.keys())

    def is_healthy(self) -> bool:
        return len(CAPABILITY_DESCRIPTIONS) == len(Capability)
