"""
jarvis.config

Configuration Framework per JARVIS-001 §17.

Three distinct, deliberately SEPARATE configuration classes:

  1. OPERATIONAL  — timeouts, retry counts, log verbosity. Freely
     changeable. Tier 0 governance.
  2. STRUCTURAL   — agent registry entries, domain hierarchy, tier
     thresholds. Tier 2 governance, reviewed and audited.
  3. CONSTITUTIONAL REFERENCE — which Constitution version this Core
     instance targets. Changeable only through Article VII's amendment
     process. Never stored alongside classes 1 or 2.

This separation is not a convenience grouping — it exists specifically so
a routine operational-config deployment can never accidentally (or
maliciously) alter which Constitution is in effect. See JARVIS-001 §17.
"""

from jarvis.config.settings import (
    ConstitutionalReferenceConfig,
    JarvisSettings,
    OperationalConfig,
    StructuralConfig,
    load_settings,
)

__all__ = [
    "ConstitutionalReferenceConfig",
    "JarvisSettings",
    "OperationalConfig",
    "StructuralConfig",
    "load_settings",
]
