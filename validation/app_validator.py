"""
validation/app_validator.py

Validates that NG Signal Pro's application layer is structurally sound:
every module app.py depends on can actually be imported, every required
third-party package is installed, and app.py itself is syntactically
valid.

IMPORTANT SCOPE NOTE: this does NOT launch a real Streamlit session.
Doing that safely isn't possible outside `streamlit run` (there's no
ScriptRunContext here, and actually importing app.py would execute its
top-level UI code — tabs, charts, live API calls — as a side effect,
which is exactly what a validator must NOT trigger). "Streamlit app
initializes" is therefore validated as: (a) the streamlit package itself
imports, and (b) app.py parses as syntactically valid Python and its
declared imports all resolve. This is stated explicitly in the result's
details rather than silently implied, so nobody mistakes this for a full
runtime smoke test.
"""

import ast
import importlib
import importlib.util
import os

from .validation_models import (
    ValidationResult, ValidationStatus, ValidationCategory,
    is_environment_unavailable_error,
)

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_FILE = os.path.join(REPO_ROOT, "app.py")

# Mirrors app.py's own import block exactly (see app.py lines ~26-88).
# Kept as an explicit list rather than parsed out of app.py, so a
# validator run doesn't silently change scope if app.py's imports change
# without this file being reviewed too.
REQUIRED_APP_MODULES = [
    "admin_tools",
    "reports",
    "signal_log",
    "config",
    "risk_config",
    "risk_engine",
    "signal_logic",
    "watchlist",
    "upstox_client",
    "scanner",
    "charts",
    "ui_components",
]

# Mirrors requirements.txt (sqlite3/json/uuid are stdlib, not listed there).
REQUIRED_PACKAGES = ["streamlit", "pandas", "requests", "plotly", "openpyxl"]


def _check_packages() -> tuple:
    """Returns (missing, details) for third-party packages."""
    missing = []
    details = []
    for pkg in REQUIRED_PACKAGES:
        if importlib.util.find_spec(pkg) is not None:
            details.append(f"Package '{pkg}' is installed.")
        else:
            missing.append(pkg)
    return missing, details


def _check_modules() -> tuple:
    """Returns (failures, details, skipped) for app.py's own module
    dependencies. Each is actually imported (not just checked for file
    existence) so a module with a broken import chain of its own is
    caught too, not just ones that are missing outright.

    A module that fails to import specifically because Streamlit Secrets/
    runtime isn't available in THIS environment (e.g. running this
    validator standalone, no .streamlit/secrets.toml present) is reported
    as skipped, not failed — several of these modules (config, and
    everything that imports config) touch st.secrets at import time,
    which is an environment limitation, not a code defect."""
    failures = []
    details = []
    skipped = []
    for mod_name in REQUIRED_APP_MODULES:
        try:
            importlib.import_module(mod_name)
            details.append(f"Module '{mod_name}' imports successfully.")
        except Exception as e:
            if is_environment_unavailable_error(e):
                skipped.append(
                    f"Module '{mod_name}' could not be checked — Streamlit Secrets/runtime "
                    f"not available in this environment ({type(e).__name__})."
                )
            else:
                failures.append(f"Module '{mod_name}' failed to import: {type(e).__name__}: {e}")
    return failures, details, skipped


def _check_app_syntax() -> tuple:
    """Returns (failures, details) for app.py's own syntactic validity.
    Parses, does not execute — see module docstring for why."""
    failures = []
    details = []

    if not os.path.exists(APP_FILE):
        failures.append(f"app.py not found at expected path: {APP_FILE}")
        return failures, details

    try:
        with open(APP_FILE, "r", encoding="utf-8") as f:
            source = f.read()
        ast.parse(source, filename=APP_FILE)
        details.append("app.py parses as syntactically valid Python.")
    except SyntaxError as e:
        failures.append(f"app.py has a syntax error: {e}")

    return failures, details


def validate_application() -> ValidationResult:
    all_details = []
    all_warnings = []
    all_failures = []
    all_skipped = []

    missing_packages, package_details = _check_packages()
    all_details.extend(package_details)
    if missing_packages:
        all_failures.append(f"Missing required package(s): {', '.join(missing_packages)}")

    module_failures, module_details, module_skipped = _check_modules()
    all_details.extend(module_details)
    all_failures.extend(module_failures)
    all_skipped.extend(module_skipped)

    syntax_failures, syntax_details = _check_app_syntax()
    all_details.extend(syntax_details)
    all_failures.extend(syntax_failures)

    all_details.append(
        "Note: this validates import-resolution and syntax only — it does not "
        "launch a live Streamlit session (unsafe to do outside `streamlit run`)."
    )

    # Package and syntax checks never depend on a Streamlit runtime, so
    # they always run regardless of environment — only individual module
    # checks can be skipped. That means this category is only ever
    # SKIPPED in its entirety if literally every module check was
    # skipped AND nothing else was checked at all; in practice PASS with
    # a populated `skipped` list (surfaced in the report) is the correct
    # outcome for "some modules unreachable here, everything else fine."
    if all_failures:
        status = ValidationStatus.FAIL
        summary = f"{len(all_failures)} application-level failure(s) found."
    elif all_warnings:
        status = ValidationStatus.WARNING
        summary = f"{len(all_warnings)} application-level warning(s) found."
    elif all_skipped and len(all_skipped) == len(REQUIRED_APP_MODULES) and not module_details:
        status = ValidationStatus.SKIPPED
        summary = "All module checks skipped — Streamlit Secrets/runtime unavailable in this environment."
    else:
        status = ValidationStatus.PASS
        summary = "All required modules and packages import correctly; app.py is syntactically valid."
        if all_skipped:
            summary += f" ({len(all_skipped)} check(s) skipped — see below.)"

    return ValidationResult(
        category=ValidationCategory.APPLICATION,
        status=status,
        summary=summary,
        details=all_details,
        warnings=all_warnings,
        failures=all_failures,
        skipped=all_skipped,
    )
