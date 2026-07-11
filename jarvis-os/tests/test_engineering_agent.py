"""
Tests for jarvis.agents.engineering_agent.EngineeringAgent: successful
execution, unsupported-task handling, and — SPRINT-1C/1D — refusal to
execute without governance clearance.
"""

from __future__ import annotations

import pytest

from jarvis.agents.engineering_agent import EngineeringAgent, GovernanceViolationError
from jarvis.agents.models import ExecutionStatus
from jarvis.audit import AuditLedger
from jarvis.intake import IntentProcessor, TaskPlanner


@pytest.fixture()
def pipeline(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    ledger.connect()
    return IntentProcessor(audit_ledger=ledger), TaskPlanner(audit_ledger=ledger)


def _cleared(task):
    """Test helper: mark a task as governance-cleared, exactly as only
    TaskExecutionWorkflow is meant to do in production."""
    task.metadata["governance_cleared"] = True
    return task


def test_direct_execution_without_clearance_raises(pipeline):
    intent_processor, task_planner = pipeline
    task = task_planner.plan(intent_processor.process("Analyze GitHub repository"))

    agent = EngineeringAgent()
    with pytest.raises(GovernanceViolationError):
        agent.execute(task)  # no governance_cleared marker set — must fail


def test_successful_execution_on_matching_task_when_cleared(pipeline):
    intent_processor, task_planner = pipeline
    task = _cleared(task_planner.plan(intent_processor.process("Analyze GitHub repository")))

    agent = EngineeringAgent()
    result = agent.execute(task)

    assert result.status is ExecutionStatus.SUCCESS
    assert result.message == "Engineering Agent placeholder executed successfully."
    assert result.executed_by == agent.agent_id
    assert "capability matched" in result.evidence
    assert "placeholder execution" in result.evidence
    assert "no external integrations used" in result.evidence
    assert result.errors == ()


def test_unsupported_task_returns_failed_result_when_cleared(pipeline):
    intent_processor, task_planner = pipeline
    task = _cleared(task_planner.plan(intent_processor.process("research recent market signals")))

    agent = EngineeringAgent()
    result = agent.execute(task)

    assert result.status is ExecutionStatus.FAILED
    assert "capability_mismatch" in result.errors


def test_unsupported_task_without_clearance_raises_governance_error_first(pipeline):
    """
    Governance clearance is checked BEFORE capability matching — an
    uncleared, unsupported task must still raise GovernanceViolationError,
    not a capability-mismatch FAILED result, since the bypass attempt
    itself is the more serious violation.
    """
    intent_processor, task_planner = pipeline
    task = task_planner.plan(intent_processor.process("research recent market signals"))

    agent = EngineeringAgent()
    with pytest.raises(GovernanceViolationError):
        agent.execute(task)


def test_capabilities_metadata_version_reporting():
    agent = EngineeringAgent()
    assert agent.capabilities() == ("engineering", "repository-analysis", "code-review")
    assert agent.version() == "0.1.0"
    meta = agent.metadata()
    assert meta["agent_id"] == agent.agent_id
    assert meta["display_name"] == "Engineering Agent"


def test_health_default_is_healthy():
    agent = EngineeringAgent()
    status = agent.health()
    assert status.healthy is True
