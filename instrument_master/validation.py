"""
instrument_master/validation.py

Post-load / post-update integrity checks for the Instrument Master.

REDESIGNED (this session) from a single flat "any issue = failure" report
into a severity-based rule framework, after a real production run showed
the old design's central flaw: it could not distinguish a genuine database
integrity failure from a well-known, benign characteristic of Upstox's raw
instrument feed (e.g. index reference rows legitimately carrying
lot_size=0, or a symbol briefly having two ACTIVE listings during a
corporate action). Under the old design both looked identical — one
`issues` list, `passed = len(issues) == 0` — so either everything was
fatal or nothing was, with no way to express "this is worth a human
noticing" versus "this must never ship."

Design:
  - Every check is an independent "rule" function: `(conn) -> list[ValidationIssue]`.
    Adding a new check means adding a new rule function and appending it to
    RULES — no other code changes, so this stays open to extension (new
    corporate-action patterns, new schema constraints, etc.) without
    becoming a monolith.
  - Every issue carries a `Severity` (INFO / WARNING / FAIL) AND full
    row-level identifying detail (instrument_key, trading_symbol, exchange,
    segment, expiry, reason) — never just a count. A count tells you
    something is wrong; the row detail is what lets a human or a future
    Research Engine actually act on it. This is the audit trail, not a
    side effect of it.
  - `validate(db)` runs every rule, partitions the results by severity, and
    returns `passed = (failure_count == 0)`. WARNING and INFO issues are
    always fully reported but never block a commit — only FAIL does.
    Nothing is ever silently dropped: a rule that finds nothing simply
    contributes zero issues.

This module deliberately does NOT delete or exclude any row it flags.
Flag-and-report only; removal requires a human decision (or a future,
separately-reviewed rule) backed by objective evidence, never an inference
made in the same run that raised the flag.
"""

from __future__ import annotations

import datetime as dt
import logging
import re
from dataclasses import dataclass
from enum import Enum

from . import schema

logger = logging.getLogger(__name__)


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


# ---------------------------------------------------------------------------
# Framework primitives
# ---------------------------------------------------------------------------

class Severity(str, Enum):
    """Per-issue severity. String-valued so it prints/serializes cleanly.

    INFO    — purely operational visibility; never blocks anything
              (e.g. a stale research lock — worth knowing, not a defect).
    WARNING — a real, logged anomaly that is a known/plausible
              characteristic of the source data (or otherwise recoverable)
              rather than corruption. Does NOT stop the sync or the commit.
    FAIL    — a genuine integrity failure: structural, schema-level, or a
              tradable-instrument anomaly with no benign explanation. Stops
              the sync (non-zero exit) and the DB is not committed.
    """
    INFO = "INFO"
    WARNING = "WARNING"
    FAIL = "FAIL"


@dataclass(frozen=True)
class ValidationIssue:
    """One finding from one rule, at row-level granularity wherever the
    rule concerns specific rows. `instrument_key` may hold a comma-joined
    list for rules that report on a *group* of rows (e.g. a duplicate
    group) rather than a single row — callers that need an exact per-row
    count should split on ',' rather than assuming one issue == one row.
    """
    rule: str
    severity: Severity
    message: str
    instrument_key: str | None = None
    trading_symbol: str | None = None
    exchange: str | None = None
    segment: str | None = None
    expiry: str | None = None
    reason: str | None = None

    def as_dict(self) -> dict:
        return {
            "rule": self.rule,
            "severity": self.severity.value,
            "message": self.message,
            "instrument_key": self.instrument_key,
            "trading_symbol": self.trading_symbol,
            "exchange": self.exchange,
            "segment": self.segment,
            "expiry": self.expiry,
            "reason": self.reason,
        }


# Instrument types that are legitimately non-tradable reference instruments
# on Upstox — these are known, expected to lack lot_size/tick_size, and
# should never trip a FAIL. Extend this set only with evidence (a specific
# instrument_type confirmed non-tradable), never to silence an unexplained
# finding.
NON_TRADABLE_INSTRUMENT_TYPES = {"INDEX"}

