"""
scripts/run_update.py

Incremental update — intended to be run on a schedule (e.g. daily via
GitHub Actions, same pattern as check_signals.yml). Identical logic to
init_db.py's sync step; kept as a separate entrypoint for clearer CI naming
and so init-specific messaging can diverge later without touching the core
sync path.

Usage:
    python scripts/run_update.py

Exit code is non-zero if validation finds issues, so a CI job can fail loudly.
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

    before_count = db.count()
    summary = run_full_sync(db, rules, settings)
    after_count = db.count()

    summary.print_report()
    print(f"Row count: {before_count:,} -> {after_count:,}")

    report = validation.validate(db)
    validation.print_report(report)

    db.close()

    if not report["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
