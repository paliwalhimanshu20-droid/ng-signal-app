"""
NGSP-002 — Market Intelligence Engine: Data Provider Abstraction
=================================================================

Defines the provider interfaces the Market Intelligence Engine
depends on. No concrete API (Upstox, NSE, screener, etc.) is
referenced here — real providers implement these interfaces in
their own modules and are injected into ``MarketEngine``.

Responsibilities
----------------
- ``MarketDataProvider``: broad market conditions (Nifty, VIX,
  sector RS, breadth).
- ``FundamentalDataProvider``: per-symbol fundamental metrics.
- ``EventDataProvider``: per-symbol upcoming corporate events.
- ``StaticDataProvider``: in-memory implementation used for tests,
  backtests, and offline evaluation.

Future extension points
-----------------------
- ``UpstoxMarketDataProvider`` built on the existing upstox_client.
- Caching decorator providers (e.g. monthly fundamental cache).
- ``LiquidityDataProvider`` for NGSP-003+.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Dict, List, Optional

from market_models import (
    FundamentalSnapshot,
    MarketSnapshot,
    UpcomingEvent,
)


class MarketDataProvider(ABC):
    """Supplies broad market condition snapshots."""

    @abstractmethod
    def get_market_snapshot(self, sector: Optional[str] = None) -> MarketSnapshot:
        """Return the current :class:`MarketSnapshot`.

        Parameters
        ----------
        sector:
            Optional sector name so relative strength can be computed
            for the symbol's sector. Providers may ignore it.
        """


class FundamentalDataProvider(ABC):
    """Supplies per-symbol fundamental metrics (monthly cadence)."""

    @abstractmethod
    def get_fundamentals(self, symbol: str) -> Optional[FundamentalSnapshot]:
        """Return fundamentals for ``symbol`` or ``None`` if unavailable."""


class EventDataProvider(ABC):
    """Supplies per-symbol upcoming corporate events."""

    @abstractmethod
    def get_upcoming_events(self, symbol: str) -> List[UpcomingEvent]:
        """Return all known future events for ``symbol`` (may be empty)."""


class StaticDataProvider(MarketDataProvider, FundamentalDataProvider, EventDataProvider):
    """In-memory provider for tests, backtesting, and offline runs.

    Construct with pre-built snapshots and use as all three provider
    types at once, or pass only what a given engine requires.
    """

    def __init__(
        self,
        market_snapshot: Optional[MarketSnapshot] = None,
        fundamentals: Optional[Dict[str, FundamentalSnapshot]] = None,
        events: Optional[Dict[str, List[UpcomingEvent]]] = None,
    ) -> None:
        self._market_snapshot = market_snapshot
        self._fundamentals: Dict[str, FundamentalSnapshot] = fundamentals or {}
        self._events: Dict[str, List[UpcomingEvent]] = events or {}

    # -- MarketDataProvider -------------------------------------------------

    def get_market_snapshot(self, sector: Optional[str] = None) -> MarketSnapshot:
        if self._market_snapshot is None:
            raise ValueError("StaticDataProvider has no market snapshot configured")
        return self._market_snapshot

    # -- FundamentalDataProvider --------------------------------------------

    def get_fundamentals(self, symbol: str) -> Optional[FundamentalSnapshot]:
        return self._fundamentals.get(symbol)

    # -- EventDataProvider ---------------------------------------------------

    def get_upcoming_events(self, symbol: str) -> List[UpcomingEvent]:
        return list(self._events.get(symbol, []))
