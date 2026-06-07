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


# ---------------- STREAMLIT UI ----------------

st.title("📊 Natural Gas Signal App (MCX)")
st.write("Click buttons below to run system")

# ---------------- RUN ANALYSIS ----------------

if st.button("Run Analysis"):

    sig = generate_signal()

    message = f"""
🔥 NATURAL GAS SIGNAL (MCX)

Type: {sig['type']}
Entry: {sig['entry']}
Stop Loss: {sig['sl']}
Target 1: {sig['t1']}
Target 2: {sig['t2']}
"""

    st.subheader("Generated Signal")
    st.write(sig)

    st.subheader("Message Preview")
    st.text(message)

    result = send_telegram(message)

    st.subheader("Telegram Response")
    st.write(result)

    if result.get("ok"):
        st.success("Signal sent to Telegram successfully!")
    else:
        st.error("Telegram message failed!")


# ---------------- NATGAS CONTRACT TEST (PLACEHOLDER) ----------------

def load_instruments():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/complete.csv"
    df = None
    try:
        import pandas as pd
        df = pd.read_csv(url)
    except Exception as e:
        return str(e)

    return df


if st.button("Find NATGAS Contracts"):

    df = load_instruments()

    if isinstance(df, str):
        st.error(df)
    else:
        natgas = df[df['tradingsymbol'].str.contains("GAS", na=False)]
        st.write(natgas.head(20))
