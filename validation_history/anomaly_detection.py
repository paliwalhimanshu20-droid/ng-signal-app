"""
validation_history/anomaly_detection.py

Early Warning System: compares the LATEST snapshot for a category against
a rolling baseline built from the N prior snapshots of that same category,
and flags abnormal changes as HIGH PRIORITY AnomalyFlags.

Design decisions worth being explicit about:

  - Requires a minimum baseline size (MIN_BASELINE_SNAPSHOTS) before
    detecting anything. You cannot detect a "sudden" change against zero
    or one prior data points — running with too little history returns no
    anomalies rather than guessing, which is the honest answer.
  - Thresholds are percentage-of-baseline-average, not fixed absolute
    numbers, since "items" means something different per category
    (instruments vs. partitions vs. experiments) — a percentage-based rule
    is the one thing that transfers across every future validator without
    per-category tuning.
  - Anomalies are advisory (HIGH PRIORITY), not an additional FAIL
    condition. A real spike might be a legitimate event (e.g. a large
    exchange listing day genuinely adds thousands of new instruments) —
    the Early Warning System's job is to make sure a human notices and
    can decide, not to silently block a pipeline over a pattern that
    might be perfectly fine. Genuine integrity failures are already
    caught by the point-in-time FAIL rules (e.g. instrument_master/
    validation.py) — this layer is purely about trend context those
    rules structurally cannot see.
"""

from __future__ import annotations

from .models import AnomalyFlag

MIN_BASELINE_SNAPSHOTS = 3

INSTRUMENT_COUNT_DROP_THRESHOLD = 0.05      # flag if total_items drops >5% vs baseline average
WARNING_SPIKE_MULTIPLIER = 3.0              # flag if warning_count > 3x baseline average
FAIL_SPIKE_MULTIPLIER = 2.0                 # flag if failure_count > 2x baseline average
DEACTIVATED_SPIKE_MULTIPLIER = 3.0          # flag if deactivated_items > 3x baseline average
MALFORMED_CATEGORY_KEYS = (                 # failure/warning rule names that indicate malformed records
    "malformed_instrument_key",
    "required_fields_present",
)
MALFORMED_ABSOLUTE_FLOOR = 5                # even without history, this many malformed rows is worth flagging

# Below this baseline average, percentage-based thresholds are too noisy
# to be meaningful (e.g. going from 1 warning to 4 is "300% higher" but
# not actually interesting) — use this floor as the denominator instead.
_MIN_BASELINE_FLOOR = 3.0


def _avg(values: list) -> float:
    values = [v for v in values if v is not None]
    return sum(values) / len(values) if values else 0.0


