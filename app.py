import streamlit as st
import requests

# ---------------- TELEGRAM CONFIG ----------------
BOT_TOKEN = "8281917891:AAEM1gWz7jaJMpIZj5Wlu9D75RQv2JcPtsQ"
CHAT_ID = "8351444537"

def send_telegram(message):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    payload = {
        "chat_id": CHAT_ID,
        "text": message
    }
    response = requests.post(url, data=payload)

st.write("Status Code:", response.status_code)
st.write("Response:", response.json())

# ---------------- APP UI ----------------
st.title("📊 Natural Gas Signal App (MCX)")
st.write("Click the button to generate signal")

# ---------------- SIMPLE STRATEGY (A+ FILTER BASE) ----------------
def generate_signal():

    # Example logic (we will replace with real Upstox data later)
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
🔥 NATURAL GAS SIGNAL (MCX)

Type: {sig['type']}
Entry: {sig['entry']}
Stop Loss: {sig['sl']}
Target 1: {sig['t1']}
Target 2: {sig['t2']}

Timeframe: DAILY
"""

    send_telegram(message)

    st.success("Signal sent to Telegram!")
    st.write(sig)
