"""
NGSP-002 — Market Intelligence Engine: Unit Tests
==================================================

Run with::

    python -m unittest test_market_engine -v
"""

from __future__ import annotations

import unittest
from datetime import date, timedelta

from event_engine import EventEngine
from fundamental_engine import FundamentalEngine
from market_config import (
    DEFAULT_CONFIG,
    ConfidenceConfig,
    EventConfig,
    FundamentalConfig,
    MarketConfig,
    RegimeConfig,
)
from market_data import StaticDataProvider
from market_engine import ConfidenceCalculator, MarketEngine, RegimeEngine
from market_models import (
    EventType,
    FundamentalSnapshot,
    MarketSnapshot,
    NiftyTrend,
    RegimeType,
    RiskLevel,
    UpcomingEvent,
)
from market_utils import clamp, days_until, is_stale, scale_linear

TODAY = date(2026, 7, 2)


def bull_snapshot() -> MarketSnapshot:
    return MarketSnapshot(
        nifty_trend=NiftyTrend.STRONG_UP,
        india_vix=11.0,
        sector_relative_strength=4.0,
        market_breadth=0.72,
    )


def bear_snapshot() -> MarketSnapshot:
    return MarketSnapshot(
        nifty_trend=NiftyTrend.STRONG_DOWN,
        india_vix=24.0,
        sector_relative_strength=-5.0,
        market_breadth=0.25,
    )


def neutral_snapshot() -> MarketSnapshot:
    return MarketSnapshot(
        nifty_trend=NiftyTrend.SIDEWAYS,
        india_vix=16.0,
        sector_relative_strength=0.0,
        market_breadth=0.50,
    )


def good_fundamentals(symbol: str = "TEST") -> FundamentalSnapshot:
    return FundamentalSnapshot(
        symbol=symbol,
        roe=22.0,
        debt_to_equity=0.4,
        eps_growth=18.0,
        sales_growth=14.0,
        profit_growth=20.0,
        last_updated=TODAY - timedelta(days=5),
    )


def poor_fundamentals(symbol: str = "TEST") -> FundamentalSnapshot:
    return FundamentalSnapshot(
        symbol=symbol,
        roe=6.0,
        debt_to_equity=2.5,
        eps_growth=-4.0,
        sales_growth=1.0,
        profit_growth=-8.0,
        last_updated=TODAY - timedelta(days=5),
    )


# ---------------------------------------------------------------------------
# Utils
# ---------------------------------------------------------------------------

class TestUtils(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp(5, 0, 10), 5)
        self.assertEqual(clamp(-1, 0, 10), 0)
        self.assertEqual(clamp(11, 0, 10), 10)

    def test_clamp_inverted_bounds_raises(self):
        with self.assertRaises(ValueError):
            clamp(5, 10, 0)

    def test_scale_linear_and_inversion(self):
        self.assertAlmostEqual(scale_linear(5, 0, 10), 50.0)
        self.assertAlmostEqual(scale_linear(0, 0, 10, 100, 0), 100.0)
        self.assertAlmostEqual(scale_linear(10, 0, 10, 100, 0), 0.0)
        # out-of-range clamps
        self.assertAlmostEqual(scale_linear(-5, 0, 10), 0.0)
        self.assertAlmostEqual(scale_linear(15, 0, 10), 100.0)

    def test_scale_linear_zero_range_raises(self):
        with self.assertRaises(ValueError):
            scale_linear(1, 5, 5)

    def test_days_until(self):
        self.assertEqual(days_until(TODAY + timedelta(days=3), today=TODAY), 3)
        self.assertEqual(days_until(TODAY - timedelta(days=2), today=TODAY), -2)

    def test_is_stale(self):
        self.assertTrue(is_stale(None, 30, today=TODAY))
        self.assertTrue(is_stale(TODAY - timedelta(days=45), 30, today=TODAY))
        self.assertFalse(is_stale(TODAY - timedelta(days=10), 30, today=TODAY))


# ---------------------------------------------------------------------------
# Config validation
# ---------------------------------------------------------------------------

