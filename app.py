import streamlit as st
import requests
import pandas as pd
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM0YjU0N2I5NjIwYjc0YmFmYTQzZjciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxODM5MTc1LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODE5MDY0MDB9.qaiOTY6_DPANwtKu2MfsBwVeJoUi3pzHVA8tzYIjTDE"

IST = ZoneInfo("Asia/Kolkata")

# ================= STEP 1: INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json"

    try:
        r = requests.get(url, timeout=10)

        if r.status_code != 200:
            st.error(f"Instrument API failed: {r.status_code}")
            return []

        if "application/json" not in r.headers.get("Content-Type", ""):
            st.error("Invalid response (not JSON)")
            return []

        return r.json()

    except Exception as e:
        st.error(f"Instrument load error: {e}")
        return []

# ================= STEP 2: SYMBOL → KEY MAPPING =================

def build_symbol_map():
    data = load_instrument_master()

    # ✅ FIX: handle API failure safely
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

# ================= STEP 3: FINAL INSTRUMENT REGISTRY =================

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

# ================= SAFE API FUNCTIONS =================

def get_live_price(key):
    try:
        url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"
        headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}

        r = requests.get(url, headers=headers).json()

        k = list(r["data"].keys())[0]
        return r["data"][k]["last_price"]

    except:
        return None


def get_candles(key):
    try:
        url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/2026-06-09"
        headers = {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}

        r = requests.get(url, headers=headers).json()

        return r.get("data", {}).get("candles", None)

    except:
        return None

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

# ================= SIGNAL ENGINE =================

def analyze(price, ema20, ema50, atr_val):

    score = 0

    if ema20 > ema50:
        score += 3
    else:
        score += 3

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2

    if abs(price - ema20) < atr_val * 2:
        score += 2

    score = min(score, 10)

    signal = "NO TRADE"

    if score >= 8:
        signal = "BUY" if ema20 > ema50 else "SELL"
    elif score >= 5:
        signal = "WATCH"

    return signal, score

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

        closes = [c[4] for c in reversed(candles)]

        price = get_live_price(key)
        if not price:
            continue

        ema20 = ema(closes[:20], 20)
        ema50 = ema(closes[:50], 50)
        atr_val = atr(candles)

        signal, score = analyze(price, ema20, ema50, atr_val)

        if signal != "NO TRADE":

            results.append({
                "Instrument": name,
                "Signal": signal,
                "Score": score,
                "Price": round(price, 2)
            })

    df = pd.DataFrame(results)

    if not df.empty:
        df = df.sort_values(by="Score", ascending=False)

    return df

# ================= UI =================

st.title("📊 Stable Portfolio Scanner v1")

instruments = get_instruments()
selected = st.selectbox("Select Stock", list(instruments.keys()))

if st.button("🚀 Single Analysis"):

    key = instruments[selected]

    candles = get_candles(key)

    if candles:
        closes = [c[4] for c in reversed(candles)]

        price = get_live_price(key)

        ema20 = ema(closes[:20], 20)
        ema50 = ema(closes[:50], 50)
        atr_val = atr(candles)

        signal, score = analyze(price, ema20, ema50, atr_val)

        st.success(f"""
Signal: {signal}
Score: {score}/10
Price: {price}
""")

if st.button("🚀 Scan Portfolio"):

    df = scan_portfolio()

    if df.empty:
        st.warning("No signals found")
    else:
        st.dataframe(df)
        st.write("🔥 Best Trade:")
        st.json(df.iloc[0].to_dict())
