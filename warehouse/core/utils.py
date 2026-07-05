"""
warehouse.core.utils
======================

Small, dependency-light utility functions shared across the warehouse
foundation. Anything used by 2+ modules belongs here rather than being
copy-pasted (the brief explicitly forbids duplicated code).
"""

from __future__ import annotations

import hashlib
import re
import uuid
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Iterable

_INSTRUMENT_ID_SAFE_PATTERN = re.compile(r"[^A-Za-z0-9_\-]")


def utc_now() -> datetime:
    """Timezone-aware 'now' in UTC. Never use naive `datetime.now()` anywhere
    in the warehouse — every timestamp stored must be unambiguous."""
    return datetime.now(timezone.utc)


def new_uuid() -> str:
    """Generate a UUID4 string, used for job_id / checkpoint_id / run_id."""
    return str(uuid.uuid4())


def safe_path_component(value: str) -> str:
    """
    Sanitize a string (e.g. an instrument_id or symbol) for safe use as a
    directory/file name component. Replaces anything that isn't
    alphanumeric, underscore, or hyphen with an underscore.
    """
    return _INSTRUMENT_ID_SAFE_PATTERN.sub("_", value.strip())


def ensure_directory(path: Path) -> Path:
    """Create a directory (and parents) if it doesn't exist. Returns the path."""
    path = Path(path)
    path.mkdir(parents=True, exist_ok=True)
    return path


def file_sha256(path: Path, chunk_size: int = 1024 * 1024) -> str:
    """Compute the SHA-256 hex digest of a file's contents, streamed in
    chunks so multi-GB Parquet files don't need to be loaded into memory."""
    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(chunk_size), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def bytes_to_human(num_bytes: int) -> str:
    """Render a byte count as a human-readable string, e.g. '482.3 MB'."""
    value = float(num_bytes)
    for unit in ("B", "KB", "MB", "GB", "TB", "PB"):
        if value < 1024.0:
            return f"{value:.1f} {unit}"
        value /= 1024.0
    return f"{value:.1f} EB"


def year_month_range(start: date, end: date) -> Iterable[tuple[int, int]]:
    """
    Yield (year, month) tuples spanning [start, end] inclusive, in order.
    Used by the partition manager and (later) the downloader to enumerate
    which monthly partitions a date range touches.
    """
    if start > end:
        raise ValueError(f"start ({start}) must be <= end ({end})")
    year, month = start.year, start.month
    while (year, month) <= (end.year, end.month):
        yield year, month
        if month == 12:
            year, month = year + 1, 1
        else:
            month += 1


def year_range(start: date, end: date) -> Iterable[int]:
    """Yield years spanning [start, end] inclusive. Used for yearly-granularity
    partitions (1hour/1day/1week timeframes)."""
    if start > end:
        raise ValueError(f"start ({start}) must be <= end ({end})")
    return range(start.year, end.year + 1)


def chunked(items: list, size: int) -> Iterable[list]:
    """Yield successive `size`-length chunks from `items`. Used for batching
    parallel-download work (future module) without pulling it into scope here."""
    for i in range(0, len(items), size):
        yield items[i : i + size]
