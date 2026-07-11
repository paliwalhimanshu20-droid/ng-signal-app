"""
jarvis.logging_.setup

Explicit, one-time logging configuration for JARVIS Core.

Design reference: JARVIS-001 §20. This module's entire reason for
existing is to make sure `logging.basicConfig()` (or equivalent explicit
handler configuration) is ALWAYS called, exactly once, before any other
JARVIS subsystem logs anything — see the package docstring in
jarvis/logging_/__init__.py for why this is treated as non-negotiable
rather than routine hygiene.
"""

from __future__ import annotations

import logging
import sys

_CONFIGURED = False

_LOG_FORMAT = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
_DATE_FORMAT = "%Y-%m-%dT%H:%M:%S%z"


def configure_logging(level: str = "INFO") -> None:
    """
    Configure JARVIS's operational logging, once.

    Idempotent by design: calling this more than once (e.g., in tests, or
    if a future subsystem imports it defensively) does not attach
    duplicate handlers. This is a deliberate safety property, not an
    accident of implementation — duplicate handlers would produce
    duplicate log lines, which is its own small but real diagnostic
    hazard.

    This function writes to stdout unconditionally. Per JARVIS-001 §20,
    operational logs are a diagnostic aid, correlated to but never a
    substitute for the Audit Ledger (jarvis.audit) — nothing in the Audit
    Ledger's completeness may ever depend on this function having been
    called correctly.
    """
    global _CONFIGURED
    if _CONFIGURED:
        return

    root_logger = logging.getLogger("jarvis")
    root_logger.setLevel(level.upper())

    handler = logging.StreamHandler(stream=sys.stdout)
    handler.setFormatter(logging.Formatter(fmt=_LOG_FORMAT, datefmt=_DATE_FORMAT))
    root_logger.addHandler(handler)

    # Explicitly do not propagate to the bare Python root logger — JARVIS's
    # logging namespace is self-contained, so a host application or test
    # runner's own root-logger configuration can never silently swallow or
    # duplicate JARVIS's operational logs.
    root_logger.propagate = False

    _CONFIGURED = True
    root_logger.info("Operational logging configured (level=%s).", level.upper())


def get_logger(name: str) -> logging.Logger:
    """
    Return a logger scoped under the 'jarvis' namespace.

    Modules should always call this rather than `logging.getLogger(__name__)`
    directly with no namespace discipline — keeping everything under a
    single 'jarvis.*' hierarchy is what makes `configure_logging`'s
    single root-handler approach actually cover every subsystem.
    """
    if not name.startswith("jarvis"):
        name = f"jarvis.{name}"
    return logging.getLogger(name)
