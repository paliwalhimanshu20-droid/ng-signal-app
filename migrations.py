"""
instrument_master/migrations.py

Lightweight schema versioning/migration system — no ORM, no external
migration framework. A `schema_version` table tracks which numbered
migrations have been applied; on every DB connection, any migrations newer
than the current version are applied in order, inside the same connection
used by the rest of the app.

This exists so future schema changes (Research Engine fields, DNA Engine
fields, etc.) can be added WITHOUT editing schema.py's original
CREATE_TABLE_SQL and WITHOUT breaking anyone's existing database file —
new columns/tables just get bolted on the next time they open the DB.

To add a future migration:
    1. Write a new `migration_00N_description(conn)` function below.
    2. Append (N, "description", migration_00N_description) to MIGRATIONS.
    3. Never edit or renumber a migration that has already shipped —
       existing databases will have already recorded it as applied.
"""

import datetime as dt
import logging

logger = logging.getLogger(__name__)

SCHEMA_VERSION_TABLE = "schema_version"


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


def _ensure_version_table(conn):
    conn.execute(f"""
        CREATE TABLE IF NOT EXISTS {SCHEMA_VERSION_TABLE} (
            version     INTEGER PRIMARY KEY,
            applied_at  TEXT NOT NULL,
            description TEXT
        )
    """)


def current_version(conn) -> int:
    _ensure_version_table(conn)
    cur = conn.execute(f"SELECT MAX(version) AS v FROM {SCHEMA_VERSION_TABLE}")
    row = cur.fetchone()
    return row["v"] or 0


def _column_exists(conn, table: str, column: str) -> bool:
    cur = conn.execute(f"PRAGMA table_info({table})")
    return any(r["name"] == column for r in cur.fetchall())


def _add_column_if_missing(conn, table: str, column: str, coltype_and_default: str):
    if not _column_exists(conn, table, column):
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {coltype_and_default}")
        logger.info("  + added column %s.%s", table, column)


# ---------------------------------------------------------------------------
# Migration 1 — baseline. The original `instruments` table is created
# directly by schema.CREATE_TABLE_SQL (unchanged, per NGSP-003A.1). This
# migration is a version marker only, so schema_version correctly reflects
# "v1 already exists" for databases created before migrations existed.
# ---------------------------------------------------------------------------
def migration_001_baseline(conn):
    pass  # no-op — schema.py already establishes the v1 table


# ---------------------------------------------------------------------------
# Migration 2 — Research Scheduler fields + future research metadata fields.
# All additive (ALTER TABLE ADD COLUMN), all nullable/defaulted, so existing
# rows are unaffected and no data migration is required.
# ---------------------------------------------------------------------------
def migration_002_research_scheduler_and_metadata(conn):
    from . import schema
    table = schema.TABLE_NAME

    for col, coltype in schema.RESEARCH_SCHEDULER_COLUMNS:
        _add_column_if_missing(conn, table, col, coltype)

    for col, coltype in schema.RESEARCH_METADATA_COLUMNS:
        _add_column_if_missing(conn, table, col, coltype)


# ---------------------------------------------------------------------------
# Migration 3 — research_log table: one row per research run/attempt.
# Kept as a SEPARATE table (not columns on `instruments`) because this is
# the field that genuinely doesn't scale as columns — an instrument gets
# re-researched repeatedly over its life, so this is a one-to-many
# relationship, not one-to-one. See README "Scalability" section.
# ---------------------------------------------------------------------------
def migration_003_research_log_table(conn):
    conn.execute("""
        CREATE TABLE IF NOT EXISTS research_log (
            research_log_id        INTEGER PRIMARY KEY AUTOINCREMENT,
            instrument_key         TEXT NOT NULL,
            run_started_at         TEXT NOT NULL,
            run_completed_at       TEXT,
            research_status_before TEXT,
            research_status_after  TEXT,
            maturity_level_before  INTEGER,
            maturity_level_after   INTEGER,
            engine_version         TEXT,
            result_summary         TEXT,
            success                INTEGER,
            error_message          TEXT,
            created_at             TEXT NOT NULL,
            FOREIGN KEY (instrument_key) REFERENCES instruments(instrument_key)
        )
    """)
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_research_log_instrument "
        "ON research_log (instrument_key)"
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_research_log_started "
        "ON research_log (run_started_at)"
    )


# ---------------------------------------------------------------------------
# Migration 4 — indexes to support scheduler query patterns at scale
# (e.g. "give me the next 500 instruments due for research, in priority
# order" against 100,000+ rows).
# ---------------------------------------------------------------------------
def migration_004_scheduler_indexes(conn):
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_next_research_date "
        "ON instruments (next_research_scheduled_date)"
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_scheduler_due "
        "ON instruments (scheduler_enabled, active_status, "
        "next_research_scheduled_date, research_priority)"
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_research_lock "
        "ON instruments (research_lock_owner, research_lock_expires_at)"
    )


MIGRATIONS = [
    (1, "baseline schema (instruments table)", migration_001_baseline),
    (2, "add research scheduler + future research metadata fields", migration_002_research_scheduler_and_metadata),
    (3, "add research_log table for per-run history at scale", migration_003_research_log_table),
    (4, "add scheduler-related indexes", migration_004_scheduler_indexes),
]


def run_migrations(conn) -> list:
    """Apply any migrations newer than the DB's current version, in order."""
    _ensure_version_table(conn)
    current = current_version(conn)
    applied = []

    for version, description, fn in MIGRATIONS:
        if version <= current:
            continue
        logger.info("Applying migration %d: %s", version, description)
        fn(conn)
        conn.execute(
            f"INSERT INTO {SCHEMA_VERSION_TABLE} (version, applied_at, description) "
            f"VALUES (?, ?, ?)",
            (version, _now_iso(), description),
        )
        applied.append(version)

    conn.commit()
    if applied:
        logger.info("Migrations applied: %s (now at v%d)", applied, current_version(conn))
    else:
        logger.info("Schema already at latest version (v%d)", current)
    return applied
