"""
jarvis.orchestrator.task_planner

Design reference: JARVIS-001 §11 (Task Planning).

Per §11, a real Task Planner decomposes a StructuredIntent into a task
graph where every node carries a tier classification, and the graph's
overall tier is the MAXIMUM tier of any node (never averaged, never
silently dominated by a majority of low-tier nodes). Sprint-0 implements
the shape and the max-tier rollup rule as real, tested logic — the actual
decomposition (turning intent into task nodes) is not implemented, since
that requires domain knowledge about real agents this sprint doesn't have.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import IntEnum


class Tier(IntEnum):
    """
    The four approval tiers, per JARVIS-001 §11.

    IntEnum specifically so that `max()` over a set of tiers is a correct,
    trivial way to implement the graph-level rollup rule — this is a
    deliberate implementation choice in service of that rule, not an
    incidental one.
    """

    TIER_0_INFORMATIONAL = 0
    TIER_1_REVERSIBLE_LOW_STAKES = 1
    TIER_2_CONSEQUENTIAL_REVERSIBLE = 2
    TIER_3_IRREVERSIBLE_OR_HIGH_STAKES = 3


@dataclass(frozen=True)
class TaskNode:
    """A single node in a task graph. Sprint-0 shape only — no execution semantics yet."""

    node_id: str
    description: str
    tier: Tier
    depends_on: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class TaskGraph:
    """
    A task graph: a set of TaskNodes plus their overall rolled-up tier.

    `overall_tier` MUST equal max(node.tier for node in nodes) — enforced
    in `TaskPlanner.plan()` below, per JARVIS-001 §11's explicit rejection
    of any weaker rollup rule ("a nine-step workflow that is entirely Tier
    0 except for one Tier 3 step is a Tier 3 workflow").
    """

    nodes: tuple[TaskNode, ...]
    overall_tier: Tier


class TaskPlanner:
    """
    Decomposes a StructuredIntent into a TaskGraph.

    Sprint-0 implementation note: `plan()` always returns an empty task
    graph (Tier 0 by definition, since there are no nodes to roll up from
    real decomposition logic). This is a structural placeholder — real
    decomposition requires knowing what agents exist and what they can do,
    which depends on a populated Agent Registry with real, active agents,
    out of scope for this sprint.
    """

    def plan(self, intent_description: str) -> TaskGraph:
        nodes: tuple[TaskNode, ...] = ()
        overall_tier = self._rollup_tier(nodes)
        return TaskGraph(nodes=nodes, overall_tier=overall_tier)

    @staticmethod
    def _rollup_tier(nodes: tuple[TaskNode, ...]) -> Tier:
        """
        Compute a TaskGraph's overall tier as the MAX of its nodes' tiers.

        Per JARVIS-001 §11, this is the one piece of Task Planning logic
        that Sprint-0 implements for real and tests directly (see
        tests/test_task_planner.py) — it's small, self-contained, and
        exactly the kind of rule that's easy to get subtly wrong (e.g.
        by averaging instead of maxing) if it's left unimplemented until
        a later sprint under time pressure.
        """
        if not nodes:
            return Tier.TIER_0_INFORMATIONAL
        return max(node.tier for node in nodes)
