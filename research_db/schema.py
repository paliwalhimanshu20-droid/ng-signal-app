"""
research_db/schema.py

Authoritative table definitions for the Research & Learning Database (v1
baseline — frozen once shipped, same convention as NGSP-003A.1's
instrument_master/schema.py). Future changes go through migrations.py.

===========================================================================
DESIGN NOTES (read this before touching the schema)
===========================================================================

Core identity model — three related but distinct IDs:

  experiment_id   Stable identifier for a *conceptual* research question,
                   e.g. "test RSI variants on NATURALGAS". Can span many
                   runs and versions over years.

  version_id      A specific version of that experiment's design (e.g. v1
                   used RSI(14), v2 redesigned to RSI(21)). Tracked in
                   `experiment_versions`. Exactly one version per
                   experiment_id is marked is_current_version=1 at a time;
                   OLDER VERSIONS ARE NEVER DELETED OR OVERWRITTEN.

  run_id          A single concrete execution. Every run is its own
                   immutable row in `research_experiments`, even if it's a
                   rerun of the exact same experiment_id/version_id (e.g.
                   re-running after an engine bugfix). This is what makes
                   every result reproducible years later: nothing is ever
                   updated in place, a rerun is just a new row.

Primary/foreign keys use INTEGER surrogate keys (`id`, `experiment_row_id`)
rather than TEXT UUIDs for the actual join columns — at millions of rows,
integer joins are dramatically cheaper than text/UUID joins in SQLite.
The human-facing `experiment_id` / `run_id` TEXT columns remain indexed and
queryable, but child tables FK against `research_experiments.id` (INTEGER).

What's mutable vs immutable:
  - `research_experiments.research_status` — MUTABLE. This is execution
    lifecycle state (PENDING -> RUNNING -> COMPLETED/FAILED), not a research
    finding, so it's fine to update in place via update_status().
  - Everything else — indicator/strategy/parameter/regime results,
    performance metrics, validation results, notes — APPEND-ONLY. A
    "change" is always a new row, never an UPDATE. This is what "never
    overwritten" and "everything must be reproducible" mean in practice.
  - `validation_results` is explicitly append-only per spec ("never delete
    validation history") — re-validating an experiment adds a new row;
    the latest one by `created_at` is the current status.
"""

# ---------------------------------------------------------------------------
# Table names
# ---------------------------------------------------------------------------
TABLE_EXPERIMENTS = "research_experiments"
TABLE_EXPERIMENT_VERSIONS = "experiment_versions"
TABLE_INDICATOR_RESULTS = "indicator_test_results"
TABLE_STRATEGY_RESULTS = "strategy_test_results"
TABLE_PARAMETER_RESULTS = "parameter_test_results"
TABLE_REGIME_RESULTS = "market_regime_results"
TABLE_PERFORMANCE_METRICS = "performance_metrics"
TABLE_VALIDATION_RESULTS = "validation_results"
TABLE_EXPERIMENT_NOTES = "experiment_notes"

# Added via migration 2 (research_db/migrations.py), not part of the frozen
# v1 baseline below — migrated from signal_log.csv. See migrations.py's
# migration_002_add_live_trades_table for the actual DDL.
TABLE_LIVE_TRADES = "live_trades"

# Added via migration 3 (research_db/migrations.py) — NGSP Phase 0, PR 6a.
# Independent of TABLE_LIVE_TRADES: live_trades only ever holds rows that
# passed the actionable BUY/SELL gate; this table holds the COMPLETE output
# of every background scan (every watchlist instrument, every signal type,
# including HOLD/WATCH), append-only, one batch per scan run. This is what
# the Scanner tab's "Full Scanned Universe" table and opportunity cards
# actually need to read from instead of recomputing (PR 6b) — live_trades
# alone can't answer "what did the whole scan see," only "what became a
# trade." See migrations.py's migration_003_add_scan_snapshots_table for
# the actual DDL and design notes on how this is meant to feed Phase 1's
# Historical Data Warehouse rather than being a throwaway cache.
TABLE_SCAN_SNAPSHOTS = "scan_snapshots"

