"""
jarvis.execution

Sprint-1B: the Workflow Engine that drives a READY_FOR_ROUTING Task
through routing and execution to COMPLETED or FAILED, auditing every
transition.

This is a NEW package, distinct from Sprint-0's
jarvis.orchestrator.workflow_engine.WorkflowEngine, which remains exactly
as Sprint-0 left it (its WorkflowState/suspend-resume model addresses a
different concern — approval-gate suspension per JARVIS-001 §12 — not yet
relevant since the Approval Engine doesn't exist). This sprint's Workflow
Engine drives the TASK lifecycle (jarvis.intake.models.TaskStatus)
through its newly added ROUTING/EXECUTING/COMPLETED/FAILED states.
"""

from jarvis.execution.workflow import TaskExecutionWorkflow, WorkflowError

__all__ = ["TaskExecutionWorkflow", "WorkflowError"]
