"""
validation_history/__init__.py

Validation Intelligence Framework — cross-module validation history,
trend analytics, and an early-warning anomaly detector, shared by every
validator in NG Signal Pro.

Public API, intentionally small:

    from validation_history import record_snapshot, get_recent, detect_anomalies_for

    snapshot_id = record_snapshot(ValidationSnapshot(
        category="Instrument Master",
        status="WARNING",
        total_items=126_644,
        new_items=312,
        updated_items=48,
        deactivated_items=5,
        info_count=0, warning_count=3, failure_count=0, quarantined_count=4,
        execution_seconds=42.7,
        source_version="v4",
        source_timestamp="2026-07-05T02:00:11Z",
        summary="126,644 instruments — structurally sound; 3 warning(s).",
        warning_categories={"duplicate_active_contracts": 1, "lot_size_valid": 1, "tick_size_valid": 1},
    ))

    recent = get_recent("Instrument Master", limit=30)
    anomalies = detect_anomalies_for("Instrument Master")

Any future validator (Warehouse, Research Database, Market Context, Market
DNA, Instrument DNA, Strategy Research, AI Research Journal, Strategy
Optimizer, Continuous Learning Engine) integrates by building one
ValidationSnapshot with its own `category` string after it runs — no
schema change, no new module, no coordination with this package's
internals required. See validation_history/schema.py's docstring for why
one shared table supports this.

This package has NO dependency on the `validation/` package (the
Streamlit-facing Validation Center) or on any Streamlit import — it is
safe to import and use from a plain CI script with no Streamlit runtime
present, which is the actual environment scripts/run_update.py runs in via
GitHub Actions. `validation/instrument_master_validator.py` (and any
future validator module) may import THIS package to surface trend data
inside a ValidationResult; the dependency only ever points that direction.
"""

from __future__ import annotations

from . import settings
from .anomaly_detection import detect_anomalies
from .database import ValidationHistoryStore
from .models import AnomalyFlag, ValidationSnapshot

__all__ = [
    "ValidationSnapshot",
    "AnomalyFlag",
    "record_snapshot",
    "get_recent",
    "get_since",
    "get_categories",
    "detect_anomalies_for",
]


def _store(db_path: str | None = None) -> ValidationHistoryStore:
    return ValidationHistoryStore(db_path or settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)


def record_snapshot(snapshot: ValidationSnapshot, db_path: str | None = None) -> int:
    """Records one snapshot and returns its snapshot_id. Opens and closes
    its own connection — callers don't need to manage a store instance for
    a single write, which is the common case (one call per validation run)."""
    store = _store(db_path)
    try:
        return store.record(snapshot)
    finally:
        store.close()


def get_recent(category: str, limit: int = 50, db_path: str | None = None) -> list[dict]:
    store = _store(db_path)
    try:
        return store.get_recent(category, limit=limit)
    finally:
        store.close()


def get_since(category: str, since_iso: str, db_path: str | None = None) -> list[dict]:
    store = _store(db_path)
    try:
        return store.get_since(category, since_iso)
    finally:
        store.close()


def get_categories(db_path: str | None = None) -> list[str]:
    store = _store(db_path)
    try:
        return store.get_categories()
    finally:
        store.close()


def detect_anomalies_for(category: str, lookback: int = 10, db_path: str | None = None) -> list[AnomalyFlag]:
    """Convenience wrapper: fetches enough recent history for `category`
    and runs the Early Warning System against it in one call — the shape
    scripts/run_update.py (and any future validator's equivalent script)
    actually wants at its call site."""
    recent = get_recent(category, limit=lookback, db_path=db_path)
    return detect_anomalies(recent)
