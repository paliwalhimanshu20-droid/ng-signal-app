import streamlit as st
import requests
import pandas as pd
import json
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"
IST = ZoneInfo("Asia/Kolkata")

# ================= SAFE REQUEST =================

def safe_get(url, headers=None):
    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code != 200:
            return None
        return r.json()
    except:
        return None

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json"
    return safe_get(url)

def build_symbol_map():
    data = load_instrument_master()
    if not isinstance(data, list):
        return {}

    mapping = {}

    for i in data:
        try:
            sym = i.get("trading_symbol")
            key = i.get("instrument_key")
            if sym and key:
                mapping[sym.upper()] = key
        except:
            continue

    return mapping

# ================= WATCHLIST =================

def get_watchlist():
    symbols = build_symbol_map()

    return {
        "Tata Motors": symbols.get("TATAMOTORS"),
        "ITC": symbols.get("ITC"),
        "NTPC": symbols.get("NTPC"),
        "ONGC": symbols.get("ONGC"),
        "BEL": symbols.get("BEL"),
        "Power Grid": symbols.get("POWERGRID"),
        "Coal India": symbols.get("COALINDIA"),
        "Suzlon": symbols.get("SUZLON"),
        "Wipro": symbols.get("WIPRO"),
        "IOC": symbols.get("IOC"),
    }

# ================= MARKET DATA =================

def get_price(key):
    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"
    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    try:
        k = list(data["data"].keys())[0]
        return data["data"][k]["last_price"]
    except:
        return None


def get_candles(key):
    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/2026-06-09"
    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})
    if not data:
        return None
    return data.get("data", {}).get("candles", None)

# ================= INDICATORS =================

def ema(prices, period):
    m = 2 / (period + 1)
    e = prices[0]
    for p in prices[1:]:
        e = (p - e) * m + e
    return e


def atr(candles):
    trs = []
    for i in range(1, len(candles)):
        h, l, pc = candles[i][1], candles[i][2], candles[i-1][4]
        trs.append(max(h-l, abs(h-pc), abs(l-pc)))
    return sum(trs[:14]) / 14

# ================= PRODUCTION ENGINE =================

def signal_engine(price, ema20, ema50, atr_val):

    score = 0
    reasons = []

    trend = "Bullish" if ema20 > ema50 else "Bearish"
    score += 3

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2
        reasons.append("Trend confirmation")

    if atr_val and abs(price - ema20) < atr_val * 1.5:
        score += 2
        reasons.append("Valid volatility zone")

    if ema20 > ema50 and price > ema50:
        score += 2
        reasons.append("Momentum breakout bullish")

    if ema20 < ema50 and price < ema50:
        score += 2
        reasons.append("Momentum breakdown bearish")

    score = min(score, 10)

    probability = int((score / 10) * 100)

    if score >= 8:
        signal = "BUY" if trend == "Bullish" else "SELL"
    elif score >= 6:
        signal = "WATCH"
    else:
        signal = "NO TRADE"

    return signal, score, probability, trend, reasons

# ================= SL / TP =================

def levels(price, atr_val, signal):

    risk = atr_val * 1.5

    if signal == "BUY":
        return round(price-risk,2), round(price+risk*2,2), round(price+risk*3,2)
    else:
        return round(price+risk,2), round(price-risk*2,2), round(price-risk*3,2)

# ================= SCANNER =================

def run_scanner():

    watchlist = get_watchlist()
    results = []

    for name, key in watchlist.items():

        if not key:
            continue

        candles = get_candles(key)
        if not candles:
            continue

        try:
            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = get_price(key)
            if not price:
                continue

            ema20 = ema(closes[:20], 20)
            ema50 = ema(closes[:50], 50)
            atr_val = atr(candles)

            signal, score, prob, trend, regime, expected_move, reasons = signal_engine(
    price,
    ema20,
    ema50,
    atr_val
)

            if signal in ["BUY", "SELL", "WATCH"]:

                sl, t1, t2 = levels(price, atr_val, signal)

                results.append({
                    "Instrument": name,
                    "Signal": signal,
                    "Trend": trend,
                    "Score": score,
                    "Prob%": prob,
                    "Price": round(price, 2),
                    "SL": sl,
                    "T1": t1,
                    "T2": t2,
                    "Reason": " | ".join(reasons)
                })

        except:
            continue

    df = pd.DataFrame(results)

    if not df.empty:
        df = df.sort_values(["Score", "Prob%"], ascending=False)

    return df.head(5)

# ================= UI =================

st.title("📊 Production Trading System v1")

col1, col2, col3 = st.columns(3)

with col1:
    run = st.button("🚀 Run Live Scan")

with col2:
    auto = st.toggle("Auto Refresh")

with col3:
    st.write("Status: LIVE")

if run:

    df = run_scanner()

    if df.empty:
        st.warning("No strong setups found")
    else:
        st.success("🔥 Top 5 Opportunities")

        st.dataframe(df)

        best = df.iloc[0]

        st.subheader("🥇 Best Trade Setup")

        st.json({
            "Instrument": best["Instrument"],
            "Signal": best["Signal"],
            "Trend": best["Trend"],
            "Entry": best["Price"],
            "StopLoss": best["SL"],
            "Target1": best["T1"],
            "Target2": best["T2"],
            "Reason": best["Reason"]
        })
