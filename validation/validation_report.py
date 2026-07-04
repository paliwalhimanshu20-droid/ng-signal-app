"""
validation/validation_report.py

Formats a ValidationSummary into the structured, human-readable report
NGSP-003B.1 specifies. Text output only — no PDF generation, per the
ticket's explicit scope.
"""

from .validation_models import ValidationSummary, ValidationCategory, OverallStatus

_CATEGORY_ORDER = [
    ValidationCategory.APPLICATION,
    ValidationCategory.DATABASE,
    ValidationCategory.DASHBOARD,
    ValidationCategory.CONFIGURATION,
]

_DEPLOYMENT_STATUS_LABEL = {
    OverallStatus.READY: "READY",
    OverallStatus.READY_WITH_WARNINGS: "READY (WITH WARNINGS)",
    OverallStatus.NOT_READY: "NOT READY",
}


def build_report_text(summary: ValidationSummary) -> str:
    lines = []
    lines.append("=" * 50)
    lines.append("NG SIGNAL PRO VALIDATION REPORT")
    lines.append("=" * 50)
    lines.append("")

    for category in _CATEGORY_ORDER:
        result = summary.result_for(category)
        if result is None:
            lines.append(f"{category.value:.<20} SKIPPED")
            continue
        lines.append(f"{category.value:.<20} {result.status.value}")

    lines.append("-" * 50)
    lines.append("")
    lines.append("Overall Health")
    lines.append(str(summary.health_score))
    lines.append("")
    lines.append("Deployment Status")
    lines.append(_DEPLOYMENT_STATUS_LABEL[summary.overall_status])
    lines.append("")

    warnings = summary.all_warnings
    lines.append("Warnings")
    if warnings:
        for w in warnings:
            lines.append(f"  - {w}")
    else:
        lines.append("None")
    lines.append("")

    failures = summary.all_failures
    lines.append("Failures")
    if failures:
        for f in failures:
            lines.append(f"  - {f}")
    else:
        lines.append("None")
    lines.append("")

    skipped = summary.all_skipped
    lines.append("Skipped")
    if skipped:
        for s in skipped:
            lines.append(f"  - {s}")
    else:
        lines.append("None")
    lines.append("")

    recommendations = _build_recommendations(summary)
    lines.append("Recommendations")
    if recommendations:
        for r in recommendations:
            lines.append(f"  - {r}")
    else:
        lines.append("None")
    lines.append("")
    lines.append("=" * 50)

    return "\n".join(lines)


def _build_recommendations(summary: ValidationSummary) -> list:
    """Derives plain-language next steps from the raw warnings/failures,
    rather than hardcoding advice unrelated to what was actually found."""
    recs = []

    db_result = summary.result_for(ValidationCategory.DATABASE)
    if db_result and db_result.metrics.get("live_trades_total") == 0:
        recs.append(
            "live_trades is empty — run the one-time signal_log.csv migration "
            "if historical continuity is needed, otherwise this is expected "
            "before the first live signal is generated."
        )

    config_result = summary.result_for(ValidationCategory.CONFIGURATION)
    if config_result:
        for w in config_result.warnings:
            if "GITHUB_TOKEN" in w or "GITHUB_REPO" in w:
                recs.append("Set GITHUB_TOKEN and GITHUB_REPO in Streamlit Secrets before relying on live signal pushes.")
            if "UPSTOX_ACCESS_TOKEN" in w:
                recs.append("Set UPSTOX_ACCESS_TOKEN in Streamlit Secrets before relying on live price data.")

    if summary.all_skipped:
        recs.append(
            f"{len(summary.all_skipped)} check(s) were skipped in this environment "
            "(Streamlit Secrets/runtime unavailable) — re-run the Validation Center from "
            "within a live Streamlit session for complete coverage before final sign-off."
        )

    if summary.overall_status == OverallStatus.NOT_READY:
        recs.append("Resolve all listed failures before considering this build production-ready.")

    # De-duplicate while preserving order (multiple validators can surface
    # the same underlying config gap).
    seen = set()
    deduped = []
    for r in recs:
        if r not in seen:
            deduped.append(r)
            seen.add(r)
    return deduped


def print_report(summary: ValidationSummary) -> None:
    print(build_report_text(summary))
