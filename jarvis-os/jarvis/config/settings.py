"""
jarvis.config.settings

Concrete configuration loading for JARVIS Core.

Design reference: JARVIS-001 §17 (Configuration Framework), JARVIS-004 §13
(Configuration Management).

Sprint-0 scope: configuration is loaded from environment variables with
documented defaults, and from a single structural-config file for registry
/ tier-threshold data. No remote config service, no hot-reload, no
business logic — this is intentionally the simplest implementation that
still honors the three-class separation, which is the property that
actually matters architecturally.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class OperationalConfig:
    """
    Class 1 — freely changeable, Tier 0 governance.

    Sprint-0 fields are intentionally minimal: only what the Foundation
    Bootstrap itself needs (log verbosity, health-check behavior). Future
    sprints add fields here as real subsystems are implemented — this
    class is expected to grow; classes 2 and 3 are expected to stay small
    and change rarely, by design (JARVIS-001 §17).
    """

    log_level: str = "INFO"
    health_check_timeout_seconds: float = 5.0

    @classmethod
    def from_env(cls) -> "OperationalConfig":
        return cls(
            log_level=os.environ.get("JARVIS_LOG_LEVEL", "INFO").upper(),
            health_check_timeout_seconds=float(
                os.environ.get("JARVIS_HEALTH_CHECK_TIMEOUT_SECONDS", "5.0")
            ),
        )


@dataclass(frozen=True)
class StructuralConfig:
    """
    Class 2 — Tier 2 governance: reviewed and audited before changing.

    Sprint-0 scope: holds only the data path to the (currently empty)
    Agent Registry's persisted state. No registry entries, no domain
    hierarchy, no tier thresholds are defined yet — those arrive with the
    agents that need them, per JARVIS-003 §21's Future Agent Expansion
    Framework. Defining them speculatively now would be exactly the kind
    of premature commitment JARVIS-002 §34 warns against.
    """

    registry_state_path: Path = field(default_factory=lambda: Path("data/registry_state.json"))

    # SPRINT-3 ADDITION: where the Memory Foundation's PersistenceLayer
    # stores Session/Conversation/Preference/Knowledge snapshots. Kept in
    # StructuralConfig (Tier 2, reviewed) rather than Operational — like
    # registry_state_path, this is "where persistent state lives," not a
    # freely-tunable runtime knob.
    memory_storage_path: Path = field(default_factory=lambda: Path("data/memory"))

    @classmethod
    def from_env(cls) -> "StructuralConfig":
        raw_path = os.environ.get("JARVIS_REGISTRY_STATE_PATH", "data/registry_state.json")
        raw_memory_path = os.environ.get("JARVIS_MEMORY_STORAGE_PATH", "data/memory")
        return cls(registry_state_path=Path(raw_path), memory_storage_path=Path(raw_memory_path))


@dataclass(frozen=True)
class ConstitutionalReferenceConfig:
    """
    Class 3 — changeable ONLY through Article VII's amendment process.

    Deliberately isolated in its own class, its own file, and its own
    environment variable namespace — never merged with operational or
    structural config, so that no routine configuration deployment can
    ever accidentally change which Constitution is in effect
    (JARVIS-001 §17).
    """

    constitution_path: Path = field(default_factory=lambda: Path("data/constitution.json"))

    @classmethod
    def from_env(cls) -> "ConstitutionalReferenceConfig":
        raw_path = os.environ.get("JARVIS_CONSTITUTION_PATH", "data/constitution.json")
        return cls(constitution_path=Path(raw_path))


@dataclass(frozen=True)
class JarvisSettings:
    """
    Top-level settings object combining all three configuration classes.

    Consumers should always go through the named sub-config (settings.operational,
    settings.structural, settings.constitutional_reference) rather than
    treating this as a flat namespace — the separation is the point, and
    collapsing it at the call site would defeat the purpose just as surely
    as collapsing it in storage would.
    """

    operational: OperationalConfig
    structural: StructuralConfig
    constitutional_reference: ConstitutionalReferenceConfig


def load_settings() -> JarvisSettings:
    """
    Load all three configuration classes from their independent sources.

    This is the only function other modules should call to obtain
    configuration — it exists so there is exactly one place that assembles
    the three classes together, keeping the assembly itself auditable.
    """
    return JarvisSettings(
        operational=OperationalConfig.from_env(),
        structural=StructuralConfig.from_env(),
        constitutional_reference=ConstitutionalReferenceConfig.from_env(),
    )
