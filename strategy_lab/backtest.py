import pandas as pd
from datetime import datetime
from strategy_lab.strategies import run_strategy

def run_backtest(data_dict, config):
    """
    data_dict format:
        {
            "RELIANCE": candles,
            "TCS": candles
        }

    candles format:
        [ [ts, o, h, l, c, v, oi], ... ]  (newest-first or oldest-first consistent)
    """

    results = []

    for symbol, candles in data_dict.items():

        if not candles or len(candles) < 50:
            continue

        signals = run_strategy(symbol, candles, config)

        for s in signals:
            results.append({
                "symbol": symbol,
                "time": datetime.now(),
                "signal": s["signal"],
                "score": s["score"],
                "price": s["price"],
                "sl": s["sl"],
                "t1": s["t1"],
                "t2": s["t2"],
                "trend": s["trend"],
                "regime": s["regime"]
            })

    return pd.DataFrame(results)


def save_backtest(df, path="backtest_results.csv"):
    df.to_csv(path, index=False)
    return path


def load_backtest(path="backtest_results.csv"):
    try:
        return pd.read_csv(path)
    except:
        return pd.DataFrame()


def summarize_backtest(df):
    if df.empty:
        return {}

    total = len(df)
    buy = len(df[df["signal"] == "BUY"])
    sell = len(df[df["signal"] == "SELL"])

    return {
        "total_signals": total,
        "buy": buy,
        "sell": sell
    }