# If invalid lot_size/tick_size shows up on an instrument_type NOT in
# NON_TRADABLE_INSTRUMENT_TYPES (i.e. something that should be tradable —
# EQ, FUT, CE, PE, COM, ...), that is treated as a genuine integrity
# concern (FAIL), not a benign quirk. Unlike the duplicate-contract check,
# no percentage tolerance applies here — even one such row means the
# source record itself is broken.


# ---------------------------------------------------------------------------
# Rules
# ---------------------------------------------------------------------------
# Each rule takes the raw sqlite3 connection and returns a list of
# ValidationIssue. A rule that finds nothing returns an empty list. A rule
# that itself raises is treated as a FAIL by validate() below (a broken
# check must never silently pass).

def rule_nonzero_instrument_count(conn) -> list[ValidationIssue]:
    cur = conn.execute(f"SELECT COUNT(*) AS c FROM {schema.TABLE_NAME}")
    c = cur.fetchone()["c"]
    if c == 0:
        return [ValidationIssue(
            rule="nonzero_instrument_count",
            severity=Severity.FAIL,
            message="Instrument Master contains zero rows after sync",
            reason="Either the download/parse pipeline produced no records, "
                   "or every record failed insertion. Never a benign state.",
        )]
    return []


def rule_required_fields_present(conn) -> list[ValidationIssue]:
    issues = []
    for f in ["instrument_key", "trading_symbol", "exchange", "segment"]:
        cur = conn.execute(
            f"SELECT rowid, instrument_key, trading_symbol, exchange, segment, expiry "
            f"FROM {schema.TABLE_NAME} WHERE {f} IS NULL OR {f} = ''"
        )
        for r in cur.fetchall():
            issues.append(ValidationIssue(
                rule="required_fields_present",
                severity=Severity.FAIL,
                message=f"Required field '{f}' is NULL/empty",
                instrument_key=r["instrument_key"] or f"rowid:{r['rowid']}",
                trading_symbol=r["trading_symbol"], exchange=r["exchange"],
                segment=r["segment"], expiry=r["expiry"],
                reason="Structural parsing/insert failure, not a market-data quirk — "
                       "a required column should never be empty at this stage.",
            ))
    return issues


def rule_malformed_instrument_key(conn) -> list[ValidationIssue]:
    """instrument_key must match Upstox's own EXCHANGE_SEGMENT|IDENTIFIER
    convention (e.g. 'NSE_EQ|INE002A01018', 'MCX_FO|NATURALGAS...',
    'NSE_INDEX|Nifty 50'). Note the prefix legitimately contains
    underscores (EXCHANGE_SEGMENT) and the identifier half can legitimately
    contain spaces for index display names — the pattern only requires a
    non-empty EXCHANGE_SEGMENT prefix, a single '|' separator, and a
    non-empty identifier, not that either half be a single "word"."""
    pattern = re.compile(r"^[A-Za-z0-9_]+\|.+$")
    issues = []
    cur = conn.execute(f"SELECT rowid, instrument_key, trading_symbol, exchange, segment FROM {schema.TABLE_NAME}")
    for r in cur.fetchall():
        key = r["instrument_key"]
        if not key or not pattern.match(key):
            issues.append(ValidationIssue(
                rule="malformed_instrument_key",
                severity=Severity.FAIL,
                message="instrument_key does not match the expected EXCHANGE_SEGMENT|IDENTIFIER format",
                instrument_key=key or f"rowid:{r['rowid']}",
                trading_symbol=r["trading_symbol"], exchange=r["exchange"], segment=r["segment"],
                reason="Malformed keys break every downstream lookup (warehouse registry, "
                       "signal log, research scheduler) that joins on instrument_key.",
            ))
    return issues


