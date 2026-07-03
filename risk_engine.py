"""
risk_engine.py
================
Position Sizing & Risk Management Engine (NGSP-003).

Turns a BUY/SELL signal (entry/SL/T1/T2 + confidence/regime/score, all of
which signal_engine() and levels() already produce) into an account-aware
trade plan: how many shares/lots to take, how much capital that requires,
what you stand to make or lose, and a plain-English quality read.

=========================================================
THIS IS A DECISION-SUPPORT MODULE, NOT AN EXECUTION ENGINE
=========================================================
Nothing here places, modifies, or cancels a broker order, and nothing
here talks to a broker API. Every function takes numbers in and returns
numbers out. It exists so a human can decide what to do next — sizing,
not automation. Built deliberately as a standalone module so a future
Execution Engine could sit on top of it later (consume
generate_trade_summary()'s output as its sizing input) without this
module needing to change.

Design discipline matches signal_logic.py: ZERO dependency on Streamlit,
pandas, requests, or any network/secrets access. Every number this module
needs comes in as a function argument; every tunable constant lives in
risk_config.py, not here. This keeps the module trivially unit-testable
(see test_risk_engine.py) and reusable from anywhere — a signal card, a
report, a future backtest, or a future execution engine.
"""

import math

from risk_config import (
    DEFAULT_ACCOUNT_SIZE, DEFAULT_RISK_PER_TRADE_PCT,
    LOT_SIZE_MAP, DEFAULT_EQUITY_LOT_SIZE, DEFAULT_ROUND_MODE,
    MIN_ACCEPTABLE_RR,
    EXCELLENT_RR, EXCELLENT_CONFIDENCE_PCT, EXCELLENT_REGIME,
    EXCELLENT_MIN_TECHNICAL_SCORE,
    GOOD_RR, GOOD_CONFIDENCE_PCT, GOOD_MIN_TECHNICAL_SCORE,
    EXPOSURE_LOW_MAX_PCT, EXPOSURE_MEDIUM_MAX_PCT,
    PORTFOLIO_RISK_LOW_MAX_PCT, PORTFOLIO_RISK_MEDIUM_MAX_PCT,
)


# =========================================================
# Lot size resolution
# =========================================================

def resolve_lot_size(instrument_name, manual_override=None):
    """
    "Auto" vs "Manual Override" per the Settings spec.

    manual_override: if given (not None, not ""), used verbatim — this is
    the "Manual Override" Settings mode. Raises ValueError if it isn't a
    positive number, so a bad manual entry fails loudly at the settings
    boundary instead of silently producing a quantity of 0 three
    functions later.

    Auto mode: looks instrument_name up in risk_config.LOT_SIZE_MAP by
    substring match (same "does the display name contain this token"
    convention watchlist.py/config.py already use for "(MCX)" detection).
    Falls back to risk_config.DEFAULT_EQUITY_LOT_SIZE (1) for anything not
    found — i.e. NSE cash equities, which trade in whole shares.
    """
    if manual_override is not None and manual_override != "":
        try:
            override_val = float(manual_override)
        except (TypeError, ValueError):
            raise ValueError(f"Manual lot size override must be a number, got {manual_override!r}")
        if override_val <= 0:
            raise ValueError(f"Manual lot size override must be positive, got {override_val}")
        return override_val

    for token, lot_size in LOT_SIZE_MAP.items():
        if token in (instrument_name or ""):
            return lot_size

    return DEFAULT_EQUITY_LOT_SIZE


# =========================================================
# Position sizing
# =========================================================

