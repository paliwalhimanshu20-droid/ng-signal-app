import streamlit as st
import requests

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
        "t1": round(entry + (2 * risk), 2),
        "t2": round(entry + (3 * risk), 2)
    }

    return signal

# ---------------- UPSTOX TEST ----------------

def load_instruments():

    url = "https://api.upstox.com/v2/instruments"

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json"
    }

    response = requests.get(url, headers=headers)

    st.write("Status Code:", response.status_code)
    st.write("Response:", response.text)

    return response

# ---------------- APP UI ----------------

st.title("📊 Natural Gas Signal App (MCX)")
st.write("NG Signal Pro - Testing Phase")

if st.button("Run Analysis"):

    signal = generate_signal()

    msg = f"""
🔥 NG BUY SIGNAL

Entry: {signal['entry']}
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
