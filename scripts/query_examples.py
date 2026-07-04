"""
scripts/query_examples.py

Example read-only queries against the Instrument Master Database, showing
the kinds of lookups the future Research Engine will do. Safe to run
anytime after init_db.py.

Usage:
    python scripts/query_examples.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from instrument_config import settings
from instrument_master.database import InstrumentDatabase


def main():
    db = InstrumentDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)

    print(f"\nTotal instruments in DB: {db.count():,}")

    print("\n-- Priority 1 instruments (NG Signal Pro core) --")
    rows = db.query("research_priority = 1 AND active_status = 'ACTIVE'")
    for r in rows[:20]:
        print(f"  {r['trading_symbol']:<15} {r['exchange']:<6} {r['segment']:<10} "
              f"status={r['research_status']}")

    print("\n-- MCX commodities (Priority 2) --")
    rows = db.query("research_priority = 2 AND active_status = 'ACTIVE'")
    print(f"  {len(rows):,} MCX instruments found")
    for r in rows[:10]:
        print(f"  {r['trading_symbol']:<20} group={r['commodity_group']}")

    print("\n-- Everything queued for research --")
    rows = db.query("research_status = 'QUEUED'")
    print(f"  {len(rows):,} instruments queued")

    print("\n-- Sample: NATURALGAS instruments --")
    rows = db.query("trading_symbol LIKE ? AND active_status = 'ACTIVE'", ("%NATURALGAS%",))
    for r in rows[:10]:
        print(f"  {r['instrument_key']:<30} expiry={r['expiry']} lot={r['lot_size']}")

    db.close()


if __name__ == "__main__":
    main()
