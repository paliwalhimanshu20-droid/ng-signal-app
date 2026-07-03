"""
test_risk_engine.py
=====================
Unit tests for risk_engine.py (NGSP-003). Mirrors test_technical_engine.py's
naming/style convention.

Coverage (per the NGSP-003 spec's Testing section):
  - NSE stocks (lot_size=1)
  - MCX commodities (lot_size from risk_config.LOT_SIZE_MAP)
  - High-priced instruments
  - Low-priced instruments
  - Small accounts / large accounts
  - Very tight Stop Loss / very wide Stop Loss
  - Capital insufficient (capital_required > account_size)
  - Different Risk %
  - Lot-size rounding (nearest_lot vs nearest_share)
  - Invalid Stop Loss (wrong side of entry, equal to entry)
  - Quantity = 0 rejection
  - Trade quality tiers (Excellent/Good/Average/Poor)
  - Portfolio risk modeling
  - SELL-side signals (not just BUY)

Run with: python3 -m unittest test_risk_engine.py -v
"""

import unittest

from risk_engine import (
    resolve_lot_size,
    calculate_position_size,
    calculate_risk_reward,
    calculate_position_exposure,
    calculate_portfolio_risk,
    calculate_trade_quality,
    generate_trade_summary,
)
from risk_config import MIN_ACCEPTABLE_RR, DEFAULT_EQUITY_LOT_SIZE


class TestResolveLotSize(unittest.TestCase):

    def test_equity_defaults_to_one(self):
        self.assertEqual(resolve_lot_size("Reliance Industries"), DEFAULT_EQUITY_LOT_SIZE)

    def test_natural_gas_auto_lot_size(self):
        self.assertEqual(resolve_lot_size("Natural Gas (MCX)"), 1250)

    def test_manual_override_wins_over_auto(self):
        self.assertEqual(resolve_lot_size("Natural Gas (MCX)", manual_override=100), 100)

    def test_manual_override_accepts_string_number(self):
        self.assertEqual(resolve_lot_size("Reliance Industries", manual_override="50"), 50)

    def test_manual_override_rejects_zero(self):
        with self.assertRaises(ValueError):
            resolve_lot_size("Reliance Industries", manual_override=0)

    def test_manual_override_rejects_negative(self):
        with self.assertRaises(ValueError):
            resolve_lot_size("Reliance Industries", manual_override=-5)

    def test_manual_override_rejects_non_numeric(self):
        with self.assertRaises(ValueError):
            resolve_lot_size("Reliance Industries", manual_override="abc")

    def test_empty_string_override_falls_back_to_auto(self):
        self.assertEqual(resolve_lot_size("Reliance Industries", manual_override=""), 1)