def calculate_position_size(entry, stop_loss, signal,
                             account_size=DEFAULT_ACCOUNT_SIZE,
                             risk_per_trade_pct=DEFAULT_RISK_PER_TRADE_PCT,
                             lot_size=DEFAULT_EQUITY_LOT_SIZE,
                             round_mode=DEFAULT_ROUND_MODE):
    """
    The core sizing calculation:
        Maximum Risk      = Account Size x Risk% / 100
        Risk Per Unit      = |Entry - Stop Loss|
        Recommended Qty    = Maximum Risk / Risk Per Unit, rounded per round_mode
        Capital Required   = Quantity x Entry Price

    signal: "BUY" or "SELL" — used only to validate that stop_loss is on
    the correct side of entry (below entry for BUY, above for SELL). A
    Stop Loss on the wrong side isn't a risk boundary at all, so this is
    treated as invalid, not just unusual.

    round_mode: "nearest_lot" floors quantity down to a whole multiple of
    lot_size (never rounds UP past the risk budget). "nearest_share"
    floors down to a whole unit, ignoring lot_size (same math with
    lot_size effectively 1) — this is the "Nearest Share" Settings option
    for instruments you don't want lot-constrained.

    Returns a dict — see inline comments for every key. Never raises for
    ordinary bad-signal inputs (invalid SL, zero risk budget, etc.) —
    those come back as is_valid=False + a human-readable reason, because
    a signal card needs to *display* "why not", not crash. Genuinely
    programmer-error inputs (negative account size, unknown signal
    direction, non-numeric lot_size) do raise ValueError, since those
    indicate a caller bug, not a market condition.
    """
    if account_size is None or account_size <= 0:
        raise ValueError(f"account_size must be positive, got {account_size}")
    if risk_per_trade_pct is None or risk_per_trade_pct <= 0:
        raise ValueError(f"risk_per_trade_pct must be positive, got {risk_per_trade_pct}")
    if signal not in ("BUY", "SELL"):
        raise ValueError(f"signal must be 'BUY' or 'SELL', got {signal!r}")
    if lot_size is None or lot_size <= 0:
        raise ValueError(f"lot_size must be positive, got {lot_size}")
    if round_mode not in ("nearest_lot", "nearest_share"):
        raise ValueError(f"round_mode must be 'nearest_lot' or 'nearest_share', got {round_mode!r}")

    max_risk_amount = round(account_size * risk_per_trade_pct / 100, 2)

    result = {
        "is_valid": True,
        "invalid_reason": None,
        "max_risk_amount": max_risk_amount,
        "risk_per_unit": None,
        "raw_quantity": None,
        "quantity": 0,
        "capital_required": 0.0,
        "lot_size": lot_size,
        "round_mode": round_mode,
        "warnings": [],
    }

    if entry is None or stop_loss is None or entry == "N/A" or stop_loss == "N/A":
        result["is_valid"] = False
        result["invalid_reason"] = "Missing Entry or Stop Loss — cannot size this trade."
        return result

    entry = float(entry)
    stop_loss = float(stop_loss)

    # Stop Loss must sit on the correct side of Entry for the signal
    # direction, and must not equal Entry (zero risk-per-unit -> division
    # by zero and a meaningless "infinite quantity").
    if signal == "BUY" and stop_loss >= entry:
        result["is_valid"] = False
        result["invalid_reason"] = f"Invalid Stop Loss for BUY: {stop_loss} is not below Entry {entry}."
        return result
    if signal == "SELL" and stop_loss <= entry:
        result["is_valid"] = False
        result["invalid_reason"] = f"Invalid Stop Loss for SELL: {stop_loss} is not above Entry {entry}."
        return result

    risk_per_unit = abs(entry - stop_loss)
    result["risk_per_unit"] = round(risk_per_unit, 4)

    raw_quantity = max_risk_amount / risk_per_unit
    result["raw_quantity"] = round(raw_quantity, 4)

    if round_mode == "nearest_lot":
        quantity = math.floor(raw_quantity / lot_size) * lot_size
    else:  # nearest_share
        quantity = math.floor(raw_quantity)

    if quantity <= 0:
        result["is_valid"] = False
        result["invalid_reason"] = (
            f"Risk budget (₹{max_risk_amount:,.2f}) is smaller than the risk on a single "
            f"{'lot' if round_mode == 'nearest_lot' else 'unit'} "
            f"({'lot size ' + str(lot_size) + ' x ' if round_mode == 'nearest_lot' else ''}"
            f"₹{risk_per_unit:,.2f} risk-per-unit). Increase account size / risk %, or skip this trade."
        )
        return result

    capital_required = round(quantity * entry, 2)

    result["quantity"] = quantity
    result["capital_required"] = capital_required

    if capital_required > account_size:
        result["warnings"].append(
            f"Capital required (₹{capital_required:,.2f}) exceeds account size "
            f"(₹{account_size:,.2f}) — this sizing assumes cash-equivalent capital, "
            f"not margin/leverage. Reduce risk % or account size, or confirm your "
            f"broker's margin allows this position."
        )

    return result


