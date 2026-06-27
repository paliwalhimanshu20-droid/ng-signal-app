import pandas as pd

def compute_win_rate(df):
    if df.empty:
        return 0.0

    wins = df[df.get("pnl", 0) > 0]
    return round((len(wins) / len(df)) * 100, 2)


def compute_expectancy(df):
    if df.empty or "pnl" not in df.columns:
        return 0.0

    return round(df["pnl"].mean(), 4)


def compute_profit_factor(df):
    if df.empty or "pnl" not in df.columns:
        return 0.0

    gains = df[df["pnl"] > 0]["pnl"].sum()
    losses = abs(df[df["pnl"] < 0]["pnl"].sum())

    if losses == 0:
        return float("inf")

    return round(gains / losses, 2)


def compute_metrics(df):
    return {
        "win_rate": compute_win_rate(df),
        "expectancy": compute_expectancy(df),
        "profit_factor": compute_profit_factor(df),
        "total_trades": len(df)
    }
