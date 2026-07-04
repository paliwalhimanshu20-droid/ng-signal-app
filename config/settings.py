"""
config/settings.py — Research & Learning Database configuration.

Deliberately separate from NGSP-003A.1's config/settings.py (different
project folder entirely) to keep the two modules fully independent.
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "research_learning.db")

# "DELETE" = single .db file, matches the git-sync pattern used elsewhere in
# NG Signal Pro (e.g. signal_log.csv). Switch to "WAL" only if this runs as
# a long-lived process with concurrent readers/writers.
SQLITE_JOURNAL_MODE = "DELETE"
