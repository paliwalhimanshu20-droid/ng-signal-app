"""
NGSP-002 — Market Intelligence Engine: Data Models
===================================================

Structured models exchanged between the engines. No business logic
lives here — only typed containers with light convenience helpers.

Responsibilities
----------------
- Define enums for regimes, event types, and risk levels.
- Define input snapshot models (market + fundamentals + events).
- Define output models (MarketRegime, FundamentalResult, EventResult,
  MarketConfidence, MarketIntelligence).

Future extension points
-----------------------
- Add `LiquiditySnapshot` / `InstitutionalFlowSnapshot` inputs.
- Add serialisation helpers (e.g. `to_json`) for dashboard transport.
"""

from __future__ import annotations

from dataclasses import dataclass, field, asdict
from datetime import date, datetime
from enum import Enum
from typing import Any, Dict, List, Optional


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

class RegimeType(str, Enum):
    """Overall market regime classification."""

    BULL = "BULL"
    NEUTRAL = "NEUTRAL"
    BEAR = "BEAR"


class NiftyTrend(str, Enum):
    """Directional state of the Nifty 50 index."""

    STRONG_UP = "STRONG_UP"       # e.g. above EMA20 and EMA50, rising
    UP = "UP"                     # above EMA50
    SIDEWAYS = "SIDEWAYS"
    DOWN = "DOWN"                 # below EMA50
    STRONG_DOWN = "STRONG_DOWN"   # below EMA20 and EMA50, falling


class EventType(str, Enum):
    """Supported corporate event categories."""

    EARNINGS = "EARNINGS"
    BOARD_MEETING = "BOARD_MEETING"
    DIVIDEND = "DIVIDEND"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    CORPORATE_ACTION = "CORPORATE_ACTION"


class RiskLevel(str, Enum):
    """Event risk severity."""

    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


# ---------------------------------------------------------------------------
# Input models
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class MarketSnapshot:
    """Point-in-time view of broad market conditions.

    Provider-agnostic: any data layer (Upstox, NSE, cached CSV) may
    construct this object.

    Attributes
    ----------
    nifty_trend:
        Directional state of Nifty 50.
    india_vix:
        Latest India VIX level.
    sector_relative_strength:
        Sector RS vs Nifty, in percent. Positive = outperforming.
    market_breadth:
        Advance/decline ratio expressed as fraction of advancing
        stocks, in [0, 1]. 0.5 is balanced.
    as_of:
        Timestamp of the snapshot.
    """

    nifty_trend: NiftyTrend
    india_vix: float
    sector_relative_strength: float
    market_breadth: float
    as_of: datetime = field(default_factory=datetime.now)


@dataclass(frozen=True)
class FundamentalSnapshot:
    """Monthly-refresh fundamental metrics for a single symbol.

    Any metric may be ``None`` when the provider has no data; the
    fundamental engine treats missing metrics as neutral (no points,
    with an explanatory reason).
    """

    symbol: str
    roe: Optional[float] = None                 # %
    debt_to_equity: Optional[float] = None      # ratio
    eps_growth: Optional[float] = None          # % YoY
    sales_growth: Optional[float] = None        # % YoY
    profit_growth: Optional[float] = None       # % YoY
    last_updated: Optional[date] = None


@dataclass(frozen=True)
class UpcomingEvent:
    """A single scheduled corporate event for a symbol."""

    symbol: str
    event_type: EventType
    event_date: date
    description: str = ""


# ---------------------------------------------------------------------------
# Output models
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class MarketRegime:
    """Result of market regime classification."""

    market_score: float          # composite, 0–100
    regime: RegimeType
    multiplier: float            # applied to technical conviction
    reasons: List[str] = field(default_factory=list)
    confidence: float = 0.0      # how decisively the regime was classified, 0–100

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class FundamentalResult:
    """Result of the fundamental quality gate."""

    score: float                 # 0–100
    passed: bool
    reasons: List[str] = field(default_factory=list)
    last_updated: Optional[date] = None
    is_stale: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class EventResult:
    """Result of event risk evaluation."""

    risk_level: RiskLevel
    suppress_signal: bool
    days_remaining: Optional[int]        # None when no event upcoming
    event_type: Optional[EventType]      # nearest / highest-risk event
    reasons: List[str] = field(default_factory=list)
    penalty: float = 0.0                 # confidence points to subtract

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class MarketConfidence:
    """Blended confidence combining all intelligence layers."""

    technical_score: float
    market_score: float
    fundamental_score: float
    event_penalty: float
    final_confidence: float
    multiplier: float
    reasons: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class MarketIntelligence:
    """Top-level result returned by ``MarketEngine.evaluate()``.

    The Signal Engine consumes this object; the Market Intelligence
    Engine never emits buy/sell decisions itself.
    """

    symbol: str
    regime: MarketRegime
    fundamentals: FundamentalResult
    events: EventResult
    confidence: MarketConfidence
    evaluated_at: datetime = field(default_factory=datetime.now)

    @property
    def signal_allowed(self) -> bool:
        """Convenience: True when nothing blocks acting on a signal."""
        return self.fundamentals.passed and not self.events.suppress_signal

    def all_reasons(self) -> List[str]:
        """Flattened explanations across all layers (dashboard-ready)."""
        return (
            list(self.regime.reasons)
            + list(self.fundamentals.reasons)
            + list(self.events.reasons)
            + list(self.confidence.reasons)
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "symbol": self.symbol,
            "signal_allowed": self.signal_allowed,
            "regime": self.regime.to_dict(),
            "fundamentals": self.fundamentals.to_dict(),
            "events": self.events.to_dict(),
            "confidence": self.confidence.to_dict(),
            "evaluated_at": self.evaluated_at.isoformat(),
        }
