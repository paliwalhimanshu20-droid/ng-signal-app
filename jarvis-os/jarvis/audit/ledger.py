"""
jarvis.audit.ledger

Sprint-0 Audit Ledger: a minimal, append-only, file-backed ledger capable
of recording system-level events (bootstrap lifecycle transitions,
subsystem initialization) with Article IV's traceability properties, at
the scope this sprint requires.

Design reference: JARVIS-001 §16, §26 Step 2; JARVIS-004 §16 (this document
does not implement §16's cross-document Regression Testing use of the
Ledger — that requires real task-graph content this sprint doesn't have
yet).

Storage: append-only JSON Lines (one JSON object per line). This is a
deliberately "boring technology" choice (JARVIS-001 §3, Principle 2) — a
flat, append-only file is trivially auditable by hand, requires no
database dependency for Sprint-0, and its append-only nature is easy to
verify by inspection. A future sprint may replace the storage backend
without changing this class's public interface, which is the point of
keeping the interface narrow now.
"""

from __future__ import annotations

import json
import threading
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional
from uuid import uuid4


class AuditLedgerError(Exception):
    """
    Raised when the Audit Ledger cannot be reached or written to.

    Per JARVIS-001 §23 (Error Handling Strategy), this is a
    constitutional-boundary error, not a recoverable operational one —
    any caller catching this must halt the affected operation rather than
    retry-and-proceed. Sprint-0's Bootstrap treats a failure to establish
    Ledger connectivity as fatal, exactly as JARVIS-001 §7 requires.
    """


@dataclass(frozen=True)
class LedgerEntry:
    """
    A single, immutable Audit Ledger entry.

    Sprint-0 fields are intentionally minimal — this is a system-event
    entry shape, not yet the full task-graph entry shape JARVIS-001 §16
    describes for real Orchestrator activity (proposed/evidenced/approved/
    executed/outcome). That richer shape arrives with the Orchestrator's
    real implementation in a later sprint.
    """

    entry_id: str
    timestamp: str
    event_type: str
    message: str
    details: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> str:
        return json.dumps(asdict(self), sort_keys=True)


class AuditLedger:
    """
    Minimal, append-only Audit Ledger for Sprint-0.

    Usage is intentionally narrow: `connect()` to establish and verify
    write access (called during Bootstrap Step 2, before any other
    subsystem initializes), then `record()` for each system event.
    """

    def __init__(self, storage_path: Path) -> None:
        self._storage_path = storage_path
        self._connected = False
        self._lock = threading.Lock()

    @property
    def is_connected(self) -> bool:
        return self._connected

    def connect(self) -> None:
        """
        Establish and verify Audit Ledger connectivity.

        Per JARVIS-001 §7 Step 2, this must succeed before Bootstrap
        proceeds to any subsequent step. Verification here means: the
        storage directory exists (or can be created) and is writable —
        confirmed by an actual write, not merely a permissions check,
        since a permissions check can pass while an underlying write
        still fails for other reasons (disk full, read-only filesystem
        mounted unexpectedly, etc.).
        """
        try:
            self._storage_path.parent.mkdir(parents=True, exist_ok=True)
            # Touch the file to confirm it's writable; do not truncate if
            # it already exists, since prior entries must never be lost.
            with open(self._storage_path, "a", encoding="utf-8"):
                pass
        except OSError as exc:
            raise AuditLedgerError(
                f"Could not establish Audit Ledger connectivity at "
                f"{self._storage_path}: {exc}"
            ) from exc

        self._connected = True

    def record(
        self,
        event_type: str,
        message: str,
        details: Optional[dict[str, Any]] = None,
    ) -> LedgerEntry:
        """
        Append one entry to the Ledger.

        Raises AuditLedgerError if the Ledger has not been connected, or
        if the write itself fails — both are treated as fatal by any
        caller following JARVIS-001 §23's error-handling strategy, never
        silently swallowed.
        """
        if not self._connected:
            raise AuditLedgerError(
                "AuditLedger.record() called before connect(). "
                "Per JARVIS-001 §7, no auditable event may be recorded "
                "before Ledger connectivity is established."
            )

        entry = LedgerEntry(
            entry_id=str(uuid4()),
            timestamp=datetime.now(timezone.utc).isoformat(),
            event_type=event_type,
            message=message,
            details=details or {},
        )

        try:
            with self._lock:
                with open(self._storage_path, "a", encoding="utf-8") as handle:
                    handle.write(entry.to_json() + "\n")
        except OSError as exc:
            raise AuditLedgerError(f"Failed to write Audit Ledger entry: {exc}") from exc

        return entry

    def read_all(self) -> list[LedgerEntry]:
        """
        Read every entry currently in the Ledger, in append order.

        Sprint-0 scope: a simple full-file read, adequate for a bootstrap-
        only ledger. Not intended for use once real task-graph volume
        exists — a later sprint should add indexed/paginated reads before
        this is used against production-scale data.
        """
        if not self._storage_path.exists():
            return []

        entries: list[LedgerEntry] = []
        with open(self._storage_path, "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                raw = json.loads(line)
                entries.append(LedgerEntry(**raw))
        return entries
