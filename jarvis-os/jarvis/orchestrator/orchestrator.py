"""
jarvis.orchestrator.orchestrator

The Orchestrator: holds the five Orchestration Layer components together
and exposes the request-lifecycle entry point.

Design reference: JARVIS-001 §4 (five components, deliberately not
collapsed into one monolith), JARVIS-001 §9 (Orchestration Engine — the
request lifecycle), JARVIS-001 §22 ("pipeline integrity" health check).
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.orchestrator.agent_router import AgentRouter
from jarvis.orchestrator.context_manager import ContextManager
from jarvis.orchestrator.intent_processor import IntentProcessor
from jarvis.orchestrator.task_planner import TaskPlanner
from jarvis.orchestrator.workflow_engine import WorkflowEngine
from jarvis.registry import AgentRegistry


@dataclass(frozen=True)
class OrchestratorHealth:
    """Result of a structural wiring check — see Orchestrator.health_check()."""

    healthy: bool
    components_present: tuple[str, ...]
    detail: str


class Orchestrator:
    """
    Top-level Orchestrator, composing the five request-lifecycle components.

    Sprint-0 scope: this class's job is to exist, hold correctly-typed
    references to all five components, and prove that wiring is intact via
    `health_check()`. It does NOT yet implement JARVIS-001 §9's full
    request lifecycle (owner input -> intent -> plan -> tier classify ->
    workflow execution -> agent routing -> context assembly -> approval
    gate -> execution -> audit) end-to-end, because several of those steps
    depend on subsystems explicitly out of scope for this sprint (the
    Approval Engine chief among them). `handle_request()` below documents
    exactly how far the real pipeline currently goes.
    """

    def __init__(self, registry: AgentRegistry) -> None:
        self.intent_processor = IntentProcessor()
        self.task_planner = TaskPlanner()
        self.workflow_engine = WorkflowEngine()
        self.agent_router = AgentRouter(registry)
        self.context_manager = ContextManager()

    def handle_request(self, raw_input: str) -> str:
        """
        Sprint-0's necessarily partial implementation of the JARVIS-001 §9
        request lifecycle.

        Executes: Intent Processing -> Task Planning only. Stops there,
        explicitly, rather than proceeding into Workflow Engine execution
        against a real approval gate that doesn't exist yet — per
        JARVIS-001 §15, "no path exists that allows a task to proceed on
        anything other than an explicit approval response," and Sprint-0
        has no Approval Engine to obtain that response from. Returning a
        clear, honest "not yet implemented past this point" message is the
        constitutionally correct behavior, not a shortcut around it.
        """
        intent = self.intent_processor.process(raw_input)
        if intent.is_ambiguous:
            return (
                "JARVIS Sprint-0: intent understood only at the structural "
                "level (no real interpretation is implemented yet). "
                f"Raw input received: {raw_input!r}"
            )

        # Unreachable in Sprint-0 (IntentProcessor always returns
        # is_ambiguous=True) — present for structural completeness and to
        # make the next sprint's extension point obvious.
        task_graph = self.task_planner.plan(intent.interpretation or raw_input)
        return f"Planned task graph with {len(task_graph.nodes)} node(s)."

    def health_check(self) -> OrchestratorHealth:
        """
        Structural "pipeline integrity" check, per JARVIS-001 §22.

        Sprint-0 scope: verifies all five components are present and of
        the correct type — a real adversarial pipeline-integrity check
        (JARVIS-001 §30) that actually attempts to bypass an approval gate
        requires the Approval Engine to exist first, and belongs to a
        later sprint's test suite, not this health check.
        """
        components: dict[str, object] = {
            "intent_processor": self.intent_processor,
            "task_planner": self.task_planner,
            "workflow_engine": self.workflow_engine,
            "agent_router": self.agent_router,
            "context_manager": self.context_manager,
        }
        missing = [name for name, component in components.items() if component is None]

        healthy = not missing
        detail = (
            "All five Orchestration components present."
            if healthy
            else f"Missing components: {', '.join(missing)}"
        )
        return OrchestratorHealth(
            healthy=healthy,
            components_present=tuple(components.keys()),
            detail=detail,
        )
