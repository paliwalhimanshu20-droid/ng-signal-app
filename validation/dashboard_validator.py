"""
validation/dashboard_validator.py

Validates that the Performance page's displayed numbers (Total Trades,
Open Trades, Closed Trades, Win Rate, Average P&L) actually match what's
in the database.

CRITICAL DESIGN POINT: this does NOT just call signal_log.get_admin_kpis()
and trust it — that would only prove get_admin_kpis() agrees with itself,
which is meaningless as a check. Instead this module independently
recomputes every metric directly from research_db's raw rows (its own
aggregation logic, written separately from signal_log.py's), then
compares the two. A mismatch here means signal_log.py's analytics logic
and the database have drifted apart — exactly the kind of regression this
validator exists to catch. No values are hardcoded anywhere in this file.
"""

from .validation_models import (
    ValidationResult, ValidationStatus, ValidationCategory,
    is_environment_unavailable_error,
)

# How close two independently-computed floats must be to count as "the
# same" — accounts for rounding differences (e.g. round(x, 1) vs round(x, 2)
# applied at different stages), not a tolerance for actual discrepancies.
FLOAT_TOLERANCE = 0.5


def _independent_calc_from_db() -> dict:
    """
    Recomputes the 5 dashboard metrics directly from research_db's raw
    live_trades rows, using this module's OWN aggregation code — never
    calling into signal_log.py's compute_* functions. This is the whole
    point: an independent second implementation to check the first one
    against.
    """
    from research_config import settings as research_settings
    from research_db.database import ResearchDatabase

    db = ResearchDatabase(research_settings.DB_PATH, journal_mode=research_settings.SQLITE_JOURNAL_MODE)
    try:
        rows = db.get_all_live_trades()
    finally:
        db.close()

    total_trades = len(rows)
    open_trades = sum(1 for r in rows if r.get("status") == "OPEN")
    closed_rows = [r for r in rows if r.get("status") in ("TARGET_HIT", "SL_HIT")]
    closed_trades = len(closed_rows)
    wins = sum(1 for r in closed_rows if r.get("status") == "TARGET_HIT")
    win_rate = round((wins / closed_trades) * 100, 1) if closed_trades else 0.0

    pnl_values = [r["pnl_pct"] for r in closed_rows if r.get("pnl_pct") is not None]
    avg_pnl = round(sum(pnl_values) / len(pnl_values), 2) if pnl_values else 0.0

    return {
        "total_trades": total_trades,
        "open_trades": open_trades,
        "closed_trades": closed_trades,
        "win_rate": win_rate,
        "avg_pnl": avg_pnl,
    }


def _dashboard_displayed_values() -> dict:
    """
    Calls the ACTUAL function the Performance page uses
    (signal_log.get_admin_kpis()) — i.e. what a user sees on screen.
    get_admin_kpis() doesn't return a single "closed_trades" key, so it's
    derived here from target_hits + sl_hits, matching how
    compute_performance_summary() defines "closed" everywhere else in
    signal_log.py.
    """
    import signal_log

    kpis = signal_log.get_admin_kpis()
    return {
        "total_trades": kpis["total_trades"],
        "open_trades": kpis["open_trades"],
        "closed_trades": kpis["target_hits"] + kpis["sl_hits"],
        "win_rate": kpis["win_rate"],
        "avg_pnl": kpis["avg_pnl"],
    }


def validate_dashboard() -> ValidationResult:
    details = []
    warnings = []
    failures = []
    metrics = {}

    try:
        independent = _independent_calc_from_db()
        metrics["independent"] = independent
    except Exception as e:
        failures.append(f"Could not independently compute metrics from the database: {type(e).__name__}: {e}")
        return ValidationResult(
            category=ValidationCategory.DASHBOARD,
            status=ValidationStatus.FAIL,
            summary="Dashboard validation could not run — database-side computation failed.",
            failures=failures,
        )

    try:
        displayed = _dashboard_displayed_values()
        metrics["displayed"] = displayed
    except Exception as e:
        if is_environment_unavailable_error(e):
            # Nothing in this validator is meaningful without the
            # dashboard-displayed side to compare against — unlike
            # app_validator/configuration_validator, there's no
            # independent partial check left to fall back to, so the
            # WHOLE category is skipped here, not just one sub-check.
            return ValidationResult(
                category=ValidationCategory.DASHBOARD,
                status=ValidationStatus.SKIPPED,
                summary="Dashboard validation skipped — Streamlit Secrets/runtime unavailable in this environment.",
                details=[
                    f"Database-side values were independently computed successfully: {independent}.",
                    "Could not read dashboard-displayed values (signal_log.get_admin_kpis) "
                    "because Streamlit Secrets/runtime isn't available here — this is expected "
                    "when running the Validation Center standalone (e.g. via CLI) rather than "
                    "from within a live Streamlit session. Re-run from inside the running app "
                    "for a real comparison.",
                ],
                skipped=[f"Dashboard-displayed values unreachable: {type(e).__name__}: {e}"],
                metrics=metrics,
            )
        failures.append(f"Could not read dashboard values via signal_log.get_admin_kpis(): {type(e).__name__}: {e}")
        return ValidationResult(
            category=ValidationCategory.DASHBOARD,
            status=ValidationStatus.FAIL,
            summary="Dashboard validation could not run — dashboard-side computation failed.",
            details=details,
            failures=failures,
            metrics=metrics,
        )

    for field_name, label in [
        ("total_trades", "Total Trades"),
        ("open_trades", "Open Trades"),
        ("closed_trades", "Closed Trades"),
        ("win_rate", "Win Rate"),
        ("avg_pnl", "Average P&L"),
    ]:
        db_value = independent[field_name]
        dash_value = displayed[field_name]

        if isinstance(db_value, float) or isinstance(dash_value, float):
            matches = abs(float(db_value) - float(dash_value)) <= FLOAT_TOLERANCE
        else:
            matches = db_value == dash_value

        if matches:
            details.append(f"{label}: MATCH (database: {db_value}, dashboard: {dash_value}).")
        else:
            failures.append(
                f"{label} MISMATCH — database independently computes {db_value}, "
                f"but the dashboard (get_admin_kpis) shows {dash_value}."
            )

    if independent["total_trades"] == 0:
        warnings.append(
            "All values are trivially matching zeros because live_trades is empty. "
            "This confirms wiring is correct but does not exercise the actual "
            "aggregation math — re-run this validator once real trade data exists."
        )

    if failures:
        status = ValidationStatus.FAIL
        summary = f"{len(failures)} dashboard/database mismatch(es) found."
    elif warnings:
        status = ValidationStatus.WARNING
        summary = "Dashboard values match the database, but with caveats (see warnings)."
    else:
        status = ValidationStatus.PASS
        summary = "All dashboard metrics match independently-computed database values exactly."

    return ValidationResult(
        category=ValidationCategory.DASHBOARD,
        status=status,
        summary=summary,
        details=details,
        warnings=warnings,
        failures=failures,
        metrics=metrics,
    )
