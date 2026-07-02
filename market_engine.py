"""
NGSP-002 — Market Intelligence Engine: Regime, Confidence & Facade
===================================================================

Contains:

- ``RegimeEngine`` — classifies the market regime from a
  :class:`MarketSnapshot`.
- ``ConfidenceCalculator`` — blends the technical score (NGSP-001),
  market regime, fundamental score, and event penalty into one
  final confidence value.
- ``MarketEngine`` — the single public facade. Call
  ``MarketEngine.evaluate(symbol, technical_score, ...)`` and
  receive one :class:`MarketIntelligence` object.

The Market Intelligence Engine never generates buy/sell signals.
It returns structured condition assessments for the Signal Engine
(NGSP-001 consumer) to use.

Future extension points
-----------------------
- Liquidity filter as an additional engine + confidence input.
- Institutional flow (FII/DII) as a regime component.
- Multi-timeframe regime (daily + weekly agreement).
- AI Strategy Optimizer feeding adaptive weights into
  ``ConfidenceConfig`` at runtime.
"""

from __future__ import annotations

from datetime import date
from typing import List, Optional

from event_engine import EventEngine
from fundamental_engine import FundamentalEngine
from market_config import DEFAULT_CONFIG, MarketConfig, RegimeConfig, ConfidenceConfig
from market_data import (
    EventDataProvider,
    FundamentalDataProvider,
    MarketDataProvider,
)
from market_models import (
    EventResult,
    FundamentalResult,
    MarketConfidence,
    MarketIntelligence,
    MarketRegime,
    MarketSnapshot,
    NiftyTrend,
    RegimeType,
)
from market_utils import clamp, scale_linear


# ---------------------------------------------------------------------------
# Regime Engine
# ---------------------------------------------------------------------------