# ---------------------------------------------------------------------------
# DDL — order matters (FK targets must exist before the tables that reference
# them). Each entry is executed in sequence.
# ---------------------------------------------------------------------------
CREATE_TABLES_SQL = [
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_EXPERIMENT_VERSIONS} (
        version_id          INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_id       TEXT NOT NULL,
        version_number      INTEGER NOT NULL,
        parent_version_id   INTEGER,
        is_current_version  INTEGER NOT NULL DEFAULT 1,
        change_description  TEXT,
        created_at          TEXT NOT NULL,
        FOREIGN KEY (parent_version_id) REFERENCES {TABLE_EXPERIMENT_VERSIONS}(version_id),
        UNIQUE (experiment_id, version_number)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_EXPERIMENTS} (
        id                       INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_id            TEXT NOT NULL,
        run_id                   TEXT NOT NULL UNIQUE,
        version_id               INTEGER,
        timestamp                TEXT NOT NULL,
        instrument_key           TEXT NOT NULL,
        research_engine_version  TEXT,
        research_type            TEXT NOT NULL,
        research_status          TEXT NOT NULL DEFAULT 'PENDING',
        execution_time_seconds   REAL,
        created_by               TEXT,
        source_data_version      TEXT,
        random_seed              INTEGER,
        notes                    TEXT,
        created_at               TEXT NOT NULL,
        FOREIGN KEY (version_id) REFERENCES {TABLE_EXPERIMENT_VERSIONS}(version_id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_INDICATOR_RESULTS} (
        result_id            INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id    INTEGER NOT NULL,
        indicator_name       TEXT NOT NULL,
        parameters            TEXT,   -- JSON string
        weight                 REAL,  -- reserved for future weighted-ensemble use
        calculation_version      TEXT,
        result                     TEXT,  -- JSON string; flexible result shape
        created_at                   TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_STRATEGY_RESULTS} (
        result_id              INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id      INTEGER NOT NULL,
        strategy_name           TEXT NOT NULL,
        strategy_version          TEXT,
        entry_rules                 TEXT,  -- JSON string
        exit_rules                    TEXT,  -- JSON string
        stop_loss_model                  TEXT,
        target_model                        TEXT,
        position_sizing_model                  TEXT,
        created_at                                TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_PARAMETER_RESULTS} (
        result_id            INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id    INTEGER NOT NULL,
        parameter_set         TEXT,  -- JSON string, full parameter combination tested
        parameter_name           TEXT,  -- optional, for single-parameter sweeps
        parameter_value             TEXT,
        result_score                   REAL,
        result_detail                     TEXT,  -- JSON string
        created_at                           TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_REGIME_RESULTS} (
        regime_id            INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id    INTEGER NOT NULL,
        regime_type          TEXT NOT NULL,  -- e.g. TRENDING, RANGING, ... (extendable, see REGIME_TYPE_SUGGESTED)
        regime_start_date     TEXT,
        regime_end_date          TEXT,
        notes                       TEXT,
        created_at                     TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_PERFORMANCE_METRICS} (
        metric_id                  INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id          INTEGER NOT NULL,
        regime_id                  INTEGER,  -- NULL = whole-experiment metrics; set = regime-sliced metrics
        win_rate                   REAL,
        profit_factor               REAL,
        net_profit                     REAL,
        gross_profit                      REAL,
        gross_loss                           REAL,
        average_trade                           REAL,
        max_drawdown                               REAL,
        max_winning_streak                            INTEGER,
        max_losing_streak                                INTEGER,
        expectancy                                          REAL,
        sharpe_ratio                                           REAL,
        sortino_ratio                                             REAL,
        calmar_ratio                                                 REAL,
        recovery_factor                                                 REAL,
        avg_holding_time_minutes                                          REAL,
        total_trades                                                        INTEGER,
        extra_metrics                                                          TEXT,  -- JSON catch-all for future metrics
        created_at                                                                TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id),
        FOREIGN KEY (regime_id) REFERENCES {TABLE_REGIME_RESULTS}(regime_id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_VALIDATION_RESULTS} (
        validation_id         INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_row_id     INTEGER NOT NULL,
        validation_status     TEXT NOT NULL,
        validated_by           TEXT,
        validation_notes          TEXT,
        validated_at                 TEXT,
        created_at                      TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
    f"""
    CREATE TABLE IF NOT EXISTS {TABLE_EXPERIMENT_NOTES} (
        note_id              INTEGER PRIMARY KEY AUTOINCREMENT,
        experiment_id        TEXT NOT NULL,
        experiment_row_id    INTEGER,  -- optional: set if note is about one specific run
        note_text            TEXT NOT NULL,
        created_by             TEXT,
        created_at                TEXT NOT NULL,
        FOREIGN KEY (experiment_row_id) REFERENCES {TABLE_EXPERIMENTS}(id)
    );
    """,
]

# ---------------------------------------------------------------------------
# Indexes — built for the query patterns the spec calls out explicitly:
# instrument history, strategy history, indicator history, recent
# experiments, performance ranking, at millions of rows.
# ---------------------------------------------------------------------------
CREATE_INDEXES_SQL = [
    f"CREATE INDEX IF NOT EXISTS idx_exp_instrument ON {TABLE_EXPERIMENTS} (instrument_key, timestamp);",
    f"CREATE INDEX IF NOT EXISTS idx_exp_type ON {TABLE_EXPERIMENTS} (research_type, timestamp);",
    f"CREATE INDEX IF NOT EXISTS idx_exp_recent ON {TABLE_EXPERIMENTS} (timestamp);",
    f"CREATE INDEX IF NOT EXISTS idx_exp_experiment_id ON {TABLE_EXPERIMENTS} (experiment_id);",
    f"CREATE INDEX IF NOT EXISTS idx_exp_status ON {TABLE_EXPERIMENTS} (research_status);",
    f"CREATE INDEX IF NOT EXISTS idx_exp_run_id ON {TABLE_EXPERIMENTS} (run_id);",

    f"CREATE INDEX IF NOT EXISTS idx_versions_experiment ON {TABLE_EXPERIMENT_VERSIONS} (experiment_id, version_number);",
    f"CREATE INDEX IF NOT EXISTS idx_versions_current ON {TABLE_EXPERIMENT_VERSIONS} (experiment_id, is_current_version);",

    f"CREATE INDEX IF NOT EXISTS idx_indicator_name ON {TABLE_INDICATOR_RESULTS} (indicator_name);",
    f"CREATE INDEX IF NOT EXISTS idx_indicator_experiment ON {TABLE_INDICATOR_RESULTS} (experiment_row_id);",

    f"CREATE INDEX IF NOT EXISTS idx_strategy_name_version ON {TABLE_STRATEGY_RESULTS} (strategy_name, strategy_version);",
    f"CREATE INDEX IF NOT EXISTS idx_strategy_experiment ON {TABLE_STRATEGY_RESULTS} (experiment_row_id);",

    f"CREATE INDEX IF NOT EXISTS idx_parameter_experiment ON {TABLE_PARAMETER_RESULTS} (experiment_row_id);",
    f"CREATE INDEX IF NOT EXISTS idx_parameter_name ON {TABLE_PARAMETER_RESULTS} (parameter_name);",

    f"CREATE INDEX IF NOT EXISTS idx_regime_experiment ON {TABLE_REGIME_RESULTS} (experiment_row_id);",
    f"CREATE INDEX IF NOT EXISTS idx_regime_type ON {TABLE_REGIME_RESULTS} (regime_type);",

    f"CREATE INDEX IF NOT EXISTS idx_metrics_experiment ON {TABLE_PERFORMANCE_METRICS} (experiment_row_id);",
    f"CREATE INDEX IF NOT EXISTS idx_metrics_regime ON {TABLE_PERFORMANCE_METRICS} (regime_id);",
    f"CREATE INDEX IF NOT EXISTS idx_metrics_sharpe ON {TABLE_PERFORMANCE_METRICS} (sharpe_ratio);",
    f"CREATE INDEX IF NOT EXISTS idx_metrics_profit_factor ON {TABLE_PERFORMANCE_METRICS} (profit_factor);",
    f"CREATE INDEX IF NOT EXISTS idx_metrics_win_rate ON {TABLE_PERFORMANCE_METRICS} (win_rate);",
    f"CREATE INDEX IF NOT EXISTS idx_metrics_expectancy ON {TABLE_PERFORMANCE_METRICS} (expectancy);",

    f"CREATE INDEX IF NOT EXISTS idx_validation_experiment ON {TABLE_VALIDATION_RESULTS} (experiment_row_id);",
    f"CREATE INDEX IF NOT EXISTS idx_validation_status ON {TABLE_VALIDATION_RESULTS} (validation_status);",

    f"CREATE INDEX IF NOT EXISTS idx_notes_experiment_id ON {TABLE_EXPERIMENT_NOTES} (experiment_id);",
]

# ---------------------------------------------------------------------------
# Enums — enforced at the application layer (database.py), not as SQLite
# CHECK constraints, so new values can be added without a migration.
# ---------------------------------------------------------------------------

# live_trades.status — execution lifecycle for a live BUY/SELL signal
# (MUTABLE — same lifecycle signal_log.csv's status column always had).
# Set once by check_signals.check_outcome() (OPEN -> TARGET_HIT/SL_HIT) or
# by the open-signal expiry pass (OPEN -> EXPIRED); never by anything else.
LIVE_TRADE_STATUS_VALUES = ["OPEN", "TARGET_HIT", "SL_HIT", "EXPIRED"]

# research_experiments.research_status — execution lifecycle (MUTABLE)
EXPERIMENT_STATUS_VALUES = ["PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"]

# validation_results.validation_status — per spec, exact required set
VALIDATION_STATUS_VALUES = [
    "NOT_VALIDATED",
    "VALIDATION_PENDING",
    "VALIDATED",
    "REJECTED",
    "ARCHIVED",
]

# research_experiments.research_type — suggested set, NOT enforced (a new
# research type should never require a migration or code change)
RESEARCH_TYPE_SUGGESTED = ["INDICATOR", "STRATEGY", "PARAMETER", "REGIME", "COMPOSITE"]

# market_regime_results.regime_type — suggested set, explicitly extendable
# per spec ("Future regimes must be easily added")
REGIME_TYPE_SUGGESTED = [
    "TRENDING", "RANGING", "HIGH_VOLATILITY", "LOW_VOLATILITY", "NEWS_EVENT", "UNKNOWN",
]

# Columns in performance_metrics that are safe to interpolate into an
# ORDER BY clause for ranking queries. SQL identifiers can't be
# parameterized with `?`, so ranking queries MUST validate the requested
# metric against this whitelist before building SQL — this is the injection
# guard for get_performance_ranking().
RANKABLE_METRIC_COLUMNS = [
    "win_rate", "profit_factor", "net_profit", "gross_profit", "gross_loss",
    "average_trade", "max_drawdown", "max_winning_streak", "max_losing_streak",
    "expectancy", "sharpe_ratio", "sortino_ratio", "calmar_ratio",
    "recovery_factor", "avg_holding_time_minutes", "total_trades",
]

# Columns in research_experiments that search_experiments() is allowed to
# filter on — same injection-guard rationale.
SEARCHABLE_EXPERIMENT_COLUMNS = [
    "instrument_key", "research_type", "research_status", "experiment_id",
    "run_id", "created_by",
]
