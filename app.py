import streamlit as st
import requests
from datetime import datetime
import csv
import os
import pandas as pd

# ---------------- TELEGRAM CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"

# ---------------- UPSTOX CONFIG ----------------

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTI4NDMxODA0OTE1YzBkOTcxODczZWYiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgxMDIzNTEyLCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODEwNDI0MDB9.IS7eJJdFu1RlTeLqP39r3_2JXXu9ki9KGftvTpLaSIo"

# ---------------- TELEGRAM FUNCTION ----------------

def send_telegram(message):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"

    payload = {
        "chat_id": CHAT_ID,
        "text": message
    }

    response = requests.post(url, data=payload)
    return response.json()
def save_signal(signal):

    file_name = "signal_history.csv"

    file_exists = os.path.isfile(file_name)

    with open(file_name, mode="a", newline="") as file:

        writer = csv.writer(file)

        if not file_exists:
            writer.writerow([
                "Time",
                "Signal",
                "Price",
                "EMA20",
                "EMA50",
                "ATR",
                "SL",
                "Target1",
                "Target2"
            ])

        writer.writerow([
            datetime.now().strftime("%d-%m-%Y %H:%M"),
            signal["type"],
            signal["price"],
            signal["ema20"],
            signal["ema50"],
            signal["atr"],
            signal["sl"],
            signal["t1"],
            signal["t2"]
        ])
# ---------------- SIGNAL LOGIC ----------------
def test_historical_data():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/historical-candle/MCX_FO|504266/30minute/2026-06-09"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)

    return response
def get_historical_candles():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/historical-candle/MCX_FO|504266/30minute/2026-06-09"

    response = requests.get(url, headers=headers)

    data = response.json()

    candles = data["data"]["candles"]

    return candles
def calculate_ema(prices, period):

    multiplier = 2 / (period + 1)

    ema = prices[0]

    for price in prices[1:]:
        ema = (price - ema) * multiplier + ema

    return round(ema, 2)
def calculate_atr(candles, period=14):

    trs = []

    for i in range(1, len(candles)):

        high = candles[i][1]
        low = candles[i][2]
        prev_close = candles[i-1][4]

        tr = max(
            high - low,
            abs(high - prev_close),
            abs(low - prev_close)
        )

        trs.append(tr)

    atr = sum(trs[:period]) / period

    return round(atr, 2)
def get_live_price():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/market-quote/ltp?instrument_key=MCX_FO|504266"

    response = requests.get(url, headers=headers)

    data = response.json()

    return data["data"]["MCX_FO:NATGASMINI26JUNFUT"]["last_price"]
def calculate_trade_levels(signal_type, current_price, atr):

    risk = atr * 1.5

    if signal_type == "BUY":

        sl = current_price - risk
        t1 = current_price + (risk * 2)
        t2 = current_price + (risk * 3)

    else:

        sl = current_price + risk
        t1 = current_price - (risk * 2)
        t2 = current_price - (risk * 3)

    return round(sl, 2), round(t1, 2), round(t2, 2)
def get_trend(ema20, ema50):

    if ema20 > ema50:
        return "Bullish"

    elif ema20 < ema50:
        return "Bearish"

    else:
        return "Sideways"
def calculate_score(price, ema20, ema50):

    score = 0

    if ema20 > ema50:
        score += 4

    if price > ema20:
        score += 2

    if abs(ema20 - ema50) > 1:
        score += 2

    if price > ema50:
        score += 2

    return score
def get_confidence(score):

    if score >= 8:
        return "High"

    elif score >= 5:
        return "Medium"

    else:
        return "Low"
def get_signal_type(ema20, ema50):

    if ema20 > ema50:
        return "BUY"

    elif ema20 < ema50:
        return "SELL"

    else:
        return "NO TRADE"
def get_recommendation(score):

    if score >= 8:
        return "Strong Trade"

    elif score >= 5:
        return "Watch Setup"

    else:
        return "Avoid Trade"