class RegimeEngine:
    """Classifies market regime from broad market conditions.

    Composite ``market_score`` (0–100) is a weighted blend of:

    - Nifty trend component
    - India VIX component (lower VIX scores higher)
    - Sector relative strength component
    - Market breadth component
    """

    _TREND_SCORES = {
        NiftyTrend.STRONG_UP: 100.0,
        NiftyTrend.UP: 75.0,
        NiftyTrend.SIDEWAYS: 50.0,
        NiftyTrend.DOWN: 25.0,
        NiftyTrend.STRONG_DOWN: 0.0,
    }

    def __init__(self, config: RegimeConfig) -> None:
        config.validate()
        self._config = config

    def evaluate(self, snapshot: MarketSnapshot) -> MarketRegime:
        """Classify ``snapshot`` into a :class:`MarketRegime`.

        Raises
        ------
        ValueError
            On invalid inputs (negative VIX, breadth outside [0, 1]).
        """
        self._validate_snapshot(snapshot)
        cfg = self._config
        reasons: List[str] = []

        # --- Nifty trend component -------------------------------------
        trend_score = self._TREND_SCORES[snapshot.nifty_trend]
        if trend_score >= 75.0:
            reasons.append(f"Nifty trend {snapshot.nifty_trend.value} (supportive)")
        elif trend_score <= 25.0:
            reasons.append(f"Nifty trend {snapshot.nifty_trend.value} (hostile)")
        else:
            reasons.append(f"Nifty trend {snapshot.nifty_trend.value} (neutral)")

        # --- VIX component (inverted: low VIX = high score) --------------
        vix_score = scale_linear(
            snapshot.india_vix,
            in_low=cfg.vix_low,
            in_high=cfg.vix_high,
            out_low=100.0,
            out_high=0.0,
        )
        if snapshot.india_vix <= cfg.vix_low:
            reasons.append(f"Low India VIX ({snapshot.india_vix:.1f}) — calm market")
        elif snapshot.india_vix >= cfg.vix_extreme:
            reasons.append(
                f"Extreme India VIX ({snapshot.india_vix:.1f}) — crisis conditions"
            )
        elif snapshot.india_vix >= cfg.vix_high:
            reasons.append(f"High India VIX ({snapshot.india_vix:.1f}) — stressed market")
        else:
            reasons.append(f"India VIX {snapshot.india_vix:.1f} — moderate volatility")

        # --- Sector relative strength (-10%..+10% mapped to 0..100) ------
        rs_score = scale_linear(
            snapshot.sector_relative_strength,
            in_low=-10.0,
            in_high=10.0,
        )
        if snapshot.sector_relative_strength > 0:
            reasons.append(
                f"Sector outperforming Nifty by "
                f"{snapshot.sector_relative_strength:.1f}%"
            )
        elif snapshot.sector_relative_strength < 0:
            reasons.append(
                f"Sector underperforming Nifty by "
                f"{abs(snapshot.sector_relative_strength):.1f}%"
            )
        else:
            reasons.append("Sector in line with Nifty")

        # --- Breadth (fraction advancing, 0..1 mapped to 0..100) ---------
        breadth_score = scale_linear(snapshot.market_breadth, in_low=0.0, in_high=1.0)
        if snapshot.market_breadth >= 0.6:
            reasons.append(
                f"Healthy breadth ({snapshot.market_breadth:.0%} advancing)"
            )
        elif snapshot.market_breadth <= 0.4:
            reasons.append(f"Weak breadth ({snapshot.market_breadth:.0%} advancing)")
        else:
            reasons.append(f"Balanced breadth ({snapshot.market_breadth:.0%} advancing)")

        # --- Composite ----------------------------------------------------
        market_score = (
            trend_score * cfg.weight_nifty_trend
            + vix_score * cfg.weight_vix
            + rs_score * cfg.weight_sector_rs
            + breadth_score * cfg.weight_breadth
        )
        market_score = clamp(market_score, 0.0, 100.0)

        regime, multiplier = self._classify(market_score, snapshot.india_vix, reasons)
        confidence = self._classification_confidence(market_score)

        reasons.append(
            f"Composite market score {market_score:.1f}/100 -> {regime.value} regime "
            f"(multiplier x{multiplier:.2f})"
        )

        return MarketRegime(
            market_score=round(market_score, 2),
            regime=regime,
            multiplier=multiplier,
            reasons=reasons,
            confidence=round(confidence, 2),
        )

    # ------------------------------------------------------------------

    def _classify(
        self,
        market_score: float,
        vix: float,
        reasons: List[str],
    ):
        cfg = self._config
        if market_score >= cfg.bull_threshold:
            regime, multiplier = RegimeType.BULL, cfg.bull_multiplier
        elif market_score <= cfg.bear_threshold:
            regime, multiplier = RegimeType.BEAR, cfg.bear_multiplier
        else:
            regime, multiplier = RegimeType.NEUTRAL, cfg.neutral_multiplier

        # Extreme VIX overrides the regime multiplier (risk-off override).
        if vix >= cfg.vix_extreme:
            multiplier = min(multiplier, cfg.extreme_vix_multiplier)
            reasons.append(
                f"Extreme-VIX override: multiplier capped at "
                f"x{cfg.extreme_vix_multiplier:.2f}"
            )
        return regime, multiplier

    def _classification_confidence(self, market_score: float) -> float:
        """Distance from the nearest regime boundary, scaled to 0–100."""
        cfg = self._config
        distance = min(
            abs(market_score - cfg.bull_threshold),
            abs(market_score - cfg.bear_threshold),
        )
        # Within 0 of a boundary -> 0 confidence; >= 20 points away -> 100.
        return scale_linear(distance, in_low=0.0, in_high=20.0)

    @staticmethod
    def _validate_snapshot(snapshot: MarketSnapshot) -> None:
        if snapshot.india_vix < 0:
            raise ValueError(f"India VIX cannot be negative: {snapshot.india_vix}")
        if not 0.0 <= snapshot.market_breadth <= 1.0:
            raise ValueError(
                f"market_breadth must be in [0, 1]: {snapshot.market_breadth}"
            )
        if not isinstance(snapshot.nifty_trend, NiftyTrend):
            raise ValueError(f"Invalid nifty_trend: {snapshot.nifty_trend!r}")


# ---------------------------------------------------------------------------
# Confidence Calculator
# ---------------------------------------------------------------------------

