"""
warehouse.downloader.exceptions
==================================

Exception hierarchy for NGWH-002 (Historical Downloader). Every exception
here subclasses `warehouse.core.exceptions.WarehouseError`, so callers that
only know about the foundation's exception contract (e.g. `except
WarehouseError`) continue to catch downloader failures without change.
This is additive to the foundation, not a modification of it.
"""

from __future__ import annotations

from warehouse.core.exceptions import WarehouseError


class DownloaderError(WarehouseError):
    code = "DOWNLOADER_ERROR"


class UpstoxAPIError(DownloaderError):
    """Raised when the Upstox historical-candle endpoint returns a non-200
    response after all configured retries are exhausted."""
    code = "UPSTOX_API_ERROR"


class UpstoxRateLimitError(UpstoxAPIError):
    """Raised specifically for HTTP 429 after retry/backoff is exhausted —
    kept distinct from other API errors because the known root cause
    (Streamlit Community Cloud's shared-IP rate limiting) is operationally
    different from an auth failure or a malformed request."""
    code = "UPSTOX_RATE_LIMIT_ERROR"


class UpstoxAuthError(UpstoxAPIError):
    code = "UPSTOX_AUTH_ERROR"


class CandleValidationError(DownloaderError):
    """Raised when candle data returned by Upstox fails sanity validation
    (non-chronological, OHLC inconsistency, impossible values) — this is a
    data-quality gate, distinct from a transport-level API error."""
    code = "CANDLE_VALIDATION_ERROR"


class UnsupportedIntervalError(DownloaderError):
    """Raised when asked to download a Timeframe that has no direct Upstox
    interval mapping (e.g. 5min/15min/1hour, which are DERIVED_TIMEFRAMES
    per the approved architecture and must be produced by resampling the
    base timeframe, not fetched directly)."""
    code = "UNSUPPORTED_INTERVAL_ERROR"


class CoveragePlanningError(DownloaderError):
    code = "COVERAGE_PLANNING_ERROR"


class BatchRunError(DownloaderError):
    code = "BATCH_RUN_ERROR"
