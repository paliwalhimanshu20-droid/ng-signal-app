"""
scripts/migrate.py

Explicitly checks/applies pending schema migrations. Safe to run any time.

Usage:
    python scripts/migrate.py
"""

import logging
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from research_config import settings
from research_db.database import ResearchDatabase
from research_db import migrations

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def main():
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    version = migrations.current_version(db.conn)
    print(f"\nDatabase at {settings.DB_PATH}")
    print(f"Schema version: v{version}")
    print(f"Total experiments: {db.count_experiments():,}\n")
    db.close()


if __name__ == "__main__":
    main()
