"""
instrument_master/validation.py

Post-load / post-update integrity checks. Raises no exceptions by default —
returns a report dict so callers (scripts, CI) can decide how to react
(e.g. fail a GitHub Action if critical issues are found).
"""

import logging
import datetime as dt

from . import schema

logger = logging.getLogger(__name__)


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


def validate(db) -> dict:
    issues = []
    conn = db.conn

    total = db.count()

    # 1. Duplicate instrument_key (shouldn't be possible with PK, but check
    #    for near-duplicates: same trading_symbol+exchange+segment+expiry
    #    mapped to >1 active instrument_key, which would indicate a rollover
    #    that wasn't handled correctly).
    dup_cur = conn.execute(f"""
        SELECT trading_symbol, exchange, segment, expiry, COUNT(*) as c
        FROM {schema.TABLE_NAME}
        WHERE active_status = 'ACTIVE'
        GROUP BY trading_symbol, exchange, segment, expiry
        HAVING c > 1
    """)
    dup_rows = dup_cur.fetchall()
    if dup_rows:
        issues.append(
            f"{len(dup_rows)} groups of active instruments share the same "
            f"(symbol, exchange, segment, expiry) — possible rollover handling issue"
        )

    # 2. Required fields NULL
    for field in ["instrument_key", "trading_symbol", "exchange", "segment"]:
        cur = conn.execute(
            f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE {field} IS NULL OR {field} = ''"
        )
        c = cur.fetchone()["c"]
        if c:
            issues.append(f"{c} rows have NULL/empty required field '{field}'")

    # 3. lot_size / tick_size sanity (only where present — some index rows
    #    legitimately have NULL lot_size)
    cur = conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE lot_size IS NOT NULL AND lot_size <= 0"
    )
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} rows have lot_size <= 0")

    cur = conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE tick_size IS NOT NULL AND tick_size <= 0"
    )
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} rows have tick_size <= 0")

    # 4. NG Signal Pro fields must never be NULL for status/priority/maturity
    for field in ["research_status", "research_priority", "research_maturity_level", "active_status"]:
        cur = conn.execute(
            f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE {field} IS NULL"
        )
        c = cur.fetchone()["c"]
        if c:
            issues.append(f"{c} rows have NULL '{field}' (should always have a default)")

    # 5. research_status must be one of the allowed enum values
    placeholders = ", ".join(["?"] * len(schema.RESEARCH_STATUS_VALUES))
    cur = conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE research_status NOT IN ({placeholders})",
        schema.RESEARCH_STATUS_VALUES,
    )
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} rows have an invalid research_status value")

    # 6. Scheduler fields must never be NULL where they have NOT NULL defaults
    for field in ["scheduler_enabled", "research_attempts_count"]:
        cur = conn.execute(
            f"SELECT COUNT(*) as c FROM {schema.TABLE_NAME} WHERE {field} IS NULL"
        )
        c = cur.fetchone()["c"]
        if c:
            issues.append(f"{c} rows have NULL '{field}' (should always have a default)")

    # 7. research_log referential integrity — every logged run must point
    #    at an instrument that still exists (even if INACTIVE).
    cur = conn.execute(f"""
        SELECT COUNT(*) as c FROM {schema.RESEARCH_LOG_TABLE} rl
        WHERE NOT EXISTS (
            SELECT 1 FROM {schema.TABLE_NAME} i WHERE i.instrument_key = rl.instrument_key
        )
    """)
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} research_log rows reference an instrument_key that no longer exists")

    # 8. Stale locks — a lock past its expiry that was never released is not
    #    a hard failure, just worth surfacing (a scheduler worker likely died).
    now_iso = _now_iso()
    cur = conn.execute(f"""
        SELECT COUNT(*) as c FROM {schema.TABLE_NAME}
        WHERE research_lock_owner IS NOT NULL
          AND research_lock_expires_at IS NOT NULL
          AND research_lock_expires_at < ?
    """, (now_iso,))
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} rows hold an expired research lock (stale — a worker likely died mid-run)")

    report = {
        "total_rows": total,
        "issues_found": len(issues),
        "issues": issues,
        "passed": len(issues) == 0,
    }
    return report


def print_report(report: dict):
    print("\n=== Instrument Master Validation ===")
    print(f"  Total rows: {report['total_rows']:,}")
    if report["passed"]:
        print("  Status: PASSED — no issues found")
    else:
        print(f"  Status: {report['issues_found']} ISSUE(S) FOUND")
        for issue in report["issues"]:
            print(f"    - {issue}")
    print("=====================================\n")
