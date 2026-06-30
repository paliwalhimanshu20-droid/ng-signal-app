import pandas as pd
from io import BytesIO
from datetime import datetime, timedelta

from signal_log import load_signal_log


def generate_weekly_report():
    """
    Returns:
        week_df : DataFrame
        summary : dict
    """

    df = load_signal_log()

    if df.empty:
        return pd.DataFrame(), {
            "total_trades": 0,
            "closed_trades": 0,
            "wins": 0,
            "losses": 0,
            "win_rate": 0,
            "avg_pnl": 0,
        }

    # Convert timestamp safely
    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")
    df = df.dropna(subset=["timestamp"])

    # Remove timezone if present
    try:
        df["timestamp"] = df["timestamp"].dt.tz_localize(None)
    except Exception:
        pass

    cutoff = datetime.now() - timedelta(days=7)

    week_df = df[df["timestamp"] >= cutoff].copy()

    closed = week_df[
        week_df["status"].isin(["TARGET_HIT", "SL_HIT"])
    ].copy()

    summary = {
        "total_trades": len(week_df),
        "closed_trades": len(closed),
        "wins": (closed["status"] == "TARGET_HIT").sum(),
        "losses": (closed["status"] == "SL_HIT").sum(),
        "win_rate": 0,
        "avg_pnl": 0,
    }

    if summary["closed_trades"] > 0:
        summary["win_rate"] = round(
            summary["wins"] / summary["closed_trades"] * 100,
            1,
        )

        closed["pnl_pct"] = pd.to_numeric(
            closed["pnl_pct"],
            errors="coerce",
        )

        summary["avg_pnl"] = round(
            closed["pnl_pct"].mean(),
            2,
        )

    return week_df, summary


def weekly_report_excel(report_df):
    """
    Converts the weekly report DataFrame into an Excel file.
    """

    output = BytesIO()

    with pd.ExcelWriter(output, engine="openpyxl") as writer:
        report_df.to_excel(
            writer,
            index=False,
            sheet_name="Weekly Report",
        )

    output.seek(0)
    return output
