"""
jarvis.intelligence.intelligence_engine

Sprint-4 Part 1 — Intelligence Engine.

IntelligenceEngine is the ONLY entry point for intelligent reasoning —
every other component in this package (ContextBuilder, GoalManager,
PlanningEngine, DecisionEngine, SpecialistCoordinator, PromptBuilder,
ReasoningPipeline) is an internal collaborator, constructed and owned
here, never imported or constructed directly by anything outside
jarvis.intelligence. This mirrors jarvis.memory.MemoryManager's own
"sole entry point" rule from Sprint-3, applied to this layer.

`analyze()` is the single public operation: given raw owner text, run
the full Reasoning Pipeline and return a Recommendation. It never
executes anything — no agent call, no kernel call, no workflow call
appears anywhere in this module or anything it constructs.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intelligence.context_builder import ContextBuilder
from jarvis.intelligence.decision_engine import DecisionEngine
from jarvis.intelligence.goal_manager import GoalManager
from jarvis.intelligence.health import IntelligenceHealthReport, run_intelligence_health_check
from jarvis.intelligence.models import Recommendation
from jarvis.intelligence.planning_engine import PlanningEngine
from jarvis.intelligence.prompt_builder import PromptBuilder
from jarvis.intelligence.reasoning_pipeline import ReasoningPipeline
from jarvis.intelligence.specialist_coordinator import SpecialistCoordinator
from jarvis.memory import MemoryManager

__all__ = ["IntelligenceEngine"]


class IntelligenceEngine:
    def __init__(self, memory_manager: MemoryManager, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

        self.context_builder = ContextBuilder(memory_manager=memory_manager, audit_ledger=audit_ledger)
        self.goal_manager = GoalManager(audit_ledger=audit_ledger)
        self.planning_engine = PlanningEngine(audit_ledger=audit_ledger)
        self.decision_engine = DecisionEngine(audit_ledger=audit_ledger)
        self.specialist_coordinator = SpecialistCoordinator(audit_ledger=audit_ledger)
        self.prompt_builder = PromptBuilder(audit_ledger=audit_ledger)
        self.reasoning_pipeline = ReasoningPipeline(
            context_builder=self.context_builder,
            goal_manager=self.goal_manager,
            planning_engine=self.planning_engine,
            decision_engine=self.decision_engine,
            specialist_coordinator=self.specialist_coordinator,
            prompt_builder=self.prompt_builder,
            audit_ledger=audit_ledger,
        )

    def analyze(self, raw_input: str) -> Recommendation:
        """Run the full Reasoning Pipeline (Part 8) and return a Recommendation. Never executes; never calls a kernel, agent, or workflow."""
        return self.reasoning_pipeline.run(raw_input)

    def health_check(self) -> IntelligenceHealthReport:
        return run_intelligence_health_check(self)
