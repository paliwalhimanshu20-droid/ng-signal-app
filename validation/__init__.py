"""
validation/__init__.py

NGSP-003B.1 — Validation Center.

Public API is deliberately a single function, per the ticket's
integration rules ("Only expose run_validation()"):

    from validation import run_validation
    summary = run_validation()

Everything else in this package (individual validators, models, report
formatting) is available via its own submodule for future integration
work, but run_validation() is the one call site future code (e.g. a
later Admin Center panel) should depend on.
"""

from .validation_runner import run_validation

__all__ = ["run_validation"]
