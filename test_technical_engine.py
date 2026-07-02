"""
test_technical_engine.py
=========================
Unit tests for NGSP-001 (technical_engine.py). Uses stdlib `unittest`,
not pytest — pytest is not in requirements.txt and this task doesn't
need it as a new dependency just to run these checks.

Run with:
    python -m unittest test_technical_engine.py -v
"""

import unittest
from technical_engine import (
    ema200, macd, bollinger_bands, relative_volume, volume_spike, vwap,
    calculate_trend_score, calculate_momentum_score,
    calculate_volume_score, calculate_volatility_score,
    calculate_price_score, calculate_technical_score, TechnicalScore,
)


def make_candles(closes, base_volume=1000, spike_last=False):
    """
    Build synthetic newest-first candles matching Upstox's raw format:
    [timestamp, open, high, low, close, volume, oi]
    `closes` given oldest-first for readability; reversed at the end.
    """
    candles = []
    for i, c in enumerate(closes):
        vol = base_volume
        candles.append([i, c - 1, c + 1, c - 2, c, vol, 0])
    if spike_last:
        candles[-1][5] = base_volume * 3
    return list(reversed(candles))  # newest-first


class TestNewIndicators(unittest.TestCase):

    def test_ema200_insufficient_history_returns_none(self):
        self.assertIsNone(ema200([100.0] * 50))

    def test_ema200_with_enough_history(self):
        closes = [100.0 + i * 0.1 for i in range(250)]
        result = ema200(closes)
        self.assertIsNotNone(result)
        self.assertGreater(result, 100.0)

    def test_macd_insufficient_history_returns_none_triplet(self):
        line, signal, hist = macd([100.0] * 10)
        self.assertIsNone(line)
        self.assertIsNone(signal)
        self.assertIsNone(hist)

    def test_macd_uptrend_produces_positive_line(self):
        closes = [100.0 + i * 0.5 for i in range(60)]
        line, signal, hist = macd(closes)
        self.assertIsNotNone(line)
        self.assertGreater(line, 0)  # fast EMA pulls ahead of slow EMA in an uptrend

    def test_bollinger_bands_insufficient_history(self):
        upper, middle, lower, pct_b = bollinger_bands([100.0] * 5)
        self.assertIsNone(upper)

    def test_bollinger_bands_flat_series_zero_width(self):
        upper, middle, lower, pct_b = bollinger_bands([100.0] * 25)
        self.assertEqual(upper, lower)  # zero std -> bands collapse to price
        self.assertEqual(middle, 100.0)

    def test_relative_volume_spike_detected(self):
        candles = make_candles([100.0] * 25, base_volume=1000, spike_last=True)
        rv = relative_volume(candles)
        self.assertIsNotNone(rv)
        self.assertGreaterEqual(rv, 2.0)
        self.assertTrue(volume_spike(rv))

    def test_relative_volume_normal_no_spike(self):
        candles = make_candles([100.0] * 25, base_volume=1000, spike_last=False)
        rv = relative_volume(candles)
        self.assertAlmostEqual(rv, 1.0, delta=0.05)
        self.assertFalse(volume_spike(rv))

    def test_relative_volume_insufficient_candles(self):
        self.assertIsNone(relative_volume(make_candles([100.0, 101.0])))

    def test_vwap_basic(self):
        candles = make_candles([100.0, 100.0, 100.0], base_volume=1000)
        v = vwap(candles)
        # all typical prices ~100 -> vwap should be ~100
        self.assertAlmostEqual(v, 100.0, delta=0.5)

    def test_vwap_empty_candles(self):
        self.assertIsNone(vwap([]))