def rule_duplicate_primary_keys(conn) -> list[ValidationIssue]:
    """Defensive read-time check. Should be structurally impossible under
    the PRIMARY KEY constraint on instrument_key — if this ever fires, the
    DB file itself is corrupted or was written outside the normal
    insert/update code path (e.g. a manual SQL edit)."""
    cur = conn.execute(
        f"SELECT instrument_key, COUNT(*) AS c FROM {schema.TABLE_NAME} "
        f"GROUP BY instrument_key HAVING c > 1"
    )
    issues = []
    for r in cur.fetchall():
        issues.append(ValidationIssue(
            rule="duplicate_primary_keys",
            severity=Severity.FAIL,
            message=f"instrument_key appears {r['c']} times — impossible under the PRIMARY KEY constraint",
            instrument_key=r["instrument_key"],
            reason="Indicates DB corruption or an out-of-band write, not a sync-time issue.",
        ))
    return issues


def rule_duplicate_active_contracts(conn) -> list[ValidationIssue]:
    """Flags ACTIVE instruments that share a full contract identity.

    FIXED (this session): the original check grouped only by
    (trading_symbol, exchange, segment, expiry) — omitting instrument_type,
    strike, and option_type. That is too coarse: it's the exact tuple that
    would already treat every leg of an options chain (same underlying +
    expiry, many strikes) as "duplicates" if Upstox ever reused a plain
    underlying symbol across strikes. Grouping by the full contract
    identity first removes that entire class of false positive.

    Any group that still collides after that refinement is a genuinely
    interesting case, but not automatically corruption: Upstox is known to
    keep both the old and new instrument_key ACTIVE for a transition
    window during corporate actions — renames, demergers, relistings (this
    project hit exactly this with TATAMOTORS -> TMPV post-demerger).
    Classified WARNING, not FAIL: real, logged, and left for a human/
    scheduled review to resolve — never auto-deleted, since telling the
    'old' listing from the 'new' one requires context (e.g. corporate
    action date) this rule doesn't have.
    """
    cur = conn.execute(f"""
        SELECT trading_symbol, exchange, segment, expiry, instrument_type, strike, option_type,
               COUNT(*) AS c, GROUP_CONCAT(instrument_key) AS keys
        FROM {schema.TABLE_NAME}
        WHERE active_status = 'ACTIVE'
        GROUP BY trading_symbol, exchange, segment, expiry, instrument_type, strike, option_type
        HAVING c > 1
    """)
    issues = []
    for r in cur.fetchall():
        issues.append(ValidationIssue(
            rule="duplicate_active_contracts",
            severity=Severity.WARNING,
            message=f"{r['c']} ACTIVE instruments share identical (symbol, exchange, segment, "
                    f"expiry, instrument_type, strike, option_type)",
            instrument_key=r["keys"],
            trading_symbol=r["trading_symbol"], exchange=r["exchange"],
            segment=r["segment"], expiry=r["expiry"],
            reason="Likely a corporate-action transition window (rename/demerger/relisting) where "
                   "Upstox briefly lists both the old and new instrument_key as ACTIVE. Preserved, "
                   "not deleted — flag for manual/scheduled review, not treated as corruption.",
        ))
    return issues


def _lot_or_tick_rule(conn, column: str, rule_name: str) -> list[ValidationIssue]:
    cur = conn.execute(f"""
        SELECT instrument_key, trading_symbol, exchange, segment, expiry, instrument_type
        FROM {schema.TABLE_NAME}
        WHERE {column} IS NOT NULL AND {column} <= 0
    """)
    issues = []
    for r in cur.fetchall():
        itype = (r["instrument_type"] or "").upper()
        if itype in NON_TRADABLE_INSTRUMENT_TYPES:
            severity = Severity.WARNING
            reason = (
                f"instrument_type='{itype}' is a non-tradable reference instrument — Upstox "
                f"legitimately sets {column}=0 for these rather than NULL. Known, benign."
            )
        else:
            severity = Severity.FAIL
            reason = (
                f"instrument_type='{itype or 'UNKNOWN'}' is expected to be tradable and must have a "
                f"positive {column}. No known benign explanation — treated as real data corruption."
            )
        issues.append(ValidationIssue(
            rule=rule_name,
            severity=severity,
            message=f"{column} <= 0",
            instrument_key=r["instrument_key"], trading_symbol=r["trading_symbol"],
            exchange=r["exchange"], segment=r["segment"], expiry=r["expiry"],
            reason=reason,
        ))
    return issues


