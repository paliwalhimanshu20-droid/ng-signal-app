import streamlit as st
import requests

# ---------------- TELEGRAM CONFIG ----------------

BOT_TOKEN = "8281917891:AAHKMHhOh9ZbIoqC57xfwWRHIhdJsCg0Rmk"
CHAT_ID = "8351444537"
UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTI1YTI4ODFjNGM3YTE0YmVjMmQzZDEiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzgwODUxMzM2LCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3ODA4Njk2MDB9.Isi-qnqat8SMSXjlTlFYmvRstBpxI8TQf7_o8uXEj2s"
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
        "Authorization": f"Bearer {eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIxNzA3OTkiLCJqdGkiOiI2YTI1YTI4ODFjNGM3YTE0YmVjMmQzZDEiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSw}",
        "Accept": "application/json"
    }

    response = requests.get(url, headers=headers)

    return response

# ---------------- APP UI ----------------

st.title("📊 Natural Gas Signal App (MCX)")
st.write("NG Signal Pro - Testing Phase")

# ---------------- RUN ANALYSIS ----------------

if st.button("Run Analysis"):

    sig = generate_signal()

    message = f"""
NATURAL GAS SIGNAL (MCX)

Type: {sig['type']}
Entry: {sig['entry']}
Stop Loss: {sig['sl']}
Target 1: {sig['t1']}
Target 2: {sig['t2']}
"""

    st.subheader("Generated Signal")
    st.write(sig)

    result = send_telegram(message)

    st.subheader("Telegram Response")
    st.write(result)

    if result.get("ok"):
        st.success("Signal sent successfully!")
    else:
        st.error("Telegram message failed!")

# ---------------- FIND CONTRACTS ----------------

if st.button("Find NATGAS Contracts"):

    res = load_instruments()

    st.write("Status Code:", res.status_code)

    if res.status_code != 200:
        st.error("API Error")
        st.write(res.text)

    else:
        st.success("Instrument API Connected")
        st.write(res.json())
