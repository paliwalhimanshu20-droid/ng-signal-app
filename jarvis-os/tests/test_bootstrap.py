"""
Tests for jarvis.core.bootstrap.

Covers the end-to-end Sprint-0 Bootstrap sequence: a valid environment
boots successfully to a healthy, ready JarvisCore; a missing Constitution
reference halts Bootstrap with BootstrapError, per JARVIS-001 §7's
"halt on failure" requirement for Step 1.
"""

from __future__ import annotations

import json
import os

import pytest

from jarvis.constitution.loader import REQUIRED_ARTICLE_IDS
from jarvis.core.bootstrap import BootstrapError, boot


@pytest.fixture()
def sprint0_env(tmp_path, monkeypatch):
    """Point Sprint-0's config at a fresh, isolated temp directory."""
    constitution_path = tmp_path / "constitution.json"
    constitution_path.write_text(
        json.dumps(
            {
                "constitution_version": "1.0.0",
                "articles": [
                    {"id": article_id, "name": f"Article {article_id}", "summary": "Test."}
                    for article_id in REQUIRED_ARTICLE_IDS
                ],
            }
        ),
        encoding="utf-8",
    )

    monkeypatch.setenv("JARVIS_CONSTITUTION_PATH", str(constitution_path))
    monkeypatch.setenv("JARVIS_REGISTRY_STATE_PATH", str(tmp_path / "registry_state.json"))
    monkeypatch.chdir(tmp_path)  # AuditLedger's Sprint-0 path is relative: data/audit_ledger.jsonl
    return tmp_path


def test_boot_succeeds_with_valid_environment(sprint0_env):
    core = boot()
    try:
        assert core.ready is True
        health = core.health_check()
        assert health.healthy is True
        assert core.constitution.version == "1.0.0"
        assert core.audit_ledger.is_connected
    finally:
        core.shutdown()


def test_boot_halts_on_missing_constitution(tmp_path, monkeypatch):
    monkeypatch.setenv("JARVIS_CONSTITUTION_PATH", str(tmp_path / "does_not_exist.json"))
    monkeypatch.chdir(tmp_path)

    with pytest.raises(BootstrapError):
        boot()


def test_shutdown_records_audit_entry(sprint0_env):
    core = boot()
    entries_before = core.audit_ledger.read_all()
    core.shutdown()
    entries_after = core.audit_ledger.read_all()

    assert len(entries_after) == len(entries_before) + 1
    assert entries_after[-1].event_type == "core.shutdown"
    assert core.ready is False
