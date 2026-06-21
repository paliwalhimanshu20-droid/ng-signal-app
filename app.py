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
        return requests.get(url, timeout=20).json()
    except Exception as e:
        st.error(f"Instrument Master Error: {e}")
        return []

def load_instrument_file():
    try:
        return pd.read_csv("instruments.csv")
    except:
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
        "ITC": "NSE_EQ|INE154A01025",
        "RELIANCE": "NSE_EQ|INE002A01018",
        "SBIN": "NSE_EQ|INE062A01020",
        "HDFCBANK": "NSE_EQ|INE040A01034",
        "ICICIBANK": "NSE_EQ|INE090A01021",
        "TCS": "NSE_EQ|INE467B01029",
        "INFY": "NSE_EQ|INE009A01021",
        "WIPRO": "NSE_EQ|INE075A01022",
        "ONGC": "NSE_EQ|INE213A01029",
        "NTPC": "NSE_EQ|INE733E01010",
        "POWERGRID": "NSE_EQ|INE752E01010",
        "TATAMOTORS": "NSE_EQ|INE155A01022"
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

    if not data:
        return None

    try:
        k = list(data["data"].keys())[0]
        return data["data"][k]["last_price"]
    except:
        return None


def get_candles(key):
    today = datetime.now(IST).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{today}"

    return safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}) \
        .get("data", {}).get("candles", None)

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

# ================= REGIME =================

def detect_regime(ema20, ema50, price):
    gap = abs(ema20 - ema50)

    if gap > price * 0.01:
        return "TRENDING"
    elif gap > price * 0.005:
        return "BREAKOUT"
    else:
        return "RANGING"

# ================= SIGNAL ENGINE =================

def signal_engine(price, ema20, ema50, atr_val):

    score = 4
    reasons = ["Base Score"]

    trend = "Bullish" if ema20 > ema50 else "Bearish"
    regime = detect_regime(ema20, ema50, price)

    expected_move = round((atr_val / price) * 100, 2)

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2
        reasons.append("Trend confirmation")

    if atr_val and abs(price - ema20) < atr_val * 1.5:
        score += 2
        reasons.append("Valid volatility zone")

    if ema20 > ema50 and price > ema50:
        score += 2
        reasons.append("Momentum bullish")

    if ema20 < ema50 and price < ema50:
        score += 2
        reasons.append("Momentum bearish")

    score = min(score, 10)
    probability = int((score / 10) * 100)

    if score >= 8:
        signal = "BUY" if trend == "Bullish" else "SELL"
    elif score >= 6:
        signal = "WATCH"
    else:
        signal = "NO TRADE"

    return signal, score, probability, trend, regime, expected_move, reasons

# ================= LEVELS =================

def levels(price, atr_val, signal, trend):
    risk = atr_val * 1.5

    if trend == "Bullish":
        return round(price - risk,2), round(price + risk*2,2), round(price + risk*3,2)
    else:
        return round(price + risk,2), round(price - risk*2,2), round(price - risk*3,2)

# ================= SCANNER (FIXED) =================

def run_scanner():

    watchlist = get_watchlist()
    results = []

    for name, key in watchlist.items():

        if not key:
            continue

        instrument_key = key

        try:
            candles = get_candles(instrument_key)

            if not candles:
                continue

            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = get_price(instrument_key)

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

            if score >= 7 and signal in ["BUY", "SELL", "WATCH"]:

                sl, t1, t2 = levels(
                    price,
                    atr_val,
                    signal,
                    trend
                )

                risk = abs(price - sl)
                reward = abs(t1 - price)

                rr = round(reward / risk, 2) if risk > 0 else 0

                results.append({
                    "Instrument": name,
                    "Signal": signal,
                    "Confidence":
                      "High" if score >= 9
                       else "Medium" if score >= 7
                       else "Low",
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

run = st.button("🚀 Run Live Scan")

if run:

st.session_state.scan_count += 1

st.session_state.last_scan = datetime.now(
    IST
).strftime("%d-%m-%Y %H:%M:%S")

df = run_scanner()

if df.empty:

    st.warning("No strong setups found")

else:

    # =========================
    # BUY SETUPS
    # =========================

    buy_df = df[df["Signal"] == "BUY"]

    if not buy_df.empty:

        st.subheader("🟢 BUY Opportunities")

        for _, row in buy_df.iterrows():

            st.success(
                f"{row['Instrument']} | "
                f"Price: {row['Price']} | "
                f"Confidence: {row['Prob%']}% | "
                f"RR: {row['RR']}"
            )

    # =========================
    # SELL SETUPS
    # =========================

    sell_df = df[df["Signal"] == "SELL"]

    if not sell_df.empty:

        st.subheader("🔴 SELL Opportunities")

        for _, row in sell_df.iterrows():

            st.error(
                f"{row['Instrument']} | "
                f"Price: {row['Price']} | "
                f"Confidence: {row['Prob%']}% | "
                f"RR: {row['RR']}"
            )

    # =========================
    # BEST SETUP
    # =========================

    best = df.iloc[0]

    st.markdown("---")
    st.subheader("🥇 Best Trade Setup")

    c1, c2, c3 = st.columns(3)

    with c1:
        st.metric("Instrument", best["Instrument"])
        st.metric("Signal", best["Signal"])

    with c2:
        st.metric("Entry", best["Price"])
        st.metric("SL", best["SL"])

    with c3:
        st.metric("T1", best["T1"])
        st.metric("T2", best["T2"])

    st.info(
        f"Trend: {best['Trend']}\n\n"
        f"Confidence: {best['Prob%']}%\n\n"
        f"RR: {best['RR']}\n\n"
        f"Reason: {best['Reason']}"
    )

    st.markdown("---")

    st.caption(
        f"Scans Run: {st.session_state.scan_count} | "
        f"Last Scan: {st.session_state.last_scan}"
    )
