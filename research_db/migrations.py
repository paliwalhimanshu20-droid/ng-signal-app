"""
research_db/migrations.py

Same lightweight, dependency-free migration system used in NGSP-003A.1
(instrument_master/migrations.py) — intentionally duplicated rather than
imported, so this module has zero dependency on that package and stays
fully modular per spec.

A `schema_version` table tracks applied migrations. `ResearchDatabase.__init__()`
runs `run_migrations(conn)` automatically on every connect. The v1 baseline
(all 9 tables in schema.py) is frozen once shipped — future changes are
additive migrations only, never edits to CREATE_TABLES_SQL.

To add a future migration:
    1. Write a new `migration_00N_description(conn)` function below.
    2. Append (N, "description", migration_00N_description) to MIGRATIONS.
    3. Never edit or renumber a migration that has already shipped.
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
# Migration 1 — baseline. All 9 tables are created directly by
# schema.CREATE_TABLES_SQL in database.py's _init_schema(). This migration
# is a version marker only.
# ---------------------------------------------------------------------------
def migration_001_baseline(conn):
    pass  # no-op — schema.py already establishes the v1 tables


MIGRATIONS = [
    (1, "baseline schema (9 research & learning tables)", migration_001_baseline),
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
