"""
tests/test_database.py

Covers: database creation, migration, insert, update, foreign key
integrity, versioning behavior, and the injection guards on
search_experiments() / get_performance_ranking().

Usage:
    python tests/test_database.py
"""

import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from research_db.database import ResearchDatabase
from research_db import migrations, validation, schema


def _fresh_db():
    tmpdir = tempfile.mkdtemp()
    return ResearchDatabase(os.path.join(tmpdir, "test.db"))


def test_database_creation_and_migration():
    db = _fresh_db()
    v = migrations.current_version(db.conn)
    assert v == 1, f"expected v1 baseline, got v{v}"
    assert db.count_experiments() == 0
    report = validation.validate(db)
    assert report["passed"]
    db.close()
    print("PASS: test_database_creation_and_migration")


def test_create_experiment_and_insert_results():
    db = _fresh_db()
    exp = db.create_experiment(
        instrument_key="NSE_EQ|INE002A01018", research_type="INDICATOR",
        created_by="test", random_seed=1,
    )
    run_id = exp["run_id"]

    db.store_indicator_result(run_id, "RSI", parameters={"period": 14}, result={"score": 0.7})
    db.store_strategy_result(run_id, "MOMENTUM_V1", entry_rules={"rsi": ">70"})
    regime_id = db.store_regime_result(run_id, "TRENDING")
    db.store_metrics(run_id, {"win_rate": 0.6, "sharpe_ratio": 1.5, "custom_metric_xyz": 99},
                      regime_id=regime_id)
    db.add_validation_result(run_id, "VALIDATION_PENDING")
    db.add_note(exp["experiment_id"], "test note", run_id=run_id)
    db.commit()

    full = db.get_experiment(run_id)
    assert full["research_type"] == "INDICATOR"
    assert full["latest_validation"]["validation_status"] == "VALIDATION_PENDING"

    metrics = db.get_performance_ranking(metric="sharpe_ratio", limit=5)
    assert len(metrics) == 1
    assert metrics[0]["sharpe_ratio"] == 1.5
    # unknown metric key folded into extra_metrics rather than raising
    import json
    extra = json.loads(metrics[0]["extra_metrics"])
    assert extra["custom_metric_xyz"] == 99

    db.close()
    print("PASS: test_create_experiment_and_insert_results")


def test_update_status_is_mutable():
    db = _fresh_db()
    exp = db.create_experiment(instrument_key="X", research_type="STRATEGY")
    run_id = exp["run_id"]
    db.update_status(run_id, "RUNNING")
    db.update_status(run_id, "COMPLETED")
    db.commit()
    assert db.get_experiment(run_id)["research_status"] == "COMPLETED"

    try:
        db.update_status(run_id, "NOT_A_REAL_STATUS")
        assert False, "should have raised on invalid status"
    except ValueError:
        pass

    db.close()
    print("PASS: test_update_status_is_mutable")


def test_versioning_preserves_old_versions():
    db = _fresh_db()
    exp1 = db.create_experiment(instrument_key="X", research_type="STRATEGY",
                                 notes="v1 attempt")
    experiment_id = exp1["experiment_id"]
    db.store_metrics(exp1["run_id"], {"win_rate": 0.4})

    exp2 = db.create_experiment(instrument_key="X", research_type="STRATEGY",
                                 experiment_id=experiment_id,
                                 change_description="tuned parameters")
    db.store_metrics(exp2["run_id"], {"win_rate": 0.55})
    db.commit()

    versions = db.get_experiment_versions(experiment_id)
    assert len(versions) == 2
    assert versions[0]["is_current_version"] == 0, "old version must be flagged non-current, not deleted"
    assert versions[1]["is_current_version"] == 1

    # Both runs' data must still be independently retrievable — nothing overwritten
    v1_exp = db.get_experiment(exp1["run_id"])
    v2_exp = db.get_experiment(exp2["run_id"])
    assert v1_exp is not None and v2_exp is not None
    assert v1_exp["notes"] == "v1 attempt"

    hist = db.get_instrument_history("X")
    assert len(hist) == 2, "both runs should appear in instrument history"

    db.close()
    print("PASS: test_versioning_preserves_old_versions")


