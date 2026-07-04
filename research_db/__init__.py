"""
NGSP-003A.2 — Research & Learning Database

Permanent, append-only memory of every research experiment ever run against
any instrument. Nothing in here executes research — it only stores it.

Modules:
    schema           v1 table DDL (frozen baseline) + enums/whitelists
    migrations       versioned schema upgrades (same pattern as NGSP-003A.1)
    database         data-access layer — the ONLY supported way to touch this DB
    validation       schema + referential-integrity checks

Explicitly OUT OF SCOPE (not built here):
    Live Signal Engine, AI Strategy Optimizer, Instrument DNA logic,
    Sector Intelligence logic, Continuous Research scheduling logic.
    This module stores knowledge only — it performs no research, runs no
    backtests, and makes no trading decisions.

Relationship to NGSP-003A.1 (Instrument Master Database):
    This module does NOT import or depend on the instrument_master package.
    `instrument_key` is stored as a plain indexed TEXT column — a soft
    reference to instruments.instrument_key in the other (separate) SQLite
    database, not a cross-file foreign key. This keeps the two modules
    fully decoupled, as required. NGSP-003A.1 files are untouched.
"""
