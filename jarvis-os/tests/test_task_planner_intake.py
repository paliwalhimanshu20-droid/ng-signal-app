"""
Tests for jarvis.intake.task_planner.

Covers the required Sprint-1A scenarios: valid Intent, invalid Intent,
tier calculation, priority calculation, task creation, execution plan
generation, and audit reference creation.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.intake.intent_processor import IntentProcessor
from jarvis.intake.models import Intent, IntentType, TaskPriority, TaskStatus
from jarvis.intake.task_planner import TaskPlanner, TaskPlanningError
from jarvis.orchestrator.task_planner import Tier


@pytest.fixture()
def ledger(tmp_path):
    audit_ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    audit_ledger.connect()
    return audit_ledger


@pytest.fixture()
def intent_processor(ledger):
    return IntentProcessor(audit_ledger=ledger)


@pytest.fixture()
def task_planner(ledger):
    return TaskPlanner(audit_ledger=ledger)


def test_valid_intent_produces_ready_task(intent_processor, task_planner):
    intent = intent_processor.process("Analyze GitHub repository")
    task = task_planner.plan(intent)

    assert task.status == TaskStatus.READY_FOR_ROUTING
    assert task.intent is intent
    assert task.execution_plan is not None
    assert task.audit_reference


def test_invalid_intent_wrong_type_raises(task_planner):
    with pytest.raises(TaskPlanningError):
        task_planner.plan("not an intent object")  # type: ignore[arg-type]


def test_invalid_intent_empty_raw_input_raises(task_planner):
    broken_intent = Intent.new(
        raw_input="",
        normalized_input="",
        intent_type=IntentType.UNKNOWN,
        confidence=0.0,
        confidence_reason="test",
        detected_entities={},
        is_ambiguous=True,
        requires_clarification=True,
    )
    with pytest.raises(TaskPlanningError):
        task_planner.plan(broken_intent)


def test_ambiguous_intent_holds_task_at_planning(intent_processor, task_planner):
    intent = intent_processor.process("purple elephants dancing sideways")
    assert intent.is_ambiguous is True

    task = task_planner.plan(intent)
    assert task.status == TaskStatus.PLANNING
    assert task.execution_plan is not None  # plan is still generated, just not routed


def test_unsupported_intent_reaches_ready_for_routing(intent_processor, task_planner):
    intent = intent_processor.process("Please trade Natural Gas futures")
    assert intent.intent_type == IntentType.UNSUPPORTED
    assert intent.is_ambiguous is False

    task = task_planner.plan(intent)
    assert task.status == TaskStatus.READY_FOR_ROUTING
    assert task.execution_plan.estimated_steps == 0
    assert task.execution_plan.candidate_agents == ("research", "trading")


@pytest.mark.parametrize(
    "raw_input,expected_tier",
    [
        ("Analyze GitHub repository", Tier.TIER_0_INFORMATIONAL),
        ("Draft a schedule for tomorrow", Tier.TIER_1_REVERSIBLE_LOW_STAKES),
        ("Merge this pull request and push", Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE),
        ("Deploy this to production", Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES),
    ],
)
def test_tier_calculation(intent_processor, task_planner, raw_input, expected_tier):
    intent = intent_processor.process(raw_input)
    task = task_planner.plan(intent)
    assert task.tier == expected_tier


def test_tier3_implies_approval_required(intent_processor, task_planner):
    intent = intent_processor.process("Delete the production database")
    task = task_planner.plan(intent)
    assert task.tier == Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES
    assert task.execution_plan.approval_required is True


def test_tier0_does_not_require_approval(intent_processor, task_planner):
    intent = intent_processor.process("Analyze GitHub repository")
    task = task_planner.plan(intent)
    assert task.tier == Tier.TIER_0_INFORMATIONAL
    assert task.execution_plan.approval_required is False


def test_priority_calculation_urgent_keyword(intent_processor, task_planner):
    intent = intent_processor.process("Investigate this issue immediately")
    task = task_planner.plan(intent)
    assert task.priority == TaskPriority.HIGH


def test_priority_calculation_normal_default(intent_processor, task_planner):
    intent = intent_processor.process("Analyze GitHub repository")
    task = task_planner.plan(intent)
    assert task.priority == TaskPriority.NORMAL


def test_priority_calculation_ambiguous_is_low(intent_processor, task_planner):
    intent = intent_processor.process("asdkjhaskjdh")
    task = task_planner.plan(intent)
    assert task.priority == TaskPriority.LOW


def test_task_creation_fields(intent_processor, task_planner):
    intent = intent_processor.process("Analyze GitHub repository")
    task = task_planner.plan(intent)

    assert task.task_id
    assert task.created_at
    assert task.assigned_agent is None
    assert task.parent_task is None
    assert task.child_tasks == []
    assert isinstance(task.metadata, dict)


def test_execution_plan_generation_fields(intent_processor, task_planner):
    intent = intent_processor.process("Investigate why the scanner failed")
    task = task_planner.plan(intent)
    plan = task.execution_plan

    assert plan.plan_id
    assert plan.strategy
    assert plan.estimated_steps >= 0
    assert plan.status.value == "generated"
    assert plan.approval_tier == task.tier


def test_audit_reference_created_and_traceable(intent_processor, task_planner, ledger):
    intent = intent_processor.process("Analyze GitHub repository")
    task = task_planner.plan(intent)

    entries = ledger.read_all()
    matching = [e for e in entries if e.entry_id == task.audit_reference]
    assert len(matching) == 1
    assert matching[0].event_type == "task.created"


def test_full_ledger_trail_for_one_request(intent_processor, task_planner, ledger):
    intent_processor.process("Analyze GitHub repository")
    intent = intent_processor.process("Analyze GitHub repository")
    task_planner.plan(intent)

    event_types = [e.event_type for e in ledger.read_all()]
    # Every required audit event per the Sprint-1A brief must appear at least once.
    for required_event in (
        "intent.received",
        "intent.parsed",
        "task.created",
        "execution_plan.generated",
        "task.ready_for_routing",
    ):
        assert required_event in event_types