def rule_lot_size_valid(conn) -> list[ValidationIssue]:
    return _lot_or_tick_rule(conn, "lot_size", "lot_size_valid")


def rule_tick_size_valid(conn) -> list[ValidationIssue]:
    return _lot_or_tick_rule(conn, "tick_size", "tick_size_valid")


def rule_ngsp_field_defaults(conn) -> list[ValidationIssue]:
    issues = []
    for f in ["research_status", "research_priority", "research_maturity_level", "active_status"]:
        cur = conn.execute(f"SELECT instrument_key FROM {schema.TABLE_NAME} WHERE {f} IS NULL")
        for r in cur.fetchall():
            issues.append(ValidationIssue(
                rule="ngsp_field_defaults",
                severity=Severity.FAIL,
                message=f"'{f}' is NULL despite a NOT NULL DEFAULT in schema",
                instrument_key=r["instrument_key"],
                reason="Insert/update logic bypassed the schema default — a code bug, not a "
                       "market-data issue.",
            ))
    return issues


def rule_research_status_enum(conn) -> list[ValidationIssue]:
    placeholders = ", ".join(["?"] * len(schema.RESEARCH_STATUS_VALUES))
    cur = conn.execute(
        f"SELECT instrument_key, research_status FROM {schema.TABLE_NAME} "
        f"WHERE research_status NOT IN ({placeholders})",
        schema.RESEARCH_STATUS_VALUES,
    )
    issues = []
    for r in cur.fetchall():
        issues.append(ValidationIssue(
            rule="research_status_enum",
            severity=Severity.FAIL,
            message=f"research_status='{r['research_status']}' is not a recognized enum value",
            instrument_key=r["instrument_key"],
            reason=f"Allowed values: {schema.RESEARCH_STATUS_VALUES}",
        ))
    return issues


def rule_scheduler_field_defaults(conn) -> list[ValidationIssue]:
    issues = []
    for f in ["scheduler_enabled", "research_attempts_count"]:
        cur = conn.execute(f"SELECT instrument_key FROM {schema.TABLE_NAME} WHERE {f} IS NULL")
        for r in cur.fetchall():
            issues.append(ValidationIssue(
                rule="scheduler_field_defaults",
                severity=Severity.FAIL,
                message=f"'{f}' is NULL despite a NOT NULL DEFAULT in schema",
                instrument_key=r["instrument_key"],
                reason="Code bug in the insert/update path, not a market-data issue.",
            ))
    return issues


def rule_research_log_referential_integrity(conn) -> list[ValidationIssue]:
    cur = conn.execute(f"""
        SELECT rl.research_log_id, rl.instrument_key FROM {schema.RESEARCH_LOG_TABLE} rl
        WHERE NOT EXISTS (
            SELECT 1 FROM {schema.TABLE_NAME} i WHERE i.instrument_key = rl.instrument_key
        )
    """)
    issues = []
    for r in cur.fetchall():
        issues.append(ValidationIssue(
            rule="research_log_referential_integrity",
            severity=Severity.WARNING,
            message="research_log row references an instrument_key that no longer exists",
            instrument_key=r["instrument_key"],
            reason=f"research_log_id={r['research_log_id']} — an orphaned history row. Does not "
                   f"corrupt the instrument_master table itself; worth cleaning up, not blocking.",
        ))
    return issues


def rule_stale_research_locks(conn) -> list[ValidationIssue]:
    now_iso = _now_iso()
    cur = conn.execute(f"""
        SELECT instrument_key, research_lock_owner, research_lock_expires_at
        FROM {schema.TABLE_NAME}
        WHERE research_lock_owner IS NOT NULL
          AND research_lock_expires_at IS NOT NULL
          AND research_lock_expires_at < ?
    """, (now_iso,))
    issues = []
    for r in cur.fetchall():
        issues.append(ValidationIssue(
            rule="stale_research_locks",
            severity=Severity.INFO,
            message="Expired research lock still held — a worker likely died mid-run",
            instrument_key=r["instrument_key"],
            reason=f"owner={r['research_lock_owner']}, expired_at={r['research_lock_expires_at']}",
        ))
    return issues