class ConfidenceCalculator:
    """Blends all intelligence layers into a final confidence score."""

    def __init__(self, config: ConfidenceConfig) -> None:
        config.validate()
        self._config = config

    def calculate(
        self,
        technical_score: float,
        regime: MarketRegime,
        fundamentals: FundamentalResult,
        events: EventResult,
    ) -> MarketConfidence:
        """Compute final confidence.

        Parameters
        ----------
        technical_score:
            Conviction score from the NGSP-001 Technical Intelligence
            Engine, on a 0–100 scale.
        regime, fundamentals, events:
            Outputs from the respective engines.

        Raises
        ------
        ValueError
            If ``technical_score`` is outside [0, 100].
        """
        if not 0.0 <= technical_score <= 100.0:
            raise ValueError(
                f"technical_score must be in [0, 100]: {technical_score}"
            )

        cfg = self._config
        base = (
            technical_score * cfg.weight_technical
            + regime.market_score * cfg.weight_market
            + fundamentals.score * cfg.weight_fundamental
        )
        raw = (base - events.penalty) * regime.multiplier
        final = clamp(raw, cfg.min_confidence, cfg.max_confidence)

        reasons = [
            (
                f"Blend: technical {technical_score:.1f} x {cfg.weight_technical:g} "
                f"+ market {regime.market_score:.1f} x {cfg.weight_market:g} "
                f"+ fundamental {fundamentals.score:.1f} x {cfg.weight_fundamental:g} "
                f"= {base:.1f}"
            ),
        ]
        if events.penalty > 0:
            reasons.append(f"Event penalty -{events.penalty:g}")
        reasons.append(
            f"Regime multiplier x{regime.multiplier:.2f} -> "
            f"final confidence {final:.1f}/100"
        )

        return MarketConfidence(
            technical_score=round(technical_score, 2),
            market_score=regime.market_score,
            fundamental_score=fundamentals.score,
            event_penalty=events.penalty,
            final_confidence=round(final, 2),
            multiplier=regime.multiplier,
            reasons=reasons,
        )


# ---------------------------------------------------------------------------
# Public facade
# ---------------------------------------------------------------------------

class MarketEngine:
    """Single public entry point for NGSP-002.

    Usage
    -----
    ::

        engine = MarketEngine(
            market_provider=...,       # MarketDataProvider
            fundamental_provider=...,  # FundamentalDataProvider
            event_provider=...,        # EventDataProvider
            config=DEFAULT_CONFIG,     # optional
        )
        intel = engine.evaluate("RELIANCE", technical_score=72.0)

        if intel.signal_allowed:
            conviction = intel.confidence.final_confidence

    The Signal Engine (NGSP-001 consumer) remains the sole producer
    of buy/sell signals; this engine only qualifies conditions.
    """

    def __init__(
        self,
        market_provider: MarketDataProvider,
        fundamental_provider: FundamentalDataProvider,
        event_provider: EventDataProvider,
        config: MarketConfig = DEFAULT_CONFIG,
    ) -> None:
        self._config = config.validate()
        self._market_provider = market_provider
        self._fundamental_provider = fundamental_provider
        self._event_provider = event_provider

        self._regime_engine = RegimeEngine(config.regime)
        self._fundamental_engine = FundamentalEngine(config.fundamental)
        self._event_engine = EventEngine(config.event)
        self._confidence_calculator = ConfidenceCalculator(config.confidence)

    def evaluate(
        self,
        symbol: str,
        technical_score: float,
        sector: Optional[str] = None,
        today: Optional[date] = None,
    ) -> MarketIntelligence:
        """Run all intelligence layers and return one result object.

        Parameters
        ----------
        symbol:
            NSE symbol under evaluation.
        technical_score:
            Conviction score from NGSP-001 (0–100).
        sector:
            Optional sector for relative-strength context.
        today:
            Injectable date for deterministic tests/backtests.

        Returns
        -------
        MarketIntelligence
            Regime + fundamentals + events + blended confidence, with
            structured explanations at every layer.
        """
        if not symbol or not symbol.strip():
            raise ValueError("symbol must be a non-empty string")

        snapshot = self._market_provider.get_market_snapshot(sector=sector)
        regime = self._regime_engine.evaluate(snapshot)

        fundamentals = self._fundamental_engine.evaluate(
            self._fundamental_provider.get_fundamentals(symbol),
            today=today,
        )
        events = self._event_engine.evaluate(
            self._event_provider.get_upcoming_events(symbol),
            today=today,
        )
        confidence = self._confidence_calculator.calculate(
            technical_score=technical_score,
            regime=regime,
            fundamentals=fundamentals,
            events=events,
        )

        return MarketIntelligence(
            symbol=symbol,
            regime=regime,
            fundamentals=fundamentals,
            events=events,
            confidence=confidence,
        )
