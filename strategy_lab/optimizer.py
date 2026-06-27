import pandas as pd
from strategy_lab.strategies import run_strategy

def run_parameter_test(data_dict, param_grid):
    """
    Brute-force parameter testing engine.

    This is where you will eventually answer:
      - EMA 18 vs EMA 20
      - ADX 18 vs 25
      - RSI thresholds
      - volatility filters
      - regime sensitivity
    """

    results = []

    for ema_fast in param_grid.get("ema_fast", [20]):
        for ema_slow in param_grid.get("ema_slow", [50]):
            for adx_threshold in param_grid.get("adx", [20]):

                config = {
                    "ema_fast": ema_fast,
                    "ema_slow": ema_slow,
                    "adx": adx_threshold
                }

                total_trades = 0
                wins = 0
                pnl_sum = 0

                for symbol, candles in data_dict.items():

                    signals = run_strategy(symbol, candles, config)

                    for s in signals:
                        if s["signal"] in ["BUY", "SELL"]:
                            total_trades += 1

                            # simplified placeholder pnl logic (will evolve later)
                            pnl = (s["t2"] - s["price"]) if s["signal"] == "BUY" else (s["price"] - s["t2"])
                            pnl_sum += pnl

                            if pnl > 0:
                                wins += 1

                win_rate = (wins / total_trades * 100) if total_trades else 0

                results.append({
                    "ema_fast": ema_fast,
                    "ema_slow": ema_slow,
                    "adx": adx_threshold,
                    "trades": total_trades,
                    "win_rate": round(win_rate, 2),
                    "pnl": round(pnl_sum, 2)
                })

    return pd.DataFrame(results).sort_values("win_rate", ascending=False)
