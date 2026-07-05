"""
tests/test_validation_history.py

Covers the Validation Intelligence Framework (validation_history/):
recording, retrieval, trend analytics, and the Early Warning System's
anomaly detection. Also serves as the reference example for how a future
validator (Warehouse, Research Database, Market Context, ...) integrates
with this package — see record_snapshot()/detect_anomalies_for() usage
below.

Usage:
    python -m pytest tests/test_validation_history.py -v
"""

import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from validation_history import record_snapshot, get_recent, get_categories, detect_anomalies_for
from validation_history.models import ValidationSnapshot
from validation_history import trends


def _tmp_db():
    tmpdir = tempfile.mkdtemp()
    return os.path.join(tmpdir, "validation_history_test.db")


def _healthy_snapshot(i, category="Test Category"):
    return ValidationSnapshot(
        category=category, status="WARNING", total_items=1000 + i,
        new_items=5, updated_items=2, deactivated_items=1,
        info_count=0, warning_count=3, failure_count=0, quarantined_count=3,
        execution_seconds=10.0 + i, source_version="v1",
        source_timestamp=f"2026-07-0{i+1}T02:00:00Z",
        summary="healthy", warning_categories={"some_rule": 3},
    )


def test_record_and_get_recent_roundtrip():
    db = _tmp_db()
    snapshot_id = record_snapshot(_healthy_snapshot(0), db_path=db)
    assert snapshot_id == 1
    recent = get_recent("Test Category", limit=10, db_path=db)
    assert len(recent) == 1
    assert recent[0]["category"] == "Test Category"
    assert recent[0]["warning_count"] == 3
    assert recent[0]["warning_categories"] == {"some_rule": 3}
    print("PASS: test_record_and_get_recent_roundtrip")


def test_get_recent_orders_newest_first():
    db = _tmp_db()
    for i in range(3):
        record_snapshot(_healthy_snapshot(i), db_path=db)
    recent = get_recent("Test Category", limit=10, db_path=db)
    assert len(recent) == 3
    assert recent[0]["snapshot_id"] > recent[1]["snapshot_id"] > recent[2]["snapshot_id"]
    print("PASS: test_get_recent_orders_newest_first")


def test_get_categories_discovers_distinct_categories():
    db = _tmp_db()
    record_snapshot(_healthy_snapshot(0, category="Instrument Master"), db_path=db)
    record_snapshot(_healthy_snapshot(0, category="Warehouse"), db_path=db)
    cats = get_categories(db_path=db)
    assert set(cats) == {"Instrument Master", "Warehouse"}
    print("PASS: test_get_categories_discovers_distinct_categories")


def test_categories_are_independent():
    """A snapshot recorded for one category must never appear in another
    category's history — the shared-table design must not leak across
    modules."""
    db = _tmp_db()
    record_snapshot(_healthy_snapshot(0, category="Instrument Master"), db_path=db)
    record_snapshot(_healthy_snapshot(0, category="Warehouse"), db_path=db)
    im_recent = get_recent("Instrument Master", limit=10, db_path=db)
    wh_recent = get_recent("Warehouse", limit=10, db_path=db)
    assert len(im_recent) == 1
    assert len(wh_recent) == 1
    print("PASS: test_categories_are_independent")


def test_success_rate_and_average_execution_seconds():
    db = _tmp_db()
    for i in range(4):
        record_snapshot(_healthy_snapshot(i), db_path=db)
    recent = get_recent("Test Category", limit=10, db_path=db)
    rate = trends.success_rate(recent)
    assert rate["total_runs"] == 4
    assert rate["pass_rate"] == 0.0       # all runs were WARNING, not PASS
    assert rate["non_fail_rate"] == 1.0   # none were FAIL
    avg = trends.average_execution_seconds(recent)
    assert avg == round((10.0 + 11.0 + 12.0 + 13.0) / 4, 3)
    print("PASS: test_success_rate_and_average_execution_seconds")


def test_most_common_warning_categories_aggregates_across_runs():
    db = _tmp_db()
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="WARNING", warning_categories={"rule_a": 2, "rule_b": 1},
    ), db_path=db)
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="WARNING", warning_categories={"rule_a": 1},
    ), db_path=db)
    recent = get_recent("Test Category", limit=10, db_path=db)
    common = trends.most_common_warning_categories(recent, top_n=5)
    assert common[0] == ("rule_a", 3)
    assert ("rule_b", 1) in common
    print("PASS: test_most_common_warning_categories_aggregates_across_runs")


def test_quality_score_pass_fail_warning():
    assert trends.quality_score({"status": "PASS", "total_items": 100, "quarantined_count": 0}) == 100.0
    assert trends.quality_score({"status": "FAIL", "total_items": 100, "quarantined_count": 0}) == 0.0
    assert trends.quality_score({"status": "SKIPPED", "total_items": 100, "quarantined_count": 0}) == 0.0
    # WARNING with a tiny quarantined fraction should stay close to the 60 baseline
    warn_score = trends.quality_score({"status": "WARNING", "total_items": 100000, "quarantined_count": 3})
    assert 55 <= warn_score <= 60
    print("PASS: test_quality_score_pass_fail_warning")


