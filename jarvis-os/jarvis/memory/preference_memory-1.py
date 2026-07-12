"""
jarvis.memory.preference_memory

Sprint-3 Part 5 — Preference Memory.

A flat, persisted key/value store for owner preferences (language,
theme, notification settings, approval preferences, interface settings).
"Future-safe architecture" is honored by keeping keys as free-form
namespaced strings (e.g. "interface.theme") rather than a fixed schema —
new preference keys need no migration, since each key is its own
independently-stored record.
"""

from __future__ import annotations

from typing import Any, Optional

from jarvis.memory.models import MemoryValidationError, PreferenceRecord, utc_now_iso
from jarvis.memory.persistence import PersistenceLayer

_STORAGE_KEY = "preferences"


class PreferenceMemoryStore:
    """Owned exclusively by MemoryManager."""

    def __init__(self, persistence: PersistenceLayer) -> None:
        self._persistence = persistence

    def _load_all(self) -> dict[str, Any]:
        payload, result = self._persistence.read(_STORAGE_KEY)
        if not result.ok or payload is None:
            return {}
        return payload

    def set(self, key: str, value: Any) -> PreferenceRecord:
        if not key:
            raise MemoryValidationError("Preference key must not be empty.")
        all_prefs = self._load_all()
        all_prefs[key] = {"value": value, "updated_at": utc_now_iso()}
        self._persistence.write(_STORAGE_KEY, all_prefs)
        return PreferenceRecord(key=key, value=value, updated_at=all_prefs[key]["updated_at"])

    def get(self, key: str, default: Any = None) -> Any:
        all_prefs = self._load_all()
        entry = all_prefs.get(key)
        return entry["value"] if entry else default

    def get_record(self, key: str) -> Optional[PreferenceRecord]:
        all_prefs = self._load_all()
        entry = all_prefs.get(key)
        if entry is None:
            return None
        return PreferenceRecord(key=key, value=entry["value"], updated_at=entry["updated_at"])

    def all(self) -> dict[str, Any]:
        return {key: entry["value"] for key, entry in self._load_all().items()}

    def delete(self, key: str) -> None:
        all_prefs = self._load_all()
        if key in all_prefs:
            del all_prefs[key]
            self._persistence.write(_STORAGE_KEY, all_prefs)

    def is_healthy(self) -> bool:
        payload, result = self._persistence.read(_STORAGE_KEY)
        if payload is None and result.reason == "file_not_found":
            return True
        return result.ok
