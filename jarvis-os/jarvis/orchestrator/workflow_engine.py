"""
jarvis.orchestrator.workflow_engine

Design reference: JARVIS-001 §12 (Workflow Engine).

Per §12, the Workflow Engine treats an approval gate as a first-class
suspend point — a workflow awaiting Tier 2/3 approval must be fully
checkpointed, not held only in process memory, and must be able to
survive a full Core restart. Sprint-0 implements the SUSPEND/RESUME state
shape only; it does not implement actual task execution or a real
approval-gate call (the Approval Engine is out of scope).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from jarvis.orchestrator.task_planner import TaskGraph


class WorkflowState(str, Enum):
    """Sprint-0 workflow states — matches JARVIS-001 §12's suspend/resume model at the shape level."""

    PENDING = "pending"
    RUNNING = "running"
    SUSPENDED_AWAITING_APPROVAL = "suspended_awaiting_approval"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class WorkflowInstance:
    """
    A single workflow's runtime state.

    Sprint-0 note: this object is currently held only in-memory by
    WorkflowEngine — per JARVIS-001 §6/§12's statelessness requirement, a
    later sprint MUST persist this to an external store (the Memory Layer,
    per JARVIS-002 §6) before any real Tier 2/3 approval-gating is
    implemented, or the "survives a Core restart" guarantee those sections
    require will not actually hold.
    """

    workflow_id: str
    task_graph: TaskGraph
    state: WorkflowState = WorkflowState.PENDING


class WorkflowEngine:
    """
    Executes TaskGraphs, respecting approval-gate suspend points.

    Sprint-0 implementation note: `start()` creates a WorkflowInstance and
    immediately marks it COMPLETED if its task graph is empty (Sprint-0's
    TaskPlanner always produces an empty graph — see task_planner.py).
    This is the only behavior that's meaningfully "real" yet: an empty
    graph has nothing to execute and nothing to gate, so completing it
    trivially is correct, not a shortcut around real logic that doesn't
    exist yet.
    """

    def __init__(self) -> None:
        self._workflows: dict[str, WorkflowInstance] = {}

    def start(self, workflow_id: str, task_graph: TaskGraph) -> WorkflowInstance:
        instance = WorkflowInstance(workflow_id=workflow_id, task_graph=task_graph)
        if not task_graph.nodes:
            instance.state = WorkflowState.COMPLETED
        else:
            # Sprint-0 has no real execution logic. Any non-empty graph
            # (which nothing in this sprint currently produces) is left
            # PENDING rather than fabricating an execution result.
            instance.state = WorkflowState.PENDING
        self._workflows[workflow_id] = instance
        return instance

    def get(self, workflow_id: str) -> WorkflowInstance:
        return self._workflows[workflow_id]
