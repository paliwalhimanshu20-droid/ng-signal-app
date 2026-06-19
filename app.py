import streamlit as st
import requests
import pandas as pd
import csv
import os
from datetime import datetime
from zoneinfo import ZoneInfo

# ---------------- CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"
UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"

IST = ZoneInfo("Asia/Kolkata")

# ---------------- INSTRUMENT REGISTRY ----------------

INSTRUMENTS = {
    "Natural Gas": "MCX_FO|504266",

    "Kotak Mahindra Bank": "NSE_EQ|KOTAKBANK",
    "Bank of Baroda": "NSE_EQ|BANKBARODA",
    "Jio Financial Services": "NSE_EQ|JIOFIN",
    "Federal Bank": "NSE_EQ|FEDERALBNK",
    "IRFC": "NSE_EQ|IRFC",

    "Tata Power": "NSE_EQ|TATAPOWER",
    "NTPC": "NSE_EQ|NTPC",
    "Power Grid": "NSE_EQ|POWERGRID",
    "Adani Power": "NSE_EQ|ADANIPOWER",
    "Suzlon Energy": "NSE_EQ|SUZLON",

    "Coal India": "NSE_EQ|COALINDIA",
    "ONGC": "NSE_EQ|ONGC",
    "Indian Oil": "NSE_EQ|IOC",
    "GAIL": "NSE_EQ|GAIL",
    "BPCL": "NSE_EQ|BPCL",

    "ITC": "NSE_EQ|ITC",
    "Patanjali Foods": "NSE_EQ|PATANJALI",
    "ITC Hotels": "NSE_EQ|ITCHOTELS",
    "Vishal Mega Mart": "NSE_EQ|VISHALMEGA",
    "Eternal (Zomato)": "NSE_EQ|ETERNAL",

    "Bharat Electronics": "NSE_EQ|BEL",
    "Tata Motors": "NSE_EQ|TATAMOTORS",
    "Tata Steel": "NSE_EQ|TATASTEEL",
    "Wipro": "NSE_EQ|WIPRO",
    "Motherson": "NSE_EQ|MOTHERSON"
}

# ---------------- DATA ENGINE ----------------

def get_live_price(key):
    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    data = requests.get(url, headers=headers).json()
    k = list(data["data"].keys())[0]
    return data["data"][k]["last_price"]

def get_candles(key):
    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/2026-06-09"
    headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    return requests.get(url, headers=headers).json()["data"]["candles"]

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

# ---------------- MARKET STRUCTURE ----------------

def structure(price, ema20, ema50):
    if price > ema20 > ema50:
        return "STRONG_BULL"
    if price < ema20 < ema50:
        return "STRONG_BEAR"
    if ema20 > ema50:
        return "WEAK_BULL"
    if ema20 < ema50:
        return "WEAK_BEAR"
    return "SIDEWAYS"

# ---------------- PROBABILITY ENGINE ----------------

def probability(score, structure):
    base = 50 + score * 3

    if structure.startswith("STRONG"):
        base += 20
    if structure.startswith("WEAK"):
        base -= 10

    return max(0, min(100, base))

# ---------------- CORE ANALYSIS ----------------

def analyze(price, ema20, ema50, atr_val):

    struc = structure(price, ema20, ema50)

    score = 0

    if ema20 > ema50:
        score += 3
    else:
        score += 3

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2

    if abs(price - ema20) < atr_val * 2:
        score += 2

    score = min(10, score)

    prob = probability(score, struc)

    signal = "NO TRADE"

    if score >= 8 and prob > 70:
        signal = "BUY" if ema20 > ema50 else "SELL"
    elif score >= 5 and prob > 55:
        signal = "WATCH"

    confidence = "LOW"
    if prob > 75:
        confidence = "HIGH"
    elif prob > 60:
        confidence = "MEDIUM"

    return signal, struc, score, prob, confidence

# ---------------- TRADE LEVELS ----------------

def levels(signal, price, atr_val):
    r = atr_val * 1.5
    if signal == "BUY":
        return price - r, price + r*2, price + r*3
    if signal == "SELL":
        return price + r, price - r*2, price - r*3
    return price, price, price

# ---------------- PORTFOLIO SCANNER ----------------

def scan_portfolio():

    results = []

    for name, key in INSTRUMENTS.items():

        try:
            candles = get_candles(key)
            closes = [c[4] for c in reversed(candles)]

            price = get_live_price(key)

            ema20 = ema(closes[:20], 20)
            ema50 = ema(closes[:50], 50)
            atr_val = atr(candles)

            signal, struc, score, prob, conf = analyze(
                price, ema20, ema50, atr_val
            )

            if signal != "NO TRADE":

                sl, t1, t2 = levels(signal, price, atr_val)

                results.append({
                    "Instrument": name,
                    "Signal": signal,
                    "Structure": struc,
                    "Score": score,
                    "Probability": prob,
                    "Confidence": conf,
                    "Price": round(price, 2),
                    "SL": round(sl, 2),
                    "T1": round(t1, 2),
                    "T2": round(t2, 2)
                })

        except:
            continue

    df = pd.DataFrame(results)

    if not df.empty:
        df = df.sort_values(by="Probability", ascending=False)

    return df

# ---------------- UI ----------------

st.title("📊 NG SIGNAL PRO — PORTFOLIO SCANNER v10")

instrument = st.selectbox("Select Instrument", list(INSTRUMENTS.keys()))
key = INSTRUMENTS[instrument]

if st.button("🚀 Run Single Analysis"):

    candles = get_candles(key)
    closes = [c[4] for c in reversed(candles)]

    price = get_live_price(key)

    ema20 = ema(closes[:20], 20)
    ema50 = ema(closes[:50], 50)
    atr_val = atr(candles)

    signal, struc, score, prob, conf = analyze(price, ema20, ema50, atr_val)

    sl, t1, t2 = levels(signal, price, atr_val)

    st.success(f"""
Signal: {signal}
Structure: {struc}
Score: {score}/10
Probability: {prob}%
Confidence: {conf}

SL: {sl}
T1: {t1}
T2: {t2}
""")

# ---------------- PORTFOLIO MODE ----------------

if st.button("🚀 Scan Full Portfolio"):

    df = scan_portfolio()

    if df.empty:
        st.warning("No active setups found")
    else:
        st.success("Top Opportunities Ranked")

        st.dataframe(df)

        st.write("🔥 BEST TRADE RIGHT NOW")
        st.json(df.iloc[0].to_dict())
