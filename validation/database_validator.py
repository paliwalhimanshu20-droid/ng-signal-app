"""
validation/database_validator.py

Validates the Research & Learning Database (research_db) — the storage
layer behind live_trades (NGSP-003A.2) and the research/backtesting
tables (NGSP-003A.1's sibling module). Checks connectivity, schema
completeness, read/write access, structural integrity, and row counts.

Reuses research_db/validation.py's existing validate() for
research_experiments-side referential-integrity checks rather than
re-implementing it (per NGSP-003B.1's "No duplicated logic" standard) —
this module adds the broader connectivity/table-existence/read-write/
live_trades checks that validate() doesn't cover, since that module is
scoped narrowly to the experiments schema by design (see its own
docstring).
"""

import os
import sqlite3
import time

from .validation_models import ValidationResult, ValidationStatus, ValidationCategory

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _expected_tables() -> list:
    """Every TABLE_* constant declared in research_db/schema.py, read
    dynamically so this stays correct as new tables/migrations are added
    without needing this validator edited too."""
    from research_db import schema
    return [
        value for name, value in vars(schema).items()
        if name.startswith("TABLE_") and isinstance(value, str)
    ]


def validate_database() -> ValidationResult:
    details = []
    warnings = []
    failures = []
    metrics = {}

    # ---- 1. Settings / DB path resolution ----
    try:
        from research_config import settings as research_settings
    except Exception as e:
        failures.append(f"Could not import research_config.settings: {type(e).__name__}: {e}")
        return ValidationResult(
            category=ValidationCategory.DATABASE,
            status=ValidationStatus.FAIL,
            summary="Cannot resolve database configuration — research_config.settings import failed.",
            failures=failures,
        )

    db_path = research_settings.DB_PATH
    db_existed_before = os.path.exists(db_path)
    details.append(f"Resolved database path: {db_path}")
    if db_existed_before:
        details.append("Database file already exists on disk.")
    else:
        warnings.append(
            "Database file does not exist yet on disk — opening it will create a fresh, "
            "empty database (this is expected on a brand-new deployment or before the "
            "one-time signal_log.csv migration has been run; see NGSP-003A.2 notes)."
        )

    # ---- 2. Connection ----
    try:
        from research_db.database import ResearchDatabase
        db = ResearchDatabase(db_path, journal_mode=research_settings.SQLITE_JOURNAL_MODE)
        details.append("Database connection established successfully.")
    except Exception as e:
        failures.append(f"Database connection failed: {type(e).__name__}: {e}")
        return ValidationResult(
            category=ValidationCategory.DATABASE,
            status=ValidationStatus.FAIL,
            summary="Could not connect to the database.",
            details=details,
            failures=failures,
        )

    try:
        # ---- 3. Required tables exist ----
        cur = db.conn.execute("SELECT name FROM sqlite_master WHERE type='table'")
        actual_tables = {row["name"] for row in cur.fetchall()}
        expected_tables = set(_expected_tables())
        missing_tables = expected_tables - actual_tables

        if missing_tables:
            failures.append(f"Missing expected table(s): {', '.join(sorted(missing_tables))}")
        else:
            details.append(f"All {len(expected_tables)} expected table(s) present: {', '.join(sorted(expected_tables))}")

        # ---- 4. Read access ----
        try:
            live_trade_rows = db.get_all_live_trades()
            details.append(f"Read access confirmed (live_trades: {len(live_trade_rows)} row(s) read).")
        except Exception as e:
            failures.append(f"Read access failed on live_trades: {type(e).__name__}: {e}")
            live_trade_rows = []

        # ---- 5. Write access (safe — inserted then rolled back, never committed) ----
        write_probe_id = f"__validation_write_probe_{int(time.time())}__"
        try:
            now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            db.conn.execute(
                """INSERT INTO live_trades
                   (signal_id, timestamp, instrument, signal, status, created_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (write_probe_id, now, "__VALIDATION_TEST__", "BUY", "OPEN", now),
            )
            db.conn.rollback()
            details.append("Write access confirmed (test row inserted and rolled back — nothing persisted).")
        except Exception as e:
            failures.append(f"Write access failed: {type(e).__name__}: {e}")
            try:
                db.conn.rollback()
            except Exception:
                pass

        # ---- 6. Schema integrity ----
        # 6a. Reuse the existing research_experiments-side integrity checker.
        try:
            from research_db import validation as research_db_validation
            integrity_report = research_db_validation.validate(db)
            if integrity_report["passed"]:
                details.append("research_experiments schema integrity check passed (research_db.validation).")
            else:
                for issue in integrity_report["issues"]:
                    warnings.append(f"Schema integrity (research_experiments): {issue}")
        except Exception as e:
            warnings.append(f"Could not run research_db.validation.validate(): {type(e).__name__}: {e}")

        # 6b. live_trades-specific integrity — required fields + valid status values.
        try:
            from research_db import schema as research_schema
            valid_statuses = research_schema.LIVE_TRADE_STATUS_VALUES
            placeholders = ", ".join(["?"] * len(valid_statuses))
            cur = db.conn.execute(
                f"SELECT COUNT(*) as c FROM live_trades WHERE status NOT IN ({placeholders})",
                valid_statuses,
            )
            bad_status_count = cur.fetchone()["c"]
            if bad_status_count:
                warnings.append(f"{bad_status_count} live_trades row(s) have a status outside {valid_statuses}.")

            for required_field in ("signal_id", "instrument", "signal", "timestamp"):
                cur = db.conn.execute(
                    f"SELECT COUNT(*) as c FROM live_trades WHERE {required_field} IS NULL OR {required_field} = ''"
                )
                c = cur.fetchone()["c"]
                if c:
                    warnings.append(f"{c} live_trades row(s) have a NULL/empty required field '{required_field}'.")

            if not bad_status_count and all(
                db.conn.execute(
                    f"SELECT COUNT(*) as c FROM live_trades WHERE {f} IS NULL OR {f} = ''"
                ).fetchone()["c"] == 0
                for f in ("signal_id", "instrument", "signal", "timestamp")
            ):
                details.append("live_trades schema integrity check passed (valid statuses, no missing required fields).")
        except Exception as e:
            warnings.append(f"Could not run live_trades integrity check: {type(e).__name__}: {e}")

        # ---- 7. Row counts ----
        total = len(live_trade_rows)
        open_count = sum(1 for r in live_trade_rows if r.get("status") == "OPEN")
        closed_count = sum(1 for r in live_trade_rows if r.get("status") in ("TARGET_HIT", "SL_HIT"))
        expired_count = sum(1 for r in live_trade_rows if r.get("status") == "EXPIRED")

        metrics.update({
            "live_trades_total": total,
            "live_trades_open": open_count,
            "live_trades_closed": closed_count,
            "live_trades_expired": expired_count,
            "research_experiments_total": db.count_experiments(),
        })
        details.append(
            f"live_trades row counts — total: {total}, OPEN: {open_count}, "
            f"CLOSED: {closed_count}, EXPIRED: {expired_count}."
        )
        details.append(f"research_experiments row count: {metrics['research_experiments_total']}.")

        if total == 0:
            warnings.append(
                "live_trades has zero rows. Expected if no signals have been generated yet "
                "and the one-time CSV migration hasn't been run — not a structural problem."
            )

    finally:
        db.close()

    if failures:
        status = ValidationStatus.FAIL
        summary = f"{len(failures)} database failure(s) found."
    elif warnings:
        status = ValidationStatus.WARNING
        summary = f"Database is structurally sound; {len(warnings)} warning(s) to review."
    else:
        status = ValidationStatus.PASS
        summary = "Database connection, schema, read/write access, and integrity all verified."

    return ValidationResult(
        category=ValidationCategory.DATABASE,
        status=status,
        summary=summary,
        details=details,
        warnings=warnings,
        failures=failures,
        metrics=metrics,
    )