def test_validation_history_never_deleted():
    db = _fresh_db()
    exp = db.create_experiment(instrument_key="X", research_type="STRATEGY")
    run_id = exp["run_id"]
    db.add_validation_result(run_id, "NOT_VALIDATED")
    db.add_validation_result(run_id, "VALIDATION_PENDING")
    db.add_validation_result(run_id, "VALIDATED", validated_by="analyst_1")
    db.commit()

    experiment_row_id = db._get_experiment_row_id(run_id)
    cur = db.conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_VALIDATION_RESULTS} WHERE experiment_row_id = ?",
        (experiment_row_id,),
    )
    assert cur.fetchone()["c"] == 3, "all 3 validation events should exist, none overwritten"

    latest = db.get_latest_validation_status(run_id)
    assert latest["validation_status"] == "VALIDATED"

    db.close()
    print("PASS: test_validation_history_never_deleted")


def test_foreign_key_integrity_enforced():
    db = _fresh_db()
    # Attempting to store a result against a nonexistent run_id must fail cleanly
    try:
        db.store_indicator_result("nonexistent-run-id", "RSI")
        assert False, "should have raised ValueError for unknown run_id"
    except ValueError:
        pass
    db.close()
    print("PASS: test_foreign_key_integrity_enforced")


def test_search_and_ranking_injection_guards():
    db = _fresh_db()
    exp = db.create_experiment(instrument_key="X", research_type="STRATEGY")
    db.store_metrics(exp["run_id"], {"win_rate": 0.5})
    db.commit()

    try:
        db.search_experiments(**{"instrument_key; DROP TABLE research_experiments;--": "X"})
        assert False, "should have rejected unknown filter column"
    except ValueError:
        pass

    try:
        db.get_performance_ranking(metric="win_rate; DROP TABLE performance_metrics;--")
        assert False, "should have rejected unknown ranking metric"
    except ValueError:
        pass

    # legitimate calls still work
    results = db.search_experiments(instrument_key="X")
    assert len(results) == 1
    ranked = db.get_performance_ranking(metric="win_rate")
    assert len(ranked) == 1

    db.close()
    print("PASS: test_search_and_ranking_injection_guards")


def test_query_performance_smoke():
    """Not a rigorous benchmark — just confirms indexed queries stay fast
    on a few thousand rows within this sandbox's time budget."""
    import time
    db = _fresh_db()
    for i in range(2000):
        exp = db.create_experiment(
            instrument_key=f"NSE_EQ|STOCK{i % 50}", research_type="STRATEGY",
        )
        db.store_metrics(exp["run_id"], {"sharpe_ratio": (i % 100) / 50.0})
    db.commit()

    start = time.time()
    _ = db.get_instrument_history("NSE_EQ|STOCK7", limit=100)
    _ = db.get_performance_ranking(metric="sharpe_ratio", limit=50)
    _ = db.get_recent_experiments(limit=50)
    elapsed = time.time() - start
    assert elapsed < 2.0, f"indexed queries took {elapsed:.2f}s on 2,000 rows — investigate"

    db.close()
    print(f"PASS: test_query_performance_smoke ({elapsed*1000:.1f}ms for 3 indexed queries on 2,000 rows)")


if __name__ == "__main__":
    test_database_creation_and_migration()
    test_create_experiment_and_insert_results()
    test_update_status_is_mutable()
    test_versioning_preserves_old_versions()
    test_validation_history_never_deleted()
    test_foreign_key_integrity_enforced()
    test_search_and_ranking_injection_guards()
    test_query_performance_smoke()
    print("\nAll tests passed.")
