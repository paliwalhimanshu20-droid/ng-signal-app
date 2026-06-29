import pandas as pd
from io import BytesIO
from datetime import datetime, timedelta

from signal_log import load_signal_log
from config import IST


def generate_weekly_report():
    """
    Returns all signals from the last 7 days.
    """

    df = load_signal_log()

    if df.empty:
        return pd.DataFrame()

    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    week_start = datetime.now(IST).replace(tzinfo=None) - timedelta(days=7)

    report = df[df["timestamp"] >= week_start].copy()

    return report.sort_values("timestamp", ascending=False)

def weekly_report_excel(report_df):
    """
    Converts the weekly report DataFrame into an Excel file in memory.
    """

    output = BytesIO()

    with pd.ExcelWriter(output, engine="openpyxl") as writer:
        report_df.to_excel(writer, index=False, sheet_name="Weekly Report")

    output.seek(0)
    return output
