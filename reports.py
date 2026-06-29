import pandas as pd
from io import BytesIO
from datetime import datetime, timedelta

from signal_log import load_signal_log
from config import IST

def weekly_report_excel(report_df):
    """
    Converts the weekly report DataFrame into an Excel file in memory.
    """

    output = BytesIO()

    with pd.ExcelWriter(output, engine="openpyxl") as writer:
        report_df.to_excel(writer, index=False, sheet_name="Weekly Report")

    output.seek(0)
    return output
