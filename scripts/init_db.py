"""
scripts/init_db.py

First-time setup: creates the SQLite database (if not present) and does a
full load from Upstox. Safe to re-run — it behaves the same as run_update.py
if the DB already has data (existing rows are diffed, not wiped).

Usage:
    python scripts/init_db.py
"""

import logging
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings
from instrument_master.classifier import ClassificationRules
from instrument_master.database import InstrumentDatabase
from instrument_master.update_engine import run_full_sync
from instrument_master import validation

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def main():
    db = InstrumentDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    rules = ClassificationRules(settings.CLASSIFICATION_RULES_PATH)

    summary = run_full_sync(db, rules, settings)
    summary.print_report()

    report = validation.validate(db)
    validation.print_report(report)

    db.close()

    if not report["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
