"""
jarvis.memory.knowledge_memory

Sprint-3 Part 6 — Knowledge Memory.

PLACEHOLDER ONLY, per this sprint's explicit scope: "Do NOT implement AI
knowledge. Implement only Structure, Interfaces, Storage, Health, Future
expansion hooks." No content is ever stored here in Sprint-3 — `store()`
and `retrieve()` exist so the interface shape is real and testable, but
the storage they touch is an empty, schema-versioned envelope with a
`entries: []` placeholder field, not a knowledge representation. Building
actual knowledge storage (facts, embeddings, retrieval) is out of scope
per the sprint brief's explicit exclusions (no embeddings, no vector
database, no semantic search, no AI reasoning).
"""

from __future__ import annotations

from typing import Any, Optional

from jarvis.memory.persistence import PersistenceLayer

_STORAGE_KEY = "knowledge_placeholder"


class KnowledgeMemoryPlaceholder:
    """
    Owned exclusively by MemoryManager. Deliberately minimal: this class
    exists to reserve the interface and storage shape a future sprint's
    real Knowledge Memory will fill in, without pre-committing to any
    representation choice this sprint isn't scoped to make.
    """

    def __init__(self, persistence: PersistenceLayer) -> None:
        self._persistence = persistence

    def structure(self) -> dict[str, Any]:
        """Describes the reserved (not-yet-implemented) shape — future expansion hook, not live schema."""
        return {
            "status": "placeholder",
            "implemented": False,
            "reserved_fields": ["entries", "source", "confidence", "last_verified"],
            "note": (
                "Knowledge Memory is a Sprint-3 placeholder only. No AI "
                "knowledge, embeddings, or semantic search exist here yet."
            ),
        }

    def store(self, key: str, value: Any) -> None:
        """Interface exists for future expansion; Sprint-3 stores nothing meaningful — writes an inert placeholder envelope only."""
        payload, result = self._persistence.read(_STORAGE_KEY)
        entries = payload.get("entries", {}) if (payload and result.ok) else {}
        entries[key] = value
        self._persistence.write(_STORAGE_KEY, {"entries": entries})

    def retrieve(self, key: str) -> Optional[Any]:
        payload, result = self._persistence.read(_STORAGE_KEY)
        if not result.ok or payload is None:
            return None
        return payload.get("entries", {}).get(key)

    def is_healthy(self) -> bool:
        payload, result = self._persistence.read(_STORAGE_KEY)
        if payload is None and result.reason == "file_not_found":
            return True
        return result.ok
