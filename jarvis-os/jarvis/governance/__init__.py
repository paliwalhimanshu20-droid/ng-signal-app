"""
jarvis.governance

Sprint-1C/1D: the composite "Governance Layer" — not a new engine, but
the aggregate health/wiring view across Permission Engine + Approval
Engine + the Workflow that connects them to execution, per this sprint's
explicit Health Dashboard requirement naming "Governance Layer" as its
own distinct item, separate from "Permission Engine" and "Approval
Engine" individually.
"""

from jarvis.governance.health import GovernanceLayerHealth, run_governance_health_check

__all__ = ["GovernanceLayerHealth", "run_governance_health_check"]
