"""
warehouse.downloader.rate_limiter
====================================

A single, process-wide, thread-safe token-bucket rate limiter shared by
every worker in a batch download run. This exists because the known
failure mode (documented in NGWH-001's infrastructure notes) is Streamlit
Community Cloud's *shared* outbound IP triggering Upstox 429s — the limit
that matters is total requests/second leaving the process, not per-thread,
so a single shared bucket (not one limiter per worker) is the correct
design.
"""

from __future__ import annotations

import threading
import time

from warehouse.downloader.downloader_config import RateLimitConfig


class TokenBucketRateLimiter:
    """Classic token bucket: tokens refill continuously at `rate` per
    second up to `capacity`; `acquire()` blocks until a token is available."""

    def __init__(self, config: RateLimitConfig):
        self._rate = config.requests_per_second
        self._capacity = float(config.burst_capacity)
        self._tokens = self._capacity
        self._last_refill = time.monotonic()
        self._lock = threading.Lock()

    def _refill(self) -> None:
        now = time.monotonic()
        elapsed = now - self._last_refill
        if elapsed <= 0:
            return
        self._tokens = min(self._capacity, self._tokens + elapsed * self._rate)
        self._last_refill = now

    def acquire(self, tokens: float = 1.0) -> None:
        """Block until `tokens` are available, then consume them."""
        while True:
            with self._lock:
                self._refill()
                if self._tokens >= tokens:
                    self._tokens -= tokens
                    return
                deficit = tokens - self._tokens
                wait_time = deficit / self._rate if self._rate > 0 else 0.1
            time.sleep(min(wait_time, 1.0))

    def try_acquire(self, tokens: float = 1.0) -> bool:
        """Non-blocking variant — returns False immediately if not enough
        tokens are available rather than waiting."""
        with self._lock:
            self._refill()
            if self._tokens >= tokens:
                self._tokens -= tokens
                return True
            return False
