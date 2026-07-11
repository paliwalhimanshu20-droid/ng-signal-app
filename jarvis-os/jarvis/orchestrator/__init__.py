"""
jarvis.orchestrator

The Orchestration Layer skeleton.

Design reference: JARVIS-001 §4 (System Overview — five internally
distinct Orchestration components: Intent Processor, Task Planner,
Workflow Engine, Agent Router, Context Manager), JARVIS-001 §9
(Orchestration Engine — the request lifecycle tying them together).

Sprint-0 scope: this package provides the five components as separate,
individually-named CLASSES with correct constructor signatures and clear
docstrings — matching JARVIS-001 §4's explicit warning against collapsing
them into one monolith "for convenience." None of the five implement real
behavior yet: no intent understanding, no task decomposition, no
approval-gate calls, no agent routing decisions, no context assembly.
Implementing any of those requires subsystems (Permission Engine,
Approval Engine, Evidence Framework) this sprint explicitly excludes.

What Sprint-0 DOES implement for real: the Orchestrator class that holds
all five components together and exposes a `health_check()` method,
because JARVIS-001 §22's "pipeline integrity" health check needs
something real to check the presence and wiring of, even before the
pipeline itself does anything.
"""

from jarvis.orchestrator.agent_router import AgentRouter
from jarvis.orchestrator.context_manager import ContextManager
from jarvis.orchestrator.intent_processor import IntentProcessor
from jarvis.orchestrator.orchestrator import Orchestrator
from jarvis.orchestrator.task_planner import TaskPlanner
from jarvis.orchestrator.workflow_engine import WorkflowEngine

__all__ = [
    "AgentRouter",
    "ContextManager",
    "IntentProcessor",
    "Orchestrator",
    "TaskPlanner",
    "WorkflowEngine",
]
