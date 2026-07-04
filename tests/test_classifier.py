"""
tests/test_classifier.py

Basic unit tests for the classification engine. Run with:
    python -m pytest tests/ -v
or standalone:
    python tests/test_classifier.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from instrument_config import settings
from instrument_master.classifier import (
    ClassificationRules,
    classify_instrument,
    assign_research_priority,
)


def _rules():
    return ClassificationRules(settings.CLASSIFICATION_RULES_PATH)


def test_natural_gas_classified_as_commodity_energy():
    rules = _rules()
    record = {
        "trading_symbol": "NATURALGAS",
        "display_name": "NATURAL GAS",
        "instrument_type": "FUT",
        "exchange": "MCX",
    }
    result = classify_instrument(record, rules)
    assert result["sector"] == "Commodity"
    assert result["industry"] == "Energy"
    assert result["commodity_group"] == "Natural Gas"
    print("PASS: test_natural_gas_classified_as_commodity_energy")


def test_reliance_classified_as_energy_equity():
    rules = _rules()
    record = {
        "trading_symbol": "RELIANCE",
        "display_name": "RELIANCE INDUSTRIES LTD",
        "instrument_type": "EQ",
        "exchange": "NSE",
    }
    result = classify_instrument(record, rules)
    assert result["asset_category"] == "Equity"
    assert result["sector"] == "Energy"
    print("PASS: test_reliance_classified_as_energy_equity")


def test_tcs_classified_as_it():
    rules = _rules()
    record = {
        "trading_symbol": "TCS",
        "display_name": "TATA CONSULTANCY SERVICES",
        "instrument_type": "EQ",
        "exchange": "NSE",
    }
    result = classify_instrument(record, rules)
    assert result["sector"] == "IT"
    print("PASS: test_tcs_classified_as_it")


def test_banknifty_classified_as_index():
    rules = _rules()
    record = {
        "trading_symbol": "BANKNIFTY",
        "display_name": "NIFTY BANK",
        "instrument_type": "INDEX",
        "exchange": "NSE",
    }
    result = classify_instrument(record, rules)
    assert result["asset_category"] == "Index"
    assert result["industry"] == "Banking"
    print("PASS: test_banknifty_classified_as_index")


def test_mcx_commodity_gets_priority_2():
    record = {"trading_symbol": "CRUDEOIL", "exchange": "MCX"}
    priority = assign_research_priority(record, settings)
    assert priority == 2
    print("PASS: test_mcx_commodity_gets_priority_2")


def test_natural_gas_gets_priority_1_even_on_mcx():
    record = {"trading_symbol": "NATURALGAS", "exchange": "MCX"}
    priority = assign_research_priority(record, settings)
    assert priority == 1
    print("PASS: test_natural_gas_gets_priority_1_even_on_mcx")


def test_nifty_index_gets_priority_3():
    record = {"trading_symbol": "NIFTY", "exchange": "NSE"}
    priority = assign_research_priority(record, settings)
    assert priority == 3
    print("PASS: test_nifty_index_gets_priority_3")


def test_unlisted_equity_gets_priority_5():
    record = {"trading_symbol": "SOMERANDOMSTOCK", "exchange": "NSE"}
    priority = assign_research_priority(record, settings)
    assert priority == 5
    print("PASS: test_unlisted_equity_gets_priority_5")


if __name__ == "__main__":
    test_natural_gas_classified_as_commodity_energy()
    test_reliance_classified_as_energy_equity()
    test_tcs_classified_as_it()
    test_banknifty_classified_as_index()
    test_mcx_commodity_gets_priority_2()
    test_natural_gas_gets_priority_1_even_on_mcx()
    test_nifty_index_gets_priority_3()
    test_unlisted_equity_gets_priority_5()
    print("\nAll tests passed.")
