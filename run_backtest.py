from strategy_lab.backtest import run_backtest
from strategy_lab.optimizer import run_parameter_test
from strategy_lab.reports import print_report
from strategy_lab.utils import fetch_candles, load_symbol_map

def execute_backtest():
    symbol_map = load_symbol_map()
    data_dict = fetch_candles(symbol_map, days_back=15)

    df = run_backtest(data_dict, config={})

    param_grid = {
        "ema_fast": [18, 20, 22],
        "ema_slow": [45, 50, 55],
        "adx": [18, 20, 25]
    }

    result_df = run_parameter_test(data_dict, param_grid)

    return df, result_df
