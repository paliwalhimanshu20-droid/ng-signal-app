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

    weekly = weekly.sort_values("timestamp", ascending=False)

    return weekly

def weekly_summary(report):

    if report.empty:
        return None

    total = len(report)
    buy = (report["signal"] == "BUY").sum()
    sell = (report["signal"] == "SELL").sum()

    target = (report["status"] == "TARGET_HIT").sum()
    stop = (report["status"] == "SL_HIT").sum()
    open_trades = (report["status"] == "OPEN").sum()

    avg_pnl = pd.to_numeric(
        report["pnl_pct"],
        errors="coerce"
    ).mean()

    return {
        "Total Signals": total,
        "BUY": buy,
        "SELL": sell,
        "Target Hit": target,
        "Stop Loss": stop,
        "Open": open_trades,
        "Avg P&L": 0 if pd.isna(avg_pnl) else round(avg_pnl, 2)
    }