# =========================================================
# Risk : Reward
# =========================================================

def calculate_risk_reward(entry, stop_loss, target1, target2, quantity):
    """
    Risk Per Unit        = |Entry - Stop Loss|          (already known from sizing, recomputed here to keep this function independently callable)
    Max Risk Amount      = Quantity x Risk Per Unit
    Potential Profit T1  = Quantity x |Target1 - Entry|
    Potential Profit T2  = Quantity x |Target2 - Entry|
    RR to T1 / T2         = Potential Profit / Max Risk Amount

    Returns None-safe: if target2 isn't available (e.g. levels() returned
    None for it), the T2 fields come back as None rather than raising —
    T1 is the field trade-quality/validation actually depend on.
    """
    if entry is None or stop_loss is None or entry == "N/A" or stop_loss == "N/A":
        return None

    entry = float(entry)
    stop_loss = float(stop_loss)
    risk_per_unit = abs(entry - stop_loss)

    if risk_per_unit == 0 or quantity is None or quantity <= 0:
        return {
            "risk_per_unit": round(risk_per_unit, 4),
            "max_risk_amount": 0.0,
            "potential_profit_t1": None, "potential_profit_t2": None,
            "rr_t1": None, "rr_t2": None,
        }

    max_risk_amount = round(quantity * risk_per_unit, 2)

    def _profit_and_rr(target):
        if target is None or target == "N/A":
            return None, None
        profit = round(quantity * abs(float(target) - entry), 2)
        rr = round(profit / max_risk_amount, 2) if max_risk_amount > 0 else None
        return profit, rr

    profit_t1, rr_t1 = _profit_and_rr(target1)
    profit_t2, rr_t2 = _profit_and_rr(target2)

    return {
        "risk_per_unit": round(risk_per_unit, 4),
        "max_risk_amount": max_risk_amount,
        "potential_profit_t1": profit_t1,
        "potential_profit_t2": profit_t2,
        "rr_t1": rr_t1,
        "rr_t2": rr_t2,
    }


# =========================================================
# Position exposure
# =========================================================

def calculate_position_exposure(capital_required, account_size):
    """
    Position Exposure% = (Capital Required / Account Size) x 100

    Tier per risk_config presets:
        < EXPOSURE_LOW_MAX_PCT              -> "Low"
        EXPOSURE_LOW_MAX_PCT..MEDIUM_MAX    -> "Medium"
        > EXPOSURE_MEDIUM_MAX_PCT           -> "High"
    """
    if not account_size:
        return {"exposure_pct": None, "tier": "N/A"}

    exposure_pct = round((capital_required / account_size) * 100, 2)

    if exposure_pct < EXPOSURE_LOW_MAX_PCT:
        tier = "Low"
    elif exposure_pct <= EXPOSURE_MEDIUM_MAX_PCT:
        tier = "Medium"
    else:
        tier = "High"

    return {"exposure_pct": exposure_pct, "tier": tier}


# =========================================================
# Portfolio risk
# =========================================================

