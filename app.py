import streamlit as st
import requests
import pandas as pd
import json
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTM3OGU1MjM1MTRiNTQ0YjU5OGNjNTciLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgyMDI1ODEwLCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODIwNzkyMDB9.SYWBnB-Cc_tS5uwH3zvfzuKarP5S3vWOshnoxPOaxQw"
IST = ZoneInfo("Asia/Kolkata")

# ================= SAFE REQUEST =================

def safe_get(url, headers=None):
    try:
        r = requests.get(url, headers=headers, timeout=10)

        st.write("Request URL:", url)
        st.write("Status Code:", r.status_code)

        try:
            st.write("Response:", r.json())
        except:
            st.write("Response Text:", r.text)

        if r.status_code != 200:
            return None

        return r.json()

    except Exception as e:
        st.write("Request Error:", e)
        return None

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():

    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json"

    try:

        response = requests.get(url, timeout=20)

        data = response.json()

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
def test_mcx_master():

    url = "https://api.upstox.com/v2/instruments/scrip/details.MCX.json.gz"

    try:

        df = pd.read_json(url)

        st.write("Rows:", len(df))

        st.write(df.head())

    except Exception as e:

        st.error(f"MCX Master Error: {e}")
# ================= INSTRUMENT REGISTRY (OFFLINE) =================

@st.cache_data(ttl=86400)
def load_instrument_file():
    """
    Load Upstox instrument master file (manual fallback version).
    You will place a downloaded file in project folder.
    """

    file_path = "instruments.csv"

    try:
        df = pd.read_csv(file_path)
        return df

    except Exception as e:
        st.error("Instrument file not found. Please add instruments.csv")
        return pd.DataFrame()
def get_instrument_key(symbol):
    df = load_instrument_file()

    if df.empty:
        return None

    match = df[df["trading_symbol"] == symbol]

    if match.empty:
        return None

    return match.iloc[0]["instrument_key"]
# ================= WATCHLIST =================

def get_watchlist():

    return {
        "ITC": "NSE_EQ|INE154A01025"
    }

# ================= MARKET DATA =================

def get_price(key):

    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json",
        "Api-Version": "2.0"
    }

    data = safe_get(url, headers)

    st.write("LTP Raw Response:", data)

    if not data:
      return None

    try:
        st.write("LTP Data Keys:", data.get("data", {}).keys())

        k = list(data["data"].keys())[0]

        st.write("Selected Key:", k)
        st.write("Selected Data:", data["data"][k])

        return data["data"][k]["last_price"]

    except Exception as e:
        st.write("LTP Error:", e)
        return None


def get_candles(key):

    today = datetime.now(IST).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{today}"

    data = safe_get(
        url,
        {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}
    )

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
    reasons.append("Base Score")

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

def levels(price, atr_val, signal, trend):

    risk = atr_val * 1.5

    if trend == "Bullish":

        sl = round(price - risk, 2)
        t1 = round(price + risk * 2, 2)
        t2 = round(price + risk * 3, 2)

    else:

        sl = round(price + risk, 2)
        t1 = round(price - risk * 2, 2)
        t2 = round(price - risk * 3, 2)

    return sl, t1, t2
# ================= SCANNER =================

def run_scanner():

    watchlist = get_watchlist()
    st.write(watchlist)
    results = []

    for name, key in watchlist.items():

        if not key:
            continue

        instrument_key = key

        candles = get_candles(instrument_key)
        st.write("Latest Candle:", candles[0])

        st.write("Testing Key:", instrument_key)
        st.write("Candles Found:", candles is not None)

        if not candles:
         st.warning(f"{name}: No candle data")
         continue
        try:
            closes = [c[4] for c in reversed(candles)]
            st.write("Candles Count:", len(closes))
            st.write("Last 5 Closes:", closes[-5:])

            if len(closes) < 50:
                continue

            price = get_price(instrument_key)

            st.write("Price:", price)

            if not price:
               st.warning(f"{name}: Price not found")
               continue

            ema20 = ema(closes, 20)
            ema50 = ema(closes, 50)
            atr_val = atr(candles)
            st.write("EMA20:", ema20)
            st.write("EMA50:", ema50)
            st.write("Latest Close:", closes[-1])

            signal, score, prob, trend, regime, expected_move, reasons = signal_engine(
                price,
                ema20,
                ema50,
                atr_val
            )

            st.write(
            f"Signal={signal}, Score={score}, Trend={trend}, Price={price}"
            )

            st.write("Reasons:", reasons)
            st.write("EMA20:", ema20)
            st.write("EMA50:", ema50)
            st.write("ATR:", atr_val)

            if signal in ["BUY", "SELL", "WATCH"]:

                sl, t1, t2 = levels(price, atr_val, signal, trend)

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
