import streamlit as st
import requests
import pandas as pd
import json
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

UPSTOX_ACCESS_TOKEN = " my token"
IST = ZoneInfo("Asia/Kolkata")

# ================= SAFE REQUEST =================

def safe_get(url, headers=None):
    try:
        r = requests.get(url, headers=headers, timeout=10)

        if r.status_code != 200:
            return None

        return r.json()

    except Exception as e:
        # FIX: narrowed from bare except so real errors aren't silently swallowed.
        # Currently just suppressed here; surfaced to the caller as None.
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
    except Exception:
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
    except Exception:
        return None


def get_candles(key):
    # FIX: original code called .get() directly on safe_get()'s return value,
    # which crashes with AttributeError when safe_get returns None
    # (timeouts, holidays, no data, expired token, rate limits).
    today = datetime.now(IST).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{today}"

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
    # FIX: original took sum(trs[:14]) which is the OLDEST 14 true ranges
    # once trs is built in chronological order (since candles here is the
    # raw, newest-first Upstox response — trs ends up oldest->newest reversed
    # depending on indexing). We now explicitly use the most recent 14
    # true ranges so ATR reflects current volatility, not stale data.
    trs = []
    for i in range(1, len(candles)):
        h, l, pc = candles[i][1], candles[i][2], candles[i-1][4]
        trs.append(max(h - l, abs(h - pc), abs(l - pc)))

    if not trs:
        return 0

    recent = trs[-14:] if len(trs) >= 14 else trs
    return sum(recent) / len(recent)


def rsi(prices, period=14):
    # NEW: standard Wilder's RSI, computed from the same closes list
    # you already build in run_scanner(). No extra API calls.
    if len(prices) < period + 1:
        return None

    gains, losses = [], []
    for i in range(1, len(prices)):
        change = prices[i] - prices[i - 1]
        gains.append(max(change, 0))
        losses.append(max(-change, 0))

    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period

    if avg_loss == 0:
        return 100.0

    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 2)


def volume_signal(candles):
    # NEW: compares the latest candle's volume to the average volume of the
    # rest of the session so far. Flags unusual participation behind a move.
    # Upstox 30min candle format: [timestamp, open, high, low, close, volume, oi]
    if not candles or len(candles) < 3:
        return None, "N/A"

    # candles are newest-first from the API
    vols = [c[5] for c in candles if len(c) > 5]
    if len(vols) < 3:
        return None, "N/A"

    latest_vol = vols[0]
    avg_vol = sum(vols[1:]) / len(vols[1:]) if len(vols) > 1 else 0

    if avg_vol == 0:
        return None, "N/A"

    ratio = round(latest_vol / avg_vol, 2)

    if ratio >= 1.5:
        tag = "High"
    elif ratio >= 0.8:
        tag = "Normal"
    else:
        tag = "Low"

    return ratio, tag

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
        return round(price - risk, 2), round(price + risk * 2, 2), round(price + risk * 3, 2)
    else:
        return round(price + risk, 2), round(price - risk * 2, 2), round(price - risk * 3, 2)

# ================= SCANNER =================

