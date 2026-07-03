import pandas as pd
from io import BytesIO
from datetime import datetime, timedelta

from signal_log import load_signal_log
from risk_engine import annotate_dataframe_with_risk
from risk_config import DEFAULT_ACCOUNT_SIZE, DEFAULT_RISK_PER_TRADE_PCT

# signal_log.csv's column names -> risk_engine.annotate_dataframe_with_risk()'s
# generic field names. See that function's docstring for why this mapping
# exists (scanner.py's live-scan columns use different names for the same
# fields, and this mapping is specific to the signal_log.csv schema).
_SIGNAL_LOG_RISK_COLUMN_MAP = {
    "instrument": "instrument",
    "signal": "signal",
    "entry": "entry_price",
    "sl": "sl",
    "t1": "t1",
    "t2": "t2",
    "confidence_pct": "conviction_pct",
    "score": "score",
    # signal_log.csv doesn't store a "regime" column (see risk_config.py's
    # comment on this) — omitted here, so Trade Quality can reach "Good"
    # but not "Excellent" from historical log rows, which need a regime
    # match. This is a known, documented limitation, not an oversight.
}


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

def export_excel_report(report_df, account_size=DEFAULT_ACCOUNT_SIZE, risk_per_trade_pct=DEFAULT_RISK_PER_TRADE_PCT):
    """
    account_size / risk_per_trade_pct: OPTIONAL — default to risk_config's
    defaults, so any existing call site that doesn't pass these (there
    were none touching this signature before NGSP-003) keeps working.
    app.py's Admin tab passes the same account-size/risk% the trader has
    set in Settings, so exported reports reflect their actual sizing
    assumptions rather than a generic default.

    Adds Risk_Quantity / Risk_CapitalRequired / Risk_MaxRiskAmount /
    Risk_RR_T1 / Risk_TradeQuality columns to every sheet except Summary
    (NGSP-003's "Reports -> CSV/Excel Export" requirement) — computed
    fresh at export time from each row's logged entry/SL/T1/T2, not a
    frozen snapshot from when the signal originally fired (account
    size/risk% are settings that can change; see risk_config.py).
    """
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

    open_count = len(open_df)

    def _annotate(df):
        if df.empty:
            return df
        return annotate_dataframe_with_risk(
            df, _SIGNAL_LOG_RISK_COLUMN_MAP,
            account_size=account_size,
            risk_per_trade_pct=risk_per_trade_pct,
            open_signal_count=open_count,
        )

    report_df = _annotate(report_df)
    month_df = _annotate(month_df)
    open_df = _annotate(open_df)
    closed_df = _annotate(closed_df)

    summary = pd.DataFrame({

        "Metric": [
            "Generated On",
            "Total Trades",
            "Weekly Trades",
            "Monthly Trades",
            "Open Trades",
            "Closed Trades",
            "Account Size Used",
            "Risk Per Trade % Used",
        ],

        "Value": [
            datetime.now().strftime("%Y-%m-%d %H:%M"),
            len(all_df),
            len(report_df),
            len(month_df),
            len(open_df),
            len(closed_df),
            account_size,
            risk_per_trade_pct,
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
    
