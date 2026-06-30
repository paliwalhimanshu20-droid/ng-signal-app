import pandas as pd
from io import BytesIO
from datetime import datetime, timedelta

from signal_log import load_signal_log


# ==========================================================
# Internal Helper
# ==========================================================

def _generate_report(days):

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

    df["timestamp"] = pd.to_datetime(
        df["timestamp"],
        errors="coerce",
    )

    df = df.dropna(subset=["timestamp"])

    try:
        df["timestamp"] = df["timestamp"].dt.tz_localize(None)
    except Exception:
        pass

    cutoff = datetime.now() - timedelta(days=days)

    report_df = df[df["timestamp"] >= cutoff].copy()

    closed = report_df[
        report_df["status"].isin(
            ["TARGET_HIT", "SL_HIT"]
        )
    ].copy()

    summary = {
        "total_trades": len(report_df),
        "closed_trades": len(closed),
        "wins": int((closed["status"] == "TARGET_HIT").sum()),
        "losses": int((closed["status"] == "SL_HIT").sum()),
        "win_rate": 0,
        "avg_pnl": 0,
    }

    if summary["closed_trades"] > 0:

        summary["win_rate"] = round(
            summary["wins"]
            / summary["closed_trades"]
            * 100,
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

    return report_df, summary


# ==========================================================
# Weekly Report
# ==========================================================

def generate_weekly_report():

    return _generate_report(7)


# ==========================================================
# Monthly Report
# ==========================================================

def generate_monthly_report():

    return _generate_report(30)


# ==========================================================
# Excel Export
# ==========================================================

def export_excel_report(report_df):

    output = BytesIO()

    all_df = load_signal_log()

    month_df, _ = generate_monthly_report()

    open_df = all_df[
        all_df["status"] == "OPEN"
    ].copy()

    closed_df = all_df[
        all_df["status"].isin(
            ["TARGET_HIT", "SL_HIT"]
        )
    ].copy()

    summary = pd.DataFrame({

        "Metric": [
            "Generated On",
            "Total Trades",
            "Weekly Trades",
            "Monthly Trades",
            "Open Trades",
            "Closed Trades",
        ],

        "Value": [
            datetime.now().strftime("%Y-%m-%d %H:%M"),
            len(all_df),
            len(report_df),
            len(month_df),
            len(open_df),
            len(closed_df),
        ],
    })

    with pd.ExcelWriter(
        output,
        engine="openpyxl",
    ) as writer:

        summary.to_excel(
            writer,
            sheet_name="Summary",
            index=False,
        )

        report_df.to_excel(
            writer,
            sheet_name="Weekly Trades",
            index=False,
        )

        month_df.to_excel(
            writer,
            sheet_name="Monthly Trades",
            index=False,
        )

        open_df.to_excel(
            writer,
            sheet_name="Open Trades",
            index=False,
        )

        closed_df.to_excel(
            writer,
            sheet_name="Closed Trades",
            index=False,
        )

    output.seek(0)

    return output
