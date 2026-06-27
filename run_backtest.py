from strategy_lab.backtest import run_backtest
from strategy_lab.optimizer import run_parameter_test
from strategy_lab.utils import load_symbol_map
import pandas as pd

def execute_backtest():

    symbol_map = load_symbol_map()

    all_results = []

    for instrument_name, instrument_key in symbol_map.items():

        result = run_backtest(instrument_name, instrument_key)

        if isinstance(result, dict) and "data" in result:
            df = result["data"].copy()
            df["Instrument"] = instrument_name
            all_results.append(df)

    if all_results:
        trades_df = pd.concat(all_results, ignore_index=True)
    else:
        trades_df = pd.DataFrame()

    optimizer_df = run_parameter_test({}, {})

    return trades_df, optimizer_df
