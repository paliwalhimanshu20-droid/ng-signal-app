"""
research_db/database.py

The data-access layer for the Research & Learning Database. Per spec:
"Future AI modules should never access SQL directly" — every read/write
goes through a named method here, each of which validates inputs against
schema.py's whitelists before touching SQL (this is also the injection
guard for the two methods that must build dynamic WHERE/ORDER BY clauses:
search_experiments() and get_performance_ranking()).

Nothing in this module performs research, runs a backtest, or makes a
trading decision — it only persists and retrieves data.
"""

import datetime as dt
import json
import logging
import os
import sqlite3
import uuid

from . import migrations, schema

logger = logging.getLogger(__name__)


def _now_iso() -> str:
    return dt.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


def _new_id() -> str:
    return str(uuid.uuid4())


def _to_json(value):
    if value is None:
        return None
    if isinstance(value, str):
        return value  # assume caller already passed a JSON string
    return json.dumps(value, default=str)


class ResearchDatabase:
    def __init__(self, db_path: str, journal_mode: str = "DELETE"):
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
        for stmt in schema.CREATE_TABLES_SQL:
            cur.execute(stmt)
        for stmt in schema.CREATE_INDEXES_SQL:
            cur.execute(stmt)
        self.conn.commit()
        migrations.run_migrations(self.conn)
        logger.info(
            "Research DB schema ready at %s (v%d)",
            self.db_path, migrations.current_version(self.conn),
        )

    def close(self):
        self.conn.close()

    def commit(self):
        self.conn.commit()

    # =====================================================================
    # EXPERIMENT VERSIONING
    # =====================================================================

    def _get_current_version(self, experiment_id: str):
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_EXPERIMENT_VERSIONS} "
            f"WHERE experiment_id = ? AND is_current_version = 1",
            (experiment_id,),
        )
        row = cur.fetchone()
        return dict(row) if row else None

    def create_experiment_version(self, experiment_id: str, change_description: str = None,
                                   version_number: int = None) -> int:
        """
        Create a new version for an experiment_id. If a current version
        already exists, it is flagged is_current_version=0 (the row itself
        is never deleted or altered otherwise) and the new version becomes
        current. Returns the new version_id.
        """
        now = _now_iso()
        prior = self._get_current_version(experiment_id)
        parent_version_id = prior["version_id"] if prior else None

        if version_number is None:
            cur = self.conn.execute(
                f"SELECT MAX(version_number) AS v FROM {schema.TABLE_EXPERIMENT_VERSIONS} "
                f"WHERE experiment_id = ?",
                (experiment_id,),
            )
            max_v = cur.fetchone()["v"]
            version_number = (max_v or 0) + 1

        if prior:
            self.conn.execute(
                f"UPDATE {schema.TABLE_EXPERIMENT_VERSIONS} SET is_current_version = 0 "
                f"WHERE version_id = ?",
                (prior["version_id"],),
            )

        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_EXPERIMENT_VERSIONS}
                (experiment_id, version_number, parent_version_id, is_current_version,
                 change_description, created_at)
                VALUES (?, ?, ?, 1, ?, ?)""",
            (experiment_id, version_number, parent_version_id, change_description, now),
        )
        return cur.lastrowid

    def get_experiment_versions(self, experiment_id: str) -> list:
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_EXPERIMENT_VERSIONS} "
            f"WHERE experiment_id = ? ORDER BY version_number ASC",
            (experiment_id,),
        )
        return [dict(r) for r in cur.fetchall()]

    # =====================================================================
    # CREATE EXPERIMENT
    # =====================================================================

    def create_experiment(self, instrument_key: str, research_type: str,
                           experiment_id: str = None, run_id: str = None,
                           research_engine_version: str = None,
                           execution_time_seconds: float = None,
                           created_by: str = None, source_data_version: str = None,
                           random_seed: int = None, notes: str = None,
                           research_status: str = "PENDING",
                           change_description: str = None) -> dict:
        """
        Insert a new immutable experiment run.

        - If experiment_id is omitted, a brand new conceptual experiment is
          created (new experiment_id, version 1).
        - If experiment_id IS provided and already has prior runs, this is
          treated as a new VERSION of that experiment — a new
          experiment_versions row is created and marked current; all prior
          runs/versions remain untouched in the database.
        - run_id is always unique per call (auto-generated if not supplied),
          even when rerunning the same experiment_id/version — this is what
          keeps every individual execution independently reproducible.

        Returns a dict with experiment_row_id, experiment_id, run_id, version_id.
        """
        if research_status not in schema.EXPERIMENT_STATUS_VALUES:
            raise ValueError(
                f"Invalid research_status '{research_status}'. "
                f"Must be one of {schema.EXPERIMENT_STATUS_VALUES}"
            )

        now = _now_iso()
        experiment_id = experiment_id or _new_id()
        run_id = run_id or _new_id()

        version_id = self.create_experiment_version(
            experiment_id, change_description=change_description
        )

        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_EXPERIMENTS}
                (experiment_id, run_id, version_id, timestamp, instrument_key,
                 research_engine_version, research_type, research_status,
                 execution_time_seconds, created_by, source_data_version,
                 random_seed, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                experiment_id, run_id, version_id, now, instrument_key,
                research_engine_version, research_type, research_status,
                execution_time_seconds, created_by, source_data_version,
                random_seed, notes, now,
            ),
        )
        experiment_row_id = cur.lastrowid

        return {
            "experiment_row_id": experiment_row_id,
            "experiment_id": experiment_id,
            "run_id": run_id,
            "version_id": version_id,
        }

    def update_status(self, run_id: str, research_status: str):
        """
        Update the MUTABLE execution-lifecycle status of a run
        (PENDING -> RUNNING -> COMPLETED/FAILED/CANCELLED). This is the one
        intentional exception to "never overwrite" — see schema.py's design
        notes for why.
        """
        if research_status not in schema.EXPERIMENT_STATUS_VALUES:
            raise ValueError(
                f"Invalid research_status '{research_status}'. "
                f"Must be one of {schema.EXPERIMENT_STATUS_VALUES}"
            )
        self.conn.execute(
            f"UPDATE {schema.TABLE_EXPERIMENTS} SET research_status = ? WHERE run_id = ?",
            (research_status, run_id),
        )

    def _get_experiment_row_id(self, run_id: str) -> int:
        cur = self.conn.execute(
            f"SELECT id FROM {schema.TABLE_EXPERIMENTS} WHERE run_id = ?", (run_id,)
        )
        row = cur.fetchone()
        if not row:
            raise ValueError(f"No experiment found for run_id={run_id!r}")
        return row["id"]

    # =====================================================================
    # STORE RESULTS (all append-only — every call inserts a new row)
    # =====================================================================

    def store_indicator_result(self, run_id: str, indicator_name: str, parameters=None,
                                weight: float = None, calculation_version: str = None,
                                result=None) -> int:
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_INDICATOR_RESULTS}
                (experiment_row_id, indicator_name, parameters, weight,
                 calculation_version, result, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (experiment_row_id, indicator_name, _to_json(parameters), weight,
             calculation_version, _to_json(result), now),
        )
        return cur.lastrowid

    def store_strategy_result(self, run_id: str, strategy_name: str, strategy_version: str = None,
                               entry_rules=None, exit_rules=None, stop_loss_model: str = None,
                               target_model: str = None, position_sizing_model: str = None) -> int:
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_STRATEGY_RESULTS}
                (experiment_row_id, strategy_name, strategy_version, entry_rules,
                 exit_rules, stop_loss_model, target_model, position_sizing_model, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (experiment_row_id, strategy_name, strategy_version, _to_json(entry_rules),
             _to_json(exit_rules), stop_loss_model, target_model, position_sizing_model, now),
        )
        return cur.lastrowid

    def store_parameter_result(self, run_id: str, parameter_set=None, parameter_name: str = None,
                                parameter_value=None, result_score: float = None,
                                result_detail=None) -> int:
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_PARAMETER_RESULTS}
                (experiment_row_id, parameter_set, parameter_name, parameter_value,
                 result_score, result_detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (experiment_row_id, _to_json(parameter_set), parameter_name,
             str(parameter_value) if parameter_value is not None else None,
             result_score, _to_json(result_detail), now),
        )
        return cur.lastrowid

    def store_regime_result(self, run_id: str, regime_type: str, regime_start_date: str = None,
                             regime_end_date: str = None, notes: str = None) -> int:
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_REGIME_RESULTS}
                (experiment_row_id, regime_type, regime_start_date, regime_end_date, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
            (experiment_row_id, regime_type, regime_start_date, regime_end_date, notes, now),
        )
        return cur.lastrowid

    def store_metrics(self, run_id: str, metrics: dict, regime_id: int = None,
                       extra_metrics: dict = None) -> int:
        """
        Insert a performance_metrics row. `metrics` keys should match
        columns (win_rate, profit_factor, ...); unrecognized keys are
        folded into extra_metrics (JSON) automatically rather than raising,
        since the spec explicitly calls for "design for future expansion".
        """
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()

        known_cols = [
            "win_rate", "profit_factor", "net_profit", "gross_profit", "gross_loss",
            "average_trade", "max_drawdown", "max_winning_streak", "max_losing_streak",
            "expectancy", "sharpe_ratio", "sortino_ratio", "calmar_ratio",
            "recovery_factor", "avg_holding_time_minutes", "total_trades",
        ]
        values = {c: metrics.get(c) for c in known_cols}
        overflow = {k: v for k, v in metrics.items() if k not in known_cols}
        if extra_metrics:
            overflow.update(extra_metrics)

        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_PERFORMANCE_METRICS}
                (experiment_row_id, regime_id, win_rate, profit_factor, net_profit,
                 gross_profit, gross_loss, average_trade, max_drawdown,
                 max_winning_streak, max_losing_streak, expectancy, sharpe_ratio,
                 sortino_ratio, calmar_ratio, recovery_factor, avg_holding_time_minutes,
                 total_trades, extra_metrics, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                experiment_row_id, regime_id,
                values["win_rate"], values["profit_factor"], values["net_profit"],
                values["gross_profit"], values["gross_loss"], values["average_trade"],
                values["max_drawdown"], values["max_winning_streak"], values["max_losing_streak"],
                values["expectancy"], values["sharpe_ratio"], values["sortino_ratio"],
                values["calmar_ratio"], values["recovery_factor"], values["avg_holding_time_minutes"],
                values["total_trades"], _to_json(overflow) if overflow else None, now,
            ),
        )
        return cur.lastrowid

    def add_validation_result(self, run_id: str, validation_status: str,
                               validated_by: str = None, validation_notes: str = None) -> int:
        """Append-only — re-validating adds a new row, history is never deleted."""
        if validation_status not in schema.VALIDATION_STATUS_VALUES:
            raise ValueError(
                f"Invalid validation_status '{validation_status}'. "
                f"Must be one of {schema.VALIDATION_STATUS_VALUES}"
            )
        experiment_row_id = self._get_experiment_row_id(run_id)
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_VALIDATION_RESULTS}
                (experiment_row_id, validation_status, validated_by, validation_notes,
                 validated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
            (experiment_row_id, validation_status, validated_by, validation_notes, now, now),
        )
        return cur.lastrowid

    def get_latest_validation_status(self, run_id: str):
        experiment_row_id = self._get_experiment_row_id(run_id)
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_VALIDATION_RESULTS}
                WHERE experiment_row_id = ?
                ORDER BY created_at DESC, validation_id DESC LIMIT 1""",
            (experiment_row_id,),
        )
        row = cur.fetchone()
        return dict(row) if row else None

    def add_note(self, experiment_id: str, note_text: str, created_by: str = None,
                 run_id: str = None) -> int:
        experiment_row_id = self._get_experiment_row_id(run_id) if run_id else None
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_EXPERIMENT_NOTES}
                (experiment_id, experiment_row_id, note_text, created_by, created_at)
                VALUES (?, ?, ?, ?, ?)""",
            (experiment_id, experiment_row_id, note_text, created_by, now),
        )
        return cur.lastrowid

    def get_experiment_notes(self, experiment_id: str) -> list:
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_EXPERIMENT_NOTES}
                WHERE experiment_id = ? ORDER BY created_at ASC""",
            (experiment_id,),
        )
        return [dict(r) for r in cur.fetchall()]

    # =====================================================================
    # RETRIEVE
    # =====================================================================

    def get_experiment(self, run_id: str) -> dict:
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_EXPERIMENTS} WHERE run_id = ?", (run_id,)
        )
        row = cur.fetchone()
        if not row:
            return None
        result = dict(row)
        result["latest_validation"] = self.get_latest_validation_status(run_id)
        return result

    def get_instrument_history(self, instrument_key: str, limit: int = 100) -> list:
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_EXPERIMENTS}
                WHERE instrument_key = ? ORDER BY timestamp DESC, id DESC LIMIT ?""",
            (instrument_key, limit),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_strategy_history(self, strategy_name: str, limit: int = 100) -> list:
        cur = self.conn.execute(
            f"""SELECT s.*, e.instrument_key, e.timestamp, e.run_id, e.research_status
                FROM {schema.TABLE_STRATEGY_RESULTS} s
                JOIN {schema.TABLE_EXPERIMENTS} e ON e.id = s.experiment_row_id
                WHERE s.strategy_name = ?
                ORDER BY e.timestamp DESC, e.id DESC LIMIT ?""",
            (strategy_name, limit),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_indicator_history(self, indicator_name: str, limit: int = 100) -> list:
        cur = self.conn.execute(
            f"""SELECT i.*, e.instrument_key, e.timestamp, e.run_id, e.research_status
                FROM {schema.TABLE_INDICATOR_RESULTS} i
                JOIN {schema.TABLE_EXPERIMENTS} e ON e.id = i.experiment_row_id
                WHERE i.indicator_name = ?
                ORDER BY e.timestamp DESC, e.id DESC LIMIT ?""",
            (indicator_name, limit),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_recent_experiments(self, limit: int = 50) -> list:
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_EXPERIMENTS} ORDER BY timestamp DESC, id DESC LIMIT ?",
            (limit,),
        )
        return [dict(r) for r in cur.fetchall()]

    def search_experiments(self, limit: int = 100, **filters) -> list:
        """
        Flexible search. Only columns in schema.SEARCHABLE_EXPERIMENT_COLUMNS
        may be filtered on — this whitelist is the injection guard, since
        column names can't be parameterized with `?`.

        Example: db.search_experiments(instrument_key="MCX_FO|NATURALGAS25JUL",
                                        research_type="STRATEGY", limit=20)
        """
        unknown = set(filters) - set(schema.SEARCHABLE_EXPERIMENT_COLUMNS)
        if unknown:
            raise ValueError(
                f"Cannot filter on {unknown}. Allowed: {schema.SEARCHABLE_EXPERIMENT_COLUMNS}"
            )
        if not filters:
            return self.get_recent_experiments(limit=limit)

        where_clause = " AND ".join(f"{k} = ?" for k in filters)
        params = list(filters.values()) + [limit]
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_EXPERIMENTS}
                WHERE {where_clause} ORDER BY timestamp DESC, id DESC LIMIT ?""",
            tuple(params),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_performance_ranking(self, metric: str = "sharpe_ratio", limit: int = 50,
                                 research_type: str = None, instrument_key: str = None,
                                 descending: bool = True) -> list:
        """
        Rank experiments by a performance metric. `metric` MUST be in
        schema.RANKABLE_METRIC_COLUMNS — validated before being interpolated
        into the ORDER BY clause (SQL identifiers can't be parameterized).
        """
        if metric not in schema.RANKABLE_METRIC_COLUMNS:
            raise ValueError(
                f"Cannot rank by '{metric}'. Allowed: {schema.RANKABLE_METRIC_COLUMNS}"
            )
        direction = "DESC" if descending else "ASC"

        where_clauses = [f"pm.{metric} IS NOT NULL"]
        params = []
        if research_type:
            where_clauses.append("e.research_type = ?")
            params.append(research_type)
        if instrument_key:
            where_clauses.append("e.instrument_key = ?")
            params.append(instrument_key)
        where_sql = " AND ".join(where_clauses)
        params.append(limit)

        cur = self.conn.execute(
            f"""SELECT pm.*, e.instrument_key, e.research_type, e.run_id, e.timestamp
                FROM {schema.TABLE_PERFORMANCE_METRICS} pm
                JOIN {schema.TABLE_EXPERIMENTS} e ON e.id = pm.experiment_row_id
                WHERE {where_sql}
                ORDER BY pm.{metric} {direction}
                LIMIT ?""",
            tuple(params),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_regime_results(self, run_id: str) -> list:
        experiment_row_id = self._get_experiment_row_id(run_id)
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_REGIME_RESULTS} WHERE experiment_row_id = ?",
            (experiment_row_id,),
        )
        return [dict(r) for r in cur.fetchall()]

    def count_experiments(self) -> int:
        cur = self.conn.execute(f"SELECT COUNT(*) AS c FROM {schema.TABLE_EXPERIMENTS}")
        return cur.fetchone()["c"]

    # =====================================================================
    # LIVE TRADES (migrated from signal_log.csv — see migrations.py
    # migration_002_add_live_trades_table for the table definition).
    #
    # This is a pure storage-backend swap for the live BUY/SELL signal
    # log that used to be signal_log.csv, read/written by three
    # previously-independent places: the Streamlit app (signal_log.py),
    # the outcome-checker GitHub Action (check_signals.py), and the
    # weekly Telegram report (weekly_summary.py). All three now go
    # through these methods instead of touching a CSV file directly.
    #
    # Column set and meaning are unchanged from signal_log.csv.
    # =====================================================================

    LIVE_TRADE_COLUMNS = [
        "signal_id", "timestamp", "instrument", "instrument_key", "signal",
        "trend", "confidence", "score", "entry_price", "sl", "t1", "t2",
        "status", "closed_price", "closed_at", "pnl_pct",
        "daily_trend_agree", "supertrend_agree", "market_trend_agree",
        "adx", "conviction_pct", "expected_move_pct", "t2_hit_at",
    ]

    def insert_live_trade(self, record: dict) -> int:
        """
        Inserts a new OPEN live trade row. `record` should contain (at
        minimum) the fields in LIVE_TRADE_COLUMNS other than status/
        closed_price/closed_at/pnl_pct/t2_hit_at, which default to OPEN/
        NULL as appropriate for a freshly-generated signal. Returns the
        new row's surrogate id.
        """
        now = _now_iso()
        cur = self.conn.execute(
            f"""INSERT INTO {schema.TABLE_LIVE_TRADES}
                (signal_id, timestamp, instrument, instrument_key, signal,
                 trend, confidence, score, entry_price, sl, t1, t2,
                 status, closed_price, closed_at, pnl_pct,
                 daily_trend_agree, supertrend_agree, market_trend_agree,
                 adx, conviction_pct, expected_move_pct, t2_hit_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                record["signal_id"], record["timestamp"], record["instrument"],
                record.get("instrument_key"), record["signal"], record.get("trend"),
                record.get("confidence"), record.get("score"), record.get("entry_price"),
                record.get("sl"), record.get("t1"), record.get("t2"),
                record.get("status", "OPEN"), record.get("closed_price"),
                record.get("closed_at"), record.get("pnl_pct"),
                record.get("daily_trend_agree"), record.get("supertrend_agree"),
                record.get("market_trend_agree"), record.get("adx"),
                record.get("conviction_pct"), record.get("expected_move_pct"),
                record.get("t2_hit_at"), now,
            ),
        )
        return cur.lastrowid

    def get_open_live_trade(self, instrument: str, signal: str):
        """
        Returns the existing OPEN row for this instrument+direction, or
        None. Used to avoid re-logging an unchanged open position — same
        duplicate-guard signal_log.append_new_signals() always had.
        """
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_LIVE_TRADES}
                WHERE instrument = ? AND signal = ? AND status = 'OPEN'
                LIMIT 1""",
            (instrument, signal),
        )
        row = cur.fetchone()
        return dict(row) if row else None

    def get_all_live_trades(self) -> list:
        """All trades, oldest first — matches load_signal_log()'s old
        CSV read-order so downstream pandas analytics behave identically."""
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_LIVE_TRADES} ORDER BY timestamp ASC"
        )
        return [dict(r) for r in cur.fetchall()]

    def get_open_live_trades(self) -> list:
        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_LIVE_TRADES} WHERE status = 'OPEN' ORDER BY timestamp ASC"
        )
        return [dict(r) for r in cur.fetchall()]

    def get_t2_candidate_trades(self) -> list:
        """TARGET_HIT trades not yet observed for a T2 touch — mirrors
        check_signals.py's old t2_candidates_mask exactly."""
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_LIVE_TRADES}
                WHERE status = 'TARGET_HIT'
                AND (t2_hit_at IS NULL OR t2_hit_at = '')
                ORDER BY timestamp ASC"""
        )
        return [dict(r) for r in cur.fetchall()]

    def update_live_trade_outcome(self, signal_id: str, status: str,
                                   closed_price: float, closed_at: str, pnl_pct: float):
        """The ONLY method that ever sets a trade's official outcome —
        same rule check_signals.check_outcome() always enforced."""
        self.conn.execute(
            f"""UPDATE {schema.TABLE_LIVE_TRADES}
                SET status = ?, closed_price = ?, closed_at = ?, pnl_pct = ?
                WHERE signal_id = ?""",
            (status, closed_price, closed_at, pnl_pct, signal_id),
        )

    def update_live_trade_t2_hit(self, signal_id: str, t2_hit_at: str):
        """Purely observational — never touches status/closed_price/pnl_pct.
        See check_signals.check_t2_touch()'s docstring for why."""
        self.conn.execute(
            f"UPDATE {schema.TABLE_LIVE_TRADES} SET t2_hit_at = ? WHERE signal_id = ?",
            (t2_hit_at, signal_id),
        )

    def mark_live_trade_expired(self, signal_id: str):
        self.conn.execute(
            f"UPDATE {schema.TABLE_LIVE_TRADES} SET status = 'EXPIRED' WHERE signal_id = ?",
            (signal_id,),
        )

    # ----------------------------------------------------------------
    # scan_snapshots (NGSP Phase 0, PR 6a) — see migrations.py's
    # migration_003_add_scan_snapshots_table for the full design notes.
    # Append-only: no update/delete methods exist for this table by
    # design, same as every other table here except live_trades.
    # ----------------------------------------------------------------

    def insert_scan_snapshot_rows(self, records: list) -> int:
        """
        Bulk-inserts one full scan batch (every instrument scanner.py's
        full_df returned, not just actionable BUY/SELL rows). Each dict in
        `records` should already be shaped with scan_snapshots' exact
        column names (see build_scan_snapshot_record() in
        generate_signals.py) — this method does no field mapping itself,
        it only executes the insert. Returns the number of rows inserted.
        """
        if not records:
            return 0

        now = _now_iso()
        rows = [
            (
                r["scanned_at"], r["instrument"], r.get("instrument_key"),
                r.get("sector"), r["signal"], r.get("confidence"),
                r.get("trend"), r.get("daily_trend"), r.get("market_trend"),
                r.get("supertrend"), r.get("supertrend_value"), r.get("regime"),
                r.get("adx"), r.get("conviction_pct"), r.get("daily_trend_agree"),
                r.get("supertrend_agree"), r.get("market_trend_agree"),
                r.get("score"), r.get("prob_pct"), r.get("rsi"),
                r.get("volume_ratio"), r.get("volume_label"),
                r.get("expected_move_pct"), r.get("rr"), r.get("price"),
                r.get("sl"), r.get("t1"), r.get("t2"), r.get("reason"), now,
            )
            for r in records
        ]

        self.conn.executemany(
         f"""INSERT INTO {schema.TABLE_SCAN_SNAPSHOTS}
                (scanned_at, instrument, instrument_key, sector, signal,
                 confidence, trend, daily_trend, market_trend, supertrend,
                 supertrend_value, regime, adx, conviction_pct,
                 daily_trend_agree, supertrend_agree, market_trend_agree,
                 score, prob_pct, rsi, volume_ratio, volume_label,
                 expected_move_pct, rr, price, sl, t1, t2, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            rows,
        )
        return len(rows)

    def get_latest_scan_snapshot(self) -> list:
        """
        Returns every row from the most recent scan batch (all instruments,
        every signal type — the same shape scanner.run_scanner()'s full_df
        had, just read back instead of recomputed). Empty list if no scan
        has ever been persisted yet.
        """
        cur = self.conn.execute(
            f"SELECT MAX(scanned_at) AS latest FROM {schema.TABLE_SCAN_SNAPSHOTS}"
        )
        latest = cur.fetchone()["latest"]
        if latest is None:
            return []

        cur = self.conn.execute(
            f"SELECT * FROM {schema.TABLE_SCAN_SNAPSHOTS} WHERE scanned_at = ? ORDER BY id ASC",
            (latest,),
        )
        return [dict(r) for r in cur.fetchall()]

    def get_latest_research_summary(self, instrument_key: str) -> dict:
        """
        PR 8, Part 3 support. Returns the most recent COMPLETED STRATEGY
        experiment for an instrument, joined with its strategy_results,
        best-ranked performance_metrics row, regime breakdown, and notes —
        everything research_snapshot_reader.py needs in one call, so that
        module (like scan_snapshot_reader.py before it) stays a thin
        reshape layer rather than a second place that knows the schema.

        Returns {} if no completed research exists yet for this instrument
        — a real, valid "nothing yet" state, not an error.
        """
        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_EXPERIMENTS}
                WHERE instrument_key = ? AND research_type = 'STRATEGY'
                  AND research_status = 'COMPLETED'
                ORDER BY timestamp DESC, id DESC LIMIT 1""",
            (instrument_key,),
        )
        experiment = cur.fetchone()
        if not experiment:
            return {}
        experiment = dict(experiment)
        experiment_row_id = experiment["id"]

        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_STRATEGY_RESULTS}
                WHERE experiment_row_id = ? ORDER BY result_id ASC""",
            (experiment_row_id,),
        )
        strategy_rows = [dict(r) for r in cur.fetchall()]

        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_PERFORMANCE_METRICS}
                WHERE experiment_row_id = ? AND regime_id IS NULL
                ORDER BY win_rate DESC, total_trades DESC""",
            (experiment_row_id,),
        )
        metrics_rows = [dict(r) for r in cur.fetchall()]

        cur = self.conn.execute(
            f"""SELECT r.regime_type, r.notes AS regime_notes, m.*
                FROM {schema.TABLE_REGIME_RESULTS} r
                JOIN {schema.TABLE_PERFORMANCE_METRICS} m ON m.regime_id = r.regime_id
                WHERE r.experiment_row_id = ?""",
            (experiment_row_id,),
        )
        regime_rows = [dict(r) for r in cur.fetchall()]

        notes = self.get_experiment_notes(experiment["experiment_id"])

        cur = self.conn.execute(
            f"""SELECT * FROM {schema.TABLE_INDICATOR_RESULTS}
                WHERE experiment_row_id = ? ORDER BY result_id ASC""",
            (experiment_row_id,),
        )
        indicator_rows = [dict(r) for r in cur.fetchall()]

        return {
            "experiment": experiment,
            "strategy_results": strategy_rows,
            "overall_metrics": metrics_rows,
            "regime_metrics": regime_rows,
            "indicator_results": indicator_rows,
            "notes": notes,
        }
