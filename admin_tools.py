import pandas as pd
from datetime import datetime, timedelta

from signal_log import load_signal_log


def generate_weekly_report():
    df = load_signal_log()

    if df.empty:
        return pd.DataFrame()

    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    cutoff = datetime.now() - timedelta(days=7)

    weekly = df[df["timestamp"] >= cutoff].copy()

    return weekly.sort_values("timestamp", ascending=False)


def weekly_summary(df):
    if df.empty:
        return None

    total = len(df)
    buy = len(df[df["signal"] == "BUY"])
    sell = len(df[df["signal"] == "SELL"])

    target = len(df[df["status"] == "TARGET_HIT"])
    sl = len(df[df["status"] == "SL_HIT"])

    df["pnl_pct"] = pd.to_numeric(df["pnl_pct"], errors="coerce")

    avg_pnl = round(df["pnl_pct"].mean(), 2) if not df["pnl_pct"].isna().all() else 0

    return {
        "Total Signals": total,
        "BUY": buy,
        "SELL": sell,
        "Target Hit": target,
        "Stop Loss": sl,
        "Avg P&L": avg_pnl
    }


def weekly_report_excel(df):
    from io import BytesIO

    output = BytesIO()

    with pd.ExcelWriter(output, engine="openpyxl") as writer:
        df.to_excel(writer, index=False, sheet_name="Weekly Report")

    output.seek(0)
    return output