def calculate_portfolio_risk(risk_per_trade_pct, open_signal_count, account_size=DEFAULT_ACCOUNT_SIZE):
    """
    Approximates aggregate open "heat" across the account.

    MODEL, NOT A MEASUREMENT — read this before trusting the number.
    signal_log.csv doesn't store the account size or quantity a trader
    actually used for a past OPEN signal (those are account-specific
    settings that can change between when a signal fired and when this
    engine later runs against it — see risk_config.py's comment on this).
    So instead of trying to reconstruct exact historical position sizes,
    this models "if every currently-OPEN logged signal, plus this new
    candidate, were each sized at the CURRENT Risk Per Trade% setting,
    what fraction of the account would be at risk at once" —
    (open_signal_count + 1) x risk_per_trade_pct. It's a heat-map, not an
    audited figure; treat "High" as "go check your actual open positions
    before adding another one", not as a precise number to report.

    open_signal_count: count of rows with status == "OPEN" in
    signal_log.csv, EXCLUDING the candidate this call is being made for
    (the function adds 1 for the candidate itself).

    Returns dict with portfolio_risk_pct (model estimate) and tier
    (Low/Medium/High per risk_config presets).
    """
    if open_signal_count is None or open_signal_count < 0:
        open_signal_count = 0

    portfolio_risk_pct = round((open_signal_count + 1) * risk_per_trade_pct, 2)

    if portfolio_risk_pct <= PORTFOLIO_RISK_LOW_MAX_PCT:
        tier = "Low"
    elif portfolio_risk_pct <= PORTFOLIO_RISK_MEDIUM_MAX_PCT:
        tier = "Medium"
    else:
        tier = "High"

    return {
        "portfolio_risk_pct": portfolio_risk_pct,
        "tier": tier,
        "open_signal_count": open_signal_count,
        "is_model_estimate": True,
    }


# =========================================================
# Trade quality
# =========================================================

def calculate_trade_quality(rr_t1, confidence_pct, regime, technical_score):
    """
    "Excellent" / "Good" / "Average" / "Poor", per risk_config's
    configurable thresholds (not hardcoded here).

    Excellent requires ALL of: RR >= EXCELLENT_RR, confidence_pct >=
    EXCELLENT_CONFIDENCE_PCT, regime == EXCELLENT_REGIME, technical_score
    >= EXCELLENT_MIN_TECHNICAL_SCORE — matches the spec's example rule
    exactly ("Risk Reward >2, Confidence >75, Trending Market Regime,
    High Technical Score").

    Good requires RR >= GOOD_RR AND at least one of (confidence_pct >=
    GOOD_CONFIDENCE_PCT, technical_score >= GOOD_MIN_TECHNICAL_SCORE) —
    a softer bar than Excellent, not requiring every criterion at once.

    Poor: RR below MIN_ACCEPTABLE_RR (risk_config.POOR_RR_CEILING) — a
    trade that doesn't clear the minimum acceptable reward-to-risk is
    never rated better than Poor regardless of confidence/regime/score.

    Everything else in between is "Average".

    Any missing input (None) is treated as "does not meet this
    criterion" rather than raising — a signal with no ADX/regime data
    yet, for example, just can't qualify for Excellent, it doesn't break
    the calculation.
    """
    rr_ok = rr_t1 is not None and rr_t1 >= MIN_ACCEPTABLE_RR
    if rr_t1 is None or rr_t1 < MIN_ACCEPTABLE_RR:
        return "Poor"

    excellent = (
        rr_t1 >= EXCELLENT_RR
        and confidence_pct is not None and confidence_pct >= EXCELLENT_CONFIDENCE_PCT
        and regime == EXCELLENT_REGIME
        and technical_score is not None and technical_score >= EXCELLENT_MIN_TECHNICAL_SCORE
    )
    if excellent:
        return "Excellent"

    good = (
        rr_t1 >= GOOD_RR
        and (
            (confidence_pct is not None and confidence_pct >= GOOD_CONFIDENCE_PCT)
            or (technical_score is not None and technical_score >= GOOD_MIN_TECHNICAL_SCORE)
        )
    )
    if good:
        return "Good"

    return "Average"


# =========================================================
# Orchestrator
# =========================================================

