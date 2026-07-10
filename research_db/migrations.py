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


# ---------------------------------------------------------------------------
# Migration 2 — adds live_trades, migrating the live BUY/SELL signal log
# off signal_log.csv and into the Research & Learning Database. Pure
# storage-backend swap: same columns/meaning as the old CSV, just SQLite-
# backed now so it benefits from the same GitHub-sync pattern as every
# other table here instead of a separate ad-hoc CSV push mechanism.
# ---------------------------------------------------------------------------
def migration_002_add_live_trades_table(conn):
    conn.execute("""
        CREATE TABLE IF NOT EXISTS live_trades (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
            signal_id           TEXT NOT NULL UNIQUE,
            timestamp           TEXT NOT NULL,
            instrument          TEXT NOT NULL,
            instrument_key      TEXT,
            signal              TEXT NOT NULL,
            trend               TEXT,
            confidence          TEXT,
            score               REAL,
            entry_price         REAL,
            sl                  REAL,
            t1                  REAL,
            t2                  REAL,
            status              TEXT NOT NULL DEFAULT 'OPEN',
            closed_price        REAL,
            closed_at           TEXT,
            pnl_pct             REAL,
            daily_trend_agree   TEXT,
            supertrend_agree    TEXT,
            market_trend_agree  TEXT,
            adx                 REAL,
            conviction_pct      REAL,
            expected_move_pct   REAL,
            t2_hit_at           TEXT,
            created_at          TEXT NOT NULL
        );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_live_trades_status ON live_trades (status);")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_live_trades_instrument ON live_trades (instrument, status);")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_live_trades_timestamp ON live_trades (timestamp);")


# ---------------------------------------------------------------------------
# Migration 3 — adds scan_snapshots (NGSP Phase 0, PR 6a). Independent of
# live_trades: this table holds the COMPLETE output of every background
# scan run by generate_signals.py — every watchlist instrument, every
# signal type (BUY/SELL/WATCH/HOLD), not just the ones that became a trade.
# live_trades structurally cannot answer "what did the full scan see" —
# only "what became a trade" — since it only ever receives rows that
# already passed the actionable BUY/SELL gate. This table is what lets the
# Scanner tab's "Full Scanned Universe" table and opportunity cards (PR 6b)
# read from the database instead of recomputing, without losing any
# information those views currently show.
#
# DESIGN — meant to evolve into Phase 1's Historical Data Warehouse feed,
# not be a throwaway cache:
#   - APPEND-ONLY, same convention as every other table in this DB except
#     live_trades' mutable outcome fields (see schema.py's design notes).
#     Every scan run adds a new batch of rows; nothing is ever updated or
#     deleted here. One row per instrument per scan.
#   - `scanned_at` is shared by every row in one scan batch (set once per
#     generate_signals.py run, not per row) and is both the grouping key
#     for "give me one full scan" and the ordering key for "give me the
#     latest scan" — no separate run-id needed.
#   - Every column here is a direct, unrenamed-in-meaning mapping from
#     scanner.py's full_df row dict (see scanner.run_scanner()'s
#     all_results.append({...}) block) — nothing invented, nothing
#     recomputed differently. Column names are snake_case versions of the
#     full_df keys (e.g. "Volume Ratio" -> volume_ratio, "Prob%" ->
#     prob_pct) for SQL-friendliness, same convention live_trades already
#     uses (e.g. full_df's "SL" -> live_trades.sl).
#   - This table intentionally does NOT try to be the Phase 1 warehouse
#     itself (that's Parquet+DuckDB per NGSP-003A.3, a different physical
#     design for years of tick-level history). It's meant to be a clean,
#     complete, queryable source Phase 1's ingestion can read from and
#     backfill into that warehouse, rather than Phase 1 having to
#     reconstruct scan history from scratch.
#   - GROWTH NOTE for whoever revisits this: at ~40 instruments per scan,
#     every 30 min in market hours, this is roughly 1,900 rows/trading day
#     — fine for SQLite/git-sync at Phase 0's scale, but worth watching
#     over months. Pruning/archiving strategy is explicitly a Phase 1
#     concern (that's what the warehouse migration is for), not solved
#     here.
# ---------------------------------------------------------------------------
def migration_003_add_scan_snapshots_table(conn):
    conn.execute("""
        CREATE TABLE IF NOT EXISTS scan_snapshots (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
            scanned_at          TEXT NOT NULL,
            instrument          TEXT NOT NULL,
            instrument_key      TEXT,
            sector              TEXT,
            signal              TEXT NOT NULL,
            confidence          TEXT,
            trend               TEXT,
            daily_trend         TEXT,
            market_trend        TEXT,
            supertrend          TEXT,
            supertrend_value    REAL,
            regime              TEXT,
            adx                 REAL,
            conviction_pct      REAL,
            daily_trend_agree   TEXT,
            supertrend_agree    TEXT,
            market_trend_agree  TEXT,
            score               REAL,
            prob_pct            REAL,
            rsi                 REAL,
            volume_ratio        REAL,
            volume_label        TEXT,
            expected_move_pct   REAL,
            rr                  REAL,
            price               REAL,
            sl                  REAL,
            t1                  REAL,
            t2                  REAL,
            reason              TEXT,
            created_at          TEXT NOT NULL
        );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_scan_snapshots_scanned_at ON scan_snapshots (scanned_at);")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_scan_snapshots_instrument ON scan_snapshots (instrument, scanned_at);")


def migration_004_add_confidence_source_column(conn):
    """
    PR 8, Part 4 — the ONE schema change this integration needs.
    strategy_test_results already has strategy_name/entry_rules/exit_rules/
    stop_loss_model/target_model — everything the Research Contract's
    "Best Strategy" field needs. "Confidence Source" has no existing home
    anywhere in the schema (checked research_experiments,
    strategy_test_results, performance_metrics — none of them), so this
    adds one nullable TEXT column rather than a new table. The value is
    also written into performance_metrics.extra_metrics (JSON) as a
    fallback by strategy_lab/research_bridge.py, so this column is a
    read-performance convenience, not the only place the data lives.
    """
    _add_column_if_missing(conn, "strategy_test_results", "confidence_source", "TEXT")


MIGRATIONS = [
    (1, "baseline schema (9 research & learning tables)", migration_001_baseline),
    (2, "add live_trades table (migrated from signal_log.csv)", migration_002_add_live_trades_table),
    (3, "add scan_snapshots table (NGSP Phase 0, PR 6a)", migration_003_add_scan_snapshots_table),
    (4, "add strategy_test_results.confidence_source column (PR 8, Part 4)", migration_004_add_confidence_source_column),
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
