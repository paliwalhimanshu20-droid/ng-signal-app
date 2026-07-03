"""
scripts/migrate.py

Explicitly checks/applies pending schema migrations without doing a full
Upstox sync. Useful for:
  - Upgrading an existing database after pulling new code (schema.py /
    migrations.py changes) before running init_db.py or run_update.py
  - CI/deploy steps that want migration to be a visible, separate step
  - Confirming what schema version a database is currently at

Safe to run any time, any number of times — migrations are idempotent and
only apply what hasn't already been applied.

Usage:
    python scripts/migrate.py
"""

import logging
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings
from instrument_master.database import InstrumentDatabase
from instrument_master import migrations

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def main():
    # InstrumentDatabase already runs migrations on connect — this script
    # just makes that step explicit and reports the outcome.
    db = InstrumentDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    version = migrations.current_version(db.conn)
    print(f"\nDatabase at {settings.DB_PATH}")
    print(f"Schema version: v{version}")
    print(f"Total instruments: {db.count():,}\n")
    db.close()


if __name__ == "__main__":
    main()
