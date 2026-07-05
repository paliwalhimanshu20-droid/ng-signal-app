"""
warehouse.downloader.http_client
====================================

The only module in NGWH-002 that makes an actual HTTP call to Upstox. This
deliberately mirrors the existing `upstox_client.py`'s separation-of-concerns
pattern (network isolated from logic) rather than importing that module
directly — `upstox_client.py` is tightly coupled to Streamlit (`st.cache_data`,
`st.error`, reading `st.secrets`), and NGWH-002 needs a pure, injectable,
unit-testable transport with retry/backoff/rate-limiting instead. If
Upstox changes the endpoint shape, this is the only file that needs to
change on the downloader side.

The access token is passed in explicitly by the caller (sourced from
`st.secrets` / `config.UPSTOX_ACCESS_TOKEN` at the call site) — this module
has zero Streamlit dependency, so it can be unit tested with a fake
transport with no Streamlit runtime involved.
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass
from datetime import date
from typing import Callable, Optional

import requests

from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.downloader.downloader_config import DownloaderConfig
from warehouse.downloader.exceptions import UpstoxAPIError, UpstoxAuthError, UpstoxRateLimitError
from warehouse.downloader.rate_limiter import TokenBucketRateLimiter

logger = get_logger(__name__)

UPSTOX_HISTORICAL_CANDLE_URL = "https://api.upstox.com/v2/historical-candle/{key}/{interval}/{to_date}/{from_date}"

# Signature of an injectable HTTP GET function, matching `requests.get`'s
# relevant surface. Tests inject a fake here instead of hitting the network.
HttpGetFn = Callable[..., "requests.Response"]


@dataclass(frozen=True)
class RawCandleResponse:
    instrument_key: str
    upstox_interval: str
    from_date: date
    to_date: date
    candles: list[list]  # raw Upstox rows: [timestamp, open, high, low, close, volume, oi]


class UpstoxHistoricalClient:
    """Fetches historical candles from Upstox with retry, exponential
    backoff + jitter, and shared rate limiting across all callers using the
    same `rate_limiter` instance (pass the same one into every client used
    within a batch run — see `batch_runner.py`)."""

    def __init__(
        self,
        access_token: str,
        config: DownloaderConfig,
        rate_limiter: TokenBucketRateLimiter,
        *,
        http_get: Optional[HttpGetFn] = None,
        sleep_fn: Callable[[float], None] = time.sleep,
    ):
        self._access_token = access_token
        self._config = config
        self._rate_limiter = rate_limiter
        self._http_get = http_get or requests.get
        self._sleep = sleep_fn

    def fetch_candles(self, instrument_key: str, upstox_interval: str, from_date: date, to_date: date) -> RawCandleResponse:
        """
        Fetch one chunk of candles. Retries on the configured status codes
        with exponential backoff + jitter, honoring the shared rate limiter
        before every attempt (including retries).

        Raises:
            UpstoxAuthError: on HTTP 401/403 (not retried — a token problem
                won't fix itself on retry).
            UpstoxRateLimitError: on HTTP 429 persisting past all retries.
            UpstoxAPIError: on any other non-200 response persisting past
                all retries, or a network-level exception persisting past
                all retries.
        """
        url = UPSTOX_HISTORICAL_CANDLE_URL.format(
            key=instrument_key, interval=upstox_interval,
            to_date=to_date.isoformat(), from_date=from_date.isoformat(),
        )
        headers = {"Authorization": f"Bearer {self._access_token}", "Accept": "application/json"}

        last_exc: Optional[Exception] = None
        last_status: Optional[int] = None

        for attempt in range(self._config.retry.max_retries + 1):
            self._rate_limiter.acquire()
            try:
                response = self._http_get(url, headers=headers, timeout=self._config.request_timeout_seconds)
            except requests.exceptions.RequestException as exc:
                last_exc = exc
                log_with_context(
                    logger, 30, "Upstox request raised a network exception; will retry if attempts remain",
                    instrument_key=instrument_key, attempt=attempt, error=str(exc),
                )
                self._sleep(self._backoff_seconds(attempt))
                continue

            if response.status_code == 200:
                payload = response.json()
                candles = payload.get("data", {}).get("candles", []) or []
                return RawCandleResponse(instrument_key, upstox_interval, from_date, to_date, candles)

            last_status = response.status_code
            if response.status_code in (401, 403):
                raise UpstoxAuthError(
                    f"Upstox authentication failed ({response.status_code}) for {instrument_key}",
                    context={"instrument_key": instrument_key, "status_code": response.status_code},
                )

            if response.status_code not in self._config.retry.retry_on_status_codes:
                raise UpstoxAPIError(
                    f"Upstox returned non-retryable status {response.status_code} for {instrument_key}",
                    context={"instrument_key": instrument_key, "status_code": response.status_code, "body": response.text[:300]},
                )

            log_with_context(
                logger, 30, "Upstox returned a retryable error status",
                instrument_key=instrument_key, attempt=attempt, status_code=response.status_code,
            )
            self._sleep(self._backoff_seconds(attempt))

        if last_status == 429:
            raise UpstoxRateLimitError(
                f"Upstox rate limit persisted after {self._config.retry.max_retries} retries for {instrument_key}",
                context={"instrument_key": instrument_key},
            )
        raise UpstoxAPIError(
            f"Upstox request failed after {self._config.retry.max_retries} retries for {instrument_key}",
            context={"instrument_key": instrument_key, "last_status": last_status, "last_exception": str(last_exc) if last_exc else None},
        )

    def _backoff_seconds(self, attempt: int) -> float:
        base = self._config.retry.backoff_base_seconds * (2 ** attempt)
        capped = min(base, self._config.retry.backoff_max_seconds)
        jitter = random.uniform(0, capped * 0.25)
        return capped + jitter