def generate_signal():

    current_price = 302.10
    ema20 = 292.00
    ema50 = 288.00
    atr = 3.00

    if ema20 > ema50:
        signal_type = "BUY"
        trend = "Bullish"
        sl = current_price - (1.5 * atr)
        risk = current_price - sl

    elif ema20 < ema50:
        signal_type = "SELL"
        trend = "Bearish"
        sl = current_price + (1.5 * atr)
        risk = sl - current_price

    else:
        signal_type = "NO TRADE"
        trend = "Sideways"
        sl = current_price
        risk = 0

    signal = {
        "type": signal_type,
        "trend": trend,
        "price": round(current_price, 2),
        "ema20": round(ema20, 2),
        "ema50": round(ema50, 2),
        "atr": round(atr, 2),
        "sl": round(sl, 2),
        "t1": round(current_price + (2 * risk), 2),
        "t2": round(current_price + (3 * risk), 2)
    }

    return signal

# ---------------- UPSTOX TEST ----------------

def load_instruments():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/user/profile"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)

    return response
def test_market_quote():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/market-quote/ltp?instrument_key=MCX_FO|504266"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)

    return response
def search_natgas():

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/instruments/search?query=NATGASMINI"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)

    return response

# ---------------- APP UI ----------------

st.title("📊 Natural Gas Signal App (MCX)")
st.write("NG Signal Pro - Testing Phase")

if st.button("Run Analysis"):

    signal = generate_signal()
    save_signal(signal)

    msg = f"""
🔥 NG {signal['type']} SIGNAL
Time: {datetime.now().strftime("%d-%m-%Y %H:%M")}

Timeframe: 1 Hour
Trend: {signal['trend']}

Price: {signal['price']}

EMA20: {signal['ema20']}
EMA50: {signal['ema50']}

ATR: {signal['atr']}

Stop Loss: {signal['sl']}
Target 1: {signal['t1']}
Target 2: {signal['t2']}
"""

    st.success(msg)

    telegram_response = send_telegram(msg)

    st.write("Telegram Response:")
    st.json(telegram_response)

if st.button("Find NATGAS Contracts"):

    response = load_instruments()

    st.write("Status Code:", response.status_code)

    try:
        st.json(response.json())
    except:
        st.write(response.text)


# ---------------- TEST UPSTOX TOKEN ----------------

if st.button("Test Upstox Token"):

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/user/profile"

    response = requests.get(url, headers=headers)

    st.write("Status:", response.status_code)
    st.write(response.text)
    # ---------------- VIEW SIGNAL HISTORY ----------------

if st.button("View Signal History"):

    try:
        df = pd.read_csv("signal_history.csv")
        st.dataframe(df)

    except Exception as e:
        st.error(f"Unable to load signal history: {e}")
        # ---------------- SIGNAL STATS ----------------

if st.button("Signal Statistics"):

    try:

        df = pd.read_csv("signal_history.csv")

        total_signals = len(df)

        buy_signals = len(df[df["Signal"] == "BUY"])

        sell_signals = len(df[df["Signal"] == "SELL"])

        st.metric("Total Signals", total_signals)

        st.metric("BUY Signals", buy_signals)

        st.metric("SELL Signals", sell_signals)

        st.write("Latest Signal")

        st.dataframe(df.tail(1))

    except Exception as e:

        st.error(f"Error: {e}")
        # ---------------- MARKET DATA TEST ----------------

# ---------------- MARKET DATA TEST ----------------

if st.button("Market Data Test"):

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/user/profile"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)

    try:
        st.json(response.json())
    except:
        st.write(response.text)
        # ---------------- FIND MCX INSTRUMENTS ----------------

if st.button("Find MCX Instruments"):

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    url = "https://api.upstox.com/v2/market-quote/quotes"

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)
if st.button("Test Market Quote"):

    response = test_market_quote()

    try:
        st.json(response.json())
    except:
        st.write(response.text)
        # ---------------- TEST HISTORICAL DATA ----------------

if st.button("Test Historical Data"):

    response = test_historical_data()

    try:
        st.json(response.json())
    except:
        st.write(response.text)
        # ---------------- SEARCH NATGAS ----------------

if st.button("Search NATGASMINI"):

    response = search_natgas()

    try:
        st.json(response.json())
    except:
        st.write(response.text)
