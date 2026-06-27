"""
Plotly chart-building for the per-instrument drill-down view (clicking a
row in the "Full Scanned Universe" table on the Scanner tab).
"""

import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from signal_logic import calculate_supertrend


def build_instrument_chart(instrument_name, candles):
    """
    Builds a 3-panel Plotly chart for a single instrument:
      1. Candlestick price with EMA20/EMA50 overlay
      2. RSI(14) with 30/70 reference lines
      3. Volume bars

    candles: raw Upstox candle list (newest-first), same format used
    elsewhere in this app: [timestamp, open, high, low, close, volume, oi]

    Returns a Plotly Figure, or None if there isn't enough data to chart.
    """
    if not candles or len(candles) < 15:
        return None

    # Candles arrive newest-first from Upstox — reverse to chronological
    # order for charting (oldest on the left, newest on the right).
    ordered = list(reversed(candles))

    timestamps = [pd.to_datetime(c[0]) for c in ordered]
    opens = [c[1] for c in ordered]
    highs = [c[2] for c in ordered]
    lows = [c[3] for c in ordered]
    closes = [c[4] for c in ordered]
    volumes = [c[5] if len(c) > 5 else 0 for c in ordered]

    # EMA series across the full chronological close history (for the overlay line)
    def ema_series(prices, period):
        if len(prices) < period:
            return [None] * len(prices)
        m = 2 / (period + 1)
        out = [None] * (period - 1)
        e = sum(prices[:period]) / period
        out.append(e)
        for p in prices[period:]:
            e = (p - e) * m + e
            out.append(e)
        return out

    ema20_series = ema_series(closes, 20)
    ema50_series = ema_series(closes, 50)

    # RSI series (rolling, point-by-point) for the RSI subplot
    def rsi_series(prices, period=14):
        out = [None] * len(prices)
        if len(prices) < period + 1:
            return out
        for i in range(period, len(prices)):
            window = prices[i - period:i + 1]
            gains = [max(window[j] - window[j - 1], 0) for j in range(1, len(window))]
            losses = [max(window[j - 1] - window[j], 0) for j in range(1, len(window))]
            avg_gain = sum(gains) / period
            avg_loss = sum(losses) / period
            if avg_loss == 0:
                out[i] = 100.0
            else:
                rs = avg_gain / avg_loss
                out[i] = round(100 - (100 / (1 + rs)), 2)
        return out

    rsi_vals = rsi_series(closes, 14)

    fig = make_subplots(
        rows=3, cols=1,
        shared_xaxes=True,
        row_heights=[0.55, 0.2, 0.25],
        vertical_spacing=0.04,
        subplot_titles=(f"{instrument_name} — Price, EMA & Supertrend", "RSI (14)", "Volume")
    )

    # --- Panel 1: Candlestick + EMA overlay ---
    fig.add_trace(go.Candlestick(
        x=timestamps, open=opens, high=highs, low=lows, close=closes,
        name="Price", showlegend=False
    ), row=1, col=1)

    fig.add_trace(go.Scatter(
        x=timestamps, y=ema20_series, mode="lines", name="EMA20",
        line=dict(color="#3498db", width=1.5)
    ), row=1, col=1)

    fig.add_trace(go.Scatter(
        x=timestamps, y=ema50_series, mode="lines", name="EMA50",
        line=dict(color="#e67e22", width=1.5)
    ), row=1, col=1)

    # --- Supertrend overlay ---
    # Split into two traces (Bullish/Bearish) so the line color flips at
    # each trend flip instead of drawing one flat-colored line. There's a
    # one-bar gap right at each flip (the point belongs to only one trace)
    # — a standard, accepted tradeoff for this kind of split-trace coloring.
    st_result = calculate_supertrend(candles)
    if st_result:
        st_line = st_result["supertrend"]
        st_trend = st_result["trend"]

        bullish_line = [v if t == "Bullish" else None for v, t in zip(st_line, st_trend)]
        bearish_line = [v if t == "Bearish" else None for v, t in zip(st_line, st_trend)]

        fig.add_trace(go.Scatter(
            x=timestamps, y=bullish_line, mode="lines", name="Supertrend (Up)",
            line=dict(color="#2ecc71", width=1.5), connectgaps=False
        ), row=1, col=1)

        fig.add_trace(go.Scatter(
            x=timestamps, y=bearish_line, mode="lines", name="Supertrend (Down)",
            line=dict(color="#e74c3c", width=1.5), connectgaps=False
        ), row=1, col=1)

    # --- Panel 2: RSI ---
    fig.add_trace(go.Scatter(
        x=timestamps, y=rsi_vals, mode="lines", name="RSI",
        line=dict(color="#9b59b6", width=1.5), showlegend=False
    ), row=2, col=1)

    fig.add_hline(y=70, line_dash="dot", line_color="red", row=2, col=1)
    fig.add_hline(y=30, line_dash="dot", line_color="green", row=2, col=1)

    # --- Panel 3: Volume ---
    bar_colors = [
        "#2ecc71" if closes[i] >= opens[i] else "#e74c3c"
        for i in range(len(closes))
    ]
    fig.add_trace(go.Bar(
        x=timestamps, y=volumes, name="Volume", marker_color=bar_colors,
        showlegend=False
    ), row=3, col=1)

    fig.update_layout(
        height=650,
        margin=dict(l=10, r=10, t=40, b=10),
        xaxis_rangeslider_visible=False,
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
    )

    return fig
