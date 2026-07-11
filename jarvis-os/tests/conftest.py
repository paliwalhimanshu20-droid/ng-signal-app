"""
Shared pytest fixtures.

`booted_core` reuses exactly the same boot pattern test_bootstrap.py
established for Sprint-0 — factored out here so Sprint-2's integration
tests (which need a real, running JarvisCore to build an ExecutiveKernel
against) don't duplicate it, per this sprint's "no duplicated logic"
requirement applied to the test suite itself.
"""

from __future__ import annotations

import json

import pytest

from jarvis.constitution.loader import REQUIRED_ARTICLE_IDS
from jarvis.core.bootstrap import boot


@pytest.fixture()
def booted_core(tmp_path, monkeypatch):
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
    monkeypatch.chdir(tmp_path)

    core = boot()
    yield core
    if core.ready:
        core.shutdown()
