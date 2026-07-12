"""
jarvis.intelligence

Sprint-4: the Intelligence Layer. Public surface is deliberately narrow —
IntelligenceEngine plus the read-only data types other layers need to
consume its output. Every other name in this package is an internal
collaborator of IntelligenceEngine and must never be imported or
constructed outside jarvis.intelligence (Part 1: "IntelligenceEngine is
the ONLY entry point for intelligent reasoning").
"""

from __future__ import annotations

from jarvis.intelligence.health import IntelligenceHealthReport, run_intelligence_health_check
from jarvis.intelligence.intelligence_engine import IntelligenceEngine
from jarvis.intelligence.models import (
    Context,
    Decision,
    DecisionType,
    Goal,
    GoalCategory,
    GoalStatus,
    Plan,
    PlanComplexity,
    PlannedTask,
    Recommendation,
    RiskAssessment,
    RiskLevel,
    SpecialistDomain,
    StructuredPrompt,
)

__all__ = [
    "Context",
    "Decision",
    "DecisionType",
    "Goal",
    "GoalCategory",
    "GoalStatus",
    "IntelligenceEngine",
    "IntelligenceHealthReport",
    "Plan",
    "PlanComplexity",
    "PlannedTask",
    "Recommendation",
    "RiskAssessment",
    "RiskLevel",
    "SpecialistDomain",
    "StructuredPrompt",
    "run_intelligence_health_check",
]