class TestCalculatePositionSize(unittest.TestCase):

    # ---- NSE stock, ordinary case ----
    def test_nse_stock_buy_basic(self):
        r = calculate_position_size(entry=2500, stop_loss=2470, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["max_risk_amount"], 5000.0)
        self.assertEqual(r["risk_per_unit"], 30.0)
        self.assertEqual(r["quantity"], 166)  # floor(5000/30) = 166
        self.assertEqual(r["capital_required"], 415000.0)

    # ---- SELL side ----
    def test_sell_signal_valid_stop_above_entry(self):
        r = calculate_position_size(entry=2500, stop_loss=2530, signal="SELL",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["risk_per_unit"], 30.0)

    def test_sell_signal_invalid_stop_below_entry(self):
        r = calculate_position_size(entry=2500, stop_loss=2470, signal="SELL",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertFalse(r["is_valid"])
        self.assertIn("Invalid Stop Loss", r["invalid_reason"])

    def test_buy_signal_invalid_stop_above_entry(self):
        r = calculate_position_size(entry=2500, stop_loss=2530, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertFalse(r["is_valid"])

    def test_stop_loss_equal_to_entry_is_invalid(self):
        r = calculate_position_size(entry=2500, stop_loss=2500, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertFalse(r["is_valid"])

    def test_missing_stop_loss_is_invalid(self):
        r = calculate_position_size(entry=2500, stop_loss="N/A", signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertFalse(r["is_valid"])

    # ---- High-priced instrument ----
    def test_high_priced_instrument(self):
        # e.g. an instrument trading at 50,000/unit with a proportionally wide stop
        r = calculate_position_size(entry=50000, stop_loss=49000, signal="BUY",
                                     account_size=1000000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["max_risk_amount"], 10000.0)
        self.assertEqual(r["quantity"], 10)  # floor(10000/1000)=10
        self.assertEqual(r["capital_required"], 500000.0)

    # ---- Low-priced instrument ----
    def test_low_priced_instrument(self):
        r = calculate_position_size(entry=15, stop_loss=14, signal="BUY",
                                     account_size=100000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["max_risk_amount"], 1000.0)
        self.assertEqual(r["quantity"], 1000)  # floor(1000/1)=1000

    # ---- Small vs large accounts ----
    def test_small_account(self):
        r = calculate_position_size(entry=2500, stop_loss=2470, signal="BUY",
                                     account_size=100000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["max_risk_amount"], 1000.0)
        self.assertEqual(r["quantity"], 33)  # floor(1000/30)=33

    def test_large_account(self):
        r = calculate_position_size(entry=2500, stop_loss=2470, signal="BUY",
                                     account_size=10000000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["max_risk_amount"], 100000.0)
        self.assertEqual(r["quantity"], 3333)  # floor(100000/30)=3333

    # ---- Very tight / very wide stop loss ----
    def test_very_tight_stop_loss(self):
        r = calculate_position_size(entry=2500, stop_loss=2499, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["risk_per_unit"], 1.0)
        self.assertEqual(r["quantity"], 5000)  # floor(5000/1)=5000
        # Capital required should exceed account size here -> warning expected
        self.assertGreater(r["capital_required"], 500000)
        self.assertTrue(any("exceeds account size" in w for w in r["warnings"]))

    def test_very_wide_stop_loss(self):
        r = calculate_position_size(entry=2500, stop_loss=2000, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1)
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["risk_per_unit"], 500.0)
        self.assertEqual(r["quantity"], 10)  # floor(5000/500)=10

    # ---- Capital insufficient ----
    def test_capital_required_exceeds_account_size_warns(self):
        r = calculate_position_size(entry=2500, stop_loss=2495, signal="BUY",
                                     account_size=100000, risk_per_trade_pct=2.0, lot_size=1)
        self.assertTrue(r["is_valid"])  # still valid -- it's a warning, not a rejection
        self.assertGreater(r["capital_required"], 100000)
        self.assertTrue(any("exceeds account size" in w for w in r["warnings"]))

    # ---- Different Risk % ----
    def test_risk_pct_scales_max_risk_linearly(self):
        r_half = calculate_position_size(entry=2500, stop_loss=2470, signal="BUY",
                                          account_size=500000, risk_per_trade_pct=0.5, lot_size=1)
        r_two = calculate_position_size(entry=2500, stop_loss=2470, signal="BUY",
                                         account_size=500000, risk_per_trade_pct=2.0, lot_size=1)
        self.assertEqual(r_half["max_risk_amount"], 2500.0)
        self.assertEqual(r_two["max_risk_amount"], 10000.0)
        # floor() rounding means quantity scaling is approximately, not
        # exactly, 4x -- assert the ratio is close rather than exact.
        self.assertEqual(r_half["quantity"], 83)   # floor(2500/30)
        self.assertEqual(r_two["quantity"], 333)   # floor(10000/30)

    # ---- Lot-size rounding ----
    def test_nearest_lot_rounding_floors_to_lot_multiple(self):
        # risk budget allows 1899 mmBtu worth of NG lots at 10/unit risk -> raw=1899,
        # lot_size=1250 -> only 1 full lot fits (1250), NOT 1899 units.
        r = calculate_position_size(entry=320, stop_loss=310, signal="BUY",
                                     account_size=2000000, risk_per_trade_pct=0.95, lot_size=1250,
                                     round_mode="nearest_lot")
        self.assertTrue(r["is_valid"])
        self.assertEqual(r["quantity"] % 1250, 0)
        self.assertEqual(r["quantity"], 1250)

    def test_nearest_share_ignores_lot_size(self):
        r = calculate_position_size(entry=320, stop_loss=310, signal="BUY",
                                     account_size=2000000, risk_per_trade_pct=0.95, lot_size=1250,
                                     round_mode="nearest_share")
        self.assertTrue(r["is_valid"])
        # Should NOT be constrained to a multiple of 1250
        self.assertEqual(r["quantity"], 1900)  # floor(19000/10) = 1900

    # ---- Quantity = 0 rejection ----
    def test_quantity_zero_is_rejected_small_account_large_lot(self):
        r = calculate_position_size(entry=320, stop_loss=310, signal="BUY",
                                     account_size=500000, risk_per_trade_pct=1.0, lot_size=1250)
        self.assertFalse(r["is_valid"])
        self.assertEqual(r["quantity"], 0)
        self.assertIn("Increase account size", r["invalid_reason"])

    # ---- Programmer-error inputs raise, not silently misbehave ----
    def test_negative_account_size_raises(self):
        with self.assertRaises(ValueError):
            calculate_position_size(entry=100, stop_loss=95, signal="BUY", account_size=-1000, risk_per_trade_pct=1.0, lot_size=1)

    def test_invalid_signal_direction_raises(self):
        with self.assertRaises(ValueError):
            calculate_position_size(entry=100, stop_loss=95, signal="HOLD", account_size=100000, risk_per_trade_pct=1.0, lot_size=1)

    def test_invalid_round_mode_raises(self):
        with self.assertRaises(ValueError):
            calculate_position_size(entry=100, stop_loss=95, signal="BUY", account_size=100000, risk_per_trade_pct=1.0, lot_size=1, round_mode="banana")


class TestCalculateRiskReward(unittest.TestCase):

    def test_basic_rr(self):
        r = calculate_risk_reward(entry=2500, stop_loss=2470, target1=2560, target2=2620, quantity=166)
        self.assertEqual(r["risk_per_unit"], 30.0)
        self.assertEqual(r["potential_profit_t1"], 9960.0)
        self.assertEqual(r["rr_t1"], 2.0)
        self.assertEqual(r["rr_t2"], 4.0)

    def test_missing_target2_returns_none_for_t2_only(self):
        r = calculate_risk_reward(entry=2500, stop_loss=2470, target1=2560, target2="N/A", quantity=166)
        self.assertIsNotNone(r["rr_t1"])
        self.assertIsNone(r["rr_t2"])

    def test_zero_quantity_returns_zero_risk_no_crash(self):
        r = calculate_risk_reward(entry=2500, stop_loss=2470, target1=2560, target2=2620, quantity=0)
        self.assertEqual(r["max_risk_amount"], 0.0)
        self.assertIsNone(r["rr_t1"])

    def test_missing_entry_returns_none(self):
        r = calculate_risk_reward(entry=None, stop_loss=2470, target1=2560, target2=2620, quantity=166)
        self.assertIsNone(r)


class TestCalculatePositionExposure(unittest.TestCase):

    def test_low_tier(self):
        r = calculate_position_exposure(capital_required=40000, account_size=500000)
        self.assertEqual(r["exposure_pct"], 8.0)
        self.assertEqual(r["tier"], "Low")

    def test_medium_tier(self):
        r = calculate_position_exposure(capital_required=100000, account_size=500000)
        self.assertEqual(r["exposure_pct"], 20.0)
        self.assertEqual(r["tier"], "Medium")

    def test_high_tier(self):
        r = calculate_position_exposure(capital_required=200000, account_size=500000)
        self.assertEqual(r["exposure_pct"], 40.0)
        self.assertEqual(r["tier"], "High")

    def test_boundary_at_exactly_ten_pct_is_medium_not_low(self):
        r = calculate_position_exposure(capital_required=50000, account_size=500000)
        self.assertEqual(r["exposure_pct"], 10.0)
        self.assertEqual(r["tier"], "Medium")


class TestCalculatePortfolioRisk(unittest.TestCase):

    def test_no_open_signals_just_the_candidate(self):
        r = calculate_portfolio_risk(risk_per_trade_pct=1.0, open_signal_count=0, account_size=500000)
        self.assertEqual(r["portfolio_risk_pct"], 1.0)
        self.assertEqual(r["tier"], "Low")

    def test_several_open_signals_raises_tier(self):
        r = calculate_portfolio_risk(risk_per_trade_pct=1.0, open_signal_count=6, account_size=500000)
        self.assertEqual(r["portfolio_risk_pct"], 7.0)
        self.assertEqual(r["tier"], "High")

    def test_negative_open_count_treated_as_zero(self):
        r = calculate_portfolio_risk(risk_per_trade_pct=1.0, open_signal_count=-3, account_size=500000)
        self.assertEqual(r["open_signal_count"], 0)


class TestCalculateTradeQuality(unittest.TestCase):

    def test_excellent_requires_all_criteria(self):
        q = calculate_trade_quality(rr_t1=2.5, confidence_pct=80, regime="TRENDING", technical_score=9)
        self.assertEqual(q, "Excellent")

    def test_high_rr_alone_is_not_excellent(self):
        q = calculate_trade_quality(rr_t1=3.0, confidence_pct=50, regime="RANGING", technical_score=5)
        self.assertNotEqual(q, "Excellent")

    def test_good_tier(self):
        q = calculate_trade_quality(rr_t1=1.6, confidence_pct=65, regime="RANGING", technical_score=5)
        self.assertEqual(q, "Good")

    def test_poor_below_min_rr(self):
        q = calculate_trade_quality(rr_t1=1.0, confidence_pct=90, regime="TRENDING", technical_score=10)
        self.assertEqual(q, "Poor")

    def test_average_when_rr_ok_but_nothing_else_qualifies(self):
        q = calculate_trade_quality(rr_t1=1.5, confidence_pct=40, regime="RANGING", technical_score=4)
        self.assertEqual(q, "Average")

    def test_missing_rr_is_poor_not_a_crash(self):
        q = calculate_trade_quality(rr_t1=None, confidence_pct=90, regime="TRENDING", technical_score=10)
        self.assertEqual(q, "Poor")


class TestGenerateTradeSummary(unittest.TestCase):

    def test_full_valid_nse_summary(self):
        s = generate_trade_summary(
            "Reliance Industries", "BUY", entry=2500, stop_loss=2470, target1=2560, target2=2620,
            confidence_pct=80, regime="TRENDING", technical_score=9,
            account_size=500000, risk_per_trade_pct=1.0,
        )
        self.assertTrue(s["is_valid"])
        self.assertEqual(s["trade_quality"], "Excellent")
        self.assertEqual(s["quantity"], 166)

    def test_invalid_summary_short_circuits_cleanly(self):
        s = generate_trade_summary(
            "Reliance Industries", "BUY", entry=2500, stop_loss=2530, target1=2560, target2=2620,
            account_size=500000, risk_per_trade_pct=1.0,
        )
        self.assertFalse(s["is_valid"])
        self.assertEqual(s["trade_quality"], "N/A")
        self.assertEqual(s["quantity"], 0)
        self.assertIsNone(s["rr_t1"])

    def test_ng_small_account_correctly_rejected(self):
        # Real scenario found during manual testing: a 500k account at 1%
        # risk genuinely cannot safely take even 1 NG lot at a 10-point stop.
        s = generate_trade_summary(
            "Natural Gas (MCX)", "BUY", entry=320.0, stop_loss=310.0, target1=335.0, target2=350.0,
            confidence_pct=78, regime="TRENDING", technical_score=9,
            account_size=500000, risk_per_trade_pct=1.0,
        )
        self.assertFalse(s["is_valid"])
        self.assertEqual(s["quantity"], 0)

    def test_ng_larger_account_is_accepted(self):
        s = generate_trade_summary(
            "Natural Gas (MCX)", "BUY", entry=320.0, stop_loss=310.0, target1=335.0, target2=350.0,
            confidence_pct=78, regime="TRENDING", technical_score=9,
            account_size=2000000, risk_per_trade_pct=1.0,
        )
        self.assertTrue(s["is_valid"])
        self.assertEqual(s["quantity"], 1250)

    def test_manual_lot_override_flows_through(self):
        s = generate_trade_summary(
            "Natural Gas (MCX)", "BUY", entry=320.0, stop_loss=310.0, target1=335.0, target2=350.0,
            account_size=500000, risk_per_trade_pct=1.0, lot_size_override=100,
        )
        self.assertTrue(s["is_valid"])
        self.assertEqual(s["lot_size"], 100)
        self.assertEqual(s["quantity"], 500)  # floor(5000/10/100)*100 = 500

    def test_low_rr_warning_present(self):
        s = generate_trade_summary(
            "Reliance Industries", "BUY", entry=2500, stop_loss=2470, target1=2510, target2=2520,
            account_size=500000, risk_per_trade_pct=1.0,
        )
        self.assertTrue(any("below the minimum acceptable" in w for w in s["warnings"]))

    def test_sell_side_full_summary(self):
        s = generate_trade_summary(
            "Reliance Industries", "SELL", entry=2500, stop_loss=2530, target1=2440, target2=2380,
            confidence_pct=80, regime="TRENDING", technical_score=9,
            account_size=500000, risk_per_trade_pct=1.0,
        )
        self.assertTrue(s["is_valid"])
        self.assertGreater(s["rr_t1"], 0)


if __name__ == "__main__":
    unittest.main()