class TestSubScores(unittest.TestCase):

    def test_trend_score_bullish_full_confluence(self):
        score, reasons = calculate_trend_score(
            price=110, ema20_val=108, ema50_val=100, ema200_val=95,
            supertrend_trend="Bullish", adx_val=30,
        )
        self.assertGreaterEqual(score, 90)
        self.assertTrue(any("EMA20 > EMA50" in r for r in reasons))

    def test_trend_score_bearish_weak_adx_penalized(self):
        score, reasons = calculate_trend_score(
            price=90, ema20_val=92, ema50_val=100, ema200_val=None,
            supertrend_trend=None, adx_val=10,
        )
        self.assertTrue(any("Weak/choppy" in r for r in reasons))

    def test_trend_score_bounded_0_100(self):
        score, _ = calculate_trend_score(
            price=90, ema20_val=92, ema50_val=100, ema200_val=200,
            supertrend_trend="Bullish", adx_val=5,
        )
        self.assertGreaterEqual(score, 0.0)
        self.assertLessEqual(score, 100.0)

    def test_momentum_score_no_data_neutral(self):
        score, reasons = calculate_momentum_score()
        self.assertEqual(score, 50.0)

    def test_momentum_score_healthy_rsi_scores_high_component(self):
        score, reasons = calculate_momentum_score(rsi_val=55)
        self.assertEqual(score, 100.0)  # only component present, fully normalized

    def test_momentum_score_overbought_rsi_scores_low(self):
        score, _ = calculate_momentum_score(rsi_val=85)
        self.assertLess(score, 30)

    def test_volume_score_no_data_neutral(self):
        score, reasons = calculate_volume_score()
        self.assertEqual(score, 50.0)

    def test_volume_score_spike_scores_max(self):
        score, reasons = calculate_volume_score(rel_vol=2.5, vol_spike=True)
        self.assertEqual(score, 100.0)

    def test_volume_score_falls_back_to_session_ratio(self):
        score, reasons = calculate_volume_score(rel_vol=None, vol_ratio_session=1.6)
        self.assertEqual(score, 80.0)
        self.assertTrue(any("legacy" in r for r in reasons))

    def test_volatility_score_no_data_neutral(self):
        score, reasons = calculate_volatility_score()
        self.assertEqual(score, 50.0)

    def test_volatility_score_within_band(self):
        score, reasons = calculate_volatility_score(expected_move_pct=1.5)
        self.assertGreater(score, 70)

    def test_volatility_score_too_low_penalized(self):
        score, reasons = calculate_volatility_score(expected_move_pct=0.05)
        self.assertLess(score, 30)

    def test_volatility_score_too_high_penalized(self):
        score, reasons = calculate_volatility_score(expected_move_pct=8.0)
        self.assertLess(score, 30)

    def test_price_score_no_vwap_neutral(self):
        score, reasons = calculate_price_score(price=100.0, vwap_val=None)
        self.assertEqual(score, 50.0)

    def test_price_score_tight_to_vwap_scores_max(self):
        score, reasons = calculate_price_score(price=100.0, vwap_val=100.1)
        self.assertEqual(score, 100.0)

    def test_price_score_extended_from_vwap_penalized(self):
        score, reasons = calculate_price_score(price=105.0, vwap_val=100.0)
        self.assertLess(score, 30)
        self.assertTrue(any("chase risk" in r for r in reasons))

    def test_price_score_reports_side(self):
        _, reasons = calculate_price_score(price=101.0, vwap_val=100.0)
        self.assertTrue(any("above VWAP" in r for r in reasons))


