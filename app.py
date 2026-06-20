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

    try:

        response = requests.get(url, timeout=20)

        st.write("Instrument Master Status:", response.status_code)

        st.write("Content Type:",
                 response.headers.get("content-type"))

        data = response.json()

        st.write("Records Loaded:", len(data))

        return data

    except Exception as e:

        st.error(f"Instrument Master Error: {e}")

        return []

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

    return {
        "Tata Motors": "NSE_EQ|TATAMOTORS",
        "ITC": "NSE_EQ|ITC",
        "NTPC": "NSE_EQ|NTPC",
        "ONGC": "NSE_EQ|ONGC",
        "BEL": "NSE_EQ|BEL",
        "Power Grid": "NSE_EQ|POWERGRID",
        "Coal India": "NSE_EQ|COALINDIA",
        "Suzlon": "NSE_EQ|SUZLON",
        "Wipro": "NSE_EQ|WIPRO",
        "IOC": "NSE_EQ|IOC"
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

    st.write("Testing URL:", url)

    response = requests.get(
        url,
        headers={
            "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"
        }
    )

    st.write("Status Code:", response.status_code)

    try:
        st.write(response.json())
    except:
        st.write(response.text)

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
# ================= REGIME ENGINE =================

def detect_regime(ema20, ema50, price):

    gap = abs(ema20 - ema50)

    if gap > price * 0.01:
        return "TRENDING"

    elif gap > price * 0.005:
        return "BREAKOUT"

    else:
        return "RANGING"
# ================= PRODUCTION ENGINE =================

def signal_engine(price, ema20, ema50, atr_val):

    score = 0
    reasons = []

    trend = "Bullish" if ema20 > ema50 else "Bearish"

    regime = detect_regime(
        ema20,
        ema50,
        price
    )

    expected_move = round(
        (atr_val / price) * 100,
        2
    )

    score += 3

    if (ema20 > ema50 and price > ema20) or \
       (ema20 < ema50 and price < ema20):

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

    return (
        signal,
        score,
        probability,
        trend,
        regime,
        expected_move,
        reasons
    )

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
    st.write(watchlist)
    results = []

    for name, key in watchlist.items():

        if not key:
            continue

        candles = get_candles(key)

        st.write(name, "Candles Found:", candles is not None)

        if not candles:
          continue

        try:
            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = get_price(key)

            st.write(name, "Live Price:", price)

            if not price:
              continue

            ema20 = ema(closes, 20)
            ema50 = ema(closes, 50)
            atr_val = atr(candles)

            signal, score, prob, trend, regime, expected_move, reasons = signal_engine(
    price,
    ema20,
    ema50,
    atr_val
)

            if signal in ["BUY", "SELL", "WATCH"]:

                sl, t1, t2 = levels(price, atr_val, signal)

                risk = abs(price - sl)
                reward = abs(t1 - price)

                rr = round(reward / risk, 2) if risk > 0 else 0

                results.append({
                    "Instrument": name,
                    "Signal": signal,
                    "Trend": trend,
                    "Regime": regime,
                    "Score": score,
                    "Prob%": prob,
                    "ExpectedMove%": expected_move,
                    "RR": rr,
                    "Price": round(price, 2),
                    "SL": sl,
                    "T1": t1,
                    "T2": t2,
                    "Reason": " | ".join(reasons)
                })

        except Exception as e:

            st.error(f"{name} Error: {e}")

            continue

    df = pd.DataFrame(results)

    if not df.empty:
        df = df.sort_values(["Score", "Prob%"], ascending=False)

    return df.head(5)

# ================= UI =================

st.title("📊 Production Trading System v1")
if "scan_count" not in st.session_state:
    st.session_state.scan_count = 0

if "last_scan" not in st.session_state:
    st.session_state.last_scan = "Never"

col1, col2, col3 = st.columns(3)

with col1:
    run = st.button("🚀 Run Live Scan")

with col2:
    auto = st.toggle("Auto Refresh")

with col3:
    st.write("Status: LIVE")

if run:

    st.session_state.scan_count += 1

    st.session_state.last_scan = datetime.now(
        IST
    ).strftime("%d-%m-%Y %H:%M:%S")

    df = run_scanner()

    if df.empty:

        st.warning("No strong setups found")

    else:

        st.subheader("🧠 System Health")

        c1, c2 = st.columns(2)

        with c1:
            st.metric(
                "Total Scans",
                st.session_state.scan_count
            )

        with c2:
            st.metric(
                "Last Scan",
                st.session_state.last_scan
            )

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