class TestConfig(unittest.TestCase):
    def test_default_config_valid(self):
        DEFAULT_CONFIG.validate()

    def test_bad_regime_weights_raise(self):
        with self.assertRaises(ValueError):
            RegimeConfig(weight_nifty_trend=0.9).validate()

    def test_bad_thresholds_raise(self):
        with self.assertRaises(ValueError):
            RegimeConfig(bull_threshold=30.0, bear_threshold=40.0).validate()

    def test_bad_fundamental_points_raise(self):
        with self.assertRaises(ValueError):
            FundamentalConfig(points_roe=90.0).validate()

    def test_bad_event_windows_raise(self):
        with self.assertRaises(ValueError):
            EventConfig(high_risk_days=10, medium_risk_days=5).validate()

    def test_bad_confidence_weights_raise(self):
        with self.assertRaises(ValueError):
            ConfidenceConfig(weight_technical=0.9, weight_market=0.9).validate()

    def test_from_dict_roundtrip(self):
        cfg = MarketConfig.from_dict({"regime": {"vix_low": 12.0}})
        self.assertEqual(cfg.regime.vix_low, 12.0)
        self.assertEqual(cfg.fundamental.min_roe, 15.0)  # default preserved


# ---------------------------------------------------------------------------
# Regime Engine
# ---------------------------------------------------------------------------

class TestRegimeEngine(unittest.TestCase):
    def setUp(self):
        self.engine = RegimeEngine(DEFAULT_CONFIG.regime)

    def test_bull_market(self):
        regime = self.engine.evaluate(bull_snapshot())
        self.assertEqual(regime.regime, RegimeType.BULL)
        self.assertGreaterEqual(regime.market_score, DEFAULT_CONFIG.regime.bull_threshold)
        self.assertEqual(regime.multiplier, DEFAULT_CONFIG.regime.bull_multiplier)
        self.assertTrue(any("Low India VIX" in r for r in regime.reasons))

    def test_bear_market(self):
        regime = self.engine.evaluate(bear_snapshot())
        self.assertEqual(regime.regime, RegimeType.BEAR)
        self.assertLessEqual(regime.market_score, DEFAULT_CONFIG.regime.bear_threshold)
        self.assertEqual(regime.multiplier, DEFAULT_CONFIG.regime.bear_multiplier)

    def test_neutral_market(self):
        regime = self.engine.evaluate(neutral_snapshot())
        self.assertEqual(regime.regime, RegimeType.NEUTRAL)
        self.assertEqual(regime.multiplier, DEFAULT_CONFIG.regime.neutral_multiplier)

    def test_extreme_vix_caps_multiplier(self):
        snap = MarketSnapshot(
            nifty_trend=NiftyTrend.STRONG_UP,
            india_vix=32.0,
            sector_relative_strength=6.0,
            market_breadth=0.8,
        )
        regime = self.engine.evaluate(snap)
        self.assertLessEqual(
            regime.multiplier, DEFAULT_CONFIG.regime.extreme_vix_multiplier
        )
        self.assertTrue(any("Extreme" in r for r in regime.reasons))

    def test_low_vix_scores_high_component(self):
        calm = self.engine.evaluate(bull_snapshot())
        stressed = self.engine.evaluate(
            MarketSnapshot(
                nifty_trend=NiftyTrend.STRONG_UP,
                india_vix=22.0,
                sector_relative_strength=4.0,
                market_breadth=0.72,
            )
        )
        self.assertGreater(calm.market_score, stressed.market_score)

    def test_reasons_always_present(self):
        regime = self.engine.evaluate(neutral_snapshot())
        self.assertGreaterEqual(len(regime.reasons), 4)

    def test_invalid_vix_raises(self):
        with self.assertRaises(ValueError):
            self.engine.evaluate(
                MarketSnapshot(
                    nifty_trend=NiftyTrend.UP,
                    india_vix=-1.0,
                    sector_relative_strength=0.0,
                    market_breadth=0.5,
                )
            )

    def test_invalid_breadth_raises(self):
        with self.assertRaises(ValueError):
            self.engine.evaluate(
                MarketSnapshot(
                    nifty_trend=NiftyTrend.UP,
                    india_vix=15.0,
                    sector_relative_strength=0.0,
                    market_breadth=1.5,
                )
            )


# ---------------------------------------------------------------------------
# Fundamental Engine
# ---------------------------------------------------------------------------

