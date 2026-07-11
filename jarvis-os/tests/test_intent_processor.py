"""
Tests for jarvis.intake.intent_processor.

Covers the required Sprint-1A scenarios: simple request, ambiguous
request, empty request, unsupported request, and confidence boundaries.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.intake.intent_processor import CONFIDENCE_THRESHOLD, IntentProcessor
from jarvis.intake.models import IntentType


@pytest.fixture()
def processor(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    ledger.connect()
    return IntentProcessor(audit_ledger=ledger)


def test_simple_request_is_classified_confidently(processor):
    intent = processor.process("Analyze GitHub repository")

    assert intent.intent_type == IntentType.ANALYZE
    assert intent.confidence >= CONFIDENCE_THRESHOLD
    assert intent.is_ambiguous is False
    assert intent.requires_clarification is False
    assert intent.confidence_reason  # never empty
    assert "github_repository" in intent.detected_entities.values() or intent.detected_entities


def test_ambiguous_request_with_competing_keywords_requires_clarification(processor):
    # Contains both "analyze" and "research" keywords with equal match
    # counts -> the tie-breaking rule in _classify() must flag ambiguity.
    intent = processor.process("Analyze and research the situation")

    assert intent.is_ambiguous is True
    assert intent.requires_clarification is True
    assert intent.confidence < CONFIDENCE_THRESHOLD
    assert intent.confidence_reason


def test_unrecognized_request_is_unknown_and_ambiguous(processor):
    intent = processor.process("purple elephants dancing sideways")

    assert intent.intent_type == IntentType.UNKNOWN
    assert intent.is_ambiguous is True
    assert intent.requires_clarification is True
    assert intent.confidence < CONFIDENCE_THRESHOLD


def test_empty_request_is_unknown_zero_confidence_and_ambiguous(processor):
    intent = processor.process("")

    assert intent.intent_type == IntentType.UNKNOWN
    assert intent.confidence == 0.0
    assert intent.is_ambiguous is True
    assert intent.requires_clarification is True
    assert "empty" in intent.confidence_reason.lower()


def test_whitespace_only_request_is_treated_as_empty(processor):
    intent = processor.process("   \n\t  ")

    assert intent.normalized_input == ""
    assert intent.is_ambiguous is True


def test_unsupported_request_is_recognized_not_ambiguous(processor):
    intent = processor.process("Please trade Natural Gas futures now")

    assert intent.intent_type == IntentType.UNSUPPORTED
    assert intent.is_ambiguous is False
    assert intent.requires_clarification is False
    assert intent.confidence > 0.0
    assert "trade" in intent.confidence_reason


@pytest.mark.parametrize(
    "raw_input,expected_type",
    [
        ("investigate why the deploy failed", IntentType.UNSUPPORTED),  # "deploy" wins as unsupported
        ("check the status of the system", IntentType.STATUS_CHECK),
        ("research recent market signals", IntentType.RESEARCH),
    ],
)
def test_various_recognized_intents(processor, raw_input, expected_type):
    intent = processor.process(raw_input)
    assert intent.intent_type == expected_type


def test_confidence_never_exceeds_one_or_drops_below_zero(processor):
    samples = [
        "analyze analyze analyze analyze",
        "",
        "investigate debug diagnose troubleshoot",
        "asdkjhaskjdh",
    ]
    for text in samples:
        intent = processor.process(text)
        assert 0.0 <= intent.confidence <= 1.0


def test_confidence_reason_is_always_populated(processor):
    for text in ["Analyze GitHub repository", "", "gibberish nonsense text", "trade now"]:
        intent = processor.process(text)
        assert isinstance(intent.confidence_reason, str)
        assert len(intent.confidence_reason) > 0


def test_repeated_keywords_increase_confidence_up_to_cap(processor):
    low = processor.process("analyze this")
    high = processor.process("analyze review examine this")
    assert high.confidence >= low.confidence
    assert high.confidence <= 0.9  # documented cap
