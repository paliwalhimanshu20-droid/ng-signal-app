import pandas as pd

def top_strategies(df, top_n=10):
    """
    Ranks parameter combinations from optimizer output.
    """
    if df.empty:
        return df

    return df.sort_values(["win_rate", "pnl"], ascending=False).head(top_n)


def summarize_results(df):
    if df.empty:
        return {}

    return {
        "best_win_rate": df["win_rate"].max(),
        "best_pnl": df["pnl"].max(),
        "avg_win_rate": round(df["win_rate"].mean(), 2),
        "total_tests": len(df)
    }


def group_by_ema(df):
    return df.groupby(["ema_fast", "ema_slow"]).agg({
        "win_rate": "mean",
        "pnl": "sum",
        "trades": "sum"
    }).reset_index().sort_values("win_rate", ascending=False)


def group_by_adx(df):
    return df.groupby(["adx"]).agg({
        "win_rate": "mean",
        "pnl": "sum",
        "trades": "sum"
    }).reset_index().sort_values("win_rate", ascending=False)


def print_report(df):
    print("\n=== STRATEGY LAB REPORT ===")
    print("Total Configs Tested:", len(df))

    if df.empty:
        print("No data available")
        return

    best = df.iloc[0]
    print("\nBEST CONFIG:")
    print(best.to_dict())

    print("\nSUMMARY:")
    print(summarize_results(df))