class TestFundamentalEngine(unittest.TestCase):
    def setUp(self):
        self.engine = FundamentalEngine(DEFAULT_CONFIG.fundamental)

    def test_good_fundamentals_pass(self):
        result = self.engine.evaluate(good_fundamentals(), today=TODAY)
        self.assertEqual(result.score, 100.0)
        self.assertTrue(result.passed)
        self.assertFalse(result.is_stale)
        self.assertTrue(any("ROE" in r for r in result.reasons))

    def test_poor_fundamentals_fail(self):
        result = self.engine.evaluate(poor_fundamentals(), today=TODAY)
        self.assertEqual(result.score, 0.0)
        self.assertFalse(result.passed)

    def test_partial_fundamentals(self):
        snap = FundamentalSnapshot(
            symbol="TEST",
            roe=22.0,                # +25
            debt_to_equity=0.4,      # +20
            eps_growth=18.0,         # +20
            sales_growth=2.0,        # fail
            profit_growth=-1.0,      # fail
            last_updated=TODAY - timedelta(days=5),
        )
        result = self.engine.evaluate(snap, today=TODAY)
        self.assertEqual(result.score, 65.0)
        self.assertTrue(result.passed)

    def test_missing_metrics_score_zero(self):
        snap = FundamentalSnapshot(
            symbol="TEST",
            roe=22.0,
            last_updated=TODAY - timedelta(days=5),
        )
        result = self.engine.evaluate(snap, today=TODAY)
        self.assertEqual(result.score, 25.0)
        self.assertFalse(result.passed)
        self.assertTrue(any("unavailable" in r for r in result.reasons))

    def test_stale_data_flagged_and_passes_by_default(self):
        snap = good_fundamentals()
        stale = FundamentalSnapshot(
            symbol=snap.symbol,
            roe=snap.roe,
            debt_to_equity=snap.debt_to_equity,
            eps_growth=snap.eps_growth,
            sales_growth=snap.sales_growth,
            profit_growth=snap.profit_growth,
            last_updated=TODAY - timedelta(days=60),
        )
        result = self.engine.evaluate(stale, today=TODAY)
        self.assertTrue(result.is_stale)
        self.assertTrue(result.passed)

    def test_stale_data_fails_when_configured(self):
        cfg = FundamentalConfig(stale_data_passes=False)
        engine = FundamentalEngine(cfg)
        stale = FundamentalSnapshot(
            symbol="TEST",
            roe=22.0,
            debt_to_equity=0.4,
            eps_growth=18.0,
            sales_growth=14.0,
            profit_growth=20.0,
            last_updated=TODAY - timedelta(days=60),
        )
        result = engine.evaluate(stale, today=TODAY)
        self.assertTrue(result.is_stale)
        self.assertFalse(result.passed)

    def test_no_data_at_all(self):
        result = self.engine.evaluate(None, today=TODAY)
        self.assertEqual(result.score, 0.0)
        self.assertTrue(result.is_stale)
        self.assertTrue(result.passed)  # stale_data_passes default True


# ---------------------------------------------------------------------------
# Event Engine
# ---------------------------------------------------------------------------

