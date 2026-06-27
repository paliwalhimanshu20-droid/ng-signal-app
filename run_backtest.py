import pandas as pd
from strategy_lab.backtest import run_backtest, save_backtest
from strategy_lab.optimizer import run_parameter_test
from strategy_lab.reports import print_report

# =========================
# SAMPLE DATA PLACEHOLDER
# =========================
# Replace this with real Upstox candle fetch later
# Format:
# {
#   "RELIANCE": [[ts,o,h,l,c,v,oi], ...],
#   "TCS": [[ts,o,h,l,c,v,oi], ...]
# }

data_dict = {
    "RELIANCE": [],
    "TCS": []
}

# =========================
# 1. SIMPLE BACKTEST RUN
# =========================
print("Running basic backtest...")

df = run_backtest(data_dict, config={})

print(df)

if not df.empty:
    path = save_backtest(df)
    print("Saved to:", path)

# =========================
# 2. PARAMETER OPTIMIZATION
# =========================
print("\nRunning optimizer...")

param_grid = {
    "ema_fast": [18, 20],
    "ema_slow": [45, 50],
    "adx": [18, 20, 25]
}

result_df = run_parameter_test(data_dict, param_grid)

print_report(result_df)
