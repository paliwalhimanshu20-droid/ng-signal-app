import pandas as pd
import gzip
import json

FILE_PATH = "NSE.json.gz"

def load_data():

    with gzip.open(FILE_PATH, "rt", encoding="utf-8") as f:
        return json.load(f)

def build_csv():

    data = load_data()
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
    print("Rows:", len(df))

if __name__ == "__main__":
    build_csv()
