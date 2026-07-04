"""
research_config/settings.py — Research & Learning Database configuration.

Deliberately separate from instrument_config/settings.py (Instrument Master
Database, NGSP-003A.1) to keep the two modules fully independent.

This file's content is unchanged from what was previously (incorrectly)
sitting in instrument_config/settings.py — it was always correct for
init_db.py / migrate.py / example_usage.py, just living in the wrong folder,
which collided with the Instrument Master module's own settings.
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "research_learning.db")

# "DELETE" = single .db file, matches the git-sync pattern used elsewhere in
# NG Signal Pro (e.g. signal_log.csv). Switch to "WAL" only if this runs as
# a long-lived process with concurrent readers/writers.
SQLITE_JOURNAL_MODE = "DELETE"
