"""
strategy_lab/research_bridge.py — PR 8, Part 1 (extended in the PR 8
continuation for Requirements 1-3).

Persists strategy_lab.research_engine.analyze_instrument()'s output into
research_db, using ONLY methods that already exist on ResearchDatabase
(create_experiment, store_indicator_result, store_strategy_result,
store_regime_result, store_metrics, add_note, update_status). No new
tables in this file — see research_db/migrations.py for the one small
additive column PR 8 does add (confidence_source).

Now also persists (continuation requirements):
  - Indicator Reliability (per-indicator win_rate/sample_size/average_return/
    average_drawdown) via store_indicator_result() — a method that
    existed since the original schema but was never actually called
    until now.
  - Research Score + Instrument DNA, stored as one performance_metrics
    row's extra_metrics JSON, tagged record_type="research_score_and_dna"
    so research_snapshot_reader.py can find it without a new table.

Deliberately does not touch scanner.py, signal_logic.py, or the
live-signal path in any way — this only ever reads from strategy_lab and
writes to research_db.
"""

from research_config import settings
from research_db.database import ResearchDatabase


def persist_research_result(result: dict, created_by: str = "generate_research.py") -> str:
    """
    result: the dict returned by strategy_lab.research_engine.analyze_instrument().
    Returns the run_id of the experiment created, so callers (e.g. the
    incremental-update check in generate_research.py) can reference it.

    Requires result["data_available"] is True — callers should check
    that before calling this (generate_research.py does).
    """
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    exp = None
    try:
        exp = db.create_experiment(
            instrument_key=result["instrument_key"],
            research_type="STRATEGY",
            research_engine_version="strategy_lab.research_engine v2 (PR 8 continuation)",
            created_by=created_by,
            research_status="RUNNING",
            change_description=(
                f"Indicator/strategy/DNA research run for {result['instrument_name']} "
                f"(data source: {result['data_source']})"
            ),
        )
        run_id = exp["run_id"]

        for combo in result["combinations"]:
            db.store_strategy_result(
                run_id=run_id,
                strategy_name=combo["combination_name"],
                strategy_version="v2",
                entry_rules={"engine": "signal_logic.signal_engine"},
                exit_rules={"model": "SL/T1/T2 via signal_logic.levels(), max 20 bars"},
                stop_loss_model="ATR-based, regime-adjusted (signal_logic.levels)",
                target_model="ATR-based, regime-adjusted (signal_logic.levels)",
                position_sizing_model=None,
            )
            db.store_metrics(run_id=run_id, metrics=combo["metrics"], extra_metrics={
                "strategy_rank": combo["strategy_rank"],
                "combination_name": combo["combination_name"],
                "total_signals": combo["total_signals"],
                "confidence_source": result["confidence_source"],
                "best_holding_days": combo.get("best_holding_days"),
                "worst_holding_days": combo.get("worst_holding_days"),
            })

            for regime_type, regime_metrics in combo["regime_breakdown"].items():
                regime_row_id = db.store_regime_result(
                    run_id=run_id,
                    regime_type=regime_type,
                    notes=f"Regime-sliced performance for '{combo['combination_name']}'",
                )
                db.store_metrics(run_id=run_id, metrics=regime_metrics, regime_id=regime_row_id)

        # Indicator reliability (Requirement 2) — one indicator_test_results
        # row per indicator, using the method that existed but was never
        # actually called by the first PR 8 pass.
        for indicator_name, reliability in result["indicator_reliability"].items():
            db.store_indicator_result(
                run_id=run_id,
                indicator_name=indicator_name,
                calculation_version="strategy_lab.indicator_signals v1",
                result=reliability,
            )

        # Research Score (Requirement 1) and Instrument DNA (Requirement 3)
        # — no dedicated columns for either (per "avoid schema changes
        # unless absolutely necessary"), stored as a single
        # performance_metrics row's extra_metrics JSON, tagged with
        # record_type so research_snapshot_reader.py can find it without
        # guessing. regime_id=NULL, same as any whole-experiment metric.
        db.store_metrics(run_id=run_id, metrics={}, extra_metrics={
            "record_type": "research_score_and_dna",
            "research_score": result["research_score"],
            "instrument_dna": result["instrument_dna"],
        })

        best = result["best_combination"]
        why_text = (
            f"Best strategy for {result['instrument_name']}: '{best['combination_name']}' "
            f"(rank 1 of {len(result['combinations'])}), win rate "
            f"{best['metrics']['win_rate']}% over {best['metrics']['total_trades']} simulated "
            f"trades. Research Score: {result['research_score']['score']}/100 "
            f"(win rate {result['research_score']['breakdown']['win_rate']['contribution']}pt + "
            f"sample size {result['research_score']['breakdown']['sample_size']['contribution']}pt + "
            f"profit factor {result['research_score']['breakdown']['profit_factor']['contribution']}pt + "
            f"regime consistency {result['research_score']['breakdown']['regime_consistency']['contribution']}pt). "
            f"Performs best in {result['best_market_regime'] or 'an unspecified'} regime. "
            f"Strategy family: {result['instrument_dna']['strategy_family']}. "
            f"Confidence source: {result['confidence_source']}."
        )
        db.add_note(experiment_id=exp["experiment_id"], run_id=run_id,
                     note_text=why_text, created_by=created_by)

        db.update_status(run_id, "COMPLETED")
        db.commit()
        return run_id
    except Exception:
        if exp is not None:
            db.update_status(exp["run_id"], "FAILED")
            db.commit()
        raise
    finally:
        db.close()


def apply_confidence_source_column(run_id: str, confidence_source: str):
    """
    Writes the dedicated confidence_source column (PR 8's one additive
    migration) on every strategy_test_results row belonging to this run.
    Kept as a separate, optional step from persist_research_result() so
    a database that hasn't been migrated yet still gets a fully usable
    research record (the value is already captured in extra_metrics
    above either way) — this just also makes it directly queryable as a
    real column for read performance.
    """
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    try:
        experiment_row_id = db._get_experiment_row_id(run_id)
        db.conn.execute(
            "UPDATE strategy_test_results SET confidence_source = ? WHERE experiment_row_id = ?",
            (confidence_source, experiment_row_id),
        )
        db.commit()
    finally:
        db.close()
