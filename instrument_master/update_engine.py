"""
instrument_master/update_engine.py

Orchestrates a full sync cycle:
  1. download latest instrument file
  2. parse + normalize
  3. classify each record
  4. diff against what's already in the DB
  5. insert new, update changed, deactivate removed
  6. preserve NG Signal Pro research fields on every existing row

This is the only module that ties downloader/parser/classifier/database
together — each of those stays independently testable.
"""

import logging

from . import classifier as classifier_mod
from . import downloader
from . import parser as parser_mod

logger = logging.getLogger(__name__)


class UpdateSummary:
    def __init__(self):
        self.new_count = 0
        self.updated_count = 0
        self.unchanged_count = 0
        self.deactivated_count = 0
        self.total_source_records = 0

    def as_dict(self):
        return {
            "total_source_records": self.total_source_records,
            "new": self.new_count,
            "updated": self.updated_count,
            "unchanged": self.unchanged_count,
            "deactivated": self.deactivated_count,
        }

    def print_report(self):
        print("\n=== Instrument Master Update Summary ===")
        for k, v in self.as_dict().items():
            print(f"  {k:>22}: {v:,}")
        print("=========================================\n")


def run_full_sync(db, rules: classifier_mod.ClassificationRules, settings) -> UpdateSummary:
    summary = UpdateSummary()

    raw_bytes = downloader.download_raw(
        settings.UPSTOX_INSTRUMENTS_URL, settings.DOWNLOAD_TIMEOUT_SECONDS
    )
    raw_records = parser_mod.parse_raw_json(raw_bytes)
    normalized = parser_mod.normalize_records(raw_records)
    summary.total_source_records = len(normalized)

    existing_keys_before = db.get_all_instrument_keys()
    seen_keys = set()

    for record in normalized:
        key = record["instrument_key"]
        seen_keys.add(key)

        classification = classifier_mod.classify_instrument(record, rules)

        if key not in existing_keys_before:
            priority = classifier_mod.assign_research_priority(record, settings)
            template = classifier_mod.assign_sector_template(classification)
            db.insert_new(record, classification, priority, template)
            summary.new_count += 1
            continue

        # Existing instrument: only touch trading/classification fields if
        # the source data actually changed (cheap hash comparison).
        prior_hash = db.get_source_hash(key)
        if prior_hash == record.get("record_hash"):
            summary.unchanged_count += 1
            continue

        db.update_trading_fields(key, record, classification)
        summary.updated_count += 1

    # Anything that existed before but wasn't in this sync's payload is
    # no longer tradable on Upstox — deactivate, don't delete, so research
    # history is preserved.
    removed_keys = existing_keys_before - seen_keys
    for key in removed_keys:
        db.deactivate(key)
        summary.deactivated_count += 1

    db.commit()
    return summary
