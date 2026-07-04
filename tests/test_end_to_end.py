"""
tests/test_end_to_end.py

Simulates a real Upstox complete.json.gz payload (since the sandbox running
this test has no network access to assets.upstox.com) to validate the full
pipeline: parse -> classify -> insert -> re-sync -> update -> deactivate,
while confirming NG Signal Pro research fields survive updates untouched.

Usage:
    python tests/test_end_to_end.py
"""

import gzip
import io
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from instrument_config import settings
from instrument_master.classifier import ClassificationRules
from instrument_master.database import InstrumentDatabase
from instrument_master.update_engine import run_full_sync
from instrument_master import validation


SAMPLE_BATCH_1 = [
    {
        "instrument_key": "MCX_FO|NATURALGAS25JUL",
        "trading_symbol": "NATURALGAS",
        "exchange": "MCX",
        "segment": "MCX_FO",
        "instrument_type": "FUT",
        "name": "NATURAL GAS JUL FUT",
        "isin": None,
        "lot_size": 1250,
        "tick_size": 0.1,
        "freeze_quantity": 5000,
        "expiry": 1753900200000,  # some epoch ms
        "strike_price": 0.0,
        "option_type": None,
    },
    {
        "instrument_key": "NSE_EQ|INE002A01018",
        "trading_symbol": "RELIANCE",
        "exchange": "NSE",
        "segment": "NSE_EQ",
        "instrument_type": "EQ",
        "name": "RELIANCE INDUSTRIES LTD",
        "isin": "INE002A01018",
        "lot_size": 1,
        "tick_size": 0.05,
        "freeze_quantity": 100000,
        "expiry": None,
        "strike_price": 0.0,
        "option_type": None,
    },
    {
        "instrument_key": "NSE_INDEX|Nifty Bank",
        "trading_symbol": "BANKNIFTY",
        "exchange": "NSE",
        "segment": "NSE_INDEX",
        "instrument_type": "INDEX",
        "name": "NIFTY BANK",
        "isin": None,
        "lot_size": None,
        "tick_size": None,
        "freeze_quantity": None,
        "expiry": None,
        "strike_price": 0.0,
        "option_type": None,
    },
    {
        # This one will "expire"/disappear in batch 2 to test deactivation
        "instrument_key": "MCX_FO|CRUDEOIL25JUL",
        "trading_symbol": "CRUDEOIL",
        "exchange": "MCX",
        "segment": "MCX_FO",
        "instrument_type": "FUT",
        "name": "CRUDE OIL JUL FUT",
        "isin": None,
        "lot_size": 100,
        "tick_size": 1.0,
        "freeze_quantity": 10000,
        "expiry": 1753900200000,
        "strike_price": 0.0,
        "option_type": None,
    },
]

# Batch 2: RELIANCE tick_size changes (simulating a real update),
# CRUDEOIL contract is gone (expired/rolled), a brand new instrument appears.
SAMPLE_BATCH_2 = [
    SAMPLE_BATCH_1[0],  # NATURALGAS unchanged
    {
        **SAMPLE_BATCH_1[1],
        "tick_size": 0.10,  # changed from 0.05
    },
    SAMPLE_BATCH_1[2],  # BANKNIFTY unchanged
    # CRUDEOIL25JUL omitted -> should be deactivated
    {
        "instrument_key": "MCX_FO|CRUDEOIL25AUG",
        "trading_symbol": "CRUDEOIL",
        "exchange": "MCX",
        "segment": "MCX_FO",
        "instrument_type": "FUT",
        "name": "CRUDE OIL AUG FUT",
        "isin": None,
        "lot_size": 100,
        "tick_size": 1.0,
        "freeze_quantity": 10000,
        "expiry": 1756492200000,
        "strike_price": 0.0,
        "option_type": None,
    },
]


def _make_fake_raw(records):
    """
    download_raw() in the real module returns DECOMPRESSED bytes (it handles
    the gzip internally). Our monkeypatch replaces download_raw entirely, so
    it must also return decompressed bytes to match that contract.
    """
    return json.dumps(records).encode("utf-8")


