"""
instrument_master/parser.py

Turns the raw Upstox JSON payload into a list of normalized dict records
using field names that match our schema. Does not touch the database and
does not classify — see database.py and classifier.py.
"""

import datetime as dt
import hashlib
import json
import logging

logger = logging.getLogger(__name__)

# Map from Upstox's raw JSON field names to our schema field names.
# Upstox's complete.json.gz records typically look like:
# {
#   "segment": "NSE_EQ", "name": "RELIANCE INDUSTRIES LTD",
#   "exchange": "NSE", "isin": "INE002A01018",
#   "instrument_type": "EQ", "instrument_key": "NSE_EQ|INE002A01018",
#   "lot_size": 1, "freeze_quantity": 100000.0, "exchange_token": "2885",
#   "tick_size": 5.0, "trading_symbol": "RELIANCE", "short_name": "RELIANCE",
#   "expiry": 1735689000000, "strike_price": 0.0
# }
RAW_FIELD_MAP = {
    "instrument_key": "instrument_key",
    "trading_symbol": "trading_symbol",
    "exchange": "exchange",
    "segment": "segment",
    "instrument_type": "instrument_type",
    "name": "display_name",
    "isin": "isin",
    "lot_size": "lot_size",
    "tick_size": "tick_size",
    "freeze_quantity": "freeze_quantity",
    "expiry": "expiry_raw",
    "strike_price": "strike",
    "option_type": "option_type",
}

REQUIRED_RAW_FIELDS = ["instrument_key", "trading_symbol", "exchange", "segment"]


class ParseError(Exception):
    pass


def parse_raw_json(raw_bytes: bytes) -> list[dict]:
    """Parse raw JSON bytes into a list of dicts as-is from Upstox."""
    try:
        data = json.loads(raw_bytes)
    except json.JSONDecodeError as exc:
        raise ParseError(f"Instrument master JSON is malformed: {exc}") from exc

    if not isinstance(data, list):
        raise ParseError("Expected top-level JSON array from Upstox instrument file")

    logger.info("Parsed %d raw records", len(data))
    return data


def _epoch_ms_to_iso_date(value) -> str | None:
    if value in (None, "", 0):
        return None
    try:
        ts = float(value) / 1000.0
        return dt.datetime.utcfromtimestamp(ts).strftime("%Y-%m-%d")
    except (ValueError, OverflowError, OSError):
        return None


def normalize_records(raw_records: list[dict]) -> list[dict]:
    """
    Convert raw Upstox dicts into normalized records matching our schema
    field names. Records missing required fields are skipped and logged.
    """
    normalized = []
    skipped = 0

    for raw in raw_records:
        if not all(raw.get(f) for f in REQUIRED_RAW_FIELDS):
            skipped += 1
            continue

        rec = {}
        for raw_key, schema_key in RAW_FIELD_MAP.items():
            rec[schema_key] = raw.get(raw_key)

        rec["expiry"] = _epoch_ms_to_iso_date(rec.pop("expiry_raw", None))

        # asset_class: Upstox doesn't give this explicitly; derive a coarse
        # placeholder here — the classifier module refines this further.
        rec["asset_class"] = rec.get("instrument_type")

        # Currency: all Upstox instruments (NSE/BSE/MCX) are INR-denominated.
        rec["currency"] = "INR"

        # Per-record hash for cheap change detection in the update engine.
        rec["record_hash"] = hashlib.sha256(
            json.dumps(rec, sort_keys=True, default=str).encode("utf-8")
        ).hexdigest()

        normalized.append(rec)

    if skipped:
        logger.warning("Skipped %d records missing required fields", skipped)

    logger.info("Normalized %d records", len(normalized))
    return normalized
