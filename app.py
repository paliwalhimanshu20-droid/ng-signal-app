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

UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTI2ZTQzMWY5NGYyMDA2YTM2ZDA0MjYiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgwOTMzNjgxLCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODA5NTYwMDB9.Vval62uG6JVI-lR8prMg1PS5qAPn7XEcnPau_Z3Sp9I"

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

def generate_signal():

    current_price = 290.00
    ema20 = 292.00
    ema50 = 288.00
    atr = 3.00

    if ema20 > ema50:
        signal_type = "BUY"
        trend = "Bullish"
        sl = current_price - (1.5 * atr)

    elif ema20 < ema50:
        signal_type = "SELL"
        trend = "Bearish"
        sl = current_price + (1.5 * atr)

    else:
        signal_type = "NO TRADE"
        trend = "Sideways"
        sl = current_price

    risk = abs(current_price - sl)

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

    url = "https://api.upstox.com/v2/market-quote/ltp"

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

    st.write("Testing market-data access...")
    st.write("Token OK")
if st.button("Test Market Quote"):

    response = test_market_quote()

    try:
        st.json(response.json())
    except:
        st.write(response.text)
