"""
instrument_master/classifier.py

Rule-driven classification engine. Reads config/classification_rules.json
so new sectors/commodity groups/index groups can be added WITHOUT touching
this file. Also assigns NG Signal Pro research_priority and
sector_template_assigned based on config/settings.py watchlists.

Nothing in here is hardcoded per-instrument — all mappings come from config.
"""

import json
import logging

logger = logging.getLogger(__name__)


class ClassificationRules:
    """Loads and holds the classification rule set from JSON config."""

    def __init__(self, rules_path: str):
        with open(rules_path, "r") as f:
            self._rules = json.load(f)

    @property
    def asset_category_by_instrument_type(self) -> dict:
        return self._rules.get("asset_category_by_instrument_type", {})

    @property
    def commodity_groups(self) -> list:
        return self._rules.get("commodity_groups", [])

    @property
    def index_groups(self) -> list:
        return self._rules.get("index_groups", [])

    @property
    def equity_sector_by_symbol(self) -> dict:
        return self._rules.get("equity_sector_by_symbol", {})


def _matches(pattern_rule: dict, symbol: str, display_name: str) -> bool:
    match_type = pattern_rule.get("match_type", "contains")
    patterns = pattern_rule.get("patterns", [])
    haystacks = [(symbol or "").upper(), (display_name or "").upper()]

    for pattern in patterns:
        p = pattern.upper()
        for h in haystacks:
            if match_type == "exact" and h == p:
                return True
            if match_type == "contains" and p in h:
                return True
            if match_type == "startswith" and h.startswith(p):
                return True
    return False


def classify_instrument(record: dict, rules: ClassificationRules) -> dict:
    """
    Given a normalized instrument record, return the classification fields:
    sector, industry, commodity_group, asset_category.
    Does not mutate the input record.
    """
    symbol = record.get("trading_symbol", "") or ""
    display_name = record.get("display_name", "") or ""
    instrument_type = (record.get("instrument_type") or "").upper()
    exchange = (record.get("exchange") or "").upper()

    result = {
        "sector": None,
        "industry": None,
        "commodity_group": None,
        "asset_category": rules.asset_category_by_instrument_type.get(
            instrument_type, "Other"
        ),
    }

    # Commodities (MCX)
    if exchange == "MCX":
        result["asset_category"] = "Commodity"
        for rule in rules.commodity_groups:
            if _matches(rule, symbol, display_name):
                result["commodity_group"] = rule.get("commodity_group")
                result["sector"] = rule.get("sector", "Commodity")
                result["industry"] = rule.get("industry")
                return result
        # MCX instrument not in the rule list yet — flagged for classifier extension
        result["sector"] = "Commodity"
        result["commodity_group"] = "Unclassified"
        return result

    # Indices
    if instrument_type == "INDEX":
        result["asset_category"] = "Index"
        result["sector"] = "Index"
        for rule in rules.index_groups:
            if _matches(rule, symbol, display_name):
                result["industry"] = rule.get("industry")
                return result
        result["industry"] = "Unclassified Index"
        return result

    # Equities
    if instrument_type == "EQ":
        result["asset_category"] = "Equity"
        mapping = rules.equity_sector_by_symbol.get(symbol.upper())
        if mapping:
            result["sector"] = mapping.get("sector")
            result["industry"] = mapping.get("industry")
        else:
            result["sector"] = "Unclassified"
            result["industry"] = "Unclassified"
        return result

    # Derivatives (F&O) inherit classification from their underlying symbol
    if instrument_type in ("FUT", "CE", "PE"):
        result["asset_category"] = "Derivative"
        # Try commodity match first (MCX FUT already handled above via exchange check,
        # this covers NSE/BSE index & stock derivatives)
        for rule in rules.index_groups:
            if _matches(rule, symbol, display_name):
                result["sector"] = "Index"
                result["industry"] = rule.get("industry")
                return result
        mapping = rules.equity_sector_by_symbol.get(symbol.upper())
        if mapping:
            result["sector"] = mapping.get("sector")
            result["industry"] = mapping.get("industry")
        else:
            result["sector"] = "Unclassified"
            result["industry"] = "Unclassified"
        return result

    return result


def assign_research_priority(record: dict, settings) -> int:
    """
    Priority 1: current NG Signal Pro watchlist + core commodities
    Priority 2: MCX commodities (general)
    Priority 3: major indices
    Priority 4: top NSE stocks (Nifty 50 list)
    Priority 5: everything else
    """
    symbol = (record.get("trading_symbol") or "").upper()
    exchange = (record.get("exchange") or "").upper()

    if symbol in [s.upper() for s in settings.CURRENT_WATCHLIST]:
        return 1
    if symbol in [s.upper() for s in settings.CORE_COMMODITY_SYMBOLS]:
        return 1
    if exchange == settings.MCX_EXCHANGE_NAME:
        return 2
    if symbol in [s.upper() for s in settings.MAJOR_INDICES]:
        return 3
    if symbol in [s.upper() for s in settings.NIFTY_50_SYMBOLS]:
        return 4
    return 5


def assign_sector_template(classification: dict) -> str | None:
    """
    Maps a classification to a placeholder sector-template name. The actual
    template *content* belongs to the future Backtesting Engine — this
    module only assigns which template NAME an instrument should eventually
    use, so that engine has something to key off of.
    """
    sector = classification.get("sector")
    if not sector or sector == "Unclassified":
        return None
    return f"TEMPLATE_{sector.upper().replace(' ', '_')}"
