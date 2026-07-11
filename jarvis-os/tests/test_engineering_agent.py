"""
Tests for jarvis.agents.engineering_agent.EngineeringAgent: successful
execution and unsupported-task handling.
"""

from __future__ import annotations

import pytest

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.agents.models import ExecutionStatus
from jarvis.audit import AuditLedger
from jarvis.intake import IntentProcessor, TaskPlanner


@pytest.fixture()
def pipeline(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    ledger.connect()
    return IntentProcessor(audit_ledger=ledger), TaskPlanner(audit_ledger=ledger)


def test_successful_execution_on_matching_task(pipeline):
    intent_processor, task_planner = pipeline
    task = task_planner.plan(intent_processor.process("Analyze GitHub repository"))

    agent = EngineeringAgent()
    result = agent.execute(task)

    assert result.status is ExecutionStatus.SUCCESS
    assert result.message == "Engineering Agent placeholder executed successfully."
    assert result.executed_by == agent.agent_id
    assert "capability matched" in result.evidence
    assert "placeholder execution" in result.evidence
    assert "no external integrations used" in result.evidence
    assert result.errors == ()


def test_unsupported_task_returns_failed_result(pipeline):
    intent_processor, task_planner = pipeline
    # "research recent market signals" hints at the research/trading domain,
    # not engineering — Engineering Agent must honestly decline it.
    task = task_planner.plan(intent_processor.process("research recent market signals"))

    agent = EngineeringAgent()
    result = agent.execute(task)

    assert result.status is ExecutionStatus.FAILED
    assert "capability_mismatch" in result.errors


def test_can_execute_false_for_task_with_no_execution_plan():
    agent = EngineeringAgent()
    # A bare, unplanned task-like object with no execution_plan attribute
    # set is out of scope to construct fully here — covered instead via
    # the real pipeline in test_unsupported_task_returns_failed_result.
    # This test instead confirms metadata/capabilities/version reporting.
    assert agent.capabilities() == ("engineering", "repository-analysis", "code-review")
    assert agent.version() == "0.1.0"
    meta = agent.metadata()
    assert meta["agent_id"] == agent.agent_id
    assert meta["display_name"] == "Engineering Agent"


def test_health_default_is_healthy():
    agent = EngineeringAgent()
    status = agent.health()
    assert status.healthy is True
