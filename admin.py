"""
Admin Center utilities for NG Signal Pro.

This module contains developer/admin tools such as:

- Weekly Report
- Monthly Report
- Strategy Lab
- Diagnostics
- Data Management
- Future Learning Engine

Only the Weekly Report is implemented initially.
"""

import pandas as pd
from datetime import datetime, timedelta

from signal_log import load_signal_log


def generate_weekly_report():
    """
    Returns all trades from the last 7 days.
    """

    df = load_signal_log()

    if df.empty:
        return pd.DataFrame()

    df["timestamp"] = pd.to_datetime(df["timestamp"])

    last_week = datetime.now() - timedelta(days=7)

    report = df[df["timestamp"] >= last_week]

    return report
