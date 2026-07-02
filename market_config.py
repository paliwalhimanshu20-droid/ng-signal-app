"""
NGSP-002 — Market Intelligence Engine: Configuration
=====================================================

Every tunable threshold, weight, and multiplier used by the Market
Intelligence Engine lives here. Business logic modules must import
values from this file and must never hardcode constants.

Responsibilities
----------------
- Define regime classification thresholds (bull / neutral / bear).
- Define India VIX bands.
- Define fundamental quality gate thresholds and pass score.
- Define event risk windows and penalties.
- Define confidence blending weights and clamping bounds.

Future extension points
-----------------------
- Add liquidity filter thresholds.
- Add institutional flow thresholds.
- Add per-sector overrides via `SectorOverrides`.
- Load from YAML/JSON at runtime (see `MarketConfig.from_dict`).
"""

from __future__ import annotations

from dataclasses import dataclass, field, asdict
from typing import Any, Dict


# ---------------------------------------------------------------------------
# Regime configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class RegimeConfig:
    """Thresholds used to classify the overall market regime.

    The regime engine produces a composite ``market_score`` in [0, 100].
    Scores are then mapped to a regime label and a signal multiplier.
    """

    # Composite market-score boundaries (0–100 scale)
    bull_threshold: float = 65.0        # score >= this  -> BULL
    bear_threshold: float = 35.0        # score <= this  -> BEAR
    # anything strictly between the two thresholds -> NEUTRAL

    # India VIX bands
    vix_low: float = 13.0               # VIX below this = calm market
    vix_high: float = 20.0              # VIX above this = stressed market
    vix_extreme: float = 28.0           # VIX above this = crisis conditions

    # Component weights for the composite market score (must sum to 1.0)
    weight_nifty_trend: float = 0.40
    weight_vix: float = 0.25
    weight_sector_rs: float = 0.20
    weight_breadth: float = 0.15

    # Regime multipliers applied to technical conviction downstream
    bull_multiplier: float = 1.10
    neutral_multiplier: float = 1.00
    bear_multiplier: float = 0.70
    extreme_vix_multiplier: float = 0.50   # overrides regime multiplier

    def validate(self) -> None:
        total = (
            self.weight_nifty_trend
            + self.weight_vix
            + self.weight_sector_rs
            + self.weight_breadth
        )
        if abs(total - 1.0) > 1e-6:
            raise ValueError(
                f"RegimeConfig component weights must sum to 1.0, got {total}"
            )
        if self.bear_threshold >= self.bull_threshold:
            raise ValueError("bear_threshold must be below bull_threshold")


# ---------------------------------------------------------------------------
# Fundamental quality gate configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class FundamentalConfig:
    """Thresholds for the monthly-refresh fundamental quality gate."""

    # Metric pass thresholds
    min_roe: float = 15.0               # % — return on equity
    max_debt_to_equity: float = 1.0     # ratio
    min_eps_growth: float = 10.0        # % YoY
    min_sales_growth: float = 8.0       # % YoY
    min_profit_growth: float = 10.0     # % YoY

    # Per-metric score contribution (must sum to 100)
    points_roe: float = 25.0
    points_debt_to_equity: float = 20.0
    points_eps_growth: float = 20.0
    points_sales_growth: float = 15.0
    points_profit_growth: float = 20.0

    # Gate
    min_pass_score: float = 60.0        # score >= this passes the gate

    # Data freshness
    refresh_days: int = 30              # monthly refresh cadence
    stale_data_passes: bool = True      # stale data: pass-through with warning

    def validate(self) -> None:
        total = (
            self.points_roe
            + self.points_debt_to_equity
            + self.points_eps_growth
            + self.points_sales_growth
            + self.points_profit_growth
        )
        if abs(total - 100.0) > 1e-6:
            raise ValueError(
                f"FundamentalConfig points must sum to 100, got {total}"
            )


# ---------------------------------------------------------------------------
# Event risk configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class EventConfig:
    """Windows and penalties for upcoming corporate events."""

    # Days-before-event windows
    high_risk_days: int = 2             # within this window -> HIGH risk
    medium_risk_days: int = 5           # within this window -> MEDIUM risk
    low_risk_days: int = 10             # within this window -> LOW risk

    # Confidence penalties per risk level (points subtracted, 0–100 scale)
    penalty_high: float = 25.0
    penalty_medium: float = 12.0
    penalty_low: float = 5.0
    penalty_none: float = 0.0

    # Signal suppression: suppress new signals inside the HIGH window
    # for these event types (earnings surprises gap hardest)
    suppress_on_high_risk: bool = True

    def validate(self) -> None:
        if not (self.high_risk_days <= self.medium_risk_days <= self.low_risk_days):
            raise ValueError(
                "Event windows must satisfy high <= medium <= low days"
            )


# ---------------------------------------------------------------------------
# Confidence blending configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class ConfidenceConfig:
    """Weights used to blend all intelligence layers into final confidence.

    ``final_confidence`` is computed as::

        base = technical * w_technical
             + market    * w_market
             + fundamental * w_fundamental
        final = clamp((base - event_penalty) * regime_multiplier,
                      min_confidence, max_confidence)
    """

    weight_technical: float = 0.55
    weight_market: float = 0.25
    weight_fundamental: float = 0.20

    min_confidence: float = 0.0
    max_confidence: float = 100.0

    def validate(self) -> None:
        total = self.weight_technical + self.weight_market + self.weight_fundamental
        if abs(total - 1.0) > 1e-6:
            raise ValueError(
                f"ConfidenceConfig weights must sum to 1.0, got {total}"
            )
        if self.min_confidence >= self.max_confidence:
            raise ValueError("min_confidence must be below max_confidence")


# ---------------------------------------------------------------------------
# Root configuration object
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class MarketConfig:
    """Root configuration for the Market Intelligence Engine."""

    regime: RegimeConfig = field(default_factory=RegimeConfig)
    fundamental: FundamentalConfig = field(default_factory=FundamentalConfig)
    event: EventConfig = field(default_factory=EventConfig)
    confidence: ConfidenceConfig = field(default_factory=ConfidenceConfig)

    def validate(self) -> "MarketConfig":
        """Validate all sub-configs. Returns self for chaining."""
        self.regime.validate()
        self.fundamental.validate()
        self.event.validate()
        self.confidence.validate()
        return self

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "MarketConfig":
        """Build a config from a plain dict (e.g. loaded from YAML/JSON).

        Unknown keys are ignored; missing keys fall back to defaults.
        """
        return cls(
            regime=RegimeConfig(**raw.get("regime", {})),
            fundamental=FundamentalConfig(**raw.get("fundamental", {})),
            event=EventConfig(**raw.get("event", {})),
            confidence=ConfidenceConfig(**raw.get("confidence", {})),
        ).validate()


DEFAULT_CONFIG = MarketConfig().validate()