def detect_anomalies(snapshots: list[dict]) -> list[AnomalyFlag]:
    """`snapshots` newest-first, as returned by
    ValidationHistoryStore.get_recent(category, limit=N+1) — index 0 is
    the run being checked, the rest form the baseline. Returns an empty
    list if there isn't enough history yet or nothing crosses a threshold.
    """
    if len(snapshots) < MIN_BASELINE_SNAPSHOTS + 1:
        return []

    latest = snapshots[0]
    baseline = snapshots[1:MIN_BASELINE_SNAPSHOTS + 1]
    flags: list[AnomalyFlag] = []

    # ---- Sudden drop in instrument (item) count ----
    baseline_total_avg = _avg([b["total_items"] for b in baseline])
    if latest["total_items"] is not None and baseline_total_avg > 0:
        drop_fraction = (baseline_total_avg - latest["total_items"]) / baseline_total_avg
        if drop_fraction > INSTRUMENT_COUNT_DROP_THRESHOLD:
            flags.append(AnomalyFlag(
                metric="total_items",
                message=(
                    f"Item count dropped {drop_fraction:.1%} vs the {len(baseline)}-run baseline "
                    f"average ({baseline_total_avg:.0f} -> {latest['total_items']}) — verify this "
                    f"wasn't a partial/failed download before trusting downstream research priority."
                ),
                current_value=latest["total_items"],
                baseline_value=round(baseline_total_avg, 1),
            ))

    # ---- Sudden increase in WARNING count ----
    baseline_warn_avg = max(_avg([b["warning_count"] for b in baseline]), _MIN_BASELINE_FLOOR)
    if latest["warning_count"] > baseline_warn_avg * WARNING_SPIKE_MULTIPLIER:
        flags.append(AnomalyFlag(
            metric="warning_count",
            message=(
                f"WARNING count ({latest['warning_count']}) is more than "
                f"{WARNING_SPIKE_MULTIPLIER:.0f}x the {len(baseline)}-run baseline average "
                f"({baseline_warn_avg:.1f}) — review the warning detail even though this doesn't "
                f"block the commit."
            ),
            current_value=latest["warning_count"],
            baseline_value=round(baseline_warn_avg, 1),
        ))

    # ---- Sudden increase in FAIL count ----
    baseline_fail_avg = _avg([b["failure_count"] for b in baseline])
    fail_floor = max(baseline_fail_avg, _MIN_BASELINE_FLOOR)
    if latest["failure_count"] > 0 and baseline_fail_avg == 0.0:
        flags.append(AnomalyFlag(
            metric="failure_count",
            message=(
                f"FAIL count is {latest['failure_count']} after {len(baseline)} consecutive clean "
                f"runs (baseline average 0) — this category has never failed recently; treat this "
                f"run's failures as a priority regression, not routine noise."
            ),
            current_value=latest["failure_count"],
            baseline_value=0,
        ))
    elif latest["failure_count"] > fail_floor * FAIL_SPIKE_MULTIPLIER:
        flags.append(AnomalyFlag(
            metric="failure_count",
            message=(
                f"FAIL count ({latest['failure_count']}) is more than "
                f"{FAIL_SPIKE_MULTIPLIER:.0f}x the {len(baseline)}-run baseline average "
                f"({baseline_fail_avg:.1f})."
            ),
            current_value=latest["failure_count"],
            baseline_value=round(baseline_fail_avg, 1),
        ))

    # ---- Large number of deactivated instruments ----
    baseline_deact_avg = max(_avg([b["deactivated_items"] for b in baseline]), _MIN_BASELINE_FLOOR)
    if (latest["deactivated_items"] or 0) > baseline_deact_avg * DEACTIVATED_SPIKE_MULTIPLIER:
        flags.append(AnomalyFlag(
            metric="deactivated_items",
            message=(
                f"Deactivated count ({latest['deactivated_items']}) is more than "
                f"{DEACTIVATED_SPIKE_MULTIPLIER:.0f}x the {len(baseline)}-run baseline average "
                f"({baseline_deact_avg:.1f}) — a mass deactivation can mean the source feed had a "
                f"partial outage that looked like 'these no longer exist' rather than a genuine "
                f"delisting wave."
            ),
            current_value=latest["deactivated_items"],
            baseline_value=round(baseline_deact_avg, 1),
        ))

    # ---- Large number of malformed records ----
    latest_malformed = sum(
        (latest.get("failure_categories") or {}).get(k, 0) for k in MALFORMED_CATEGORY_KEYS
    )
    baseline_malformed_avg = _avg([
        sum((b.get("failure_categories") or {}).get(k, 0) for k in MALFORMED_CATEGORY_KEYS)
        for b in baseline
    ])
    if latest_malformed >= MALFORMED_ABSOLUTE_FLOOR and (
        baseline_malformed_avg == 0.0 or latest_malformed > max(baseline_malformed_avg, _MIN_BASELINE_FLOOR) * 2.0
    ):
        flags.append(AnomalyFlag(
            metric="malformed_records",
            message=(
                f"{latest_malformed} malformed/required-field-missing record(s) this run "
                f"(baseline average {baseline_malformed_avg:.1f}) — this pattern typically means "
                f"the upstream source format changed rather than isolated bad rows; worth checking "
                f"before the next scheduled sync."
            ),
            current_value=latest_malformed,
            baseline_value=round(baseline_malformed_avg, 1),
        ))

    return flags
