import streamlit as st
import requests
from datetime import datetime
from zoneinfo import ZoneInfo
import csv
import os
import time

# ---------------- CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"
UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"

IST = ZoneInfo("Asia/Kolkata")

# ---------------- SESSION ----------------

if "last_signal" not in st.session_state:
    st.session_state.last_signal = None

# ---------------- DATA ----------------

def get_historical_candles():
    url = "https://api.upstox.com/v2/historical-candle/MCX_FO|504266/30minute/2026-06-09"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    return requests.get(url, headers=headers).json()["data"]["candles"]

def get_live_price():
    url = "https://api.upstox.com/v2/market-quote/ltp?instrument_key=MCX_FO|504266"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    data = requests.get(url, headers=headers).json()
    key = list(data["data"].keys())[0]
    return data["data"][key]["last_price"]

# ---------------- INDICATORS ----------------

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

# ---------------- STRUCTURE (v3 CORE FIX) ----------------

def get_structure(price, ema20, ema50):

    if price > ema20 > ema50:
        return "Strong Bullish"

    if price < ema20 < ema50:
        return "Strong Bearish"

    if ema20 > ema50:
        return "Weak Bullish"

    if ema20 < ema50:
        return "Weak Bearish"

    return "Sideways"

# ---------------- v4 PROBABILITY ENGINE ----------------

def probability_engine(score, structure, price, ema20, ema50, atr_val):

    base_prob = 50

    # score contribution
    base_prob += score * 3

    # structure strength
    if structure in ["Strong Bullish", "Strong Bearish"]:
        base_prob += 20
    elif structure in ["Weak Bullish", "Weak Bearish"]:
        base_prob -= 10

    # price alignment bonus
    if price > ema20 > ema50 or price < ema20 < ema50:
        base_prob += 15
    else:
        base_prob -= 15

    # volatility penalty
    if atr_val < abs(ema20 - ema50) * 0.5:
        base_prob += 5

    return max(0, min(100, base_prob))

# ---------------- v3 + v4 ENGINE ----------------

def analyze_market(price, ema20, ema50, atr_val):

    structure = get_structure(price, ema20, ema50)

    score = 0

    # EMA alignment
    if ema20 > ema50:
        score += 3
    else:
        score += 3

    # Price confirmation
    if ema20 > ema50 and price > ema20:
        score += 2
    elif ema20 < ema50 and price < ema20:
        score += 2

    # Strength
    if abs(ema20 - ema50) > atr_val * 0.5:
        score += 2

    # volatility quality
    score += 1

    # avoid overextension
    if abs(price - ema20) < atr_val * 2:
        score += 2

    # structure penalty
    if structure in ["Weak Bullish", "Weak Bearish"]:
        score -= 2

    score = max(0, min(score, 10))

    # ---------------- v3 DECISION ----------------

    signal = "NO TRADE"

    if score >= 8:
        if ema20 > ema50 and price > ema20:
            signal = "BUY"
        elif ema20 < ema50 and price < ema20:
            signal = "SELL"

    elif score >= 5:
        if structure in ["Strong Bullish", "Strong Bearish"]:
            signal = "WATCH"
        else:
            signal = "NO TRADE"

    # ---------------- CONFIDENCE ----------------

    confidence = "Low"
    if score >= 8:
        confidence = "High"
    elif score >= 5:
        confidence = "Medium"

    # ---------------- v4 PROBABILITY ----------------

    prob = probability_engine(score, structure, price, ema20, ema50, atr_val)

    return signal, structure, score, confidence, prob

# ---------------- TRADE LEVELS ----------------

def trade_levels(signal, price, atr_val):
    risk = atr_val * 1.5

    if signal == "BUY":
        return price - risk, price + risk*2, price + risk*3
    if signal == "SELL":
        return price + risk, price - risk*2, price - risk*3

    return price, price, price

# ---------------- SAVE ----------------

def save_signal(data):

    file = "signal_history_v4.csv"
    exists = os.path.isfile(file)

    with open(file, "a", newline="") as f:
        w = csv.writer(f)

        if not exists:
            w.writerow([
                "Time","Signal","Structure","Price",
                "Score","Confidence","Probability",
                "SL","T1","T2"
            ])

        w.writerow([
            datetime.now(IST).strftime("%d-%m-%Y %H:%M"),
            data["signal"],
            data["structure"],
            data["price"],
            data["score"],
            data["confidence"],
            data["probability"],
            data["sl"],
            data["t1"],
            data["t2"]
        ])

# ---------------- UI ----------------

st.title("📊 NG Signal Pro v3 + v4 Engine")

if st.button("🚀 Run Analysis"):

    candles = get_historical_candles()
    closes = [c[4] for c in reversed(candles)]

    price = get_live_price()
    atr_val = atr(candles)

    ema20 = ema(closes[:20], 20)
    ema50 = ema(closes[:50], 50)

    signal, structure, score, confidence, prob = analyze_market(
        price, ema20, ema50, atr_val
    )

    if signal == "NO TRADE":
        st.warning(f"No Trade | Structure: {structure} | Prob: {prob}%")
        st.stop()

    sl, t1, t2 = trade_levels(signal, price, atr_val)

    data = {
        "signal": signal,
        "structure": structure,
        "price": round(price, 2),
        "score": score,
        "confidence": confidence,
        "probability": prob,
        "sl": round(sl, 2),
        "t1": round(t1, 2),
        "t2": round(t2, 2)
    }

    save_signal(data)

    st.success(f"""
📊 SIGNAL GENERATED

Signal: {signal}
Structure: {structure}
Score: {score}/10
Confidence: {confidence}
Probability: {prob}%

SL: {sl}
T1: {t1}
T2: {t2}
""")

    st.write(data)
