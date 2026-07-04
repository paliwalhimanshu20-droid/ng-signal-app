"""
validation/validation_runner.py

Single entry point for the Validation Center: run_validation(). Executes
every validator, collects results, and computes an overall HealthScore
and OverallStatus.

Per NGSP-003B.1's integration rules, this is NOT wired into app.py or the
Admin Center yet — it's a standalone function future integration can call.
"""

import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from .validation_models import ValidationSummary, HealthScore, OverallStatus, ValidationStatus
from .app_validator import validate_application
from .database_validator import validate_database
from .dashboard_validator import validate_dashboard
from .configuration_validator import validate_configuration

# Equal weighting across the four v1 validators. When future validators
# (Trading, Security, Regression, Architecture, Risk, AI Governance —
# see NGSP-003B.1's Future Compatibility section) are added, update this
# list; the weight-per-category is always 100 / len(VALIDATOR_FUNCTIONS),
# so no other code needs to change.
VALIDATOR_FUNCTIONS = [
    validate_application,
    validate_database,
    validate_dashboard,
    validate_configuration,
]

# How much of a category's weight survives at each status level.
# SKIPPED is intentionally absent here — see _calculate_health_score()
# below, which excludes skipped categories from scoring entirely rather
# than assigning them a weight, so "not applicable in this environment"
# neither helps nor hurts the health percentage.
_STATUS_WEIGHT = {
    ValidationStatus.PASS: 1.0,
    ValidationStatus.WARNING: 0.6,
    ValidationStatus.FAIL: 0.0,
}


def _calculate_health_score(results: list) -> HealthScore:
    scored = [r for r in results if r.status != ValidationStatus.SKIPPED]
    if not scored:
        # Everything was skipped — no basis to score at all. 0% here
        # means "unknown," not "unhealthy"; the report's Skipped section
        # makes that distinction clear to the reader.
        return HealthScore(percent=0.0)

    per_category_weight = 100.0 / len(scored)
    total = sum(_STATUS_WEIGHT[r.status] * per_category_weight for r in scored)
    return HealthScore(percent=round(total, 1))


def _determine_overall_status(results: list) -> OverallStatus:
    if any(r.status == ValidationStatus.FAIL for r in results):
        return OverallStatus.NOT_READY
    if any(r.status == ValidationStatus.WARNING for r in results):
        return OverallStatus.READY_WITH_WARNINGS
    return OverallStatus.READY


def run_validation() -> ValidationSummary:
    """
    Executes every registered validator and returns one consolidated
    ValidationSummary. Each validator is independently wrapped so that
    one validator crashing (e.g. an unrelated import error) doesn't
    prevent the others from running or reporting.
    """
    from .validation_models import ValidationResult, ValidationCategory

    results = []
    for validator_fn in VALIDATOR_FUNCTIONS:
        try:
            results.append(validator_fn())
        except Exception as e:
            # A validator itself crashing is, in effect, a FAIL for that
            # category — reported rather than allowed to abort the whole run.
            category_guess = getattr(validator_fn, "__module__", "unknown").split(".")[-1]
            results.append(ValidationResult(
                category=ValidationCategory.APPLICATION,  # best-effort; see summary text for the real source
                status=ValidationStatus.FAIL,
                summary=f"Validator '{validator_fn.__name__}' crashed: {type(e).__name__}: {e}",
                failures=[f"Validator '{validator_fn.__name__}' ({category_guess}) raised {type(e).__name__}: {e}"],
            ))

    health_score = _calculate_health_score(results)
    overall_status = _determine_overall_status(results)

    return ValidationSummary(
        results=results,
        health_score=health_score,
        overall_status=overall_status,
    )


if __name__ == "__main__":
    # Manual, ad-hoc run: `python -m validation.validation_runner` from repo root.
    from .validation_report import print_report
    summary = run_validation()
    print_report(summary)