def generate_trade_summary(instrument_name, signal, entry, stop_loss, target1, target2,
                            confidence_pct=None, regime=None, technical_score=None,
                            account_size=DEFAULT_ACCOUNT_SIZE,
                            risk_per_trade_pct=DEFAULT_RISK_PER_TRADE_PCT,
                            lot_size_override=None,
                            round_mode=DEFAULT_ROUND_MODE,
                            open_signal_count=0):
    """
    The single call a signal card / report row needs — runs the whole
    pipeline (lot size -> position size -> risk:reward -> exposure ->
    portfolio risk -> trade quality) and returns one flat dict with every
    field the spec's "Signal Card" section lists, plus a `warnings` list
    covering every validation rule in the spec's "Risk Engine" section:
        - Reject quantity = 0            -> is_valid=False, reason in invalid_reason
        - Reject invalid Stop Loss       -> is_valid=False, reason in invalid_reason
        - Warn capital > account size    -> in warnings
        - Warn RR < 1.5                  -> in warnings
        - Highlight excellent setups     -> trade_quality == "Excellent"

    When is_valid is False, every downstream numeric field is None/0 and
    `invalid_reason` explains why in plain language — callers should show
    that message, not a generic error.
    """
    lot_size = resolve_lot_size(instrument_name, manual_override=lot_size_override)

    sizing = calculate_position_size(
        entry, stop_loss, signal,
        account_size=account_size,
        risk_per_trade_pct=risk_per_trade_pct,
        lot_size=lot_size,
        round_mode=round_mode,
    )

    summary = {
        "instrument": instrument_name,
        "signal": signal,
        "entry": entry,
        "stop_loss": stop_loss,
        "target1": target1,
        "target2": target2,
        "confidence_pct": confidence_pct,
        "regime": regime,
        "technical_score": technical_score,
        "account_size": account_size,
        "risk_per_trade_pct": risk_per_trade_pct,
        "lot_size": lot_size,
        "round_mode": round_mode,
        "is_valid": sizing["is_valid"],
        "invalid_reason": sizing["invalid_reason"],
        "max_risk_amount": sizing["max_risk_amount"],
        "risk_per_unit": sizing["risk_per_unit"],
        "quantity": sizing["quantity"],
        "capital_required": sizing["capital_required"],
        "potential_profit_t1": None,
        "potential_profit_t2": None,
        "rr_t1": None,
        "rr_t2": None,
        "exposure_pct": None,
        "exposure_tier": "N/A",
        "portfolio_risk_pct": None,
        "portfolio_risk_tier": "N/A",
        "trade_quality": "Poor",
        "warnings": list(sizing["warnings"]),
    }

    if not sizing["is_valid"]:
        summary["trade_quality"] = "N/A"
        return summary

    rr = calculate_risk_reward(entry, stop_loss, target1, target2, sizing["quantity"])
    if rr is not None:
        summary["potential_profit_t1"] = rr["potential_profit_t1"]
        summary["potential_profit_t2"] = rr["potential_profit_t2"]
        summary["rr_t1"] = rr["rr_t1"]
        summary["rr_t2"] = rr["rr_t2"]

        if rr["rr_t1"] is not None and rr["rr_t1"] < MIN_ACCEPTABLE_RR:
            summary["warnings"].append(
                f"Risk:Reward to T1 ({rr['rr_t1']}) is below the minimum acceptable "
                f"1:{MIN_ACCEPTABLE_RR} — proceed with caution."
            )

    exposure = calculate_position_exposure(sizing["capital_required"], account_size)
    summary["exposure_pct"] = exposure["exposure_pct"]
    summary["exposure_tier"] = exposure["tier"]

    portfolio_risk = calculate_portfolio_risk(risk_per_trade_pct, open_signal_count, account_size)
    summary["portfolio_risk_pct"] = portfolio_risk["portfolio_risk_pct"]
    summary["portfolio_risk_tier"] = portfolio_risk["tier"]

    summary["trade_quality"] = calculate_trade_quality(
        summary["rr_t1"], confidence_pct, regime, technical_score
    )

    if summary["trade_quality"] == "Excellent":
        summary["warnings"].insert(
            0,
            f"Excellent setup: RR {summary['rr_t1']}, confidence {confidence_pct}%, "
            f"{regime} regime, technical score {technical_score}/10."
        )

    return summary


