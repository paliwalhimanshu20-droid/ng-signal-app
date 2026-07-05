"""
scripts/run_update.py

Incremental update — intended to be run on a schedule (e.g. daily via
GitHub Actions, same pattern as check_signals.yml). Identical logic to
init_db.py's sync step; kept as a separate entrypoint for clearer CI naming
and so init-specific messaging can diverge later without touching the core
sync path.

Usage:
    python scripts/run_update.py

Exit code is non-zero only if instrument_master.validation.validate()
finds a genuine FAIL-severity integrity issue (see that module's
docstring for the INFO/WARNING/FAIL design). WARNING-level findings —
known, recoverable market-data anomalies such as a corporate-action
duplicate listing or an INDEX row's zero lot_size — are fully reported
below and in the JSON audit report, but do NOT fail the job or block the
commit step in .github/workflows/update_instrument_master.yml.

This is also the reference integration for the Validation Intelligence
Framework (validation_history/): after every run, a ValidationSnapshot is
recorded and the Early Warning System checks it against recent history.
Recording/anomaly-detection failures are caught and logged but never
affect the exit code — a broken history logger is an observability
problem, not an Instrument Master integrity failure, and must never be
allowed to block a otherwise-healthy sync. Any future validator script
(Warehouse, Research Database, ...) should follow this same shape.
"""

import json
import logging
import sys
import os
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from instrument_config import settings
from instrument_master.classifier import ClassificationRules
from instrument_master.database import InstrumentDatabase
from instrument_master.update_engine import run_full_sync
from instrument_master import migrations
from instrument_master import validation

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# Written alongside the DB and committed together, so the audit trail
# travels with the data it describes rather than only living in a
# GitHub Actions log that eventually rolls off.
AUDIT_REPORT_PATH = os.path.join(settings.DATA_DIR, "instrument_master_validation_report.json")

# The one identity string this module will always use when recording to
# validation_history — every trend/history query for Instrument Master
# keys off this exact value.
VALIDATION_HISTORY_CATEGORY = "Instrument Master"


def main():
    run_started = time.monotonic()

    db = InstrumentDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    rules = ClassificationRules(settings.CLASSIFICATION_RULES_PATH)

    before_count = db.count()
    summary = run_full_sync(db, rules, settings)
    after_count = db.count()

    summary.print_report()
    print(f"Row count: {before_count:,} -> {after_count:,}")

    report = validation.validate(db)
    validation.print_report(report)

    schema_version = migrations.current_version(db.conn)
    db.close()

    execution_seconds = round(time.monotonic() - run_started, 3)

    # ---- Institutional audit trail: sync counters + validation report ----
    audit = {
        "generated_at": report["generated_at"],
        "execution_seconds": execution_seconds,
        "schema_version": schema_version,
        "sync": summary.as_dict(),
        "row_count_before": before_count,
        "row_count_after": after_count,
        "validation": {
            "total_rows": report["total_rows"],
            "rules_run": report["rules_run"],
            "info_count": report["info_count"],
            "warning_count": report["warning_count"],
            "failure_count": report["failure_count"],
            "quarantined_count": report["quarantined_count"],
            "passed": report["passed"],
            "info": report["info"],
            "warnings": report["warnings"],
            "failures": report["failures"],
        },
    }

    # ---- Validation Intelligence Framework: record history + check for anomalies ----
    # Deliberately best-effort: this is observability, not the sync itself.
    # A history-store problem must never turn a healthy sync into a failed
    # CI run, and must never suppress the FAIL-driven exit code below.
    anomalies_payload = []
    try:
        from validation_history import record_snapshot, detect_anomalies_for
        from validation_history.models import ValidationSnapshot
        from collections import Counter

        def _tally(issues):
            c = Counter()
            for i in issues:
                c[i["rule"]] += 1
            return dict(c)

        record_snapshot(ValidationSnapshot(
            category=VALIDATION_HISTORY_CATEGORY,
            status=("FAIL" if report["failure_count"] else
                    "WARNING" if report["warning_count"] else "PASS"),
            total_items=report["total_rows"],
            new_items=summary.new_count,
            updated_items=summary.updated_count,
            deactivated_items=summary.deactivated_count,
            info_count=report["info_count"],
            warning_count=report["warning_count"],
            failure_count=report["failure_count"],
            quarantined_count=report["quarantined_count"],
            execution_seconds=execution_seconds,
            source_version=f"v{schema_version}",
            source_timestamp=summary.download_started_at,
            summary=(f"{report['total_rows']:,} instruments — "
                     f"{report['warning_count']} warning(s), {report['failure_count']} failure(s)."),
            warning_categories=_tally(report["warnings"]),
            failure_categories=_tally(report["failures"]),
            metrics={"unchanged_items": summary.unchanged_count},
        ))
        print(f"Validation history recorded (category='{VALIDATION_HISTORY_CATEGORY}').")

        anomalies = detect_anomalies_for(VALIDATION_HISTORY_CATEGORY, lookback=10)
        if anomalies:
            print(f"\n  --- EARLY WARNING SYSTEM: {len(anomalies)} HIGH PRIORITY finding(s) ---")
            for a in anomalies:
                print(f"    [HIGH_PRIORITY] {a.metric}: {a.message}")
            print()
        anomalies_payload = [a.as_dict() for a in anomalies]
    except Exception as e:
        logger.warning("Validation history recording/anomaly-detection skipped: %s: %s", type(e).__name__, e)

    audit["anomalies"] = anomalies_payload

    os.makedirs(settings.DATA_DIR, exist_ok=True)
    with open(AUDIT_REPORT_PATH, "w") as f:
        json.dump(audit, f, indent=2)
    print(f"Audit report written: {AUDIT_REPORT_PATH}")

    print("\n=== Audit Summary ===")
    print(f"  {'downloaded':>14}: {summary.total_source_records:,}")
    print(f"  {'inserted':>14}: {summary.new_count:,}")
    print(f"  {'updated':>14}: {summary.updated_count:,}")
    print(f"  {'unchanged':>14}: {summary.unchanged_count:,}")
    print(f"  {'quarantined':>14}: {report['quarantined_count']:,}")
    print(f"  {'warning_count':>14}: {report['warning_count']:,}")
    print(f"  {'failure_count':>14}: {report['failure_count']:,}")
    print(f"  {'exec_seconds':>14}: {execution_seconds}")
    print(f"  {'high_priority':>14}: {len(anomalies_payload)}")
    print("======================\n")

    if not report["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()


