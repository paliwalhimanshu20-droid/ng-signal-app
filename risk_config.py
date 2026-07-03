"""
risk_config.py
===============
Every tunable number the Position Sizing & Risk Management Engine
(risk_engine.py, NGSP-003) uses. Nothing in risk_engine.py is hardcoded —
if you want different account presets, a different "Excellent" bar, or a
different lot size for an instrument, this is the only file you should
need to touch.

Mirrors the separation already used elsewhere in this codebase
(technical_config.py <-> technical_engine.py, config.py <-> scanner.py):
config holds the numbers, the engine holds the math.

IMPORTANT — this module has ZERO dependency on Streamlit, pandas,
requests, or any network/secrets access, same discipline as
signal_logic.py and technical_config.py. It's just constants.
"""

# ================= ACCOUNT SIZE =================
ACCOUNT_SIZE_PRESETS = [100_000, 500_000, 1_000_000]
DEFAULT_ACCOUNT_SIZE = 500_000

# ================= RISK PER TRADE =================
RISK_PER_TRADE_PRESETS_PCT = [0.5, 1.0, 1.5, 2.0]
DEFAULT_RISK_PER_TRADE_PCT = 1.0

# ================= LOT SIZE =================
# "Auto" mode looks the instrument up here by substring match against its
# display name (same convention watchlist.py/config.py already use for
# detecting "(MCX)" commodity entries). NSE cash equities default to 1
# (whole shares, no lot concept). "Manual Override" in Settings bypasses
# this dict entirely and uses whatever the trader types in.
#
# MCX lot sizes are revised by the exchange periodically (they are NOT a
# fixed constant of the underlying commodity) — treat the value below as a
# starting point that needs verifying against the current MCX contract
# specification before sizing a real trade with it, not a guaranteed-
# current fact. Use the Manual Override setting if it's ever changed.
LOT_SIZE_MAP = {
    "Natural Gas": 1250,   # MCX Natural Gas — mmBtu per lot, verify before live use
}
DEFAULT_EQUITY_LOT_SIZE = 1

ROUND_MODE_OPTIONS = ["nearest_lot", "nearest_share"]
DEFAULT_ROUND_MODE = "nearest_lot"

# ================= RISK:REWARD =================
# Used against Risk:Reward to T1 (the nearer, higher-probability target —
# T2 is the stretch target and isn't held to this bar).
MIN_ACCEPTABLE_RR = 1.5

# ================= TRADE QUALITY =================
# "Excellent" requires ALL of these to hold (per the spec's example rule).
# "technical_score" here is the 0-10 score signal_engine() already
# produces (Score column in scanner output / score column in signal_log).
EXCELLENT_RR = 2.0
EXCELLENT_CONFIDENCE_PCT = 75
EXCELLENT_REGIME = "TRENDING"
EXCELLENT_MIN_TECHNICAL_SCORE = 8

# "Good" is a softer version of the same bar — most, not all, criteria.
GOOD_RR = MIN_ACCEPTABLE_RR
GOOD_CONFIDENCE_PCT = 60
GOOD_MIN_TECHNICAL_SCORE = 7

# Below GOOD_RR is "Poor" regardless of anything else — a trade whose
# reward doesn't clear the minimum acceptable RR is never "Average".
POOR_RR_CEILING = MIN_ACCEPTABLE_RR

# ================= POSITION EXPOSURE =================
# Position Exposure% = (Capital Required / Account Size) * 100
EXPOSURE_LOW_MAX_PCT = 10
EXPOSURE_MEDIUM_MAX_PCT = 25
# > EXPOSURE_MEDIUM_MAX_PCT = "High"

# ================= PORTFOLIO RISK =================
# Portfolio Risk Score approximates aggregate open "heat" as (number of
# still-OPEN logged signals + this candidate) x risk-per-trade%. This is
# a MODEL, not a measurement: signal_log.csv does not (and per this
# task's "don't modify completed engines" instruction, still does not)
# store the actual account size/quantity a trader used for a past OPEN
# signal, since those are account-specific settings that can change
# between when a signal fired and when this engine runs. The model's
# assumption is "if every open signal were sized at the CURRENT Risk Per
# Trade setting, how much of the account would be at risk at once" — see
# risk_engine.calculate_portfolio_risk()'s docstring.
PORTFOLIO_RISK_LOW_MAX_PCT = 3.0
PORTFOLIO_RISK_MEDIUM_MAX_PCT = 6.0
# > PORTFOLIO_RISK_MEDIUM_MAX_PCT = "High"
