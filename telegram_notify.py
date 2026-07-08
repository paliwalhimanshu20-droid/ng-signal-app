"""
telegram_notify.py

Shared Telegram sender — extracted verbatim from check_signals.py so both
check_signals.py (outcome alerts) and generate_signals.py (new-signal
alerts, NGSP Phase 0) call one implementation instead of duplicating it.
No logic changed: same env vars, same endpoint, same timeout, same
failure handling. Zero Streamlit dependency, safe to import from either
a Streamlit session or a bare GitHub Actions runner.

Secrets required (set as GitHub Actions secrets, NOT hardcoded):
  TELEGRAM_BOT_TOKEN    - your bot's token from @BotFather
  TELEGRAM_CHAT_ID      - the chat id to send alerts to
"""

import os
import requests

TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")


def send_telegram_message(text):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        print("Telegram not configured — skipping notification.")
        return

    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    try:
        r = requests.post(
            url,
            data={"chat_id": TELEGRAM_CHAT_ID, "text": text, "parse_mode": "Markdown"},
            timeout=10
        )
        if r.status_code != 200:
            print(f"Telegram send failed: {r.status_code} {r.text[:200]}")
    except Exception as e:
        print(f"Telegram send error: {e}")