# Registry — append new rules here. Order doesn't affect correctness, only
# the order issues appear in the report.
RULES = [
    rule_nonzero_instrument_count,
    rule_required_fields_present,
    rule_malformed_instrument_key,
    rule_duplicate_primary_keys,
    rule_ngsp_field_defaults,
    rule_research_status_enum,
    rule_scheduler_field_defaults,
    rule_duplicate_active_contracts,
    rule_lot_size_valid,
    rule_tick_size_valid,
    rule_research_log_referential_integrity,
    rule_stale_research_locks,
]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def validate(db) -> dict:
    """Runs every rule in RULES and returns a structured audit report.

    `passed` now means "zero FAIL-severity issues" — WARNING and INFO
    issues are fully reported but never flip `passed` to False. This is
    the field scripts/run_update.py checks to decide its exit code, so
    this is also, in effect, the workflow's commit gate.
    """
    conn = db.conn
    total = db.count()

    all_issues: list[ValidationIssue] = []
    for rule_fn in RULES:
        try:
            all_issues.extend(rule_fn(conn))
        except Exception as e:
            all_issues.append(ValidationIssue(
                rule=getattr(rule_fn, "__name__", "unknown_rule"),
                severity=Severity.FAIL,
                message=f"Validation rule crashed: {type(e).__name__}: {e}",
                reason="A rule raising an exception is itself treated as a FAIL — a broken check "
                       "must never silently pass.",
            ))

    info = [i for i in all_issues if i.severity == Severity.INFO]
    warnings = [i for i in all_issues if i.severity == Severity.WARNING]
    failures = [i for i in all_issues if i.severity == Severity.FAIL]

    # "Quarantined" = rows flagged at WARNING (not FAIL, not deleted — just
    # logged for review). Counted per-row even for group-style issues whose
    # instrument_key field holds a comma-joined list.
    quarantined_count = 0
    for i in warnings:
        if i.instrument_key:
            quarantined_count += len(i.instrument_key.split(","))

    report = {
        "generated_at": _now_iso(),
        "total_rows": total,
        "rules_run": len(RULES),
        "info": [i.as_dict() for i in info],
        "warnings": [i.as_dict() for i in warnings],
        "failures": [i.as_dict() for i in failures],
        "info_count": len(info),
        "warning_count": len(warnings),
        "failure_count": len(failures),
        "quarantined_count": quarantined_count,
        "passed": len(failures) == 0,
        # Back-compat aliases for any older caller expecting the pre-redesign
        # flat shape (issues_found / issues as plain strings).
        "issues_found": len(warnings) + len(failures),
        "issues": [f"[{i.severity.value}] {i.message} ({i.instrument_key})" for i in warnings + failures],
    }
    return report


def print_report(report: dict):
    print("\n=== Instrument Master Validation ===")
    print(f"  Total rows:      {report['total_rows']:,}")
    print(f"  Rules run:       {report['rules_run']}")
    print(f"  Info:            {report['info_count']}")
    print(f"  Warnings:        {report['warning_count']}  (quarantined rows: {report['quarantined_count']})")
    print(f"  Failures:        {report['failure_count']}")

    if report["failure_count"]:
        print("\n  --- FAIL (blocks commit) ---")
        for i in report["failures"]:
            print(f"    [{i['rule']}] {i['message']} | key={i['instrument_key']} | {i['reason']}")

    if report["warning_count"]:
        print("\n  --- WARNING (logged, does not block commit) ---")
        for i in report["warnings"]:
            print(f"    [{i['rule']}] {i['message']} | key={i['instrument_key']} | {i['reason']}")

    if report["info_count"]:
        print("\n  --- INFO ---")
        for i in report["info"]:
            print(f"    [{i['rule']}] {i['message']} | key={i['instrument_key']}")

    print()
    if report["passed"]:
        status = "PASSED" if not report["warning_count"] else "PASSED WITH WARNINGS"
        print(f"  Status: {status}")
    else:
        print(f"  Status: FAILED — {report['failure_count']} genuine integrity failure(s)")
    print("=====================================\n")
