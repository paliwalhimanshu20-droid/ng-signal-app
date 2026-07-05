"""
validation/validation_models.py

Strongly-typed data structures shared by every validator in this package.
Each validator (app_validator, database_validator, dashboard_validator,
configuration_validator) returns exactly one ValidationResult.
validation_runner.py collects all four into a single ValidationSummary
with an overall HealthScore and OverallStatus.

Deliberately dataclasses/enums, not raw dicts, per NGSP-003B.1's coding
standard — matches the typed style already used in the Intelligence
Engines (technical_engine.py, market_engine.py) rather than the looser
dict-based style signal_log.py uses.
"""

from dataclasses import dataclass, field
from enum import Enum


class ValidationStatus(str, Enum):
    """Per-category verdict. String-valued Enum so it prints cleanly
    (e.g. in f-strings and the report) without needing .value everywhere.

    SKIPPED (added at architecture review, NGSP-003B.1 feedback round 1):
    distinct from FAIL. FAIL means a check ran and found a real problem.
    SKIPPED means a check could not be run at all in the current
    environment — e.g. Streamlit Secrets access when this validator is
    invoked standalone (`python -m validation.validation_runner`)
    outside a live Streamlit session, where st.secrets has nothing to
    read regardless of whether the actual deployed app is healthy.
    Conflating the two would make "not applicable here" look identical
    to "actually broken," which is exactly what this status prevents."""
    PASS = "PASS"
    WARNING = "WARNING"
    FAIL = "FAIL"
    SKIPPED = "SKIPPED"


class ValidationCategory(str, Enum):
    """The validators this module ships with. New validators (per
    NGSP-003B.1's Future Compatibility section — Trading, Security,
    Regression, Architecture, Risk, AI Governance) add new members here
    without touching existing ones. WAREHOUSE added by NGWH-003 (Warehouse
    Operations Center) — delegates to warehouse.bootstrap.WarehouseHealthChecker,
    see validation/warehouse_validator.py. INSTRUMENT_MASTER added when the
    Instrument Master's own severity-based rule framework
    (instrument_master/validation.py) was wired into this Center — see
    validation/instrument_master_validator.py. Anticipated future
    additions following the same pattern: Market Context, Research
    Database, Strategy, Data Quality, Corporate Action, Market DNA — each
    should ship as its own delegating validator module, not be folded into
    an existing category."""
    APPLICATION = "Application"
    DATABASE = "Database"
    DASHBOARD = "Dashboard"
    CONFIGURATION = "Configuration"
    WAREHOUSE = "Warehouse"
    INSTRUMENT_MASTER = "Instrument Master"


class OverallStatus(str, Enum):
    """Deployment readiness verdict for the whole system, derived from
    the worst individual category result — see validation_runner.py's
    _determine_overall_status()."""
    READY = "READY"
    READY_WITH_WARNINGS = "READY WITH WARNINGS"
    NOT_READY = "NOT READY"


@dataclass(frozen=True)
class ValidationResult:
    """
    The output of exactly one validator.

    - `summary`: one-line human-readable verdict, always present.
    - `details`: informational checks that passed (for a full audit trail
      even when everything is fine — useful for the "PASS" case, which
      otherwise has nothing to show).
    - `warnings` / `failures`: only populated when something is actually
      wrong. A WARNING-status result should have >=1 warning and 0
      failures; a FAIL-status result should have >=1 failure.
    - `metrics`: any numeric/raw values a validator computed that other
      code (e.g. a future dashboard) might want to display directly —
      e.g. database_validator's row counts, dashboard_validator's
      independently-recomputed KPI values.
    """
    category: ValidationCategory
    status: ValidationStatus
    summary: str
    details: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    failures: list = field(default_factory=list)
    skipped: list = field(default_factory=list)
    metrics: dict = field(default_factory=dict)


@dataclass(frozen=True)
class HealthScore:
    """Overall system health, 0-100. See validation_runner.py's
    _calculate_health_score() for exactly how category results map to
    a percentage."""
    percent: float

    def __str__(self) -> str:
        return f"{self.percent:.0f}%"


@dataclass(frozen=True)
class ValidationSummary:
    """The complete result of run_validation() — one ValidationResult per
    category, plus the derived overall health score and readiness status."""
    results: list
    health_score: HealthScore
    overall_status: OverallStatus

    def result_for(self, category: "ValidationCategory"):
        """Look up a single category's result by name. Returns None if
        that category wasn't run (shouldn't happen in practice, but kept
        defensive for when future validators are added incrementally)."""
        for r in self.results:
            if r.category == category:
                return r
        return None

    @property
    def all_warnings(self) -> list:
        out = []
        for r in self.results:
            out.extend(r.warnings)
        return out

    @property
    def all_failures(self) -> list:
        out = []
        for r in self.results:
            out.extend(r.failures)
        return out

    @property
    def all_skipped(self) -> list:
        out = []
        for r in self.results:
            out.extend(r.skipped)
        return out


def is_environment_unavailable_error(exc: Exception) -> bool:
    """
    Shared classifier used by app_validator, configuration_validator, and
    dashboard_validator to distinguish "this check cannot run in the
    current environment" from "this check ran and failed." True for
    errors that mean Streamlit Secrets (or an active Streamlit runtime)
    simply isn't available here — e.g. running this validator standalone
    via CLI with no .streamlit/secrets.toml and no live session — NOT for
    genuine bugs.

    Matches by exception class name AND message content rather than
    importing a specific Streamlit exception class directly, so this
    keeps working across Streamlit versions without a hard version
    dependency.
    """
    exc_name = type(exc).__name__
    if "SecretNotFound" in exc_name or "StreamlitSecrets" in exc_name:
        return True
    message = str(exc).lower()
    if "no secrets found" in message or "secrets.toml" in message:
        return True
    return False
