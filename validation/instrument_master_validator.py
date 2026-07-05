"""
validation/instrument_master_validator.py

Validates the Instrument Master (instrument_config / instrument_master
package) as a Validation Center category, wired in after a real production
run exposed that instrument_master/validation.py's old design treated
every finding as fatal. That module now has its own severity-based rule
framework (INFO / WARNING / FAIL per instrument_master/validation.py) —
this validator does NOT re-implement any of those rules; it only
translates that module's report into this package's PASS/WARNING/FAIL/
SKIPPED vocabulary, the same delegation pattern database_validator.py uses
for research_db.validation.validate().

SKIPPED (not FAIL) is used when the Instrument Master DB simply doesn't
exist yet on this deployment — e.g. before the "Update Instrument Master"
GitHub Actions workflow has run for the first time. That is "nothing to
validate yet," not "broken."
"""

from __future__ import annotations

import os

from .validation_models import ValidationCategory, ValidationResult, ValidationStatus


def validate_instrument_master() -> ValidationResult:
    details: list = []
    warnings: list = []
    failures: list = []
    skipped: list = []
    metrics: dict = {}

    # ---- 1. Can settings + the instrument_master package even be imported? ----
    try:
        from instrument_config import settings
    except Exception as e:
        return ValidationResult(
            category=ValidationCategory.INSTRUMENT_MASTER,
            status=ValidationStatus.FAIL,
            summary=f"Could not import instrument_config.settings: {type(e).__name__}: {e}",
            failures=[f"instrument_config.settings import failed: {type(e).__name__}: {e}"],
        )

    try:
        from instrument_master.database import InstrumentDatabase
        from instrument_master import validation as im_validation
    except Exception as e:
        return ValidationResult(
            category=ValidationCategory.INSTRUMENT_MASTER,
            status=ValidationStatus.FAIL,
            summary=f"Could not import the instrument_master package: {type(e).__name__}: {e}",
            failures=[f"instrument_master package import failed: {type(e).__name__}: {e}"],
        )

    details.append(f"Resolved Instrument Master DB path: {settings.DB_PATH}")

    # ---- 2. DB not created yet on this deployment? Not a failure. ----
    if not os.path.exists(settings.DB_PATH):
        skipped.append(
            f"Instrument Master DB not found at {settings.DB_PATH} — run the "
            "'Update Instrument Master' GitHub Actions workflow (or "
            "scripts/run_update.py) before deeper checks apply."
        )
        return ValidationResult(
            category=ValidationCategory.INSTRUMENT_MASTER,
            status=ValidationStatus.SKIPPED,
            summary="Instrument Master not yet built on this deployment — nothing to validate.",
            details=details,
            skipped=skipped,
        )

    # ---- 3. Connect + delegate to instrument_master.validation.validate() ----
    try:
        db = InstrumentDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    except Exception as e:
        return ValidationResult(
            category=ValidationCategory.INSTRUMENT_MASTER,
            status=ValidationStatus.FAIL,
            summary=f"Could not open the Instrument Master DB: {type(e).__name__}: {e}",
            details=details,
            failures=[f"InstrumentDatabase connection failed: {type(e).__name__}: {e}"],
        )

    try:
        report = im_validation.validate(db)
    except Exception as e:
        return ValidationResult(
            category=ValidationCategory.INSTRUMENT_MASTER,
            status=ValidationStatus.FAIL,
            summary=f"instrument_master.validation.validate() crashed: {type(e).__name__}: {e}",
            details=details,
            failures=[f"validate() raised {type(e).__name__}: {e}"],
        )
    finally:
        db.close()

    # ---- 4. Translate the rule-level report into this package's vocabulary ----
    # Each rule-level issue already carries full row detail (instrument_key,
    # symbol, exchange, segment, expiry, reason) — preserved verbatim in the
    # warning/failure line rather than collapsed into a bare count, so this
    # Center's report stays as actionable as instrument_master's own.
    for f in report["failures"]:
        failures.append(f"[{f['rule']}] {f['message']} | key={f['instrument_key']} | {f['reason']}")
    for w in report["warnings"]:
        warnings.append(f"[{w['rule']}] {w['message']} | key={w['instrument_key']} | {w['reason']}")
    for i in report["info"]:
        details.append(f"[{i['rule']}] {i['message']} | key={i['instrument_key']}")

    if not failures and not warnings:
        details.append(f"All {report['rules_run']} validation rules passed cleanly.")

    metrics.update({
        "total_rows": report["total_rows"],
        "rules_run": report["rules_run"],
        "info_count": report["info_count"],
        "warning_count": report["warning_count"],
        "failure_count": report["failure_count"],
        "quarantined_count": report["quarantined_count"],
    })

    # ---- 5. Historical trend, from the Validation Intelligence Framework ----
    # Read-only and best-effort: the Admin Center's "current status" must
    # never depend on history being available, and a history-store problem
    # here is not itself an Instrument Master integrity issue — it's
    # reported as an extra detail line, never escalated to WARNING/FAIL.
    # This is the "future validators produce both current status AND
    # historical trend from the same architecture" requirement made
    # concrete: any future validator_x.py can add this exact block,
    # swapping only the category string.
    try:
        from validation_history import get_recent, detect_anomalies_for
        from validation_history import trends as vh_trends

        history_category = "Instrument Master"
        recent = get_recent(history_category, limit=30)
        if recent:
            metrics["trend"] = {
                "snapshots_available": len(recent),
                "success_rate": vh_trends.success_rate(recent),
                "avg_execution_seconds": vh_trends.average_execution_seconds(recent),
                "most_common_warnings": vh_trends.most_common_warning_categories(recent, top_n=5),
                "most_common_failures": vh_trends.most_common_failure_categories(recent, top_n=5),
                "quality_score_latest": vh_trends.quality_score(recent[0]),
            }
            anomalies = detect_anomalies_for(history_category, lookback=10)
            if anomalies:
                metrics["trend"]["high_priority_anomalies"] = [a.as_dict() for a in anomalies]
                for a in anomalies:
                    details.append(f"[HIGH_PRIORITY trend anomaly] {a.metric}: {a.message}")
            details.append(f"Validation history: {len(recent)} prior snapshot(s) available for trend analysis.")
        else:
            details.append("No validation history recorded yet for this category.")
    except Exception as e:
        details.append(f"Validation history/trend lookup skipped: {type(e).__name__}: {e}")

    if failures:
        status = ValidationStatus.FAIL
        summary = (
            f"{report['total_rows']:,} instruments — {report['failure_count']} genuine integrity "
            f"failure(s) found (blocks commit)."
        )
    elif warnings:
        status = ValidationStatus.WARNING
        summary = (
            f"{report['total_rows']:,} instruments — structurally sound; "
            f"{report['warning_count']} warning(s) / {report['quarantined_count']} row(s) "
            f"quarantined for review."
        )
    else:
        status = ValidationStatus.PASS
        summary = f"{report['total_rows']:,} instruments — all {report['rules_run']} rules passed cleanly."

    return ValidationResult(
        category=ValidationCategory.INSTRUMENT_MASTER,
        status=status,
        summary=summary,
        details=details,
        warnings=warnings,
        failures=failures,
        skipped=skipped,
        metrics=metrics,
    )
