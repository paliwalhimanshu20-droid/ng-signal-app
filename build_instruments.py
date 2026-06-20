import pandas as pd
import gzip
import json

# 🔹 Load extracted NSE.json file (after unzip)
FILE_PATH = "NSE.json"

def load_json():
    with open(FILE_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

def build_csv():
    data = load_json()

    rows = []

    for item in data:
        try:
            symbol = item.get("trading_symbol")
            key = item.get("instrument_key")

            if symbol and key:
                rows.append([symbol, key])

        except:
            continue

    df = pd.DataFrame(rows, columns=["trading_symbol", "instrument_key"])

    df.to_csv("instruments.csv", index=False)

    print("DONE: instruments.csv created")
    print(df.head())

if __name__ == "__main__":
    build_csv()
