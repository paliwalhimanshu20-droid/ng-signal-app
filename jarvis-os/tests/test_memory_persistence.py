"""Tests for jarvis.memory.persistence — Sprint-3 Part 7."""

from __future__ import annotations

import json

import pytest

from jarvis.memory.persistence import PersistenceError, PersistenceLayer


def test_write_then_read_roundtrips(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.write("thing", {"a": 1, "b": "two"})

    payload, result = layer.read("thing")
    assert result.ok
    assert payload == {"a": 1, "b": "two"}


def test_read_missing_key_reports_not_found(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    payload, result = layer.read("nothing")
    assert payload is None
    assert not result.ok
    assert result.reason == "file_not_found"


def test_corrupted_primary_falls_back_to_backup(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.write("thing", {"v": 1})
    layer.write("thing", {"v": 2})  # rotates v1 to .bak, v2 becomes primary

    # Corrupt the primary file directly.
    primary = tmp_path / "thing.json"
    primary.write_text("{not valid json", encoding="utf-8")

    payload, result = layer.read("thing")
    assert result.ok
    assert "recovered_from_backup" in result.reason
    assert payload == {"v": 1}


def test_checksum_mismatch_is_detected(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.write("thing", {"v": 1})

    primary = tmp_path / "thing.json"
    envelope = json.loads(primary.read_text(encoding="utf-8"))
    envelope["payload"] = {"v": "tampered"}
    primary.write_text(json.dumps(envelope), encoding="utf-8")

    payload, result = layer.read("thing")
    assert not result.ok
    assert "checksum_mismatch" in result.reason or "backup_failed" in result.reason


def test_schema_version_mismatch_is_detected(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.write("thing", {"v": 1})

    primary = tmp_path / "thing.json"
    envelope = json.loads(primary.read_text(encoding="utf-8"))
    envelope["schema_version"] = 999
    primary.write_text(json.dumps(envelope), encoding="utf-8")

    payload, result = layer.read("thing")
    assert not result.ok


def test_delete_removes_primary_and_backup(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.write("thing", {"v": 1})
    layer.write("thing", {"v": 2})
    layer.delete("thing")

    payload, result = layer.read("thing")
    assert payload is None
    assert not result.ok


def test_append_and_read_lines(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.append_line("log", {"n": 1})
    layer.append_line("log", {"n": 2})

    records = layer.read_lines("log")
    assert [r["n"] for r in records] == [1, 2]


def test_read_lines_skips_corrupt_line_but_keeps_rest(tmp_path):
    layer = PersistenceLayer(tmp_path)
    layer.connect()
    layer.append_line("log", {"n": 1})

    path = tmp_path / "log.jsonl"
    with open(path, "a", encoding="utf-8") as handle:
        handle.write("{not valid json\n")

    layer.append_line("log", {"n": 3})

    records = layer.read_lines("log")
    assert [r["n"] for r in records] == [1, 3]


def test_connect_fails_gracefully_on_unwritable_dir(tmp_path, monkeypatch):
    layer = PersistenceLayer(tmp_path / "sub")

    def fail_mkdir(*args, **kwargs):
        raise OSError("permission denied")

    monkeypatch.setattr("pathlib.Path.mkdir", fail_mkdir)
    with pytest.raises(PersistenceError):
        layer.connect()
