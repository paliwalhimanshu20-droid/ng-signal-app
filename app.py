import streamlit as st
import requests
import pandas as pd
import json
import os
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"
IST = ZoneInfo("Asia/Kolkata")

CACHE_FILE = "instrument_cache.json"

# ================= SAFE REQUEST =================

def safe_get(url, headers=None, timeout=10):
    try:
        r = requests.get(url, headers=headers, timeout=timeout)

        if r.status_code != 200:
            return None

        try:
            return r.json()
        except:
            return None

    except:
        return None

# ================= CACHE SYSTEM =================

def save_cache(data):
    try:
        with open(CACHE_FILE, "w") as f:
            json.dump(data, f)
    except:
        pass


def load_cache():
    try:
        if os.path.exists(CACHE_FILE):
            with open(CACHE_FILE, "r") as f:
                return json.load(f)
    except:
        pass
    return []

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json"

    data = safe_get(url)

    if isinstance(data, list):
        save_cache(data)
        return data

    cached = load_cache()

    return cached if cached else []

# ================= SYMBOL MAP =================

def build_symbol_map():
    data = load_instrument_master()

    if not data:
        return {}

    symbol_map = {}

    for item in data:
        try:
            symbol = item.get("trading_symbol")
            key = item.get("instrument_key")

            if symbol and key:
                symbol_map[symbol.upper()] = key

        except:
            continue

    return symbol_map

# ================= INSTRUMENTS =================

def get_instruments():
    symbol_map = build_symbol_map()

    return {
        "Kotak Bank": symbol_map.get("KOTAKBANK"),
        "Bank of Baroda": symbol_map.get("BANKBARODA"),
        "Jio Financial": symbol_map.get("JIOFIN"),
        "Federal Bank": symbol_map.get("FEDERALBNK"),
        "IRFC": symbol_map.get("IRFC"),

        "Tata Power": symbol_map.get("TATAPOWER"),
        "NTPC": symbol_map.get("NTPC"),
        "Power Grid": symbol_map.get("POWERGRID"),
        "Adani Power": symbol_map.get("ADANIPOWER"),
        "Suzlon": symbol_map.get("SUZLON"),

        "Coal India": symbol_map.get("COALINDIA"),
        "ONGC": symbol_map.get("ONGC"),
        "IOC": symbol_map.get("IOC"),
        "GAIL": symbol_map.get("GAIL"),
        "BPCL": symbol_map.get("BPCL"),

        "ITC": symbol_map.get("ITC"),
        "BEL": symbol_map.get("BEL"),
        "Tata Motors": symbol_map.get("TATAMOTORS"),
        "Tata Steel": symbol_map.get("TATASTEEL"),
        "Wipro": symbol_map.get("WIPRO"),
    }

# ================= SAFE API =================

def get_live_price(key):
    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"
    data = safe_get(url, headers={"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    if not data:
        return None

    try:
        k = list(data["data"].keys())[0]
        return data["data"][k]["last_price"]
    except:
        return None


def get_candles(key):
    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/2026-06-09"
    data = safe_get(url, headers={"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

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

# ================= SIGNAL ENGINE v2 =================

def analyze(price, ema20, ema50, atr_val):

    score = 0
    reasons = []

    # TREND
    if ema20 > ema50:
        trend = "Bullish"
        score += 3
    else:
        trend = "Bearish"
        score += 3

    # PRICE CONFIRMATION
    if ema20 > ema50 and price > ema20:
        score += 2
        reasons.append("Price above EMA20")
    elif ema20 < ema50 and price < ema20:
        score += 2
        reasons.append("Price below EMA20")

    # ATR ZONE
    if atr_val and abs(price - ema20) < atr_val * 1.5:
        score += 2
        reasons.append("Healthy zone")

    # BREAKOUT CHECK
    if ema20 > ema50 and price > ema50 * 1.002:
        score += 2
        reasons.append("Bullish breakout")

    if ema20 < ema50 and price < ema50 * 0.998:
        score += 2
        reasons.append("Bearish breakdown")

    score = min(score, 10)

    probability = int((score / 10) * 100)

    if score >= 8:
        signal = "BUY" if trend == "Bullish" else "SELL"
    elif score >= 6:
        signal = "WATCH"
    else:
        signal = "NO TRADE"

    structure = f"{trend} | Score {score}/10 | Prob {probability}%"

    return signal, score, probability, structure, reasons

# ================= SCANNER =================

def scan_portfolio():

    instruments = get_instruments()
    results = []

    for name, key in instruments.items():

        if not key:
            continue

        candles = get_candles(key)
        if not candles:
            continue

        try:
            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = get_live_price(key)
            if not price:
                continue

            ema20 = ema(closes[:20], 20)
            ema50 = ema(closes[:50], 50)
            atr_val = atr(candles)

            signal, score, prob, structure, reasons = analyze(price, ema20, ema50, atr_val)

            # 🔥 IMPORTANT FIX: DO NOT FILTER WATCH OUT
            if signal in ["BUY", "SELL", "WATCH"]:

                results.append({
                    "Instrument": name,
                    "Signal": signal,
                    "Score": score,
                    "Prob%": prob,
                    "Price": round(price, 2),
                    "Structure": structure
                })

        except:
            continue

    df = pd.DataFrame(results)

    if not df.empty:
        df = df.sort_values(by="Score", ascending=False)

    return df

# ================= UI =================

st.title("📊 Signal Engine v2 (Probability System)")

if st.button("🚀 Run Scan"):

    df = scan_portfolio()

    if df.empty:
        st.warning("No signals found (market may be sideways or data missing)")
    else:
        st.dataframe(df)

        st.write("🔥 Top Signal:")
        st.json(df.iloc[0].to_dict())