class FakeSettings:
    """Wraps real settings but overrides the URL-fetch step via monkeypatch below."""
    pass


def main():
    tmpdir = tempfile.mkdtemp()
    db_path = os.path.join(tmpdir, "test_instrument_master.db")

    db = InstrumentDatabase(db_path)
    rules = ClassificationRules(settings.CLASSIFICATION_RULES_PATH)

    # Monkeypatch downloader.download_raw to return our synthetic batch instead
    # of hitting the real network (sandbox has no access to assets.upstox.com).
    from instrument_master import update_engine, downloader

    original_download = downloader.download_raw

    print("=== SYNC 1 (initial load) ===")
    downloader.download_raw = lambda url, timeout: _make_fake_raw(SAMPLE_BATCH_1)
    summary1 = update_engine.run_full_sync(db, rules, settings)
    summary1.print_report()
    assert summary1.new_count == 4, f"expected 4 new, got {summary1.new_count}"
    assert db.count() == 4

    # Simulate research progress on RELIANCE before the next sync, to prove
    # it survives an update.
    db.conn.execute(
        "UPDATE instruments SET research_status='RESEARCH_COMPLETE', "
        "research_maturity_level=3, last_research_date='2026-07-01' "
        "WHERE instrument_key = ?",
        ("NSE_EQ|INE002A01018",),
    )
    db.commit()
    print("Simulated research progress on RELIANCE: RESEARCH_COMPLETE, maturity=3")

    print("\n=== SYNC 2 (RELIANCE tick_size changes, CRUDEOIL rolls to new expiry) ===")
    downloader.download_raw = lambda url, timeout: _make_fake_raw(SAMPLE_BATCH_2)
    summary2 = update_engine.run_full_sync(db, rules, settings)
    summary2.print_report()

    assert summary2.new_count == 1, f"expected 1 new (CRUDEOIL25AUG), got {summary2.new_count}"
    assert summary2.updated_count == 1, f"expected 1 updated (RELIANCE), got {summary2.updated_count}"
    assert summary2.unchanged_count == 2, f"expected 2 unchanged, got {summary2.unchanged_count}"
    assert summary2.deactivated_count == 1, f"expected 1 deactivated (CRUDEOIL25JUL), got {summary2.deactivated_count}"

    # Verify RELIANCE's trading field updated but research fields preserved
    reliance = db.get_by_key("NSE_EQ|INE002A01018")
    assert reliance["tick_size"] == 0.10, "tick_size should have updated to 0.10"
    assert reliance["research_status"] == "RESEARCH_COMPLETE", "research_status must be preserved"
    assert reliance["research_maturity_level"] == 3, "maturity level must be preserved"
    assert reliance["last_research_date"] == "2026-07-01", "last_research_date must be preserved"
    print("\nVerified: RELIANCE tick_size updated to 0.10, research fields preserved")

    # Verify old CRUDEOIL contract deactivated, not deleted
    old_crude = db.get_by_key("MCX_FO|CRUDEOIL25JUL")
    assert old_crude is not None, "old contract should still exist in DB"
    assert old_crude["active_status"] == "INACTIVE", "old contract should be INACTIVE"
    print("Verified: old CRUDEOIL25JUL contract preserved with active_status=INACTIVE")

    # Verify new CRUDEOIL contract inserted fresh
    new_crude = db.get_by_key("MCX_FO|CRUDEOIL25AUG")
    assert new_crude is not None
    assert new_crude["active_status"] == "ACTIVE"
    assert new_crude["research_status"] == "NOT_RESEARCHED"
    print("Verified: new CRUDEOIL25AUG contract inserted with fresh research fields")

    # Verify priority assignment
    assert reliance["research_priority"] == 4, f"RELIANCE (Nifty50) should be priority 4, got {reliance['research_priority']}"
    natgas = db.get_by_key("MCX_FO|NATURALGAS25JUL")
    assert natgas["research_priority"] == 1, f"NATURALGAS should be priority 1, got {natgas['research_priority']}"
    banknifty = db.get_by_key("NSE_INDEX|Nifty Bank")
    assert banknifty["research_priority"] == 3, f"BANKNIFTY should be priority 3, got {banknifty['research_priority']}"
    print("Verified: research_priority assignment correct for all instrument types")

    print("\n=== VALIDATION ===")
    report = validation.validate(db)
    validation.print_report(report)
    assert report["passed"], f"validation failed: {report['issues']}"

    print("\n=== MIGRATIONS ===")
    from instrument_master import migrations as migrations_mod
    version = migrations_mod.current_version(db.conn)
    print(f"Schema version: v{version}")
    assert version == 4, f"expected schema at v4, got v{version}"

    # New columns should exist and have sane defaults on pre-existing rows
    reliance_full = db.get_by_key("NSE_EQ|INE002A01018")
    assert reliance_full["scheduler_enabled"] == 1, "scheduler_enabled should default to 1"
    assert reliance_full["research_attempts_count"] == 0, "attempts should default to 0"
    print("Verified: new scheduler columns present with correct defaults on existing rows")

    print("\n=== SCHEDULER + RESEARCH LOG HELPERS ===")
    db.set_research_schedule(
        "NSE_EQ|INE002A01018", next_date="2026-06-01", frequency_days=90
    )
    db.commit()
    due = db.get_due_for_research(as_of_date="2026-07-03", limit=10)
    due_keys = [r["instrument_key"] for r in due]
    assert "NSE_EQ|INE002A01018" in due_keys, "RELIANCE should be due for research"
    print(f"Verified: get_due_for_research() returns {len(due)} instrument(s), RELIANCE included")

    locked = db.acquire_research_lock(
        "NSE_EQ|INE002A01018", owner="test-worker-1", expires_at="2026-07-03T23:59:59Z"
    )
    assert locked, "should acquire lock on unlocked instrument"
    locked_again = db.acquire_research_lock(
        "NSE_EQ|INE002A01018", owner="test-worker-2", expires_at="2026-07-03T23:59:59Z"
    )
    assert not locked_again, "second worker should NOT acquire an already-held lock"
    db.release_research_lock("NSE_EQ|INE002A01018")
    db.commit()
    print("Verified: research lock acquire/blocking/release works")

    run_id = db.log_research_run(
        instrument_key="NSE_EQ|INE002A01018",
        run_started_at="2026-07-03T10:00:00Z",
        run_completed_at="2026-07-03T10:05:00Z",
        status_before="RESEARCH_COMPLETE",
        status_after="RESEARCH_COMPLETE",
        maturity_before=3,
        maturity_after=3,
        engine_version="test-v0",
        result_summary="test run",
        success=True,
    )
    db.commit()
    assert run_id is not None
    history = db.get_research_history("NSE_EQ|INE002A01018")
    assert len(history) == 1
    reliance_after_log = db.get_by_key("NSE_EQ|INE002A01018")
    assert reliance_after_log["research_attempts_count"] == 1, "attempts_count should increment"
    assert reliance_after_log["last_research_run_id"] == run_id
    print("Verified: log_research_run() inserts history row and updates attempts_count")

    db.update_research_metadata(
        "NSE_EQ|INE002A01018",
        data_quality_score=0.95,
        external_reference_ids='{"yfinance": "RELIANCE.NS"}',
    )
    db.commit()
    reliance_meta = db.get_by_key("NSE_EQ|INE002A01018")
    assert reliance_meta["data_quality_score"] == 0.95
    print("Verified: update_research_metadata() writes descriptive fields correctly")

    # Re-validate after all the above writes
    report2 = validation.validate(db)
    validation.print_report(report2)
    assert report2["passed"], f"validation failed after scheduler ops: {report2['issues']}"

    downloader.download_raw = original_download
    db.close()

    print("ALL END-TO-END TESTS PASSED")


if __name__ == "__main__":
    main()
