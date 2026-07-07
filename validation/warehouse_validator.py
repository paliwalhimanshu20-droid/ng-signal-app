"""
validation/warehouse_validator.py

Validates the Historical Intelligence Warehouse (NGWH-001 foundation +
NGWH-002 downloader) as a new Validation Center category, per NGSP-003A.3's
NGWH-003 Operations Center scope.

Deliberately does NOT re-implement warehouse health logic — it wraps
`warehouse.bootstrap.WarehouseHealthChecker` (already exists, already
tested in NGWH-001) and translates its HealthStatus categories into this
package's PASS/WARNING/FAIL/SKIPPED vocabulary, exactly the same
delegation pattern `database_validator.py` uses for `research_db.validation.validate()`.

SKIPPED (not FAIL) is used when the warehouse hasn't been bootstrapped at
all yet on this deployment — a warehouse that has simply never been
initialized is not the same failure class as one that IS initialized but
broken, mirroring this package's own SKIPPED semantics (see
validation_models.py's ValidationStatus docstring).

=== TEMPORARY TIMING INSTRUMENTATION (this session) ===
This is the confirmed source of the "Bootstrapping warehouse -> Metadata
DuckDB connection opened -> Warehouse bootstrap complete -> Metadata
DuckDB connection closed -> Metadata DuckDB connection opened" sequence
seen in the logs: WarehouseBootstrap(config).run() here is a second,
independent, UNCACHED bootstrap (bypasses get_warehouse_handles()'s
st.cache_resource entirely), followed by an explicit
handles.duckdb_manager.close(), followed by handles.job_manager.list_jobs()
calls that reopen the same connection. Every stage below is timed with
before/after timestamps. No logic changed. Remove `_timed`/`import time`/
`_now_iso` and the `with _timed(...)` blocks once done.
"""

from __future__ import annotations

import time
from contextlib import contextmanager
from datetime import datetime as _dt

from .validation_models import ValidationCategory, ValidationResult, ValidationStatus

_SLOW_THRESHOLD_MS = 50.0


def _now_iso() -> str:
    return _dt.now().isoformat(timespec="milliseconds")


@contextmanager
def _timed(label: str):
    t0 = time.perf_counter()
    print(f"[TIMING BEFORE] {label:<60s} @ {_now_iso()}")
    try:
        yield
    finally:
        ms = (time.perf_counter() - t0) * 1000
        flag = "  <<< SLOW (>50ms)" if ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING AFTER]  {label:<60s} @ {_now_iso()}  {ms:9.2f} ms{flag}")


# HealthStatus (warehouse.core.constants) -> ValidationStatus mapping.
# UNKNOWN maps to SKIPPED, not WARNING/FAIL — an UNKNOWN category result
# from WarehouseHealthChecker means "not applicable / nothing to check yet"
# (e.g. an empty catalog, or the Instrument Master simply not existing
# yet), which is this package's SKIPPED, not a real problem.
_HEALTH_STATUS_MAP = {
    "healthy": ValidationStatus.PASS,
    "degraded": ValidationStatus.WARNING,
    "unhealthy": ValidationStatus.FAIL,
    "unknown": ValidationStatus.SKIPPED,
}


