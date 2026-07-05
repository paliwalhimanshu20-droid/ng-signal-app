"""
validation_history/models.py

Typed data structures for the Validation Intelligence Framework. Kept
completely independent of validation/validation_models.py on purpose:
that package's __init__ pulls in validators that touch Streamlit (config.py,
st.secrets) at import time in a live deployment, and this package needs to
be safely importable from a plain CI script (scripts/run_update.py) with
no Streamlit runtime at all. ANY future validator module (Warehouse,
Research Database, ...) should be able to depend on validation_history
without dragging in the Validation Center or Streamlit.

Every module that wants history/trend tracking builds one ValidationSnapshot
and calls validation_history.record_snapshot(snapshot) — that's the entire
integration surface.
"""

from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


@dataclass(frozen=True)
class ValidationSnapshot:
    """One historical record of one validator's run. `category` is the
    only required identity field — use the same string a module will
    always use (e.g. "Instrument Master") so history/trend queries for
    that category find every past run.

    Fields with an obvious "generic" name (total_items/new_items/...) are
    intentionally domain-agnostic: "items" means instruments for
    Instrument Master, partitions for the Warehouse, experiments for the
    Research Database, DNA profiles for Instrument/Market DNA, and so on.
    A future validator with nothing sensible to put in one of these
    fields just leaves it None — it stays nullable specifically so no
    module is forced to invent a meaningless number.

    `metrics` is the escape hatch for anything module-specific that
    doesn't fit a generic column (e.g. Instrument Master's
    upstox_download_timestamp, or a future Strategy Optimizer's
    best-sharpe-ratio-this-run) — stored as JSON, read back by key.
    """
    category: str
    status: str  # "PASS" / "WARNING" / "FAIL" / "SKIPPED" — plain string, not an enum dependency
    total_items: int | None = None
    new_items: int | None = None
    updated_items: int | None = None
    deactivated_items: int | None = None
    info_count: int = 0
    warning_count: int = 0
    failure_count: int = 0
    quarantined_count: int = 0
    execution_seconds: float | None = None
    source_version: str | None = None
    source_timestamp: str | None = None
    summary: str | None = None
    warning_categories: dict = field(default_factory=dict)  # {rule_name: count}
    failure_categories: dict = field(default_factory=dict)  # {rule_name: count}
    metrics: dict = field(default_factory=dict)
    recorded_at: str | None = None  # filled with "now" at record time if left None


@dataclass(frozen=True)
class AnomalyFlag:
    """One Early Warning System finding — always HIGH PRIORITY by
    definition (this system doesn't emit low-priority anomalies; anything
    not worth surfacing prominently just isn't emitted at all). Comparing
    the latest snapshot against a rolling baseline of prior snapshots for
    the same category, computed by anomaly_detection.py.
    """
    metric: str
    message: str
    current_value: float | int | None
    baseline_value: float | int | None
    priority: str = "HIGH_PRIORITY"

    def as_dict(self) -> dict:
        return {
            "metric": self.metric,
            "message": self.message,
            "current_value": self.current_value,
            "baseline_value": self.baseline_value,
            "priority": self.priority,
        }
