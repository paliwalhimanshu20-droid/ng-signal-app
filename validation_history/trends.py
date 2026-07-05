"""
validation_history/trends.py

Pure functions over the list-of-dicts shape ValidationHistoryStore.get_recent()
/ get_since() return. Deliberately NOT methods on the store class: keeping
these as plain functions means an Admin Center panel (or a notebook, or a
future Continuous Learning Engine) can compute the exact same trends from
snapshots it already has in memory, without re-querying the DB or
depending on this package's I/O layer at all — only its (trivial) data
shape.

Every function is defensive about small/empty history: with 0 or 1
snapshots there's no "trend" to speak of, so functions return None/empty
rather than raising, letting a caller render "not enough history yet"
instead of crashing.

`snapshots` is always expected newest-first (ValidationHistoryStore's
convention) unless a function's docstring says otherwise.
"""

from __future__ import annotations

from collections import Counter


def instrument_count_growth(snapshots: list[dict]) -> list[tuple]:
    """[(recorded_at, total_items), ...] oldest-first — the natural order
    for a growth chart's x-axis."""
    ordered = list(reversed(snapshots))
    return [(s["recorded_at"], s["total_items"]) for s in ordered if s["total_items"] is not None]


def warning_trend(snapshots: list[dict]) -> list[tuple]:
    ordered = list(reversed(snapshots))
    return [(s["recorded_at"], s["warning_count"]) for s in ordered]


def failure_trend(snapshots: list[dict]) -> list[tuple]:
    ordered = list(reversed(snapshots))
    return [(s["recorded_at"], s["failure_count"]) for s in ordered]


def new_vs_updated_trend(snapshots: list[dict]) -> list[tuple]:
    """[(recorded_at, new_items, updated_items), ...] oldest-first."""
    ordered = list(reversed(snapshots))
    return [(s["recorded_at"], s["new_items"], s["updated_items"]) for s in ordered]


def success_rate(snapshots: list[dict]) -> dict | None:
    """Two numbers, deliberately not one: `pass_rate` (strictly PASS) and
    `non_fail_rate` (PASS or WARNING — i.e. "would have been committed
    under the WARNING-does-not-block philosophy"). Institutional practice
    generally cares about the second number more; the first is still
    useful for spotting a category that's technically fine but noisy."""
    if not snapshots:
        return None
    total = len(snapshots)
    passed = sum(1 for s in snapshots if s["status"] == "PASS")
    non_fail = sum(1 for s in snapshots if s["status"] in ("PASS", "WARNING"))
    return {
        "total_runs": total,
        "pass_rate": round(passed / total, 4),
        "non_fail_rate": round(non_fail / total, 4),
    }


def average_execution_seconds(snapshots: list[dict]) -> float | None:
    values = [s["execution_seconds"] for s in snapshots if s.get("execution_seconds") is not None]
    if not values:
        return None
    return round(sum(values) / len(values), 3)


def most_common_warning_categories(snapshots: list[dict], top_n: int = 5) -> list[tuple]:
    """[(rule_name, total_count_across_all_snapshots), ...] descending."""
    return _most_common_categories(snapshots, "warning_categories", top_n)


def most_common_failure_categories(snapshots: list[dict], top_n: int = 5) -> list[tuple]:
    return _most_common_categories(snapshots, "failure_categories", top_n)


def _most_common_categories(snapshots: list[dict], field: str, top_n: int) -> list[tuple]:
    tally = Counter()
    for s in snapshots:
        for rule_name, count in (s.get(field) or {}).items():
            tally[rule_name] += count
    return tally.most_common(top_n)


def quality_score(snapshot: dict) -> float:
    """A single 0-100 "data health" number for one snapshot, matching the
    Validation Center's own _STATUS_WEIGHT convention
    (validation/validation_runner.py) so a future combined dashboard can
    show Instrument Master's score on the same scale as every other
    category's health score: PASS counts fully, WARNING counts partially
    (scaled by how many items were actually flagged, not just a flat
    penalty), FAIL is 0.
    """
    if snapshot["status"] == "FAIL":
        return 0.0
    if snapshot["status"] == "SKIPPED":
        return 0.0
    if snapshot["status"] == "PASS":
        return 100.0
    # WARNING: start at 60 (matches _STATUS_WEIGHT's WARNING=0.6 in the
    # Validation Center) and scale down slightly further by how large a
    # fraction of total_items was quarantined, so "3 warnings out of
    # 126,644 instruments" scores higher than "3 warnings out of 12".
    base = 60.0
    total = snapshot.get("total_items") or 0
    quarantined = snapshot.get("quarantined_count") or 0
    if total > 0:
        penalty = min(30.0, (quarantined / total) * 100 * 3)  # capped, so it never goes below 30
        base -= penalty
    return round(base, 1)


def quality_score_evolution(snapshots: list[dict]) -> list[tuple]:
    """[(recorded_at, quality_score), ...] oldest-first — directly powers
    the "Quality Score Evolution" Admin Center view called out in the
    long-term roadmap."""
    ordered = list(reversed(snapshots))
    return [(s["recorded_at"], quality_score(s)) for s in ordered]