if st.button("Test Close Prices"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in candles[:10]]

    st.write("Close Prices:")
    st.write(closes)
# ---------------- TEST EMA ----------------

if st.button("Test EMA"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in candles]

    ema20 = calculate_ema(closes[:20], 20)

    st.write("EMA20:", ema20)
    # ---------------- TEST EMA50 ----------------

if st.button("Test EMA50"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    ema50 = calculate_ema(closes[:50], 50)

    st.write("EMA50:", ema50)
# ---------------- TEST TREND ----------------

if st.button("Test Trend"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    ema20 = calculate_ema(closes, 20)

    ema50 = calculate_ema(closes, 50)

    trend = get_trend(ema20, ema50)

    st.write("EMA20:", ema20)
    st.write("EMA50:", ema50)
    st.write("Trend:", trend)
    # ---------------- TEST SCORE ----------------

if st.button("Test Score"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    current_price = closes[-1]

    ema20 = calculate_ema(closes, 20)

    ema50 = calculate_ema(closes, 50)

    score = calculate_score(current_price, ema20, ema50)

    st.write("Price:", current_price)
    st.write("EMA20:", ema20)
    st.write("EMA50:", ema50)
    st.write("Score:", score, "/ 10")
    # ---------------- TEST CONFIDENCE ----------------

if st.button("Test Confidence"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    current_price = closes[-1]

    ema20 = calculate_ema(closes, 20)

    ema50 = calculate_ema(closes, 50)

    score = calculate_score(current_price, ema20, ema50)

    confidence = get_confidence(score)

    st.write("Price:", current_price)
    st.write("EMA20:", ema20)
    st.write("EMA50:", ema50)
    st.write("Score:", score, "/ 10")
    st.write("Confidence:", confidence)
# ---------------- TEST SIGNAL ----------------

if st.button("Test Signal"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    current_price = closes[-1]

    ema20 = calculate_ema(closes, 20)

    ema50 = calculate_ema(closes, 50)

    trend = get_trend(ema20, ema50)

    score = calculate_score(current_price, ema20, ema50)

    confidence = get_confidence(score)

    signal_type = get_signal_type(ema20, ema50)

    atr = calculate_atr(candles)

    sl, t1, t2 = calculate_trade_levels(
    signal_type,
    current_price,
    atr
    )
    
    recommendation = get_recommendation(score)

    st.write("Signal:", signal_type)
    st.write("Trend:", trend)
    st.write("Price:", current_price)
    st.write("Score:", score, "/10")
    st.write("Confidence:", confidence)
    st.write("Recommendation:", recommendation)
    st.write("ATR:", atr)
    st.write("Stop Loss:", sl)
    st.write("Target 1:", t1)
    st.write("Target 2:", t2)
    # ---------------- TEST ATR ----------------

if st.button("Test ATR"):

    candles = get_historical_candles()

    atr = calculate_atr(candles)

    st.write("ATR:", atr)
    # ---------------- RUN ANALYSIS ----------------

if st.button("🚀 Run Analysis"):

    candles = get_historical_candles()

    closes = [candle[4] for candle in reversed(candles)]

    current_price = closes[-1]

    ema20 = calculate_ema(closes, 20)

    ema50 = calculate_ema(closes, 50)

    trend = get_trend(ema20, ema50)

    score = calculate_score(current_price, ema20, ema50)

    confidence = get_confidence(score)

    signal_type = get_signal_type(ema20, ema50)

    recommendation = get_recommendation(score)

    atr = calculate_atr(candles)

    sl, t1, t2 = calculate_trade_levels(
        signal_type,
        current_price,
        atr
    )

    st.success(f"{signal_type} SIGNAL")

    st.write("Trend:", trend)
    st.write("Price:", current_price)

    st.write("EMA20:", round(ema20, 2))
    st.write("EMA50:", round(ema50, 2))

    st.write("Score:", score, "/10")
    st.write("Confidence:", confidence)
    st.write("Recommendation:", recommendation)

    st.write("ATR:", atr)

    st.write("Stop Loss:", sl)
    st.write("Target 1:", t1)
    st.write("Target 2:", t2)