class TestEventEngine(unittest.TestCase):
    def setUp(self):
        self.engine = EventEngine(DEFAULT_CONFIG.event)

    def _event(self, days: int, etype: EventType = EventType.EARNINGS) -> UpcomingEvent:
        return UpcomingEvent(
            symbol="TEST", event_type=etype, event_date=TODAY + timedelta(days=days)
        )

    def test_no_events(self):
        result = self.engine.evaluate([], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.NONE)
        self.assertFalse(result.suppress_signal)
        self.assertIsNone(result.days_remaining)
        self.assertIsNone(result.event_type)
        self.assertEqual(result.penalty, 0.0)

    def test_earnings_high_risk_suppresses(self):
        result = self.engine.evaluate([self._event(1)], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.HIGH)
        self.assertTrue(result.suppress_signal)
        self.assertEqual(result.days_remaining, 1)
        self.assertEqual(result.event_type, EventType.EARNINGS)
        self.assertEqual(result.penalty, DEFAULT_CONFIG.event.penalty_high)

    def test_medium_risk(self):
        result = self.engine.evaluate([self._event(4)], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.MEDIUM)
        self.assertFalse(result.suppress_signal)
        self.assertEqual(result.penalty, DEFAULT_CONFIG.event.penalty_medium)

    def test_low_risk(self):
        result = self.engine.evaluate(
            [self._event(8, EventType.DIVIDEND)], today=TODAY
        )
        self.assertEqual(result.risk_level, RiskLevel.LOW)
        self.assertEqual(result.event_type, EventType.DIVIDEND)
        self.assertEqual(result.penalty, DEFAULT_CONFIG.event.penalty_low)

    def test_far_event_is_no_risk(self):
        result = self.engine.evaluate([self._event(25)], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.NONE)
        self.assertEqual(result.penalty, 0.0)

    def test_past_events_ignored(self):
        result = self.engine.evaluate([self._event(-3)], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.NONE)

    def test_nearest_event_wins(self):
        result = self.engine.evaluate(
            [self._event(8, EventType.DIVIDEND), self._event(1, EventType.EARNINGS)],
            today=TODAY,
        )
        self.assertEqual(result.event_type, EventType.EARNINGS)
        self.assertEqual(result.days_remaining, 1)
        self.assertTrue(any("DIVIDEND" in r for r in result.reasons))

    def test_suppression_disabled_by_config(self):
        engine = EventEngine(EventConfig(suppress_on_high_risk=False))
        result = engine.evaluate([self._event(0)], today=TODAY)
        self.assertEqual(result.risk_level, RiskLevel.HIGH)
        self.assertFalse(result.suppress_signal)


# ---------------------------------------------------------------------------
# Confidence Calculator
# ---------------------------------------------------------------------------

class TestConfidenceCalculator(unittest.TestCase):
    def setUp(self):
        self.calc = ConfidenceCalculator(DEFAULT_CONFIG.confidence)
        self.regime_engine = RegimeEngine(DEFAULT_CONFIG.regime)
        self.fund_engine = FundamentalEngine(DEFAULT_CONFIG.fundamental)
        self.event_engine = EventEngine(DEFAULT_CONFIG.event)

    def test_confidence_blend_bull_no_event(self):
        regime = self.regime_engine.evaluate(bull_snapshot())
        fund = self.fund_engine.evaluate(good_fundamentals(), today=TODAY)
        events = self.event_engine.evaluate([], today=TODAY)

        result = self.calc.calculate(80.0, regime, fund, events)

        expected_base = (
            80.0 * DEFAULT_CONFIG.confidence.weight_technical
            + regime.market_score * DEFAULT_CONFIG.confidence.weight_market
            + fund.score * DEFAULT_CONFIG.confidence.weight_fundamental
        )
        expected = min(expected_base * regime.multiplier, 100.0)
        self.assertAlmostEqual(result.final_confidence, round(expected, 2), places=2)
        self.assertEqual(result.event_penalty, 0.0)

    def test_event_penalty_reduces_confidence(self):
        regime = self.regime_engine.evaluate(neutral_snapshot())
        fund = self.fund_engine.evaluate(good_fundamentals(), today=TODAY)
        no_event = self.event_engine.evaluate([], today=TODAY)
        with_event = self.event_engine.evaluate(
            [UpcomingEvent("TEST", EventType.EARNINGS, TODAY + timedelta(days=1))],
            today=TODAY,
        )
        clean = self.calc.calculate(70.0, regime, fund, no_event)
        penalised = self.calc.calculate(70.0, regime, fund, with_event)
        self.assertLess(penalised.final_confidence, clean.final_confidence)

    def test_bear_multiplier_reduces_confidence(self):
        fund = self.fund_engine.evaluate(good_fundamentals(), today=TODAY)
        events = self.event_engine.evaluate([], today=TODAY)
        bull = self.calc.calculate(
            70.0, self.regime_engine.evaluate(bull_snapshot()), fund, events
        )
        bear = self.calc.calculate(
            70.0, self.regime_engine.evaluate(bear_snapshot()), fund, events
        )
        self.assertLess(bear.final_confidence, bull.final_confidence)

    def test_confidence_clamped_to_bounds(self):
        regime = self.regime_engine.evaluate(bear_snapshot())
        fund = self.fund_engine.evaluate(poor_fundamentals(), today=TODAY)
        events = self.event_engine.evaluate(
            [UpcomingEvent("TEST", EventType.EARNINGS, TODAY)], today=TODAY
        )
        result = self.calc.calculate(0.0, regime, fund, events)
        self.assertGreaterEqual(
            result.final_confidence, DEFAULT_CONFIG.confidence.min_confidence
        )

    def test_invalid_technical_score_raises(self):
        regime = self.regime_engine.evaluate(neutral_snapshot())
        fund = self.fund_engine.evaluate(good_fundamentals(), today=TODAY)
        events = self.event_engine.evaluate([], today=TODAY)
        for bad in (-5.0, 105.0):
            with self.assertRaises(ValueError):
                self.calc.calculate(bad, regime, fund, events)


