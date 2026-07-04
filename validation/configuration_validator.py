"""
validation/configuration_validator.py

Validates that required configuration files and settings exist —
PRESENCE ONLY. No secret value is ever read into a report, logged, or
returned in a ValidationResult. Every check below reports a boolean
("set" / "not set") derived from truthiness, never the underlying string.
"""

import os

from .validation_models import (
    ValidationResult, ValidationStatus, ValidationCategory,
    is_environment_unavailable_error,
)

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Every config file NG Signal Pro currently depends on. Presence-only
# check — this list is deliberately explicit rather than a glob, so
# adding a new config file is a conscious decision to update this
# validator too.
REQUIRED_CONFIG_FILES = [
    "config.py",
    "risk_config.py",
    "market_config.py",
    "technical_config.py",
    os.path.join("research_config", "settings.py"),
    os.path.join("instrument_config", "settings.py"),
]

# config.py attributes that must exist AND be non-empty for the app to
# function — split into "secret-like" (never shown) and "structural"
# (safe to confirm exists, values are non-sensitive config data).
REQUIRED_SECRET_ATTRS = ["UPSTOX_ACCESS_TOKEN", "GITHUB_TOKEN", "GITHUB_REPO"]
REQUIRED_STRUCTURAL_ATTRS = [
    "GITHUB_BRANCH", "IST", "SIGNAL_LOG_COLUMNS",
    "COMMODITY_DEFINITIONS", "SECTOR_MAP", "SECTOR_ORDER",
]


def _check_files() -> tuple:
    missing = []
    details = []
    for rel_path in REQUIRED_CONFIG_FILES:
        full_path = os.path.join(REPO_ROOT, rel_path)
        if os.path.exists(full_path):
            details.append(f"Config file present: {rel_path}")
        else:
            missing.append(rel_path)
    return missing, details


def _check_config_attrs() -> tuple:
    """Imports config.py once and checks attribute presence/truthiness.
    Never includes the actual attribute value anywhere in the return.

    If config.py itself can't be imported specifically because Streamlit
    Secrets/runtime isn't available in this environment (e.g. running
    this validator standalone with no .streamlit/secrets.toml), every
    attribute check is reported as skipped rather than failed — nothing
    about config.py's actual correctness can be determined here, so
    calling it a failure would be misleading. A genuine import error
    (any other exception) still fails as before."""
    details = []
    warnings = []
    failures = []
    skipped = []

    try:
        import config as app_config
    except Exception as e:
        if is_environment_unavailable_error(e):
            skipped.append(
                f"All config.py attribute checks skipped — Streamlit Secrets/runtime "
                f"not available in this environment ({type(e).__name__})."
            )
            return details, warnings, failures, skipped
        failures.append(f"config.py could not be imported: {type(e).__name__}: {e}")
        return details, warnings, failures, skipped

    for attr in REQUIRED_STRUCTURAL_ATTRS:
        if hasattr(app_config, attr):
            details.append(f"config.{attr} is defined.")
        else:
            failures.append(f"config.{attr} is missing — required for the app to function.")

    for attr in REQUIRED_SECRET_ATTRS:
        if not hasattr(app_config, attr):
            failures.append(f"config.{attr} is missing entirely.")
            continue
        value = getattr(app_config, attr)
        if value:
            details.append(f"config.{attr} is SET (value not shown).")
        else:
            warnings.append(
                f"config.{attr} is NOT SET (empty). This must be configured in "
                f"Streamlit Secrets before live signals/pushes will work."
            )

    return details, warnings, failures, skipped


def validate_configuration() -> ValidationResult:
    details = []
    warnings = []
    failures = []
    skipped = []

    missing_files, file_details = _check_files()
    details.extend(file_details)
    if missing_files:
        failures.append(f"Missing required config file(s): {', '.join(missing_files)}")

    attr_details, attr_warnings, attr_failures, attr_skipped = _check_config_attrs()
    details.extend(attr_details)
    warnings.extend(attr_warnings)
    failures.extend(attr_failures)
    skipped.extend(attr_skipped)

    if attr_skipped:
        details.append(
            "GitHub/Upstox secret presence could not be checked in this environment "
            "(see skipped items) — file-existence checks above are unaffected."
        )
    else:
        github_configured = not any("GITHUB_TOKEN" in w or "GITHUB_REPO" in w for w in attr_warnings + attr_failures)
        upstox_configured = not any("UPSTOX_ACCESS_TOKEN" in w for w in attr_warnings + attr_failures)
        details.append(f"GitHub configuration (token + repo): {'present' if github_configured else 'incomplete — see warnings/failures above'}.")
        details.append(f"Upstox configuration (access token): {'present' if upstox_configured else 'incomplete — see warnings/failures above'}.")

    # File-existence checks never depend on a Streamlit runtime, so this
    # category is only ever entirely SKIPPED if literally nothing else
    # could be checked; a skipped secrets check alongside passing file
    # checks correctly stays PASS (with the skip surfaced for visibility).
    if failures:
        status = ValidationStatus.FAIL
        summary = f"{len(failures)} configuration failure(s) found."
    elif warnings:
        status = ValidationStatus.WARNING
        summary = f"Configuration structurally complete; {len(warnings)} secret(s) not yet set."
    elif skipped and not file_details:
        status = ValidationStatus.SKIPPED
        summary = "Configuration checks skipped — Streamlit Secrets/runtime unavailable in this environment."
    else:
        status = ValidationStatus.PASS
        summary = "All required configuration files, settings, and secrets are present."
        if skipped:
            summary += f" ({len(skipped)} secret check(s) skipped in this environment — see below.)"

    return ValidationResult(
        category=ValidationCategory.CONFIGURATION,
        status=status,
        summary=summary,
        details=details,
        warnings=warnings,
        failures=failures,
        skipped=skipped,
    )
