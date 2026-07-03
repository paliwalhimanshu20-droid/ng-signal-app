"""
instrument_master/downloader.py

Responsible ONLY for fetching the raw Upstox instrument file and returning
decompressed bytes. Does not parse or interpret the data — see parser.py.
"""

import gzip
import hashlib
import io
import logging

import requests

logger = logging.getLogger(__name__)


class DownloadError(Exception):
    pass


def download_raw(url: str, timeout: int) -> bytes:
    """Download the gzip file and return decompressed raw bytes (JSON text)."""
    logger.info("Downloading instrument master from %s", url)
    try:
        resp = requests.get(url, timeout=timeout, stream=True)
        resp.raise_for_status()
    except requests.RequestException as exc:
        raise DownloadError(f"Failed to download instrument master: {exc}") from exc

    content = resp.content
    logger.info("Downloaded %.2f MB (compressed)", len(content) / 1_000_000)

    try:
        with gzip.GzipFile(fileobj=io.BytesIO(content)) as gz:
            raw = gz.read()
    except OSError as exc:
        raise DownloadError(f"Failed to decompress instrument master: {exc}") from exc

    logger.info("Decompressed to %.2f MB", len(raw) / 1_000_000)
    return raw


def compute_source_hash(raw_bytes: bytes) -> str:
    """Hash of the full source payload, stored for audit/debug purposes."""
    return hashlib.sha256(raw_bytes).hexdigest()
