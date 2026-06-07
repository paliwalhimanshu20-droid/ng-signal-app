import streamlit as st
import requests

import pandas as pd

def load_instruments():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/complete.csv"

    df = pd.read_csv(url)
    return df
# ---------------- TELEGRAM CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"

# ---------------- TELEGRAM FUNCTION ----------------

def send_telegram(message):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"

    payload = {
        "chat_id": CHAT_ID,
        "text": message
    }

    response = requests.post(url, data=payload)

    return response.json()

# ---------------- APP UI ----------------

st.title("📊 Natural Gas Signal App (MCX)")
st.write("Click the button to generate signal")

# ---------------- SIGNAL LOGIC ----------------

def generate_signal():

    high = 290.0
    atr = 2.5

    entry = high
    sl = entry - (1.5 * atr)
    risk = entry - sl

    signal = {
        "type": "BUY",
        "entry": round(entry, 2),
        "sl": round(sl, 2),
        "t1": round(entry + 2 * risk, 2),
        "t2": round(entry + 3 * risk, 2)
    }

    return signal

# ---------------- BUTTON ACTION ----------------

if st.button("Run Analysis"):
    sig = generate_signal()

    message = f"""
SIGNAL GENERATED
Type: {sig['type']}
Entry: {sig['entry']}
SL: {sig['sl']}
T1: {sig['t1']}
T2: {sig['t2']}
"""

    st.write(message)


if st.button("Find NATGAS Contracts"):
    df = load_instruments()

    natgas = df[df['tradingsymbol'].str.contains("GAS", na=False)]

    st.write(natgas.head(20))
🔥 NATURAL GAS SIGNAL (MCX)

Type: {sig['type']}
Entry: {sig['entry']}
Stop Loss: {sig['sl']}
Target 1: {sig['t1']}
Target 2: {sig['t2']}

Timeframe: DAILY
"""

    result = send_telegram(message)

    st.subheader("Telegram Response")
    st.write(result)

    if result.get("ok"):
        st.success("Signal sent to Telegram successfully!")
    else:
        st.error("Telegram message failed!")
        st.write(result)

    st.subheader("Generated Signal")
    st.write(sig)
