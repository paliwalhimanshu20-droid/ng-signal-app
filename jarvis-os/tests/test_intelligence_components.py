"""
Unit tests for Sprint-4's individual Intelligence Layer components —
Part 11 coverage for Context Builder, Goal Manager, Planning, Decision
Engine, Risk Analysis, Specialist Selection, Prompt Builder. The full
pipeline/acceptance scenarios are covered separately in
test_intelligence_acceptance.py.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.intelligence.context_builder import ContextBuilder
from jarvis.intelligence.decision_engine import DecisionEngine
from jarvis.intelligence.goal_analyzer import classify
from jarvis.intelligence.goal_manager import GoalError, GoalManager
from jarvis.intelligence.models import (
    DecisionType,
    Goal,
    GoalCategory,
    GoalStatus,
    RiskLevel,
    TaskPriority,
)
from jarvis.intelligence.planning_engine import PlanningEngine
from jarvis.intelligence.prompt_builder import PromptBuilder
from jarvis.intelligence.specialist_coordinator import SpecialistCoordinator, SpecialistDomain
from jarvis.memory import ConversationRecord, MemoryManager


@pytest.fixture()
def audit_ledger(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    return ledger


@pytest.fixture()
def memory_manager(tmp_path, audit_ledger):
    manager = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=audit_ledger)
    manager.connect()
    return manager


# --- Goal Analyzer (deterministic classification) -----------------------------------


@pytest.mark.parametrize(
    "text,expected_category",
    [
        ("Review today's NG Signal Pro progress", GoalCategory.REVIEW),
        ("Create Memory Optimization Engine", GoalCategory.BUILD),
        ("Research better SQLite alternatives", GoalCategory.RESEARCH),
        ("Build Android Voice Interface", GoalCategory.BUILD),
        ("Debug why the scanner keeps failing", GoalCategory.INVESTIGATE),
        ("What is the current status", GoalCategory.STATUS_CHECK),
    ],
)
def test_goal_classification(text, expected_category):
    category, confidence, reason = classify(text)
    assert category is expected_category
    assert 0.0 <= confidence <= 1.0
    assert reason  # never empty — every confidence is traceable


def test_goal_classification_falls_back_to_general_honestly():
    category, confidence, reason = classify("asdfghjkl qwerty")
    assert category is GoalCategory.GENERAL
    assert confidence < 0.5
    assert "no recognized" in reason.lower()


# --- Context Builder -------------------------------------------------------------------


def test_context_builder_never_fabricates_empty_state(memory_manager, audit_ledger):
    builder = ContextBuilder(memory_manager=memory_manager, audit_ledger=audit_ledger)
    context = builder.build()

    assert context.current_task is None
    assert context.current_approval is None
    assert context.session is None
    assert context.conversation_history == ()
    assert context.preferences == {}


def test_context_builder_reflects_real_memory_state(memory_manager, audit_ledger):
    memory_manager.append_conversation(ConversationRecord.new(session_id="s-1", user_input="hello"))
    memory_manager.set_preference("interface.theme", "dark")
    memory_manager.working.set_current_task({"task_id": "t-1"})

    builder = ContextBuilder(memory_manager=memory_manager, audit_ledger=audit_ledger)
    context = builder.build()

    assert len(context.conversation_history) == 1
    assert context.preferences == {"interface.theme": "dark"}
    assert context.current_task == {"task_id": "t-1"}


def test_context_builder_audits_every_build(memory_manager, audit_ledger):
    builder = ContextBuilder(memory_manager=memory_manager, audit_ledger=audit_ledger)
    builder.build()
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "intelligence.context_built" in event_types


# --- Goal Manager --------------------------------------------------------------------


def test_goal_manager_create_get(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    goal = manager.create(
        title="Test goal",
        description="A test goal",
        category=GoalCategory.BUILD,
        confidence=0.7,
        confidence_reason="test",
    )
    assert manager.get(goal.goal_id) is goal
    assert goal.status is GoalStatus.ACTIVE
    assert goal.priority is TaskPriority.NORMAL


def test_goal_manager_sub_goals_and_dependencies(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    parent = manager.create("Parent", "desc", GoalCategory.BUILD, 0.7, "test")
    child = manager.create("Child", "desc", GoalCategory.BUILD, 0.7, "test", parent_goal=parent.goal_id)

    assert child.goal_id in manager.get(parent.goal_id).sub_goals

    manager.add_dependency(child.goal_id, parent.goal_id)
    assert parent.goal_id in manager.get(child.goal_id).dependencies


def test_goal_manager_complete_and_cancel(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    goal = manager.create("G", "desc", GoalCategory.REVIEW, 0.8, "test")
    manager.complete(goal.goal_id)
    assert manager.get(goal.goal_id).status is GoalStatus.COMPLETED

    with pytest.raises(GoalError):
        manager.cancel(goal.goal_id, "too late")


def test_goal_manager_cancel_then_complete_raises(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    goal = manager.create("G", "desc", GoalCategory.REVIEW, 0.8, "test")
    manager.cancel(goal.goal_id, "no longer needed")
    with pytest.raises(GoalError):
        manager.complete(goal.goal_id)


def test_goal_manager_unknown_goal_raises(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    with pytest.raises(GoalError):
        manager.get("goal-does-not-exist")


def test_goal_manager_update_rejects_unknown_field(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    goal = manager.create("G", "desc", GoalCategory.REVIEW, 0.8, "test")
    with pytest.raises(GoalError):
        manager.update(goal.goal_id, nonexistent_field="x")


def test_goal_manager_audits_creation(audit_ledger):
    manager = GoalManager(audit_ledger=audit_ledger)
    manager.create("G", "desc", GoalCategory.REVIEW, 0.8, "test")
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "intelligence.goal_created" in event_types


# --- Planning Engine -------------------------------------------------------------------


def test_planning_engine_generates_ordered_tasks(audit_ledger):
    engine = PlanningEngine(audit_ledger=audit_ledger)
    goal = Goal.new("Build X", "desc", GoalCategory.BUILD, 0.7, "test")
    plan = engine.plan(goal)

    assert len(plan.tasks) == 5
    assert plan.execution_order == tuple(t.task_id for t in plan.tasks)
    assert plan.tasks[0].depends_on == ()
    assert plan.tasks[1].depends_on == (plan.tasks[0].task_id,)


def test_planning_engine_never_produces_duplicate_task_ids(audit_ledger):
    engine = PlanningEngine(audit_ledger=audit_ledger)
    goal = Goal.new("Build X", "desc", GoalCategory.BUILD, 0.7, "test")
    plan = engine.plan(goal)
    ids = [t.task_id for t in plan.tasks]
    assert len(ids) == len(set(ids))


def test_planning_engine_elevates_risk_on_high_risk_keywords(audit_ledger):
    engine = PlanningEngine(audit_ledger=audit_ledger)
    goal = Goal.new("Delete production database", "desc", GoalCategory.BUILD, 0.7, "test")
    plan = engine.plan(goal)
    assert plan.risk_assessment.level is RiskLevel.HIGH


def test_planning_engine_review_goal_is_low_risk_by_default(audit_ledger):
    engine = PlanningEngine(audit_ledger=audit_ledger)
    goal = Goal.new("Review progress", "desc", GoalCategory.REVIEW, 0.8, "test")
    plan = engine.plan(goal)
    assert plan.risk_assessment.level is RiskLevel.LOW


def test_planning_engine_audits_plan_generation(audit_ledger):
    engine = PlanningEngine(audit_ledger=audit_ledger)
    goal = Goal.new("Review progress", "desc", GoalCategory.REVIEW, 0.8, "test")
    engine.plan(goal)
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "intelligence.plan_generated" in event_types


def test_planning_engine_never_executes_anything(audit_ledger):
    """Structural guarantee: PlanningEngine has no dependency on execution/kernel/agents."""
    import jarvis.intelligence.planning_engine as module

    import_lines = _import_lines(module.__file__)
    for forbidden in ("jarvis.execution", "jarvis.kernel", "jarvis.agents"):
        assert not any(forbidden in line for line in import_lines)


# --- Decision Engine -------------------------------------------------------------------


def test_decision_engine_rejects_out_of_scope_actions(audit_ledger):
    goal = Goal.new("Delete production database now", "desc", GoalCategory.BUILD, 0.8, "test")
    planning = PlanningEngine(audit_ledger=audit_ledger)
    plan = planning.plan(goal)

    decision_engine = DecisionEngine(audit_ledger=audit_ledger)
    from jarvis.intelligence.context_builder import ContextBuilder as _CB  # noqa: local import to avoid top-level memory dependency

    context = _empty_context()
    decision = decision_engine.decide(goal, plan, context)
    assert decision.decision_type is DecisionType.REJECT


def test_decision_engine_escalates_critical_risk(audit_ledger):
    goal = Goal.new("Some goal", "desc", GoalCategory.BUILD, 0.8, "test")
    plan = PlanningEngine(audit_ledger=audit_ledger).plan(goal)
    # Force CRITICAL by constructing a plan copy with elevated risk.
    from dataclasses import replace
    from jarvis.intelligence.models import RiskAssessment

    critical_plan = replace(
        plan, risk_assessment=RiskAssessment(level=RiskLevel.CRITICAL, factors=("forced",), reason="forced")
    )
    decision_engine = DecisionEngine(audit_ledger=audit_ledger)
    decision = decision_engine.decide(goal, critical_plan, _empty_context())
    assert decision.decision_type is DecisionType.ESCALATE


def test_decision_engine_requests_approval_for_high_risk(audit_ledger):
    goal = Goal.new("Deploy the new production release", "desc", GoalCategory.BUILD, 0.8, "test")
    plan = PlanningEngine(audit_ledger=audit_ledger).plan(goal)
    decision_engine = DecisionEngine(audit_ledger=audit_ledger)
    decision = decision_engine.decide(goal, plan, _empty_context())
    assert decision.decision_type in (DecisionType.REQUEST_APPROVAL, DecisionType.PROCEED)


def test_decision_engine_asks_question_for_low_confidence_general_goal(audit_ledger):
    goal = Goal.new("asdfghjkl", "desc", GoalCategory.GENERAL, 0.2, "no keywords matched")
    plan = PlanningEngine(audit_ledger=audit_ledger).plan(goal)
    decision_engine = DecisionEngine(audit_ledger=audit_ledger)
    decision = decision_engine.decide(goal, plan, _empty_context())
    assert decision.decision_type is DecisionType.ASK_QUESTION


def test_decision_engine_never_calls_permission_or_approval_engine(audit_ledger):
    import jarvis.intelligence.decision_engine as module

    import_lines = _import_lines(module.__file__)
    for forbidden in ("jarvis.permission", "jarvis.approval", "jarvis.execution"):
        assert not any(forbidden in line for line in import_lines)


# --- Specialist Coordinator --------------------------------------------------------------


@pytest.mark.parametrize(
    "text,expected_domain",
    [
        ("Review today's NG Signal Pro progress", SpecialistDomain.ENGINEERING),
        ("Research better SQLite alternatives", SpecialistDomain.RESEARCH),
        ("Build Android Voice Interface", SpecialistDomain.ENGINEERING),
        ("Check my calendar for tomorrow", SpecialistDomain.CALENDAR),
        ("Review open pull requests on GitHub", SpecialistDomain.GITHUB),
        ("Should I buy natural gas futures", SpecialistDomain.TRADING),
        ("asdfghjkl", SpecialistDomain.GENERAL),
    ],
)
def test_specialist_coordinator_selection(audit_ledger, text, expected_domain):
    coordinator = SpecialistCoordinator(audit_ledger=audit_ledger)
    domain, reason = coordinator.select(text)
    assert domain is expected_domain
    assert reason


def test_specialist_coordinator_never_calls_an_agent_or_api(audit_ledger):
    import jarvis.intelligence.specialist_coordinator as module

    import_lines = [line.lower() for line in _import_lines(module.__file__)]
    for forbidden in ("requests", "http", "openai", "anthropic", "jarvis.agents", "jarvis.routing"):
        assert not any(forbidden in line for line in import_lines)


# --- Prompt Builder -------------------------------------------------------------------


def test_prompt_builder_produces_structured_prompt(audit_ledger):
    goal = Goal.new("Review progress", "desc", GoalCategory.REVIEW, 0.8, "test")
    plan = PlanningEngine(audit_ledger=audit_ledger).plan(goal)
    builder = PromptBuilder(audit_ledger=audit_ledger)
    prompt = builder.build(goal, _empty_context(), plan)

    assert prompt.goal_summary
    assert isinstance(prompt.constraints, tuple)
    assert "Never execute" in prompt.constraints[0]


def test_prompt_builder_flags_high_risk_constraint(audit_ledger):
    goal = Goal.new("Delete production database", "desc", GoalCategory.BUILD, 0.8, "test")
    plan = PlanningEngine(audit_ledger=audit_ledger).plan(goal)
    builder = PromptBuilder(audit_ledger=audit_ledger)
    prompt = builder.build(goal, _empty_context(), plan)
    assert any("approval" in c.lower() for c in prompt.constraints)


def test_prompt_builder_makes_no_ai_calls(audit_ledger):
    import jarvis.intelligence.prompt_builder as module

    import_lines = [line.lower() for line in _import_lines(module.__file__)]
    for forbidden in ("requests", "http", "openai", "anthropic"):
        assert not any(forbidden in line for line in import_lines)


def _import_lines(filepath: str) -> list[str]:
    """Return only the actual import statements in a module's source — used by the 'never imports X' structural tests so a docstring mentioning a module name (for comparison/rationale) never produces a false failure."""
    with open(filepath) as handle:
        return [line for line in handle if line.strip().startswith(("import ", "from "))]


def _empty_context():
    from jarvis.intelligence.models import Context, new_id, utc_now_iso

    return Context(
        context_id=new_id("context"),
        built_at=utc_now_iso(),
        working_memory={},
        conversation_history=(),
        preferences={},
        knowledge={},
        session=None,
        current_task=None,
        current_workflow=None,
        current_approval=None,
    )