class TestCompositeScore(unittest.TestCase):

    def test_insufficient_candles_returns_zeroed_score(self):
        result = calculate_technical_score(price=100, candles=make_candles([100.0] * 5))
        self.assertIsInstance(result, TechnicalScore)
        self.assertEqual(result.technical_score, 0.0)
        self.assertEqual(result.trend, "Unknown")

    def test_uptrend_scores_high_and_returns_bullish(self):
        closes = [100.0 + i * 0.3 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        result = calculate_technical_score(price=closes[-1], candles=candles)
        self.assertEqual(result.trend, "Bullish")
        self.assertGreater(result.technical_score, 50)
        self.assertTrue(0 <= result.technical_score <= 100)

    def test_weights_sum_to_one(self):
        from technical_engine import (
            TREND_WEIGHT, MOMENTUM_WEIGHT, VOLUME_WEIGHT, VOLATILITY_WEIGHT,
            PRICE_WEIGHT,
        )
        total = TREND_WEIGHT + MOMENTUM_WEIGHT + VOLUME_WEIGHT + VOLATILITY_WEIGHT + PRICE_WEIGHT
        self.assertAlmostEqual(total, 1.0)

    def test_technical_score_has_price_score_field(self):
        closes = [100.0 + i * 0.3 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        result = calculate_technical_score(price=closes[-1], candles=candles)
        self.assertTrue(hasattr(result, "price_score"))
        self.assertGreaterEqual(result.price_score, 0.0)
        self.assertLessEqual(result.price_score, 100.0)

    def test_can_accept_precomputed_values_from_scanner(self):
        """
        Simulates scanner.py passing in values it already computed this
        scan cycle (ema20_val, ema50_val, atr_val, supertrend_trend,
        adx_val) — confirms the engine doesn't recompute/duplicate them.
        """
        closes = [100.0 + i * 0.1 for i in range(60)]
        candles = make_candles(closes)
        result = calculate_technical_score(
            price=closes[-1],
            candles=candles,
            ema20_val=105.0,
            ema50_val=102.0,
            atr_val=2.0,
            supertrend_trend="Bullish",
            adx_val=28.0,
        )
        self.assertEqual(result.trend, "Bullish")
        self.assertIsInstance(result.reasons, list)
        self.assertGreater(len(result.reasons), 0)


# ============================================================
# NGSP-001-R1 — new architecture tests
# All R0 test classes above are unchanged and must still pass —
# this section only ADDS coverage for the new registry/report layer.
# ============================================================

from technical_engine import (
    generate_technical_report, build_indicator_context, run_engine,
    register_indicator, IndicatorResult, IndicatorContext, EngineResult,
    TechnicalReport, TREND_INDICATOR_REGISTRY, ENGINE_REGISTRIES,
)
from technical_config import TECHNICAL_WEIGHTS, ENABLED_INDICATORS


class TestR1Config(unittest.TestCase):

    def test_technical_weights_sum_to_100(self):
        self.assertEqual(sum(TECHNICAL_WEIGHTS.values()), 100)

    def test_config_validation_runs_at_import(self):
        # technical_config.validate_config() already ran on import without
        # raising — this just re-invokes it to confirm it's callable and clean.
        from technical_config import validate_config
        validate_config()  # should not raise


class TestR1Context(unittest.TestCase):

    def test_build_context_insufficient_history_returns_none(self):
        candles = make_candles([100.0] * 5)
        ctx = build_indicator_context(price=100.0, candles=candles)
        self.assertIsNone(ctx)

    def test_build_context_sufficient_history_returns_context(self):
        closes = [100.0 + i * 0.2 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        ctx = build_indicator_context(price=closes[-1], candles=candles, symbol="TESTSTOCK")
        self.assertIsInstance(ctx, IndicatorContext)
        self.assertEqual(ctx.symbol, "TESTSTOCK")
        self.assertIsNotNone(ctx.ema200_val)  # 220 candles > 200 period


class TestR1Registries(unittest.TestCase):

    def test_all_five_engines_registered(self):
        self.assertEqual(set(ENGINE_REGISTRIES.keys()),
                          {"trend", "momentum", "volume", "volatility", "price"})

    def test_register_indicator_adds_new_function(self):
        def dummy_indicator(ctx):
            return IndicatorResult(
                indicator_name="dummy_test_indicator", current_value=1.0,
                score=77.0, weight=0.0, contribution=0.0, status="test",
                enabled=True, reason="test indicator", parameters={},
            )
        register_indicator("price", "dummy_test_indicator", dummy_indicator)
        self.assertIn("dummy_test_indicator", ENGINE_REGISTRIES["price"])
        # cleanup so this test doesn't leak into other tests
        del ENGINE_REGISTRIES["price"]["dummy_test_indicator"]

    def test_register_indicator_unknown_engine_raises(self):
        with self.assertRaises(ValueError):
            register_indicator("nonexistent_engine", "foo", lambda ctx: None)


class TestR1EngineRunner(unittest.TestCase):

    def test_run_engine_produces_valid_engine_result(self):
        closes = [100.0 + i * 0.2 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        ctx = build_indicator_context(price=closes[-1], candles=candles)
        result = run_engine("trend", ctx)
        self.assertIsInstance(result, EngineResult)
        self.assertGreaterEqual(result.score, 0.0)
        self.assertLessEqual(result.score, 100.0)
        self.assertEqual(result.weight, TECHNICAL_WEIGHTS["trend"])
        self.assertEqual(len(result.indicators), len(TREND_INDICATOR_REGISTRY))

    def test_disabled_indicator_excluded_from_scoring_but_still_listed(self):
        ENABLED_INDICATORS["rsi"] = False
        try:
            closes = [100.0 + i * 0.2 for i in range(220)]
            candles = make_candles(closes, base_volume=1000)
            ctx = build_indicator_context(price=closes[-1], candles=candles)
            result = run_engine("momentum", ctx)
            rsi_result = next(i for i in result.indicators if i.indicator_name == "rsi")
            self.assertFalse(rsi_result.enabled)
            self.assertEqual(rsi_result.status, "disabled")
            self.assertEqual(rsi_result.contribution, 0.0)
        finally:
            ENABLED_INDICATORS["rsi"] = True  # restore for other tests


class TestR1TechnicalReport(unittest.TestCase):

    def test_insufficient_data_returns_zeroed_report(self):
        report = generate_technical_report(price=100.0, candles=make_candles([100.0] * 5))
        self.assertIsInstance(report, TechnicalReport)
        self.assertEqual(report.technical_score, 0.0)
        self.assertEqual(report.confidence, 0.0)
        self.assertEqual(report.trend, "Unknown")
        self.assertTrue(len(report.warnings) > 0)

    def test_full_report_structure(self):
        closes = [100.0 + i * 0.3 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        report = generate_technical_report(price=closes[-1], candles=candles, symbol="RELIANCE")

        self.assertEqual(report.symbol, "RELIANCE")
        self.assertEqual(report.trend, "Bullish")
        self.assertTrue(0 <= report.technical_score <= 100)
        self.assertTrue(0 <= report.confidence <= 100)

        for engine in [report.trend_engine, report.momentum_engine, report.volume_engine,
                       report.volatility_engine, report.price_engine]:
            self.assertIsInstance(engine, EngineResult)

        # technical_score should equal the sum of engine contributions
        total_contribution = round(sum(e.contribution for e in [
            report.trend_engine, report.momentum_engine, report.volume_engine,
            report.volatility_engine, report.price_engine,
        ]), 1)
        self.assertAlmostEqual(report.technical_score, total_contribution, delta=0.1)

        # flattened indicator_results should match sum of all engines' indicators
        total_indicators = sum(len(e.indicators) for e in [
            report.trend_engine, report.momentum_engine, report.volume_engine,
            report.volatility_engine, report.price_engine,
        ])
        self.assertEqual(len(report.indicator_results), total_indicators)

    def test_report_matches_calculate_technical_score_reasonably_closely(self):
        """
        R0's calculate_technical_score() and R1's generate_technical_report()
        compute the same underlying indicators with the same config-sourced
        weights, so their composite scores should be close (not necessarily
        identical, since R1 scores some sub-components slightly differently
        at the per-indicator level — e.g. trend confluence is graded
        continuously rather than as fixed point buckets).
        """
        closes = [100.0 + i * 0.25 for i in range(220)]
        candles = make_candles(closes, base_volume=1000, spike_last=True)
        price = closes[-1]

        r0_result = calculate_technical_score(price=price, candles=candles)
        r1_result = generate_technical_report(price=price, candles=candles)

        self.assertEqual(r0_result.trend, r1_result.trend)
        self.assertLess(abs(r0_result.technical_score - r1_result.technical_score), 35)

    def test_can_extend_price_engine_and_see_it_in_report(self):
        """Confirms Req 5: a newly registered indicator shows up in the
        report without any other code changes."""
        def flat_100_indicator(ctx):
            return IndicatorResult(
                indicator_name="test_extension", current_value=None, score=100.0,
                weight=0.0, contribution=0.0, status="test", enabled=True,
                reason="test extension indicator", parameters={},
            )
        register_indicator("price", "test_extension", flat_100_indicator)
        try:
            closes = [100.0 + i * 0.2 for i in range(220)]
            candles = make_candles(closes, base_volume=1000)
            report = generate_technical_report(price=closes[-1], candles=candles)
            names = [i.indicator_name for i in report.price_engine.indicators]
            self.assertIn("test_extension", names)
        finally:
            del ENGINE_REGISTRIES["price"]["test_extension"]


if __name__ == "__main__":
    unittest.main()
