"""
scripts/example_usage.py

Demonstrates the full data-access API a future Research Engine would use.
This script does NOT perform any real research — all values below are
illustrative placeholders showing how to call each method.

Usage:
    python scripts/example_usage.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from research_config import settings
from research_db.database import ResearchDatabase


def main():
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)

    # 1. Create a new experiment (new conceptual research question)
    exp = db.create_experiment(
        instrument_key="MCX_FO|NATURALGAS25JUL",
        research_type="STRATEGY",
        research_engine_version="research-engine-v0.1",
        created_by="example_usage.py",
        source_data_version="upstox-instrument-master-v4",
        random_seed=42,
        notes="Example: testing a trend-following strategy on Natural Gas",
    )
    print(f"Created experiment: {exp}")
    run_id = exp["run_id"]
    experiment_id = exp["experiment_id"]

    # 2. Mark it running, then store some indicator results
    db.update_status(run_id, "RUNNING")
    db.store_indicator_result(
        run_id, indicator_name="EMA_CROSSOVER",
        parameters={"fast": 9, "slow": 21}, calculation_version="v1",
        result={"signal_count": 148, "avg_signal_strength": 0.62},
    )

    # 3. Store a strategy definition tested in this run
    db.store_strategy_result(
        run_id, strategy_name="TREND_FOLLOW_BASIC", strategy_version="v1",
        entry_rules={"condition": "EMA9 crosses above EMA21"},
        exit_rules={"condition": "EMA9 crosses below EMA21"},
        stop_loss_model="ATR_2x", target_model="ATR_4x",
        position_sizing_model="fixed_lot",
    )

    # 4. Store a market regime this was tested against
    regime_id = db.store_regime_result(
        run_id, regime_type="TRENDING",
        regime_start_date="2026-01-01", regime_end_date="2026-06-30",
    )

    # 5. Store performance metrics (regime-specific, via regime_id)
    db.store_metrics(run_id, metrics={
        "win_rate": 0.54, "profit_factor": 1.38, "net_profit": 42500.0,
        "sharpe_ratio": 1.21, "max_drawdown": -8200.0, "total_trades": 148,
    }, regime_id=regime_id)

    # 6. Mark the run complete and validate it
    db.update_status(run_id, "COMPLETED")
    db.add_validation_result(run_id, "VALIDATION_PENDING", validated_by="example_usage.py")
    db.add_note(experiment_id, "Initial pass looks promising, needs regime robustness check",
                created_by="example_usage.py", run_id=run_id)
    db.commit()

    # 7. Retrieve it back
    print("\nRetrieved experiment:", db.get_experiment(run_id))
    print("\nInstrument history:", db.get_instrument_history("MCX_FO|NATURALGAS25JUL"))
    print("\nStrategy history:", db.get_strategy_history("TREND_FOLLOW_BASIC"))
    print("\nPerformance ranking by sharpe_ratio:",
          db.get_performance_ranking(metric="sharpe_ratio", limit=5))
    print("\nSearch by instrument + type:",
          db.search_experiments(instrument_key="MCX_FO|NATURALGAS25JUL", research_type="STRATEGY"))

    # 8. Create a new VERSION of the same experiment (rerun with a design change)
    exp_v2 = db.create_experiment(
        instrument_key="MCX_FO|NATURALGAS25JUL",
        research_type="STRATEGY",
        experiment_id=experiment_id,  # same conceptual experiment
        change_description="v2: widened stop-loss from 2x ATR to 3x ATR",
        created_by="example_usage.py",
    )
    print(f"\nCreated v2 of the same experiment: {exp_v2}")
    print("All versions:", db.get_experiment_versions(experiment_id))

    db.close()


if __name__ == "__main__":
    main()
