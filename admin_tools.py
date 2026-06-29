import pandas as pd
from datetime import datetime, timedelta

from signal_log import load_signal_log


def generate_weekly_report():
    """
    Returns a DataFrame containing only the last 7 days
    of signals from signal_log.csv.
    """

    df = load_signal_log()

    if df.empty:
        return pd.DataFrame()

    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    cutoff = datetime.now() - timedelta(days=7)

    weekly = df[df["timestamp"] >= cutoff].copy()

    return weekly.sort_values("timestamp", ascending=False)
