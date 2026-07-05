"""
validation_history/settings.py

Deliberately its own small settings module — same convention as
instrument_config/settings.py and research_config/settings.py — so this
package's storage location stays independent and never collides with
another module's DB path (the exact class of bug fixed earlier this
session in instrument_config/settings.py).
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "validation_history.db")

SQLITE_JOURNAL_MODE = "DELETE"
