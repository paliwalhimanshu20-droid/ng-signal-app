"""
scripts/init_db.py

First-time setup: creates the Research & Learning Database (all 9 tables +
indexes) at config/settings.DB_PATH. Safe to re-run.

Usage:
    python scripts/init_db.py
"""

import logging
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from research_config import settings
from research_db.database import ResearchDatabase
from research_db import validation

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def main():
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    print(f"Research & Learning Database ready at {settings.DB_PATH}")
    print(f"Total experiments: {db.count_experiments():,}")

    report = validation.validate(db)
    validation.print_report(report)

    db.close()
    if not report["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
