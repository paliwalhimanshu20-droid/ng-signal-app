import pandas as pd
from datetime import datetime

from signal_logic import signal_engine, ema, atr
from upstox_client import get_candles_range


# -----------------------------
# BACKTEST CONFIG
# -----------------------------
CANDLE_INTERVAL_DAYS = 30   # chunk size per fetch
LOOKBACK_DAYS = 120         # total history window


# -----------------------------
# LOAD DATA
# -----------------------------
def load_history(instrument_key):
    """
    Fetch historical candles from Upstox
    """
    candles = get_candles_range(instrument_key, days_back=LOOKBACK_DAYS)

    if not candles:
        return None

    return candles


# -----------------------------
# RUN BACKTEST
# -----------------------------
def run_backtest(instrument_name, instrument_key):
    candles = load_history(instrument_key)

    if not candles or len(candles) < 100:
        return {
            "error": f"Not enough data for {instrument_name}"
        }

    results = []

    closes = []

    for i in range(len(candles) - 1, 50, -1):
        window = candles[i: i - 50: -1]

        if len(window) < 50:
            continue

        closes = [c[4] for c in window]
        price = closes[-1]

        ema20 = ema(closes, 20)
        ema50 = ema(closes, 50)

        atr_val = atr(window)

        signal, score, prob, trend, regime, exp_move, reasons, conviction = signal_engine(
            price=price,
            ema20=ema20,
            ema50=ema50,
            atr_val=atr_val
        )

        results.append({
            "Index": i,
            "Price": price,
            "Signal": signal,
            "Score": score,
            "Prob%": prob,
            "Trend": trend,
            "Regime": regime,
            "ExpectedMove%": exp_move,
            "Conviction%": conviction
        })

    df = pd.DataFrame(results)

    if df.empty:
        return {"error": "No signals generated"}

    # -----------------------------
    # SIMPLE PERFORMANCE STATS
    # -----------------------------
    total = len(df)
    buys = len(df[df["Signal"] == "BUY"])
    sells = len(df[df["Signal"] == "SELL"])
    watch = len(df[df["Signal"] == "WATCH"])
    none = len(df[df["Signal"] == "NO TRADE"])

    summary = {
        "instrument": instrument_name,
        "total_checks": total,
        "buy_signals": buys,
        "sell_signals": sells,
        "watch_signals": watch,
        "no_trade": none,
        "buy_ratio": round(buys / total * 100, 2),
        "sell_ratio": round(sells / total * 100, 2),
    }

    return {
        "summary": summary,
        "data": df
    }


# -----------------------------
# QUICK TEST RUN (OPTIONAL)
# -----------------------------
if __name__ == "__main__":
    print("Backtest module ready")
