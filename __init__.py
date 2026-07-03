"""
NGSP-003A — Instrument Master Database

Modules:
    schema           table DDL + field/status constants (v1 baseline, frozen)
    migrations       versioned schema upgrades (scheduler fields, research_log, etc.)
    downloader       fetches complete.json.gz from Upstox
    parser           raw JSON -> normalized dict records
    classifier       rule-driven sector/industry/priority/maturity assignment
    database         SQLite manager (init, migrate, upsert, queries, scheduler/log helpers)
    update_engine    diff-and-merge logic (new / changed / removed instruments)
    validation       schema + data-integrity checks

Explicitly OUT OF SCOPE for this module (future work, not built here):
    Research Engine, AI Optimizer, Backtesting Engine, Instrument DNA Engine.
    The scheduler/log DATA FIELDS exist so those engines have somewhere to
    read from and write to — the scheduling/research LOGIC itself is not
    implemented here.
"""