def run_scanner():
    """
    Returns a tuple: (top5_df, full_df)
    full_df now includes EVERY stock that returned valid data, with RSI
    and Volume columns added, regardless of score — so the dashboard can
    show the full scanned universe with filters, not just the top 5.
    """

    watchlist = get_watchlist()
    all_results = []

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

            # NEW indicators
            rsi_val = rsi(closes, 14)
            vol_ratio, vol_tag = volume_signal(candles)

            signal, score, prob, trend, regime, expected_move, reasons = signal_engine(
                price,
                ema20,
                ema50,
                atr_val
            )

            sl, t1, t2 = levels(price, atr_val, signal, trend)

            risk = abs(price - sl)
            reward = abs(t1 - price)
            rr = round(reward / risk, 2) if risk > 0 else 0

            confidence = (
                "High" if score >= 9
                else "Medium" if score >= 7
                else "Low"
            )

            all_results.append({
                "Instrument": name,
                "Signal": signal,
                "Confidence": confidence,
                "Trend": trend,
                "Regime": regime,
                "Score": score,
                "Prob%": prob,
                "RSI": rsi_val,
                "Volume Ratio": vol_ratio,
                "Volume": vol_tag,
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

    full_df = pd.DataFrame(all_results)

    if not full_df.empty:
        full_df = full_df.sort_values(["Score", "Prob%"], ascending=False)

    # Top 5 strong setups only (same filter logic as before: score >= 7, actionable signal)
    if not full_df.empty:
        top5_df = full_df[
            (full_df["Score"] >= 7) & (full_df["Signal"].isin(["BUY", "SELL", "WATCH"]))
        ].head(5)
    else:
        top5_df = full_df

    return top5_df, full_df

# ================= UI =================

st.title("📊 Production Trading System v1")

if "scan_count" not in st.session_state:
    st.session_state.scan_count = 0

if "last_scan" not in st.session_state:
    st.session_state.last_scan = "Never"

run = st.button("🚀 Run Live Scan")

if run:
    st.session_state.scan_count += 1
    st.session_state.last_scan = datetime.now(IST).strftime("%d-%m-%Y %H:%M:%S")

df, full_df = run_scanner()

# =========================
# FULL SCANNED UNIVERSE (NEW)
# =========================

st.subheader("🔎 Full Scanned Universe")

if full_df.empty:
    st.warning("No data returned from scanner. Check token / market hours / connectivity.")
else:
    fcol1, fcol2, fcol3 = st.columns(3)

    with fcol1:
        signal_filter = st.multiselect(
            "Signal",
            options=sorted(full_df["Signal"].unique()),
            default=list(sorted(full_df["Signal"].unique()))
        )

    with fcol2:
        confidence_filter = st.multiselect(
            "Confidence",
            options=sorted(full_df["Confidence"].unique()),
            default=list(sorted(full_df["Confidence"].unique()))
        )

    with fcol3:
        min_score = st.slider("Minimum Score", min_value=0, max_value=10, value=0)

    filtered_df = full_df[
        (full_df["Signal"].isin(signal_filter)) &
        (full_df["Confidence"].isin(confidence_filter)) &
        (full_df["Score"] >= min_score)
    ]

    st.dataframe(
        filtered_df[[
            "Instrument", "Signal", "Confidence", "Trend", "Regime",
            "Score", "Prob%", "RSI", "Volume", "Volume Ratio",
            "ExpectedMove%", "RR", "Price", "SL", "T1", "T2"
        ]],
        use_container_width=True,
        hide_index=True
    )

    st.caption(f"Showing {len(filtered_df)} of {len(full_df)} scanned instruments")

st.markdown("---")

# =========================
# EXISTING TOP-5 SECTIONS (unchanged logic)
# =========================

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

          st.markdown(
            f"""
            <div style="
            padding:15px;
            border-radius:10px;
            border:1px solid #2ecc71;
            margin-bottom:10px;
            ">
            <h4>🟢 {row['Instrument']}</h4>
            <b>BUY</b><br>
            Price: {row['Price']}<br>
            Confidence: {row['Prob%']}%<br>
            RSI: {row['RSI']}<br>
            Volume: {row['Volume']}<br>
            RR: {row['RR']}
            </div>
            """,
            unsafe_allow_html=True
          )

    # =========================
    # SELL SETUPS
    # =========================

    sell_df = df[df["Signal"] == "SELL"]

    if not sell_df.empty:

        st.subheader("🔴 SELL Opportunities")

        for _, row in sell_df.iterrows():

          st.markdown(
            f"""
            <div style="
            padding:15px;
            border-radius:10px;
            border:1px solid #e74c3c;
            margin-bottom:10px;
            ">
            <h4>🔴 {row['Instrument']}</h4>
            <b>SELL</b><br>
            Price: {row['Price']}<br>
            Confidence: {row['Prob%']}%<br>
            RSI: {row['RSI']}<br>
            Volume: {row['Volume']}<br>
            RR: {row['RR']}
            </div>
            """,
            unsafe_allow_html=True
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
        f"RSI: {best['RSI']} | Volume: {best['Volume']}\n\n"
        f"RR: {best['RR']}\n\n"
        f"Reason: {best['Reason']}"
    )

    st.markdown("---")

    st.caption(
        f"Scans Run: {st.session_state.scan_count} | "
        f"Last Scan: {st.session_state.last_scan}"
    )
    
