"""
research_db/validation.py

Schema + referential-integrity checks for the Research & Learning Database.
Distinct from the domain concept of "validation_results" (which tracks
whether a research FINDING has been validated) — this module checks the
DATABASE ITSELF is structurally sound.
"""

import logging

from . import schema

logger = logging.getLogger(__name__)


def validate(db) -> dict:
    issues = []
    conn = db.conn

    total_experiments = db.count_experiments()

    # 1. run_id uniqueness is enforced by a UNIQUE constraint, but confirm
    #    there's no silent corruption (defensive, cheap at any scale).
    cur = conn.execute(
        f"SELECT COUNT(*) as c, COUNT(DISTINCT run_id) as d FROM {schema.TABLE_EXPERIMENTS}"
    )
    row = cur.fetchone()
    if row["c"] != row["d"]:
        issues.append(f"run_id is not unique: {row['c']} rows but only {row['d']} distinct run_ids")

    # 2. Exactly one is_current_version per experiment_id (zero or >1 is a bug)
    cur = conn.execute(f"""
        SELECT experiment_id, COUNT(*) as c
        FROM {schema.TABLE_EXPERIMENT_VERSIONS}
        WHERE is_current_version = 1
        GROUP BY experiment_id
        HAVING c != 1
    """)
    bad = cur.fetchall()
    if bad:
        issues.append(f"{len(bad)} experiment_id(s) have != 1 current version")

    # 3. Referential integrity — every child row's experiment_row_id must
    #    point at a real research_experiments.id
    child_tables = [
        schema.TABLE_INDICATOR_RESULTS, schema.TABLE_STRATEGY_RESULTS,
        schema.TABLE_PARAMETER_RESULTS, schema.TABLE_REGIME_RESULTS,
        schema.TABLE_PERFORMANCE_METRICS, schema.TABLE_VALIDATION_RESULTS,
    ]
    for table in child_tables:
        cur = conn.execute(f"""
            SELECT COUNT(*) as c FROM {table} t
            WHERE NOT EXISTS (
                SELECT 1 FROM {schema.TABLE_EXPERIMENTS} e WHERE e.id = t.experiment_row_id
            )
        """)
        c = cur.fetchone()["c"]
        if c:
            issues.append(f"{c} rows in {table} reference a nonexistent experiment_row_id")

    # 4. performance_metrics.regime_id, if set, must point at a real regime row
    cur = conn.execute(f"""
        SELECT COUNT(*) as c FROM {schema.TABLE_PERFORMANCE_METRICS} pm
        WHERE pm.regime_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM {schema.TABLE_REGIME_RESULTS} r WHERE r.regime_id = pm.regime_id
          )
    """)
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} performance_metrics rows reference a nonexistent regime_id")

    # 5. experiment_notes.experiment_row_id, if set, must point at a real experiment
    cur = conn.execute(f"""
        SELECT COUNT(*) as c FROM {schema.TABLE_EXPERIMENT_NOTES} n
        WHERE n.experiment_row_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM {schema.TABLE_EXPERIMENTS} e WHERE e.id = n.experiment_row_id
          )
    """)
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} experiment_notes rows reference a nonexistent experiment_row_id")

    # 6. research_status must be a known value
    placeholders = ", ".join(["?"] * len(schema.EXPERIMENT_STATUS_VALUES))
    cur = conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_EXPERIMENTS} "
        f"WHERE research_status NOT IN ({placeholders})",
        schema.EXPERIMENT_STATUS_VALUES,
    )
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} rows have an invalid research_status value")

    # 7. validation_status must be a known value
    placeholders = ", ".join(["?"] * len(schema.VALIDATION_STATUS_VALUES))
    cur = conn.execute(
        f"SELECT COUNT(*) as c FROM {schema.TABLE_VALIDATION_RESULTS} "
        f"WHERE validation_status NOT IN ({placeholders})",
        schema.VALIDATION_STATUS_VALUES,
    )
    c = cur.fetchone()["c"]
    if c:
        issues.append(f"{c} validation_results rows have an invalid validation_status value")

    # 8. Required fields NULL on research_experiments
    for field in ["experiment_id", "run_id", "instrument_key", "research_type", "timestamp"]:
        cur = conn.execute(
            f"SELECT COUNT(*) as c FROM {schema.TABLE_EXPERIMENTS} "
            f"WHERE {field} IS NULL OR {field} = ''"
        )
        c = cur.fetchone()["c"]
        if c:
            issues.append(f"{c} rows have NULL/empty required field '{field}'")

    # 9. experiment_versions uniqueness — enforced by UNIQUE constraint;
    #    double-check for defensive purposes.
    cur = conn.execute(f"""
        SELECT experiment_id, version_number, COUNT(*) as c
        FROM {schema.TABLE_EXPERIMENT_VERSIONS}
        GROUP BY experiment_id, version_number
        HAVING c > 1
    """)
    dups = cur.fetchall()
    if dups:
        issues.append(f"{len(dups)} duplicate (experiment_id, version_number) pairs found")

    report = {
        "total_experiments": total_experiments,
        "issues_found": len(issues),
        "issues": issues,
        "passed": len(issues) == 0,
    }
    return report


def print_report(report: dict):
    print("\n=== Research & Learning Database Validation ===")
    print(f"  Total experiments: {report['total_experiments']:,}")
    if report["passed"]:
        print("  Status: PASSED — no issues found")
    else:
        print(f"  Status: {report['issues_found']} ISSUE(S) FOUND")
        for issue in report["issues"]:
            print(f"    - {issue}")
    print("================================================\n")
