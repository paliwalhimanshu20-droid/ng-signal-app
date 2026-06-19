import streamlit as st
import requests
from datetime import datetime
from zoneinfo import ZoneInfo
import csv
import os
import pandas as pd
import time

# ---------------- CONFIG ----------------

BOT_TOKEN = "YOUR_BOT_TOKEN"
CHAT_ID = "YOUR_CHAT_ID"
UPSTOX_ACCESS_TOKEN = "YOUR_ACCESS_TOKEN"

IST = ZoneInfo("Asia/Kolkata")

# ---------------- SESSION STATE ----------------

if "last_signal" not in st.session_state:
    st.session_state.last_signal = None

# ---------------- INSTRUMENT REGISTRY ----------------

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

# ---------------- DATA STORAGE ----------------

def save_signal(signal):
    file_name = "signal_history_v2.csv"
    file_exists = os.path.isfile(file_name)

    with open(file_name, "a", newline="") as file:
        writer = csv.writer(file)

        if not file_exists:
            writer.writerow([
                "Signal_ID", "Time", "Signal", "Price",
                "EMA20", "EMA50", "ATR", "SL", "T1", "T2",
                "Status", "Result", "Duration_Hours"
            ])

        writer.writerow([
            int(time.time()),
            datetime.now(IST).strftime("%d-%m-%Y %H:%M"),
            signal["type"],
            signal["price"],
            signal["ema20"],
            signal["ema50"],
            signal["atr"],
            signal["sl"],
            signal["t1"],
            signal["t2"],
            "OPEN",
            "PENDING",
            0
        ])

# ---------------- MARKET DATA ----------------

def get_historical_candles():
    url = f"https://api.upstox.com/v2/historical-candle/MCX_FO|504266/30minute/2026-06-09"
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

def atr(candles, period=14):
    trs = []
    for i in range(1, len(candles)):
        high, low, prev = candles[i][1], candles[i][2], candles[i-1][4]
        tr = max(high-low, abs(high-prev), abs(low-prev))
        trs.append(tr)
    return sum(trs[:period]) / period

# ---------------- LOGIC ENGINE ----------------

def get_trend(ema20, ema50):
    if ema20 > ema50:
        return "Bullish"
    elif ema20 < ema50:
        return "Bearish"
    return "Sideways"

def get_signal_type(ema20, ema50):
    if ema20 > ema50:
        return "BUY"
    elif ema20 < ema50:
        return "SELL"
    return "NO TRADE"

def get_score(price, ema20, ema50):
    score = 0
    if ema20 > ema50:
        score += 3
    else:
        score += 3

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2

    if abs(ema20 - ema50) > 1:
        score += 2

    if abs(price - ema20) > 0.5:
        score += 3

    return min(score, 10)

def get_confidence(score):
    if score >= 8:
        return "High"
    elif score >= 5:
        return "Medium"
    return "Low"

def get_reversal(price, ema20, ema50, trend):
    if trend == "Bearish" and price > ema20:
        return "Bullish Reversal Watch"
    if trend == "Bullish" and price < ema20:
        return "Bearish Reversal Watch"
    return "None"

def trade_levels(signal_type, price, atr_val):
    risk = atr_val * 1.5
    if signal_type == "BUY":
        sl = price - risk
        t1 = price + risk * 2
        t2 = price + risk * 3
    elif signal_type == "SELL":
        sl = price + risk
        t1 = price - risk * 2
        t2 = price - risk * 3
    else:
        sl = t1 = t2 = price
    return sl, t1, t2

# ---------------- UI ----------------

st.title("📊 NG Signal Pro — Phase 2")

if st.button("🚀 Run Analysis"):

    candles = get_historical_candles()
    closes = [c[4] for c in reversed(candles)]

    price = get_live_price()

    ema20 = ema(closes[:20], 20)
    ema50 = ema(closes[:50], 50)

    trend = get_trend(ema20, ema50)
    signal_type = get_signal_type(ema20, ema50)

    score = get_score(price, ema20, ema50)
    confidence = get_confidence(score)
    reversal = get_reversal(price, ema20, ema50, trend)

    if reversal != "None":
        score -= 2

    score = max(1, min(score, 10))

    atr_val = atr(candles)
    sl, t1, t2 = trade_levels(signal_type, price, atr_val)

    signal = {
        "type": signal_type,
        "trend": trend,
        "price": round(price, 2),
        "ema20": round(ema20, 2),
        "ema50": round(ema50, 2),
        "atr": round(atr_val, 2),
        "sl": round(sl, 2),
        "t1": round(t1, 2),
        "t2": round(t2, 2)
    }

    save_signal(signal)

    message = f"""
📊 NG SIGNAL PRO

Trend: {trend}
Signal: {signal_type}
Price: {price}

Confidence: {confidence}
Score: {score}/10

SL: {sl}
T1: {t1}
T2: {t2}

Reversal: {reversal}

Time: {datetime.now(IST).strftime("%d-%m-%Y %H:%M")}
"""

    if signal_type != st.session_state.last_signal:
        send_telegram(message)
        st.session_state.last_signal = signal_type
        st.success("Signal sent to Telegram")
    else:
        st.info("Duplicate signal blocked")

    st.write(signal)

# ---------------- PHASE 2 FEATURE ----------------

if st.button("📈 Signal Stats (Basic)"):

    if os.path.exists("signal_history_v2.csv"):
        df = pd.read_csv("signal_history_v2.csv")

        st.metric("Total Signals", len(df))
        st.metric("BUY Signals", len(df[df["Signal"] == "BUY"]))
        st.metric("SELL Signals", len(df[df["Signal"] == "SELL"]))

        st.dataframe(df.tail(10))
    else:
        st.warning("No signal history found")
