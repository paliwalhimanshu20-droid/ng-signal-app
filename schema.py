"""
instrument_master/schema.py

Authoritative table definition + constants for status/priority/maturity enums.
Every other module imports these constants rather than using string literals
directly, so the allowed values live in exactly one place.
"""

TABLE_NAME = "instruments"

CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {TABLE_NAME} (
    -- General
    instrument_key          TEXT PRIMARY KEY,
    trading_symbol          TEXT NOT NULL,
    exchange                TEXT NOT NULL,
    segment                 TEXT NOT NULL,
    asset_class              TEXT,
    instrument_type          TEXT,
    display_name             TEXT,
    isin                     TEXT,

    -- Trading
    lot_size                 INTEGER,
    tick_size                REAL,
    freeze_quantity           INTEGER,
    expiry                    TEXT,       -- ISO date string, NULL for non-derivatives
    strike                    REAL,
    option_type               TEXT,       -- CE / PE / NULL

    -- Classification
    sector                    TEXT,
    industry                  TEXT,
    commodity_group            TEXT,
    asset_category              TEXT,

    -- NG Signal Pro fields
    research_status             TEXT NOT NULL DEFAULT 'NOT_RESEARCHED',
    research_priority            INTEGER NOT NULL DEFAULT 5,
    research_maturity_level       INTEGER NOT NULL DEFAULT 0,
    sector_template_assigned       TEXT,
    instrument_dna_status            TEXT,
    last_research_date                TEXT,
    last_updated                       TEXT,
    active_status                       TEXT NOT NULL DEFAULT 'ACTIVE',

    -- Housekeeping
    created_at                           TEXT NOT NULL,
    row_updated_at                        TEXT NOT NULL,
    source_hash                            TEXT
);
"""

CREATE_INDEXES_SQL = [
    f"CREATE INDEX IF NOT EXISTS idx_symbol ON {TABLE_NAME} (trading_symbol);",
    f"CREATE INDEX IF NOT EXISTS idx_exchange_segment ON {TABLE_NAME} (exchange, segment);",
    f"CREATE INDEX IF NOT EXISTS idx_research_status ON {TABLE_NAME} (research_status);",
    f"CREATE INDEX IF NOT EXISTS idx_priority ON {TABLE_NAME} (research_priority);",
    f"CREATE INDEX IF NOT EXISTS idx_active ON {TABLE_NAME} (active_status);",
]

# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

RESEARCH_STATUS_VALUES = [
    "NOT_RESEARCHED",
    "QUEUED",
    "RESEARCHING",
    "RESEARCH_COMPLETE",
    "DNA_CREATED",
    "PRODUCTION_READY",
]

ACTIVE_STATUS_VALUES = ["ACTIVE", "INACTIVE"]

RESEARCH_MATURITY_LABELS = {
    0: "Imported",
    1: "Indicators Tested",
    2: "Strategies Tested",
    3: "Market Regime Tested",
    4: "Instrument DNA Generated",
    5: "Production Validated",
}

RESEARCH_PRIORITY_LABELS = {
    1: "Current NG Signal Pro Instruments",
    2: "MCX Commodities",
    3: "Major Indices",
    4: "Top NSE Stocks",
    5: "Remaining Instruments",
}

# ---------------------------------------------------------------------------
# NGSP-003A.2 additions — Research Scheduler fields + future research
# metadata fields. These are NOT part of CREATE_TABLE_SQL above (that stays
# frozen as the v1 baseline); they're applied additively via
# migrations.migration_002_research_scheduler_and_metadata so existing
# databases upgrade in place without losing data. Listed here as the single
# source of truth for column name/type, same as everything else in this file.
# ---------------------------------------------------------------------------

# Research Scheduler fields — drive "what should be researched next".
# Pure data fields only; the scheduling policy/algorithm itself is future
# Research Engine scope, not built here.
RESEARCH_SCHEDULER_COLUMNS = [
    ("next_research_scheduled_date", "TEXT"),                      # ISO date; when this instrument is next due
    ("research_frequency_days", "INTEGER"),                        # re-research cadence, e.g. 90 for quarterly
    ("scheduler_enabled", "INTEGER NOT NULL DEFAULT 1"),           # 0/1 — opt an instrument out of auto-scheduling
    ("research_attempts_count", "INTEGER NOT NULL DEFAULT 0"),     # total times picked up by the scheduler
    ("research_backoff_until", "TEXT"),                            # ISO datetime; don't retry before this if last run failed
    ("research_lock_owner", "TEXT"),                               # worker/run id holding the lock (concurrency safety)
    ("research_lock_expires_at", "TEXT"),                          # lock auto-expiry, in case a worker dies mid-run
    ("last_research_run_id", "INTEGER"),                           # FK -> research_log.research_log_id
]

# Future research metadata fields — descriptive context the Research/DNA/
# Backtesting engines will read and write once built.
RESEARCH_METADATA_COLUMNS = [
    ("data_quality_score", "REAL"),            # 0.0-1.0 confidence in available historical data
    ("data_availability_start", "TEXT"),       # earliest usable historical bar (ISO date)
    ("data_availability_end", "TEXT"),         # most recent usable historical bar (ISO date)
    ("research_notes", "TEXT"),                # freeform analyst/engine notes
    ("research_assigned_to", "TEXT"),          # which worker/engine instance currently owns this instrument's research
    ("research_engine_version", "TEXT"),       # version of the engine that produced the current maturity level
    ("external_reference_ids", "TEXT"),        # JSON string, e.g. {"yfinance": "RELIANCE.NS", "isin": "..."}
]

ALL_ADDITIVE_COLUMNS = RESEARCH_SCHEDULER_COLUMNS + RESEARCH_METADATA_COLUMNS

# research_log is a separate table (see migrations.py migration 3) — a
# one-to-many history of research runs per instrument. Kept out of
# `instruments` entirely because it's the one field that genuinely doesn't
# scale as a column: an instrument accumulates MANY research runs over its
# life, not one.
RESEARCH_LOG_TABLE = "research_log"


# Fields that must be PRESERVED on update for an already-existing instrument_key
# (i.e. never overwritten by a re-sync from Upstox — only the Research/DNA
# Engines, not built here, are allowed to change these).
PRESERVED_ON_UPDATE_FIELDS = [
    "research_status",
    "research_priority",
    "research_maturity_level",
    "sector_template_assigned",
    "instrument_dna_status",
    "last_research_date",
]

# Fields refreshed from the source file on every update for an existing row.
REFRESHABLE_TRADING_FIELDS = [
    "trading_symbol", "exchange", "segment", "asset_class", "instrument_type",
    "display_name", "isin", "lot_size", "tick_size", "freeze_quantity",
    "expiry", "strike", "option_type", "sector", "industry",
    "commodity_group", "asset_category",
]
