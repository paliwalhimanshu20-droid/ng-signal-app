"""
validation_history/schema.py

Authoritative table definition for the Validation Intelligence Framework's
history store — ONE generic table shared by every validator across NG
Signal Pro (Instrument Master today; Warehouse, Research Database, Market
Context, Market DNA, Instrument DNA, Strategy Research, AI Research
Journal, Strategy Optimizer, and the Continuous Learning Engine as each is
built), not one table per module.

WHY ONE TABLE: a per-module table would mean every new validator requires
a schema migration here just to start recording history — exactly the
"another redesign" the long-term roadmap wants to avoid. Instead:
  - Columns that are genuinely universal (status, INFO/WARNING/FAIL
    counts, timestamps, execution duration) are real typed columns, so
    trend queries and indexes work efficiently across ALL categories at
    once (e.g. "show me every FAIL across the whole platform this week").
  - Columns that are meaningful for many-but-not-all modules (total/new/
    updated/deactivated item counts) are still real columns, generic
    enough to mean "instruments" for Instrument Master, "partitions" for
    the Warehouse, "experiments" for the Research Database, etc.
  - Anything genuinely module-specific goes in `metrics_json` — a
    freeform JSON blob. A future validator NEVER needs a migration here;
    it just puts its own numbers in that blob and the Admin Center reads
    them back by key.
"""

TABLE_NAME = "validation_snapshots"

CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {TABLE_NAME} (
    snapshot_id             INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Identity — which validator, and which run of it.
    category                TEXT NOT NULL,   -- e.g. "Instrument Master", "Warehouse", "Research Database"
    recorded_at             TEXT NOT NULL,   -- ISO8601 — when the validated run this snapshot describes completed
    source_version          TEXT,            -- e.g. Instrument Master schema v4, Warehouse schema v1 — whatever
                                              -- version concept that module already tracks
    source_timestamp        TEXT,            -- e.g. Upstox download timestamp, or any upstream "data as-of" time

    -- Universal outcome — every validator produces these regardless of domain.
    status                  TEXT NOT NULL,   -- PASS / WARNING / FAIL / SKIPPED
    info_count              INTEGER NOT NULL DEFAULT 0,
    warning_count           INTEGER NOT NULL DEFAULT 0,
    failure_count           INTEGER NOT NULL DEFAULT 0,
    quarantined_count       INTEGER NOT NULL DEFAULT 0,
    execution_seconds       REAL,
    summary                 TEXT,

    -- Generic volume counters — "items" means whatever the module
    -- validates (instruments, partitions, experiments, DNA profiles...).
    total_items             INTEGER,
    new_items               INTEGER,
    updated_items           INTEGER,
    deactivated_items       INTEGER,

    -- Per-rule/category tallies, as JSON: {{"rule_name": count, ...}}.
    -- Powers "most common warning/failure categories" without needing a
    -- separate child table.
    warning_categories_json TEXT,
    failure_categories_json TEXT,

    -- Escape hatch for anything module-specific that doesn't fit the
    -- generic columns above — the mechanism that keeps this table
    -- reusable indefinitely without a migration per new validator.
    metrics_json            TEXT,

    created_at              TEXT NOT NULL
);
"""

CREATE_INDEXES_SQL = [
    # The dominant query pattern is "this category's history, most recent
    # first" — every trend function in trends.py runs this shape of query.
    f"CREATE INDEX IF NOT EXISTS idx_category_recorded_at "
    f"ON {TABLE_NAME} (category, recorded_at DESC);",
    f"CREATE INDEX IF NOT EXISTS idx_category_status "
    f"ON {TABLE_NAME} (category, status);",
]

STATUS_VALUES = ["PASS", "WARNING", "FAIL", "SKIPPED"]