def test_anomaly_detection_silent_with_insufficient_history():
    """Cannot detect a 'sudden' anything with fewer than
    MIN_BASELINE_SNAPSHOTS + 1 total snapshots — must return no anomalies
    rather than guessing."""
    db = _tmp_db()
    record_snapshot(_healthy_snapshot(0), db_path=db)
    record_snapshot(_healthy_snapshot(1), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    assert anomalies == []
    print("PASS: test_anomaly_detection_silent_with_insufficient_history")


def test_anomaly_detection_silent_on_healthy_trend():
    db = _tmp_db()
    for i in range(6):
        record_snapshot(_healthy_snapshot(i), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    assert anomalies == []
    print("PASS: test_anomaly_detection_silent_on_healthy_trend")


def test_anomaly_detection_flags_instrument_count_drop():
    db = _tmp_db()
    for i in range(4):
        record_snapshot(ValidationSnapshot(
            category="Test Category", status="PASS", total_items=1000,
            warning_count=0, failure_count=0, deactivated_items=1,
        ), db_path=db)
    # Sudden 30% drop
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="PASS", total_items=700,
        warning_count=0, failure_count=0, deactivated_items=1,
    ), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    metrics = {a.metric for a in anomalies}
    assert "total_items" in metrics
    print("PASS: test_anomaly_detection_flags_instrument_count_drop")


def test_anomaly_detection_flags_warning_spike():
    db = _tmp_db()
    for i in range(4):
        record_snapshot(ValidationSnapshot(
            category="Test Category", status="WARNING", total_items=1000,
            warning_count=3, failure_count=0, deactivated_items=1,
        ), db_path=db)
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="WARNING", total_items=1000,
        warning_count=30, failure_count=0, deactivated_items=1,
    ), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    metrics = {a.metric for a in anomalies}
    assert "warning_count" in metrics
    print("PASS: test_anomaly_detection_flags_warning_spike")


def test_anomaly_detection_flags_fail_regression_after_clean_history():
    db = _tmp_db()
    for i in range(4):
        record_snapshot(ValidationSnapshot(
            category="Test Category", status="PASS", total_items=1000,
            warning_count=0, failure_count=0, deactivated_items=1,
        ), db_path=db)
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="FAIL", total_items=1000,
        warning_count=0, failure_count=5, deactivated_items=1,
        failure_categories={"malformed_instrument_key": 5},
    ), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    metrics = {a.metric for a in anomalies}
    assert "failure_count" in metrics
    assert "malformed_records" in metrics
    print("PASS: test_anomaly_detection_flags_fail_regression_after_clean_history")


def test_anomaly_detection_flags_mass_deactivation():
    db = _tmp_db()
    for i in range(4):
        record_snapshot(ValidationSnapshot(
            category="Test Category", status="PASS", total_items=1000,
            warning_count=0, failure_count=0, deactivated_items=2,
        ), db_path=db)
    record_snapshot(ValidationSnapshot(
        category="Test Category", status="PASS", total_items=990,
        warning_count=0, failure_count=0, deactivated_items=500,
    ), db_path=db)
    anomalies = detect_anomalies_for("Test Category", lookback=10, db_path=db)
    metrics = {a.metric for a in anomalies}
    assert "deactivated_items" in metrics
    print("PASS: test_anomaly_detection_flags_mass_deactivation")


def test_backward_compatible_defaults_are_all_optional():
    """A minimal ValidationSnapshot with only category+status must not
    raise — every other field is optional, so a future validator with
    nothing to report for e.g. new/updated/deactivated items can still
    record a snapshot."""
    db = _tmp_db()
    snapshot_id = record_snapshot(ValidationSnapshot(category="Minimal", status="PASS"), db_path=db)
    assert snapshot_id == 1
    recent = get_recent("Minimal", limit=1, db_path=db)
    assert recent[0]["total_items"] is None
    print("PASS: test_backward_compatible_defaults_are_all_optional")


if __name__ == "__main__":
    test_record_and_get_recent_roundtrip()
    test_get_recent_orders_newest_first()
    test_get_categories_discovers_distinct_categories()
    test_categories_are_independent()
    test_success_rate_and_average_execution_seconds()
    test_most_common_warning_categories_aggregates_across_runs()
    test_quality_score_pass_fail_warning()
    test_anomaly_detection_silent_with_insufficient_history()
    test_anomaly_detection_silent_on_healthy_trend()
    test_anomaly_detection_flags_instrument_count_drop()
    test_anomaly_detection_flags_warning_spike()
    test_anomaly_detection_flags_fail_regression_after_clean_history()
    test_anomaly_detection_flags_mass_deactivation()
    test_backward_compatible_defaults_are_all_optional()
    print("\nAll validation_history tests passed.")
