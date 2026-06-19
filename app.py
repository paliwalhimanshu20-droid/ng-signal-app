import streamlit as st
import requests
from datetime import datetime
from zoneinfo import ZoneInfo
import csv
import os
import pandas as pd
import time

# ---------------- CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"
UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"

IST = ZoneInfo("Asia/Kolkata")

# ---------------- SESSION STATE ----------------

if "last_signal" not in st.session_state:
    st.session_state.last_signal = None

# ---------------- INSTRUMENT ----------------

INSTRUMENTS = {
    "Natural Gas": {
        "instrument_key": "MCX_FO|504266"
    }
}

# ---------------- TELEGRAM ----------------

def send_telegram(message):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    payload = {"chat_id": CHAT_ID, "text": message}
    return requests.post(url, data=payload).json()

# ---------------- SAVE SIGNAL ----------------

def save_signal(signal):
    file_name = "signal_history_v3.csv"
    file_exists = os.path.isfile(file_name)

    with open(file_name, "a", newline="") as file:
        writer = csv.writer(file)

        if not file_exists:
            writer.writerow([
                "Signal_ID", "Time", "Signal", "Trend",
                "Price", "EMA20", "EMA50", "ATR",
                "SL", "T1", "T2", "Score", "Confidence"
            ])

        writer.writerow([
            int(time.time()),
            datetime.now(IST).strftime("%d-%m-%Y %H:%M"),
            signal["signal"],
            signal["trend"],
            signal["price"],
            signal["ema20"],
            signal["ema50"],
            signal["atr"],
            signal["sl"],
            signal["t1"],
            signal["t2"],
            signal["score"],
            signal["confidence"]
        ])

# ---------------- MARKET DATA ----------------

def get_historical_candles():
    url = "https://api.upstox.com/v2/historical-candle/MCX_FO|504266/30minute/2026-06-09"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    data = requests.get(url, headers=headers).json()
    return data["data"]["candles"]

def get_live_price():
    url = "https://api.upstox.com/v2/market-quote/ltp?instrument_key=MCX_FO|504266"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    data = requests.get(url, headers=headers).json()
    key = list(data["data"].keys())[0]
    return data["data"][key]["last_price"]

# ---------------- INDICATORS ----------------

def ema(prices, period):
    multiplier = 2 / (period + 1)
    ema_val = prices[0]
    for p in prices[1:]:
        ema_val = (p - ema_val) * multiplier + ema_val
    return ema_val

def atr(candles):
    trs = []
    for i in range(1, len(candles)):
        high, low, prev = candles[i][1], candles[i][2], candles[i-1][4]
        tr = max(high - low, abs(high - prev), abs(low - prev))
        trs.append(tr)
    return sum(trs[:14]) / 14

# ---------------- V3 ENGINE ----------------

def analyze_market(price, ema20, ema50, atr_val):

    trend = "Bullish" if ema20 > ema50 else "Bearish"

    score = 0

    # EMA structure
    if ema20 > ema50:
        score += 3
    else:
        score += 3

    # Price confirmation
    if ema20 > ema50 and price > ema20:
        score += 2
    elif ema20 < ema50 and price < ema20:
        score += 2

    # Trend strength
    ema_gap = abs(ema20 - ema50)
    if ema_gap > atr_val * 0.5:
        score += 2

    # Volatility filter
    score += 1

    # Avoid chasing extended moves
    if abs(price - ema20) < atr_val * 2:
        score += 2

    score = min(score, 10)

    signal = "NO TRADE"

    if score >= 8:
        if ema20 > ema50 and price > ema20:
            signal = "BUY"
        elif ema20 < ema50 and price < ema20:
            signal = "SELL"

    elif score >= 5:
        signal = "WATCH"

    if score >= 8:
        confidence = "High"
    elif score >= 5:
        confidence = "Medium"
    else:
        confidence = "Low"

    return signal, trend, score, confidence

# ---------------- TRADE LEVELS ----------------

def trade_levels(signal, price, atr_val):
    risk = atr_val * 1.5

    if signal == "BUY":
        sl = price - risk
        t1 = price + risk * 2
        t2 = price + risk * 3
    elif signal == "SELL":
        sl = price + risk
        t1 = price - risk * 2
        t2 = price - risk * 3
    else:
        sl = t1 = t2 = price

    return sl, t1, t2

# ---------------- UI ----------------

st.title("📊 NG Signal Pro v3")

if st.button("🚀 Run Analysis"):

    candles = get_historical_candles()
    closes = [c[4] for c in reversed(candles)]

    price = get_live_price()
    atr_val = atr(candles)

    ema20 = ema(closes[:20], 20)
    ema50 = ema(closes[:50], 50)

    signal, trend, score, confidence = analyze_market(
        price, ema20, ema50, atr_val
    )

    if signal == "NO TRADE":
        st.warning("No valid trade setup (v3 filter blocked)")
        st.stop()

    sl, t1, t2 = trade_levels(signal, price, atr_val)

    signal_data = {
        "signal": signal,
        "trend": trend,
        "price": round(price, 2),
        "ema20": round(ema20, 2),
        "ema50": round(ema50, 2),
        "atr": round(atr_val, 2),
        "sl": round(sl, 2),
        "t1": round(t1, 2),
        "t2": round(t2, 2),
        "score": score,
        "confidence": confidence
    }

    save_signal(signal_data)

    message = f"""
📊 NG SIGNAL PRO v3

Signal: {signal}
Trend: {trend}
Price: {price}

Score: {score}/10
Confidence: {confidence}

SL: {sl}
T1: {t1}
T2: {t2}

Time: {datetime.now(IST).strftime("%d-%m-%Y %H:%M")}
"""

    if signal != st.session_state.last_signal:
        send_telegram(message)
        st.session_state.last_signal = signal
        st.success("Signal sent to Telegram")
    else:
        st.info("Duplicate signal blocked")

    st.write(signal_data)
