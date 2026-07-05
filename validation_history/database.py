"""
validation_history/database.py

Lightweight SQLite-backed store for the Validation Intelligence Framework.
Same "single .db file, git-sync friendly" pattern used everywhere else in
NG Signal Pro (instrument_master.db, research_learning.db, signal_log.csv's
successor live_trades) — no external DB server, works with the
GitHub-Actions-commits-the-file deployment model already in place.
"""

from __future__ import annotations

import datetime as dt
import json
import sqlite3

from . import schema
from .models import ValidationSnapshot


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


class ValidationHistoryStore:
    def __init__(self, db_path: str, journal_mode: str = "DELETE"):
        self.db_path = db_path
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute(f"PRAGMA journal_mode = {journal_mode}")
        self.conn.execute("PRAGMA synchronous = NORMAL")
        self._ensure_schema()

    def _ensure_schema(self):
        self.conn.execute(schema.CREATE_TABLE_SQL)
        for stmt in schema.CREATE_INDEXES_SQL:
            self.conn.execute(stmt)
        self.conn.commit()

    def close(self):
        self.conn.close()

    # ------------------------------------------------------------------
    # Write
    # ------------------------------------------------------------------

    def record(self, snapshot: ValidationSnapshot) -> int:
        """Inserts one snapshot row. Returns the new snapshot_id.

        Deliberately append-only — history is never updated or deleted in
        place (mirrors instrument_master's own "never delete, only
        deactivate/flag" philosophy). If a category needs pruning for size
        someday, that should be an explicit, separately-reviewed retention
        policy, not an implicit side effect of writing a new row.
        """
        recorded_at = snapshot.recorded_at or _now_iso()
        now = _now_iso()
        cur = self.conn.execute(
            f"""
            INSERT INTO {schema.TABLE_NAME} (
                category, recorded_at, source_version, source_timestamp,
                status, info_count, warning_count, failure_count, quarantined_count,
                execution_seconds, summary,
                total_items, new_items, updated_items, deactivated_items,
                warning_categories_json, failure_categories_json, metrics_json,
                created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                snapshot.category, recorded_at, snapshot.source_version, snapshot.source_timestamp,
                snapshot.status, snapshot.info_count, snapshot.warning_count,
                snapshot.failure_count, snapshot.quarantined_count,
                snapshot.execution_seconds, snapshot.summary,
                snapshot.total_items, snapshot.new_items, snapshot.updated_items,
                snapshot.deactivated_items,
                json.dumps(snapshot.warning_categories), json.dumps(snapshot.failure_categories),
                json.dumps(snapshot.metrics),
                now,
            ),
        )
        self.conn.commit()
        return cur.lastrowid

    # ------------------------------------------------------------------
    # Read
    # ------------------------------------------------------------------

    def get_recent(self, category: str, limit: int = 50) -> list[dict]:
        """Most-recent-first. This is the shape every trend function in
        trends.py expects: a list of plain dicts, newest at index 0."""
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_NAME} WHERE category = ? "
            f"ORDER BY recorded_at DESC, snapshot_id DESC LIMIT ?",
            (category, limit),
        )
        return [_row_to_dict(r) for r in cur.fetchall()]

    def get_since(self, category: str, since_iso: str) -> list[dict]:
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_NAME} WHERE category = ? AND recorded_at >= ? "
            f"ORDER BY recorded_at ASC",
            (category, since_iso),
        )
        return [_row_to_dict(r) for r in cur.fetchall()]

    def get_categories(self) -> list[str]:
        """All distinct categories with at least one recorded snapshot —
        lets an Admin Center panel discover what's available to display
        without hardcoding a module list."""
        cur = self.conn.execute(f"SELECT DISTINCT category FROM {schema.TABLE_NAME} ORDER BY category")
        return [r["category"] for r in cur.fetchall()]

    def count(self, category: str | None = None) -> int:
        if category:
            cur = self.conn.execute(
                f"SELECT COUNT(*) AS c FROM {schema.TABLE_NAME} WHERE category = ?", (category,)
            )
        else:
            cur = self.conn.execute(f"SELECT COUNT(*) AS c FROM {schema.TABLE_NAME}")
        return cur.fetchone()["c"]


def _row_to_dict(row: sqlite3.Row) -> dict:
    d = dict(row)
    for json_field in ("warning_categories_json", "failure_categories_json", "metrics_json"):
        raw = d.pop(json_field, None)
        key = json_field.replace("_json", "")
        try:
            d[key] = json.loads(raw) if raw else {}
        except (TypeError, ValueError):
            d[key] = {}
    return d
