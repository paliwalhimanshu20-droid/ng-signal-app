"""
jarvis.health

Health Monitoring for JARVIS Core.

Design reference: JARVIS-001 §22 (three health checks: Core self-health,
pipeline integrity, agent health), JARVIS-004 §36 (operational cadence
for these checks).

Sprint-0 scope: implements Core self-health (are the Bootstrap-critical
subsystems present and connected) as real, working code. Pipeline
integrity is delegated to jarvis.orchestrator.Orchestrator.health_check()
(structural-only in Sprint-0). Agent health is not implemented — it
requires Observability data (JARVIS-001 §21) and calibration tracking
(JARVIS-002 §30), both out of scope.
"""

from jarvis.health.health_check import CoreHealthReport, run_core_health_check

__all__ = ["CoreHealthReport", "run_core_health_check"]
