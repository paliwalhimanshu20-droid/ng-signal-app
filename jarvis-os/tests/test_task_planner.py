"""
Tests for jarvis.orchestrator.task_planner.

Covers JARVIS-001 §11's explicit rollup rule: a TaskGraph's overall tier
is the MAXIMUM of its nodes' tiers, never an average, never dominated by
however many low-tier nodes exist alongside a single high-tier one.
"""

from __future__ import annotations

from jarvis.orchestrator.task_planner import TaskNode, TaskPlanner, Tier


def test_empty_graph_is_tier_0():
    planner = TaskPlanner()
    graph = planner.plan("anything")
    assert graph.overall_tier == Tier.TIER_0_INFORMATIONAL
    assert graph.nodes == ()


def test_rollup_uses_max_not_average():
    """
    Direct test of JARVIS-001 §11's own stated example: eight Tier 0 nodes
    and one Tier 3 node must roll up to Tier 3, not something in between.
    """
    nodes = tuple(
        TaskNode(node_id=f"n{i}", description="low stakes", tier=Tier.TIER_0_INFORMATIONAL)
        for i in range(8)
    ) + (TaskNode(node_id="n8", description="high stakes", tier=Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES),)

    overall = TaskPlanner._rollup_tier(nodes)
    assert overall == Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES


def test_rollup_with_uniform_tier():
    nodes = (
        TaskNode(node_id="n0", description="a", tier=Tier.TIER_1_REVERSIBLE_LOW_STAKES),
        TaskNode(node_id="n1", description="b", tier=Tier.TIER_1_REVERSIBLE_LOW_STAKES),
    )
    assert TaskPlanner._rollup_tier(nodes) == Tier.TIER_1_REVERSIBLE_LOW_STAKES
