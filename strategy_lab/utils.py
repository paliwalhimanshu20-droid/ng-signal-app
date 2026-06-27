import pandas as pd
from upstox_client import get_candles_range

def fetch_candles(symbol_key_map, days_back=10):
    """
    Builds data_dict for backtest.

    Input:
        symbol_key_map:
            {
                "RELIANCE": "NSE_EQ|INE123456",
                "TCS": "NSE_EQ|INE654321"
            }

    Output:
        {
            "RELIANCE": [[ts,o,h,l,c,v,oi], ...],
            "TCS": [[...], ...]
        }
    """

    data_dict = {}

    for symbol, key in symbol_key_map.items():
        try:
            candles = get_candles_range(key, days_back=days_back)

            if not candles:
                continue

            data_dict[symbol] = candles

        except Exception as e:
            print(f"[DATA ERROR] {symbol}: {e}")

    return data_dict


def load_symbol_map():
    """
    Temporary hardcoded mapping.
    Later you can replace with watchlist.py integration.
    """
    return {
        "RELIANCE": "NSE_EQ|RELIANCE",
        "TCS": "NSE_EQ|TCS",
        "INFY": "NSE_EQ|INFY",
        "HDFCBANK": "NSE_EQ|HDFCBANK"
    }
