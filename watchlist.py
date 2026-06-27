"""
Watchlist definition and sector lookup.

This is where you add/remove tracked NSE equities (get_watchlist) or
re-bucket a symbol into a different sector — that mapping itself lives in
config.SECTOR_MAP, edit it there.

Deliberately has NO Streamlit or network dependency — it's pure data plus
two small lookup functions, safe to import (and unit-test) on its own.
"""

from config import SECTOR_MAP


def get_watchlist(commodity_contracts=None):
    """
    commodity_contracts: dict mapping display_name -> selected instrument_key,
    e.g. {"Natural Gas": "MCX_FO|NATURALGAS26JUNFUT"}. Built from the
    dashboard's expiry dropdown (see the Settings tab in app.py). If None or
    empty, commodities are simply excluded from this scan (rather than
    guessing a key).
    """
    watchlist = {
        # ---- Existing core watchlist ----
        "ITC": "NSE_EQ|INE154A01025",
        "RELIANCE": "NSE_EQ|INE002A01018",
        "SBIN": "NSE_EQ|INE062A01020",
        "HDFCBANK": "NSE_EQ|INE040A01034",
        "ICICIBANK": "NSE_EQ|INE090A01021",
        "TCS": "NSE_EQ|INE467B01029",
        "INFY": "NSE_EQ|INE009A01021",
        "WIPRO": "NSE_EQ|INE075A01022",
        "ONGC": "NSE_EQ|INE213A01029",
        "NTPC": "NSE_EQ|INE733E01010",
        "POWERGRID": "NSE_EQ|INE752E01010",
        "TATAMOTORS": "NSE_EQ|INE155A01022",

        # ---- Banking (top 5) ----
        "AXISBANK": "NSE_EQ|INE238A01034",
       # "KOTAKBANK": "NSE_EQ|INE237A01028",
        "INDUSINDBK": "NSE_EQ|INE095A01012",
        "BANKBARODA": "NSE_EQ|INE028A01039",

        # ---- IT (top 5, TCS/INFY/WIPRO already above) ----
        "HCLTECH": "NSE_EQ|INE860A01027",
        "TECHM": "NSE_EQ|INE669C01036",

        # ---- Auto (top 5, TATAMOTORS already above) ----
        "MARUTI": "NSE_EQ|INE585B01010",
        "M&M": "NSE_EQ|INE101A01026",
        "BAJAJ-AUTO": "NSE_EQ|INE917I01010",
        "HEROMOTOCO": "NSE_EQ|INE158A01026",

        # ---- Pharma (top 5) ----
        "SUNPHARMA": "NSE_EQ|INE044A01036",
       # "DRREDDY": "NSE_EQ|INE089A01023",
        "CIPLA": "NSE_EQ|INE059A01026",
        "DIVISLAB": "NSE_EQ|INE361B01024",
        "APOLLOHOSP": "NSE_EQ|INE437A01024",

        # ---- FMCG (top 5, ITC already above) ----
        "HINDUNILVR": "NSE_EQ|INE030A01027",
        #"NESTLEIND": "NSE_EQ|INE239A01016",
        "BRITANNIA": "NSE_EQ|INE216A01030",
        "TATACONSUM": "NSE_EQ|INE192A01025",

        # ---- Energy (top 5, ONGC/NTPC/POWERGRID already above) ----
        "COALINDIA": "NSE_EQ|INE522F01014",
        "BPCL": "NSE_EQ|INE029A01011",

        # ---- Metals (top 5) ----
        #"TATASTEEL": "NSE_EQ|INE081A01012",
        "JSWSTEEL": "NSE_EQ|INE019A01038",
        "HINDALCO": "NSE_EQ|INE038A01020",
        "VEDL": "NSE_EQ|INE205A01025",
        "JINDALSTEL": "NSE_EQ|INE749A01030",
    }

    # ---- Commodities (MCX F&O), expiry chosen via dashboard dropdown ----
    if commodity_contracts:
        for display_name, key in commodity_contracts.items():
            if key:
                watchlist[f"{display_name} (MCX)"] = key

    return watchlist


def get_sector(instrument_name):
    """
    Resolves an instrument's display name (as used in get_watchlist/run_scanner)
    to its sector bucket. MCX commodity entries are named "<Display> (MCX)" by
    get_watchlist(), so those are caught explicitly before the lookup.
    """
    if "(MCX)" in instrument_name:
        return "Commodities"
    return SECTOR_MAP.get(instrument_name, "Other")
