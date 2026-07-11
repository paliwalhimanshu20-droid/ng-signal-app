"""
Tests for jarvis.audit.ledger.

Covers JARVIS-001 §7 Step 2's requirement: record() must fail before
connect() is called, and a successful connect()+record() must actually
persist an append-only entry.
"""

from __future__ import annotations

import pytest

from jarvis.audit.ledger import AuditLedger, AuditLedgerError


def test_record_before_connect_raises(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    with pytest.raises(AuditLedgerError):
        ledger.record(event_type="test", message="should fail")


def test_connect_then_record_succeeds(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    ledger.connect()
    assert ledger.is_connected

    entry = ledger.record(event_type="test.event", message="hello")
    assert entry.event_type == "test.event"

    entries = ledger.read_all()
    assert len(entries) == 1
    assert entries[0].message == "hello"


def test_entries_are_append_only(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    ledger.connect()
    ledger.record(event_type="a", message="first")
    ledger.record(event_type="b", message="second")

    entries = ledger.read_all()
    assert [e.event_type for e in entries] == ["a", "b"]


def test_connect_does_not_truncate_existing_entries(tmp_path):
    path = tmp_path / "ledger.jsonl"
    ledger = AuditLedger(storage_path=path)
    ledger.connect()
    ledger.record(event_type="a", message="first")

    # Simulate a fresh process re-connecting to the same storage path.
    ledger2 = AuditLedger(storage_path=path)
    ledger2.connect()
    entries = ledger2.read_all()
    assert len(entries) == 1
    assert entries[0].event_type == "a"
