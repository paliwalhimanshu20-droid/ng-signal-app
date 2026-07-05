"""
warehouse.core.logging_config
================================

Comprehensive logging framework for the warehouse foundation.

Two output modes are supported, chosen via WarehouseConfig.logging.format:
    - "text"  → human-readable, for local/mobile-triggered runs and
                Streamlit-side viewing (matches existing NGSP log style)
    - "json"  → structured one-line-per-record JSON, for future ingestion
                into a log aggregator or the Research DB's operational logs

All warehouse modules obtain their logger via `get_logger(__name__)` —
never `logging.getLogger` directly — so every log line is guaranteed to
flow through the same handler/formatter configuration and the same
rotating file sink.
"""

from __future__ import annotations

import json
import logging
import logging.handlers
import sys
from pathlib import Path
from typing import Any, Optional

_CONFIGURED = False
_LOG_FORMAT_TEXT = (
    "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"
)


class JsonFormatter(logging.Formatter):
    """Renders each log record as a single JSON line."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        # Allow callers to attach structured context via `extra={"context": {...}}`
        context = getattr(record, "context", None)
        if context:
            payload["context"] = context
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


def configure_logging(
    *,
    log_dir: Optional[Path] = None,
    level: str = "INFO",
    fmt: str = "text",
    max_bytes: int = 10 * 1024 * 1024,
    backup_count: int = 5,
    also_console: bool = True,
) -> None:
    """
    Configure the root `warehouse` logger namespace exactly once per process.

    Safe to call multiple times — subsequent calls are no-ops unless
    `force=True` semantics are needed (not exposed; re-configuring mid-run
    is intentionally discouraged to avoid duplicate handlers).

    Args:
        log_dir: Directory to write rotating log files into. If None, only
            console logging is configured (useful for tests/sandboxes).
        level: Root log level name, e.g. "INFO", "DEBUG".
        fmt: "text" or "json".
        max_bytes: Rotating file handler max size before rollover.
        backup_count: Number of rotated backups to keep.
        also_console: Whether to also attach a console (stderr) handler.
    """
    global _CONFIGURED
    if _CONFIGURED:
        return

    root = logging.getLogger("warehouse")
    root.setLevel(level.upper())
    root.propagate = False

    formatter: logging.Formatter
    if fmt == "json":
        formatter = JsonFormatter()
    else:
        formatter = logging.Formatter(_LOG_FORMAT_TEXT, datefmt="%Y-%m-%d %H:%M:%S")

    if log_dir is not None:
        log_dir = Path(log_dir)
        log_dir.mkdir(parents=True, exist_ok=True)
        file_handler = logging.handlers.RotatingFileHandler(
            log_dir / "warehouse.log",
            maxBytes=max_bytes,
            backupCount=backup_count,
            encoding="utf-8",
        )
        file_handler.setFormatter(formatter)
        root.addHandler(file_handler)

    if also_console or log_dir is None:
        console_handler = logging.StreamHandler(stream=sys.stdout)
        console_handler.setFormatter(formatter)
        root.addHandler(console_handler)

    _CONFIGURED = True


def get_logger(name: str) -> logging.Logger:
    """
    Return a logger nested under the `warehouse` namespace.

    If `configure_logging()` has not yet been called, this falls back to a
    sane default (console-only, INFO level) so that importing a module and
    logging from it never crashes or silently drops output, even outside a
    fully bootstrapped application.
    """
    if not _CONFIGURED:
        configure_logging(log_dir=None, level="INFO", fmt="text")

    if not name.startswith("warehouse"):
        name = f"warehouse.{name}"
    return logging.getLogger(name)


def log_with_context(
    logger: logging.Logger,
    level: int,
    message: str,
    **context: Any,
) -> None:
    """
    Helper for emitting a log line with structured context that survives
    into JSON output mode, e.g.:

        log_with_context(logger, logging.INFO, "Partition written",
                          instrument_id="NSE_EQ_RELIANCE", timeframe="1day",
                          rows=8123, path=str(path))
    """
    logger.log(level, message, extra={"context": context})
