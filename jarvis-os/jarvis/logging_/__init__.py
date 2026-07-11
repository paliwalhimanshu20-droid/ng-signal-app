"""
jarvis.logging_

Operational logging for JARVIS Core — a DIAGNOSTIC aid for engineers,
explicitly NOT the constitutional record (that's jarvis.audit).

Design reference: JARVIS-001 §20 (Logging Architecture), JARVIS-001 §16
(Audit Framework Integration), JARVIS-004 §9 (Coding Standards, Standard 3).

Named `logging_` (trailing underscore) to avoid shadowing Python's
standard library `logging` module, which this package configures and
wraps rather than replaces.

Historical note carried forward deliberately from JARVIS-001 §20: an
earlier, unrelated system in this project's own history relied on
`logger.info()` calls with no `logging.basicConfig()` anywhere in the
call chain — the calls were computed but silently never appeared
anywhere, delaying a real production investigation by several rounds.
This module exists specifically so that failure mode cannot recur in
JARVIS: logging is configured explicitly, once, at Bootstrap, before
any other subsystem is permitted to log anything.
"""

from jarvis.logging_.setup import configure_logging, get_logger

__all__ = ["configure_logging", "get_logger"]
