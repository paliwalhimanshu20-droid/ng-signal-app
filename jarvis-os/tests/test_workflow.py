"""
Tests for jarvis.execution.workflow.TaskExecutionWorkflow: complete
lifecycle (CREATED -> ... -> COMPLETED) and audit coverage of every
transition.
"""

from __future__ import annotations

import pytest

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.audit import AuditLedger
from jarvis.execution import TaskExecutionWorkflow, WorkflowError
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.intake.models import TaskStatus
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry
from jarvis.routing import TaskRouter


def _activate(registry: AgentRegistry, agent_id: str) -> None:
    registry.transition(agent_id, AgentLifecycleState.REVIEWED)
    registry.transition(agent_id, AgentLifecycleState.PROVISIONED)
    registry.transition(agent_id, AgentLifecycleState.ACTIVE)


@pytest.fixture()
def ledger(tmp_path):
    audit_ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    audit_ledger.connect()
    return audit_ledger


@pytest.fixture()
def registry_with_engineering_agent(ledger):
    registry = AgentRegistry()
    agent = EngineeringAgent()
    registry.register(
        AgentRecord(
            agent_id=agent.agent_id,
            domain=agent.domain,
            parent_domain=None,
            capabilities=agent.capabilities(),
            instance=agent,
        )
    )
    _activate(registry, agent.agent_id)
    return registry


def test_complete_lifecycle_reaches_completed(ledger, registry_with_engineering_agent):
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    task = planner.plan(processor.process("Analyze GitHub repository"))
    assert task.status is TaskStatus.READY_FOR_ROUTING

    router = TaskRouter(registry=registry_with_engineering_agent, audit_ledger=ledger)
    workflow = TaskExecutionWorkflow(router=router, registry=registry_with_engineering_agent, audit_ledger=ledger)

    result_task = workflow.execute(task)

    assert result_task.status is TaskStatus.COMPLETED
    assert result_task.metadata["execution_result"]["status"] == "success"
    assert result_task.metadata["routing_decision"]["status"] == "routed"


def test_lifecycle_fails_when_no_capable_agent(ledger):
    registry = AgentRegistry()  # no agents registered at all
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    task = planner.plan(processor.process("Analyze GitHub repository"))

    router = TaskRouter(registry=registry, audit_ledger=ledger)
    workflow = TaskExecutionWorkflow(router=router, registry=registry, audit_ledger=ledger)

    result_task = workflow.execute(task)

    assert result_task.status is TaskStatus.FAILED
    assert result_task.metadata["routing_decision"]["status"] == "no_capable_agent"


def test_workflow_rejects_task_not_ready_for_routing(ledger, registry_with_engineering_agent):
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    # An ambiguous input holds the task at PLANNING, per Sprint-1A.
    task = planner.plan(processor.process("purple elephants dancing sideways"))
    assert task.status is TaskStatus.PLANNING

    router = TaskRouter(registry=registry_with_engineering_agent, audit_ledger=ledger)
    workflow = TaskExecutionWorkflow(router=router, registry=registry_with_engineering_agent, audit_ledger=ledger)

    with pytest.raises(WorkflowError):
        workflow.execute(task)


def test_every_transition_is_audited(ledger, registry_with_engineering_agent):
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    task = planner.plan(processor.process("Analyze GitHub repository"))

    router = TaskRouter(registry=registry_with_engineering_agent, audit_ledger=ledger)
    workflow = TaskExecutionWorkflow(router=router, registry=registry_with_engineering_agent, audit_ledger=ledger)
    workflow.execute(task)

    event_types = [e.event_type for e in ledger.read_all()]
    required_events = (
        "task.routing_started",
        "task.candidate_search",
        "agent.selected",
        "execution.started",
        "execution.finished",
        "task.completed",
    )
    for event in required_events:
        assert event in event_types, f"Missing required audit event: {event}"


def test_failed_execution_path_is_audited(ledger):
    registry = AgentRegistry()  # no capable agent -> failure path
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    task = planner.plan(processor.process("Analyze GitHub repository"))

    router = TaskRouter(registry=registry, audit_ledger=ledger)
    workflow = TaskExecutionWorkflow(router=router, registry=registry, audit_ledger=ledger)
    workflow.execute(task)

    event_types = [e.event_type for e in ledger.read_all()]
    assert "routing.failed" in event_types
    assert "task.failed" in event_types
