"""
Tests for jarvis.constitution.loader.

Covers the structural validation JARVIS-001 §2/§7 require: a valid
Constitution reference loads correctly; a Constitution missing any of the
seven Articles fails closed, per JARVIS-001 §2's explicit example
("a constitution missing Article II entirely should fail the check even
if its version string is 'correct'").
"""

from __future__ import annotations

import json

import pytest

from jarvis.constitution.loader import (
    ConstitutionValidationError,
    REQUIRED_ARTICLE_IDS,
    load_constitution,
)

VALID_CONSTITUTION = {
    "constitution_version": "1.0.0",
    "articles": [
        {"id": article_id, "name": f"Article {article_id}", "summary": "Test summary."}
        for article_id in REQUIRED_ARTICLE_IDS
    ],
}


def _write_constitution(tmp_path, data: dict):
    path = tmp_path / "constitution.json"
    path.write_text(json.dumps(data), encoding="utf-8")
    return path


def test_valid_constitution_loads(tmp_path):
    path = _write_constitution(tmp_path, VALID_CONSTITUTION)
    constitution = load_constitution(path)
    assert constitution.version == "1.0.0"
    assert len(constitution.articles) == 7
    for article_id in REQUIRED_ARTICLE_IDS:
        assert constitution.has_article(article_id)


def test_missing_file_raises():
    with pytest.raises(ConstitutionValidationError):
        load_constitution("does/not/exist.json")


def test_missing_article_ii_fails_even_with_correct_version(tmp_path):
    """
    Direct test of JARVIS-001 §2's own stated example: a constitution
    missing Article II must fail structurally, even if its version string
    looks correct.
    """
    broken = {
        "constitution_version": "1.0.0",
        "articles": [
            entry for entry in VALID_CONSTITUTION["articles"] if entry["id"] != "II"
        ],
    }
    path = _write_constitution(tmp_path, broken)

    with pytest.raises(ConstitutionValidationError, match="II"):
        load_constitution(path)


def test_malformed_json_fails(tmp_path):
    path = tmp_path / "constitution.json"
    path.write_text("{ not valid json", encoding="utf-8")

    with pytest.raises(ConstitutionValidationError):
        load_constitution(path)


def test_missing_version_fails(tmp_path):
    broken = {"articles": VALID_CONSTITUTION["articles"]}
    path = _write_constitution(tmp_path, broken)

    with pytest.raises(ConstitutionValidationError):
        load_constitution(path)
