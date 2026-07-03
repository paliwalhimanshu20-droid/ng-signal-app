"""
instrument_master/database.py

SQLite manager for the Instrument Master Database. Handles connection
lifecycle, schema creation + migration, and low-level CRUD. Higher-level
diff/merge logic (what changes on each sync) lives in update_engine.py —
this module just executes whatever it's told.
"""

import datetime as dt
import logging
import os
import sqlite3

from . import migrations, schema

logger = logging.getLogger(__name__)


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


class InstrumentDatabase:
    def __init__(self, db_path: str, journal_mode: str = "DELETE"):
        """
        journal_mode: "DELETE" (default) keeps everything in a single .db
        file — matches your existing GitHub-sync pattern for signal_log.csv,
        where the whole file gets pushed/pulled as one artifact. Use "WAL"
        instead only if this runs as a long-lived process with concurrent
        readers/writers (WAL creates extra -wal/-shm side files that don't
        play well with git-based single-file syncing).
        """
        self.db_path = db_path
        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute(f"PRAGMA journal_mode = {journal_mode}")
        self.conn.execute("PRAGMA synchronous = NORMAL")
        self.conn.execute("PRAGMA foreign_keys = ON")
        self._init_schema()

    def _init_schema(self):
        cur = self.conn.cursor()
        cur.execute(schema.CREATE_TABLE_SQL)
        for stmt in schema.CREATE_INDEXES_SQL:
            cur.execute(stmt)
        self.conn.commit()
        migrations.run_migrations(self.conn)
        logger.info(
            "Schema ready at %s (v%d)", self.db_path, migrations.current_version(self.conn)
        )

    def close(self):
        self.conn.close()

    # -- lookups -------------------------------------------------------

    def get_all_instrument_keys(self) -> set:
        cur = self.conn.execute(f"SELECT instrument_key FROM {schema.TABLE_NAME}")
        return {row["instrument_key"] for row in cur.fetchall()}

    def get_active_instrument_keys(self) -> set:
        cur = self.conn.execute(
            f"SELECT instrument_key FROM {schema.TABLE_NAME} WHERE active_status = 'ACTIVE'"
        )
        return {row["instrument_key"] for row in cur.fetchall()}

    def get_by_key(self, instrument_key: str):
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_NAME} WHERE instrument_key = ?",
            (instrument_key,),
        )
        row = cur.fetchone()
        return dict(row) if row else None

    def get_source_hash(self, instrument_key: str):
        cur = self.conn.execute(
            f"SELECT source_hash FROM {schema.TABLE_NAME} WHERE instrument_key = ?",
            (instrument_key,),
        )
        row = cur.fetchone()
        return row["source_hash"] if row else None

    def count(self) -> int:
        cur = self.conn.execute(f"SELECT COUNT(*) AS c FROM {schema.TABLE_NAME}")
        return cur.fetchone()["c"]

    def query(self, where_sql: str = "", params: tuple = ()) -> list:
        sql = f"SELECT * FROM {schema.TABLE_NAME}"
        if where_sql:
            sql += f" WHERE {where_sql}"
        cur = self.conn.execute(sql, params)
        return [dict(r) for r in cur.fetchall()]

    # -- writes ----------------------------------------------------------

    def insert_new(self, record: dict, classification: dict, priority: int,
                    sector_template: str):
        now = _now_iso()
        row = {
            "instrument_key": record["instrument_key"],
            "trading_symbol": record.get("trading_symbol"),
            "exchange": record.get("exchange"),
            "segment": record.get("segment"),
            "asset_class": record.get("asset_class"),
            "instrument_type": record.get("instrument_type"),
            "display_name": record.get("display_name"),
            "isin": record.get("isin"),
            "lot_size": record.get("lot_size"),
            "tick_size": record.get("tick_size"),
            "freeze_quantity": record.get("freeze_quantity"),
            "expiry": record.get("expiry"),
            "strike": record.get("strike"),
            "option_type": record.get("option_type"),
            "sector": classification.get("sector"),
            "industry": classification.get("industry"),
            "commodity_group": classification.get("commodity_group"),
            "asset_category": classification.get("asset_category"),
            "research_status": "NOT_RESEARCHED",
            "research_priority": priority,
            "research_maturity_level": 0,
            "sector_template_assigned": sector_template,
            "instrument_dna_status": None,
            "last_research_date": None,
            "last_updated": now,
            "active_status": "ACTIVE",
            "created_at": now,
            "row_updated_at": now,
            "source_hash": record.get("record_hash"),
        }
        cols = ", ".join(row.keys())
        placeholders = ", ".join(["?"] * len(row))
        self.conn.execute(
            f"INSERT INTO {schema.TABLE_NAME} ({cols}) VALUES ({placeholders})",
            tuple(row.values()),
        )

    def update_trading_fields(self, instrument_key: str, record: dict,
                               classification: dict):
        """
        Refresh trading/classification fields for an existing instrument
        WITHOUT touching NG Signal Pro research fields
        (see schema.PRESERVED_ON_UPDATE_FIELDS).
        """
        now = _now_iso()
        self.conn.execute(
            f"""
            UPDATE {schema.TABLE_NAME}
            SET trading_symbol = ?, exchange = ?, segment = ?, asset_class = ?,
                instrument_type = ?, display_name = ?, isin = ?, lot_size = ?,
                tick_size = ?, freeze_quantity = ?, expiry = ?, strike = ?,
                option_type = ?, sector = ?, industry = ?, commodity_group = ?,
                asset_category = ?, last_updated = ?, row_updated_at = ?,
                source_hash = ?, active_status = 'ACTIVE'
            WHERE instrument_key = ?
            """,
            (
                record.get("trading_symbol"), record.get("exchange"),
                record.get("segment"), record.get("asset_class"),
                record.get("instrument_type"), record.get("display_name"),
                record.get("isin"), record.get("lot_size"),
                record.get("tick_size"), record.get("freeze_quantity"),
                record.get("expiry"), record.get("strike"),
                record.get("option_type"), classification.get("sector"),
                classification.get("industry"), classification.get("commodity_group"),
                classification.get("asset_category"), now, now,
                record.get("record_hash"), instrument_key,
            ),
        )

    def deactivate(self, instrument_key: str):
        now = _now_iso()
        self.conn.execute(
            f"""
            UPDATE {schema.TABLE_NAME}
            SET active_status = 'INACTIVE', last_updated = ?, row_updated_at = ?
            WHERE instrument_key = ?
            """,
            (now, now, instrument_key),
        )

    def commit(self):
        self.conn.commit()

    # -- research scheduler fields (pure data access — no scheduling policy) --

    def set_research_schedule(self, instrument_key: str, next_date: str = None,
                               frequency_days: int = None, scheduler_enabled: bool = None):
        """Set/update when an instrument is next due for research."""
        fields, params = [], []
        if next_date is not None:
            fields.append("next_research_scheduled_date = ?")
            params.append(next_date)
        if frequency_days is not None:
            fields.append("research_frequency_days = ?")
            params.append(frequency_days)
        if scheduler_enabled is not None:
            fields.append("scheduler_enabled = ?")
            params.append(1 if scheduler_enabled else 0)
        if not fields:
            return
        params.append(instrument_key)
        self.conn.execute(
            f"UPDATE {schema.TABLE_NAME} SET {', '.join(fields)} WHERE instrument_key = ?",
            tuple(params),
        )

    def get_due_for_research(self, as_of_date: str, limit: int = 500) -> list:
        """
        Instruments due for research as of a given ISO date, ordered by
        priority then longest-waiting first. Read-only lookup — actually
        acting on this list (claiming a lock, running research) is future
        Research Engine scope.
        """
        cur = self.conn.execute(
            f"""
            SELECT * FROM {schema.TABLE_NAME}
            WHERE scheduler_enabled = 1
              AND active_status = 'ACTIVE'
              AND (next_research_scheduled_date IS NULL OR next_research_scheduled_date <= ?)
              AND (research_backoff_until IS NULL OR research_backoff_until <= ?)
            ORDER BY research_priority ASC, next_research_scheduled_date ASC
            LIMIT ?
            """,
            (as_of_date, as_of_date, limit),
        )
        return [dict(r) for r in cur.fetchall()]

    def acquire_research_lock(self, instrument_key: str, owner: str, expires_at: str) -> bool:
        """
        Best-effort lock so two scheduler workers don't research the same
        instrument concurrently. Returns True if the lock was acquired.
        """
        now = _now_iso()
        cur = self.conn.execute(
            f"""
            UPDATE {schema.TABLE_NAME}
            SET research_lock_owner = ?, research_lock_expires_at = ?
            WHERE instrument_key = ?
              AND (research_lock_owner IS NULL OR research_lock_expires_at < ?)
            """,
            (owner, expires_at, instrument_key, now),
        )
        self.conn.commit()
        return cur.rowcount > 0

    def release_research_lock(self, instrument_key: str):
        self.conn.execute(
            f"""
            UPDATE {schema.TABLE_NAME}
            SET research_lock_owner = NULL, research_lock_expires_at = NULL
            WHERE instrument_key = ?
            """,
            (instrument_key,),
        )

    # -- future research metadata fields ---------------------------------

    def update_research_metadata(self, instrument_key: str, **fields):
        """
        Generic setter for the descriptive metadata columns (data_quality_score,
        data_availability_start/end, research_notes, research_assigned_to,
        research_engine_version, external_reference_ids). Unknown keys are
        rejected to avoid silent typos writing nowhere.
        """
        allowed = {c for c, _ in schema.RESEARCH_METADATA_COLUMNS}
        unknown = set(fields) - allowed
        if unknown:
            raise ValueError(f"Unknown research metadata field(s): {unknown}")
        if not fields:
            return
        set_clause = ", ".join(f"{k} = ?" for k in fields)
        params = list(fields.values()) + [instrument_key]
        self.conn.execute(
            f"UPDATE {schema.TABLE_NAME} SET {set_clause} WHERE instrument_key = ?",
            tuple(params),
        )

    # -- research_log (append-only history, scales to millions of rows) --

    def log_research_run(self, instrument_key: str, run_started_at: str,
                          run_completed_at: str = None, status_before: str = None,
                          status_after: str = None, maturity_before: int = None,
                          maturity_after: int = None, engine_version: str = None,
                          result_summary: str = None, success: bool = None,
                          error_message: str = None) -> int:
        """
        Append one research-run record. This is the audit trail the future
        Research Engine writes to; querying/analyzing it at scale is that
        engine's job, not this module's — this is just the insert path.
        Also increments the instrument's research_attempts_count and points
        last_research_run_id at the new row.
        """
        now = _now_iso()
        cur = self.conn.execute(
            f"""
            INSERT INTO {schema.RESEARCH_LOG_TABLE} (
                instrument_key, run_started_at, run_completed_at,
                research_status_before, research_status_after,
                maturity_level_before, maturity_level_after,
                engine_version, result_summary, success, error_message, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                instrument_key, run_started_at, run_completed_at,
                status_before, status_after, maturity_before, maturity_after,
                engine_version, result_summary,
                None if success is None else int(success),
                error_message, now,
            ),
        )
        run_id = cur.lastrowid
        self.conn.execute(
            f"""
            UPDATE {schema.TABLE_NAME}
            SET research_attempts_count = research_attempts_count + 1,
                last_research_run_id = ?
            WHERE instrument_key = ?
            """,
            (run_id, instrument_key),
        )
        return run_id

    def get_research_history(self, instrument_key: str, limit: int = 50) -> list:
        cur = self.conn.execute(
            f"""
            SELECT * FROM {schema.RESEARCH_LOG_TABLE}
            WHERE instrument_key = ?
            ORDER BY run_started_at DESC
            LIMIT ?
            """,
            (instrument_key, limit),
        )
        return [dict(r) for r in cur.fetchall()]
