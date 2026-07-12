"""
Sprint-4 Acceptance Scenarios 1-4, run against a real IntelligenceEngine
over a real (temp-directory-backed) MemoryManager — the same style
test_sprint3_acceptance.py already established for Memory.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.intelligence import DecisionType, IntelligenceEngine, SpecialistDomain
from jarvis.memory import MemoryManager


@pytest.fixture()
def engine(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    memory = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    memory.connect()
    return IntelligenceEngine(memory_manager=memory, audit_ledger=ledger), ledger


# --- Acceptance Scenario 1 --------------------------------------------------------


def test_scenario_1_review_progress(engine):
    intelligence, _ = engine
    recommendation = intelligence.analyze("Review today's NG Signal Pro progress")

    assert recommendation.context is not None
    assert recommendation.goal is not None
    assert recommendation.plan is not None
    assert recommendation.specialist is SpecialistDomain.ENGINEERING
    assert recommendation.summary


# --- Acceptance Scenario 2 --------------------------------------------------------


def test_scenario_2_create_memory_optimization_engine(engine):
    intelligence, _ = engine
    recommendation = intelligence.analyze("Create Memory Optimization Engine")

    assert len(recommendation.plan.tasks) > 1  # real task breakdown, not a single opaque step
    assert recommendation.decision.risk_level is not None
    assert recommendation.prompt is not None
    # No execution occurred: nothing about the recommendation implies a
    # live Task, agent execution, or kernel involvement.
    assert not hasattr(recommendation, "execution_result")


def test_scenario_2_never_executes(engine):
    intelligence, ledger = engine
    intelligence.analyze("Create Memory Optimization Engine")
    event_types = [e.event_type for e in ledger.read_all()]
    assert not any("execut" in et for et in event_types)


# --- Acceptance Scenario 3 --------------------------------------------------------


def test_scenario_3_research_sqlite_alternatives(engine):
    intelligence, _ = engine
    recommendation = intelligence.analyze("Research better SQLite alternatives")

    assert recommendation.specialist is SpecialistDomain.RESEARCH
    assert recommendation.goal.category.value == "research"
    assert len(recommendation.plan.tasks) > 1
    assert recommendation.decision is not None


# --- Acceptance Scenario 4 --------------------------------------------------------


def test_scenario_4_build_android_voice_interface(engine):
    intelligence, _ = engine
    recommendation = intelligence.analyze("Build Android Voice Interface")

    assert recommendation.specialist is SpecialistDomain.ENGINEERING
    assert len(recommendation.plan.tasks) > 1
    assert recommendation.plan.estimated_complexity is not None
    assert recommendation.prompt is not None


# --- Health --------------------------------------------------------------------------


def test_intelligence_health_all_green(engine):
    intelligence, _ = engine
    report = intelligence.health_check()
    assert report.healthy
    assert set(report.checks) == {
        "intelligence_engine",
        "context_builder",
        "goal_manager",
        "planning_engine",
        "decision_engine",
        "specialist_coordinator",
        "prompt_builder",
        "reasoning_pipeline",
    }
    assert all(report.checks.values())


# --- Audit: every pipeline stage produces an event (Part 10) -----------------------


def test_every_pipeline_stage_is_audited(engine):
    intelligence, ledger = engine
    intelligence.analyze("Review today's NG Signal Pro progress")

    event_types = [e.event_type for e in ledger.read_all()]
    required = (
        "intelligence.request_received",
        "intelligence.context_built",
        "intelligence.goal_created",
        "intelligence.plan_generated",
        "intelligence.decision_made",
        "intelligence.specialist_selected",
        "intelligence.prompt_generated",
        "intelligence.recommendation_returned",
    )
    for event in required:
        assert event in event_types, f"Missing intelligence audit event: {event}"


# --- Determinism: same input, same shape of output (no AI, no randomness) ----------


def test_pipeline_is_deterministic_for_goal_category_and_specialist(engine):
    intelligence, _ = engine
    rec1 = intelligence.analyze("Research better SQLite alternatives")
    rec2 = intelligence.analyze("Research better SQLite alternatives")

    assert rec1.goal.category == rec2.goal.category
    assert rec1.specialist == rec2.specialist
    assert rec1.plan.risk_assessment.level == rec2.plan.risk_assessment.level
    assert [t.title for t in rec1.plan.tasks] == [t.title for t in rec2.plan.tasks]


# --- Never executes, ever -----------------------------------------------------------


def test_intelligence_engine_module_has_no_execution_dependency():
    import jarvis.intelligence.intelligence_engine as module

    with open(module.__file__) as handle:
        import_lines = [line.lower() for line in handle if line.strip().startswith(("import ", "from "))]
    for forbidden in ("jarvis.execution", "jarvis.kernel", "jarvis.agents", "requests", "openai", "anthropic"):
        assert not any(forbidden in line for line in import_lines)