# ---------------------------------------------------------------------------
# MarketEngine facade (integration)
# ---------------------------------------------------------------------------

class TestMarketEngine(unittest.TestCase):
    def _engine(self, snapshot, fundamentals=None, events=None) -> MarketEngine:
        provider = StaticDataProvider(
            market_snapshot=snapshot,
            fundamentals=fundamentals or {},
            events=events or {},
        )
        return MarketEngine(
            market_provider=provider,
            fundamental_provider=provider,
            event_provider=provider,
        )

    def test_end_to_end_bull_quality_stock(self):
        engine = self._engine(
            bull_snapshot(), fundamentals={"RELIANCE": good_fundamentals("RELIANCE")}
        )
        intel = engine.evaluate("RELIANCE", technical_score=75.0, today=TODAY)

        self.assertEqual(intel.symbol, "RELIANCE")
        self.assertEqual(intel.regime.regime, RegimeType.BULL)
        self.assertTrue(intel.fundamentals.passed)
        self.assertEqual(intel.events.risk_level, RiskLevel.NONE)
        self.assertTrue(intel.signal_allowed)
        self.assertGreater(intel.confidence.final_confidence, 70.0)
        self.assertGreater(len(intel.all_reasons()), 5)

    def test_end_to_end_earnings_suppression(self):
        events = {
            "TCS": [
                UpcomingEvent("TCS", EventType.EARNINGS, TODAY + timedelta(days=1))
            ]
        }
        engine = self._engine(
            bull_snapshot(),
            fundamentals={"TCS": good_fundamentals("TCS")},
            events=events,
        )
        intel = engine.evaluate("TCS", technical_score=85.0, today=TODAY)
        self.assertTrue(intel.events.suppress_signal)
        self.assertFalse(intel.signal_allowed)

    def test_end_to_end_poor_fundamentals_block(self):
        engine = self._engine(
            neutral_snapshot(), fundamentals={"WEAK": poor_fundamentals("WEAK")}
        )
        intel = engine.evaluate("WEAK", technical_score=90.0, today=TODAY)
        self.assertFalse(intel.fundamentals.passed)
        self.assertFalse(intel.signal_allowed)

    def test_to_dict_serialisable(self):
        engine = self._engine(
            bull_snapshot(), fundamentals={"INFY": good_fundamentals("INFY")}
        )
        intel = engine.evaluate("INFY", technical_score=60.0, today=TODAY)
        payload = intel.to_dict()
        self.assertIn("regime", payload)
        self.assertIn("confidence", payload)
        self.assertIn("signal_allowed", payload)

    def test_empty_symbol_raises(self):
        engine = self._engine(neutral_snapshot())
        with self.assertRaises(ValueError):
            engine.evaluate("  ", technical_score=50.0, today=TODAY)

    def test_custom_config_injection(self):
        cfg = MarketConfig.from_dict(
            {"confidence": {"weight_technical": 0.8,
                            "weight_market": 0.1,
                            "weight_fundamental": 0.1}}
        )
        provider = StaticDataProvider(
            market_snapshot=bull_snapshot(),
            fundamentals={"HDFC": good_fundamentals("HDFC")},
        )
        engine = MarketEngine(provider, provider, provider, config=cfg)
        intel = engine.evaluate("HDFC", technical_score=100.0, today=TODAY)
        self.assertGreater(intel.confidence.final_confidence, 90.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