def validate_warehouse() -> ValidationResult:
    _fn_t0 = time.perf_counter()
    print(f"\n########## validate_warehouse() START @ {_now_iso()} ##########")

    details: list = []
    warnings: list = []
    failures: list = []
    skipped: list = []
    metrics: dict = {}

    # ---- 1. Can the warehouse package even be imported/configured? ----
    try:
        with _timed("import warehouse / warehouse.config.validation"):
            from warehouse import load_config
            from warehouse.config.validation import validate_configuration
    except Exception as e:
        print(f"########## validate_warehouse() END (import failure) @ {_now_iso()} ##########\n")
        return ValidationResult(
            category=ValidationCategory.WAREHOUSE,
            status=ValidationStatus.FAIL,
            summary=f"Could not import the warehouse package: {type(e).__name__}: {e}",
            failures=[f"warehouse package import failed: {type(e).__name__}: {e}"],
        )

    try:
        with _timed("load_config()"):
            config = load_config()
        details.append(f"Warehouse configuration loaded (environment={config.environment}).")
    except Exception as e:
        print(f"########## validate_warehouse() END (config failure) @ {_now_iso()} ##########\n")
        return ValidationResult(
            category=ValidationCategory.WAREHOUSE,
            status=ValidationStatus.FAIL,
            summary=f"Warehouse configuration failed to load/validate: {type(e).__name__}: {e}",
            failures=[f"load_config() raised {type(e).__name__}: {e}"],
        )

    # ---- 2. Pre-flight config validation (disk space, permissions) ----
    try:
        with _timed("validate_configuration(config)"):
            report = validate_configuration(config)
        for w in report.warnings:
            warnings.append(f"Configuration: {w.message}")
        for e in report.errors:
            failures.append(f"Configuration: {e.message}")
        if report.is_valid and not report.warnings:
            details.append("Configuration pre-flight checks passed with no warnings.")
    except Exception as e:
        warnings.append(f"Could not run configuration pre-flight validation: {type(e).__name__}: {e}")

    # ---- 3. Warehouse initialized at all? ----
    with _timed("config.resolved_paths().root_dir.exists() [filesystem]"):
        resolved_root = config.resolved_paths().root_dir
        root_exists = resolved_root.exists()
    if not root_exists:
        skipped.append(
            f"Warehouse has not been bootstrapped yet at {resolved_root} — "
            "run WarehouseBootstrap.run() (e.g. via the Warehouse Dashboard's "
            "'Initialize Warehouse' action) before deeper checks apply."
        )
        print(f"########## validate_warehouse() END (not bootstrapped) @ {_now_iso()} ##########\n")
        return ValidationResult(
            category=ValidationCategory.WAREHOUSE,
            status=ValidationStatus.SKIPPED,
            summary="Warehouse not yet initialized on this deployment — nothing to validate.",
            details=details,
            warnings=warnings,
            failures=failures,
            skipped=skipped,
            metrics=metrics,
        )

    # ---- 4. Bootstrap (idempotent) + delegate to WarehouseHealthChecker ----
    try:
        from warehouse.bootstrap import WarehouseBootstrap, WarehouseHealthChecker

        with _timed("*** WarehouseBootstrap(config).run() [UNCACHED, independent of get_warehouse_handles()] ***"):
            handles = WarehouseBootstrap(config).run()
        details.append("Warehouse bootstrap confirmed idempotent (safe re-run).")
    except Exception as e:
        failures.append(f"WarehouseBootstrap.run() failed: {type(e).__name__}: {e}")
        print(f"########## validate_warehouse() END (bootstrap failure) @ {_now_iso()} ##########\n")
        return ValidationResult(
            category=ValidationCategory.WAREHOUSE,
            status=ValidationStatus.FAIL,
            summary="Warehouse bootstrap failed — see failures for detail.",
            details=details,
            warnings=warnings,
            failures=failures,
        )

    try:
        with _timed("WarehouseHealthChecker(...).run()"):
            checker = WarehouseHealthChecker(handles.config, handles.duckdb_manager, handles.partition_manager)
            health_report = checker.run()
    except Exception as e:
        failures.append(f"WarehouseHealthChecker.run() raised {type(e).__name__}: {e}")
        print(f"########## validate_warehouse() END (health check failure) @ {_now_iso()} ##########\n")
        return ValidationResult(
            category=ValidationCategory.WAREHOUSE,
            status=ValidationStatus.FAIL,
            summary="Warehouse health check crashed — see failures for detail.",
            details=details,
            warnings=warnings,
            failures=failures,
        )
    finally:
        try:
            with _timed("*** handles.duckdb_manager.close() [sets _metadata_conn = None] ***"):
                handles.duckdb_manager.close()
        except Exception:
            pass

    # ---- 5. Translate each health category into this package's vocabulary ----
    worst_status = ValidationStatus.PASS
    _SEVERITY = {ValidationStatus.PASS: 0, ValidationStatus.SKIPPED: 0, ValidationStatus.WARNING: 1, ValidationStatus.FAIL: 2}

    for category_result in health_report.categories:
        mapped = _HEALTH_STATUS_MAP.get(category_result.status.value, ValidationStatus.WARNING)
        line = f"{category_result.name}: {category_result.detail}"
        if mapped == ValidationStatus.PASS:
            details.append(line)
        elif mapped == ValidationStatus.WARNING:
            warnings.append(line)
        elif mapped == ValidationStatus.FAIL:
            failures.append(line)
        else:
            skipped.append(line)

        if _SEVERITY[mapped] > _SEVERITY[worst_status]:
            worst_status = mapped

    metrics["health_score_percent"] = health_report.health_score
    metrics["overall_status"] = health_report.overall_status.value
    metrics["categories_checked"] = len(health_report.categories)

    # ---- 6. Job/checkpoint bookkeeping snapshot (informational, non-gating) ----
    try:
        from warehouse.core.constants import JobStatus

        with _timed("*** handles.job_manager.list_jobs(RUNNING) [reopens metadata connection] ***"):
            running = handles.job_manager.list_jobs(status=JobStatus.RUNNING)
        with _timed("handles.job_manager.list_jobs(FAILED)"):
            failed = handles.job_manager.list_jobs(status=JobStatus.FAILED)
        metrics["jobs_running"] = len(running)
        metrics["jobs_failed"] = len(failed)
        if failed:
            warnings.append(f"{len(failed)} warehouse job(s) in FAILED state — review Job Management for detail.")
            if worst_status == ValidationStatus.PASS:
                worst_status = ValidationStatus.WARNING
        details.append(f"Job bookkeeping: {len(running)} running, {len(failed)} failed (historical).")
    except Exception as e:
        warnings.append(f"Could not read job bookkeeping: {type(e).__name__}: {e}")

    summary = (
        f"Warehouse health {health_report.health_score}% "
        f"({health_report.overall_status.value}) across {len(health_report.categories)} categories."
    )

    _total_ms = (time.perf_counter() - _fn_t0) * 1000
    flag = "  <<< SLOW" if _total_ms > _SLOW_THRESHOLD_MS else ""
    print(f"########## validate_warehouse() END @ {_now_iso()}  TOTAL={_total_ms:9.2f} ms{flag} ##########\n")

    return ValidationResult(
        category=ValidationCategory.WAREHOUSE,
        status=worst_status,
        summary=summary,
        details=details,
        warnings=warnings,
        failures=failures,
        skipped=skipped,
        metrics=metrics,
    )