# =========================================================
# Bulk DataFrame annotation (for reports / CSV export)
# =========================================================

def annotate_dataframe_with_risk(df, column_map,
                                  account_size=DEFAULT_ACCOUNT_SIZE,
                                  risk_per_trade_pct=DEFAULT_RISK_PER_TRADE_PCT,
                                  round_mode=DEFAULT_ROUND_MODE,
                                  lot_size_override=None,
                                  open_signal_count=0):
    """
    Bulk convenience wrapper around generate_trade_summary() for an
    entire pandas DataFrame — e.g. reports.py's Weekly/Monthly/Open/
    Closed sheets before Excel export. This is the ONLY function in this
    module that depends on pandas, kept isolated here on purpose so
    every other function in this file stays framework-agnostic and
    independently unit-testable without pandas installed at all.

    column_map: dict telling this function which of df's columns hold
    which field, since scanner.py's live-scan output (Instrument/Signal/
    Price/SL/T1/T2/Prob%/Regime/Score) and signal_log.csv's logged
    columns (instrument/signal/entry_price/sl/t1/t2/conviction_pct/score)
    name the same things differently. Required keys: instrument, signal,
    entry, sl, t1, t2. Optional keys: confidence_pct, regime, score —
    map a key to None (or omit it) if that column doesn't exist in df;
    generate_trade_summary() degrades gracefully without it (Trade
    Quality just can't reach "Excellent" without all four inputs).

    Rows where signal isn't "BUY"/"SELL" (e.g. WATCH, or a row with
    missing entry/SL) get Risk_TradeQuality="N/A" and blank numeric
    columns rather than being size — sizing a non-actionable row isn't
    meaningful.

    Returns a NEW DataFrame (never mutates df in place) with five added
    columns: Risk_Quantity, Risk_CapitalRequired, Risk_MaxRiskAmount,
    Risk_RR_T1, Risk_TradeQuality.
    """
    import pandas as pd  # local import: the only place in this module that needs pandas

    out = df.copy()

    def _get(row, key):
        col = column_map.get(key)
        if not col or col not in row.index:
            return None
        val = row[col]
        if val is None or (isinstance(val, float) and pd.isna(val)) or val == "N/A":
            return None
        return val

    quantities, capitals, max_risks, rr_t1s, qualities = [], [], [], [], []

    for _, row in out.iterrows():
        signal = _get(row, "signal")
        entry = _get(row, "entry")
        sl = _get(row, "sl")

        if signal not in ("BUY", "SELL") or entry is None or sl is None:
            quantities.append(None)
            capitals.append(None)
            max_risks.append(None)
            rr_t1s.append(None)
            qualities.append("N/A")
            continue

        summary = generate_trade_summary(
            instrument_name=_get(row, "instrument"),
            signal=signal,
            entry=entry,
            stop_loss=sl,
            target1=_get(row, "t1"),
            target2=_get(row, "t2"),
            confidence_pct=_get(row, "confidence_pct"),
            regime=_get(row, "regime"),
            technical_score=_get(row, "score"),
            account_size=account_size,
            risk_per_trade_pct=risk_per_trade_pct,
            lot_size_override=lot_size_override,
            round_mode=round_mode,
            open_signal_count=open_signal_count,
        )

        quantities.append(summary["quantity"] if summary["is_valid"] else None)
        capitals.append(summary["capital_required"] if summary["is_valid"] else None)
        max_risks.append(summary["max_risk_amount"])
        rr_t1s.append(summary["rr_t1"])
        qualities.append(summary["trade_quality"])

    out["Risk_Quantity"] = quantities
    out["Risk_CapitalRequired"] = capitals
    out["Risk_MaxRiskAmount"] = max_risks
    out["Risk_RR_T1"] = rr_t1s
    out["Risk_TradeQuality"] = qualities

    return out
