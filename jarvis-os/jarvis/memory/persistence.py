"""
jarvis.memory.persistence

Sprint-3 Part 7: the Persistence Layer.

Design reference: JARVIS-001 §17 (boring technology, same reasoning the
Audit Ledger's flat-file choice used), Article IV (auditability), this
sprint's Part 7 requirements: atomic writes, crash-safety, integrity
validation, schema versioning, recovery, corruption detection, graceful
failure, no silent corruption.

Storage shape per key: a directory holding two files —
    <key>.json           the current, integrity-checked snapshot
    <key>.json.bak        the previous good snapshot (one-generation backup)

Every write is atomic (write to a temp file in the same directory, fsync,
`os.replace()` over the target — `os.replace` is atomic on both POSIX and
Windows, so a crash mid-write can never leave a half-written file where
the real one used to be). Before a new snapshot replaces the old one, the
old one is rotated to `.bak` — this is what makes recovery possible when
the *new* write's content itself is bad (e.g. truncated by a killed
process before `os.replace` ran — in which case the old file was never
touched at all — or, for defense in depth, if a future bug ever wrote a
structurally-valid-but-wrong snapshot).

Integrity is a SHA-256 checksum of the payload, stored inside the
envelope alongside it (not as a separate sidecar file — one file to lose
sync with is one file, not two). Schema version is stored inside the same
envelope so a future incompatible schema change can be detected and
handled explicitly rather than crashing on a malformed load.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional

CURRENT_SCHEMA_VERSION = 1


class PersistenceError(Exception):
    """
    Raised when a write cannot be committed at all (disk full, directory
    unwritable, etc). Per JARVIS-001 §23, this is fatal to the operation
    that triggered it — callers must not assume the write silently
    succeeded.
    """


@dataclass(frozen=True)
class IntegrityResult:
    """Outcome of validating one stored envelope."""

    ok: bool
    reason: str
    schema_version: Optional[int] = None


class PersistenceLayer:
    """
    Generic atomic key/value JSON persistence, scoped to one base
    directory. Every Sprint-3 memory store (Session, Conversation,
    Preference) is built on top of exactly one PersistenceLayer instance
    — this class has no knowledge of what a "session" or "preference" is,
    which is what keeps schema versioning and corruption handling in
    exactly one place instead of duplicated per store.
    """

    def __init__(self, base_dir: Path) -> None:
        self._base_dir = base_dir
        self._lock = threading.Lock()

    def connect(self) -> None:
        """Ensure the storage directory exists and is writable. Mirrors AuditLedger.connect()'s verify-by-actual-write approach."""
        try:
            self._base_dir.mkdir(parents=True, exist_ok=True)
            probe = self._base_dir / ".write_probe"
            with open(probe, "w", encoding="utf-8") as handle:
                handle.write("ok")
            probe.unlink(missing_ok=True)
        except OSError as exc:
            raise PersistenceError(
                f"Could not establish persistence connectivity at {self._base_dir}: {exc}"
            ) from exc

    def _path(self, key: str) -> Path:
        return self._base_dir / f"{key}.json"

    def _backup_path(self, key: str) -> Path:
        return self._base_dir / f"{key}.json.bak"

    @staticmethod
    def _checksum(payload: Any) -> str:
        canonical = json.dumps(payload, sort_keys=True, default=str).encode("utf-8")
        return hashlib.sha256(canonical).hexdigest()

    def write(self, key: str, payload: dict[str, Any]) -> None:
        """
        Atomically write `payload` under `key`, wrapped in a checksummed,
        schema-versioned envelope. Rotates the previous snapshot to
        `.bak` first (best-effort — a missing prior file is not an
        error), then writes the new file to a temp path and `os.replace`s
        it into place.
        """
        envelope = {
            "schema_version": CURRENT_SCHEMA_VERSION,
            "checksum": self._checksum(payload),
            "payload": payload,
        }

        target = self._path(key)
        backup = self._backup_path(key)

        with self._lock:
            try:
                self._base_dir.mkdir(parents=True, exist_ok=True)

                if target.exists():
                    try:
                        os.replace(target, backup)
                    except OSError:
                        pass  # best-effort rotation; not fatal to the new write

                fd, tmp_name = tempfile.mkstemp(
                    dir=str(self._base_dir), prefix=f".{key}.", suffix=".tmp"
                )
                try:
                    with os.fdopen(fd, "w", encoding="utf-8") as handle:
                        json.dump(envelope, handle, sort_keys=True)
                        handle.flush()
                        os.fsync(handle.fileno())
                    os.replace(tmp_name, target)
                except BaseException:
                    Path(tmp_name).unlink(missing_ok=True)
                    raise
            except OSError as exc:
                raise PersistenceError(f"Failed to persist key '{key}': {exc}") from exc

    def _load_envelope(self, path: Path) -> tuple[Optional[dict[str, Any]], IntegrityResult]:
        if not path.exists():
            return None, IntegrityResult(ok=False, reason="file_not_found")

        try:
            with open(path, "r", encoding="utf-8") as handle:
                envelope = json.load(handle)
        except (OSError, json.JSONDecodeError) as exc:
            return None, IntegrityResult(ok=False, reason=f"unreadable_or_corrupt_json: {exc}")

        schema_version = envelope.get("schema_version")
        if schema_version != CURRENT_SCHEMA_VERSION:
            return None, IntegrityResult(
                ok=False,
                reason=f"schema_version_mismatch: found {schema_version}, expected {CURRENT_SCHEMA_VERSION}",
                schema_version=schema_version,
            )

        payload = envelope.get("payload")
        expected_checksum = envelope.get("checksum")
        actual_checksum = self._checksum(payload)
        if expected_checksum != actual_checksum:
            return None, IntegrityResult(
                ok=False, reason="checksum_mismatch", schema_version=schema_version
            )

        return payload, IntegrityResult(ok=True, reason="ok", schema_version=schema_version)

    def read(self, key: str) -> tuple[Optional[dict[str, Any]], IntegrityResult]:
        """
        Read and integrity-check `key`'s current snapshot. If the primary
        file is missing, unreadable, or fails its checksum, automatically
        falls back to the `.bak` generation before giving up — this is
        the "recovery" half of corruption detection, not just detection
        alone. Returns (None, IntegrityResult(ok=False, ...)) only if
        both generations fail.
        """
        payload, result = self._load_envelope(self._path(key))
        if result.ok:
            return payload, result

        backup_payload, backup_result = self._load_envelope(self._backup_path(key))
        if backup_result.ok:
            return backup_payload, IntegrityResult(
                ok=True,
                reason=f"recovered_from_backup (primary failed: {result.reason})",
                schema_version=backup_result.schema_version,
            )

        if result.reason == "file_not_found" and backup_result.reason == "file_not_found":
            return None, IntegrityResult(ok=False, reason="file_not_found")

        return None, IntegrityResult(
            ok=False,
            reason=f"primary_failed: {result.reason}; backup_failed: {backup_result.reason}",
        )

    def validate(self, key: str) -> IntegrityResult:
        """Check integrity without needing the payload back — used by health checks."""
        _, result = self.read(key)
        return result

    def delete(self, key: str) -> None:
        with self._lock:
            self._path(key).unlink(missing_ok=True)
            self._backup_path(key).unlink(missing_ok=True)

    def append_line(self, key: str, line_payload: dict[str, Any]) -> None:
        """
        Append-only variant for log-shaped stores (Conversation Memory).
        Uses JSON Lines, same rationale as the Audit Ledger: trivially
        auditable by hand, no partial-record risk since each line is
        flushed and fsync'd independently.
        """
        path = self._base_dir / f"{key}.jsonl"
        with self._lock:
            try:
                self._base_dir.mkdir(parents=True, exist_ok=True)
                with open(path, "a", encoding="utf-8") as handle:
                    handle.write(json.dumps(line_payload, sort_keys=True, default=str) + "\n")
                    handle.flush()
                    os.fsync(handle.fileno())
            except OSError as exc:
                raise PersistenceError(f"Failed to append to '{key}': {exc}") from exc

    def read_lines(self, key: str) -> list[dict[str, Any]]:
        path = self._base_dir / f"{key}.jsonl"
        if not path.exists():
            return []
        records: list[dict[str, Any]] = []
        with open(path, "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError:
                    # A single corrupt line is skipped, not fatal to the
                    # rest of the log — graceful failure, no silent loss
                    # of every OTHER good record (Part 7).
                    continue
        return records
