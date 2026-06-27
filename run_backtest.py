from strategy_lab.backtest import run_backtest, save_backtest
from strategy_lab.optimizer import run_parameter_test
from strategy_lab.reports import print_report
from strategy_lab.utils import fetch_candles, load_symbol_map

print("Fetching historical data...")

symbol_map = load_symbol_map()
data_dict = fetch_candles(symbol_map, days_back=15)

print(f"Loaded symbols: {list(data_dict.keys())}")

# =========================
# BACKTEST
# =========================
print("\nRunning backtest...")

df = run_backtest(data_dict, config={})
print(df)

if not df.empty:
    save_backtest(df)

# =========================
# OPTIMIZER
# =========================
print("\nRunning optimizer...")

param_grid = {
    "ema_fast": [18, 20, 22],
    "ema_slow": [45, 50, 55],
    "adx": [18, 20, 25]
}

result_df = run_parameter_test(data_dict, param_grid)

print_report(result_df)
