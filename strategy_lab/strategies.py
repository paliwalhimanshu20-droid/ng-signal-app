from signal_logic import (
    ema,
    atr,
    rsi,
    compute_adx,
    calculate_supertrend,
    signal_engine,
    levels
)

def run_strategy(symbol, candles, config=None):
    """
    Core strategy engine used by both:
      - live app (conceptually)
      - backtest engine

    candles format:
      [ [ts, open, high, low, close, volume, oi], ... ]
    """

    signals = []

    if not candles or len(candles) < 60:
        return signals

    # Ensure chronological order (oldest first)
    candles = list(reversed(candles))

    closes = [c[4] for c in candles]

    ema20 = ema(closes, 20)
    ema50 = ema(closes, 50)
    atr_val = atr(candles)

    adx_val = compute_adx(candles)
    supertrend_data = calculate_supertrend(list(reversed(candles)))

    supertrend_trend = None
    if supertrend_data:
        supertrend_trend = supertrend_data["latest_trend"]

    price = closes[-1]

    signal, score, prob, trend, regime, expected_move, reasons, conviction = signal_engine(
        price=price,
        ema20=ema20,
        ema50=ema50,
        atr_val=atr_val,
        supertrend_trend=supertrend_trend,
        adx_val=adx_val
    )

    sl, t1, t2 = levels(price, atr_val, signal, trend, regime)

    signals.append({
        "symbol": symbol,
        "signal": signal,
        "score": score,
        "prob": prob,
        "price": price,
        "sl": sl,
        "t1": t1,
        "t2": t2,
        "trend": trend,
        "regime": regime,
        "reasons": reasons,
        "conviction": conviction
    })

    return signals
