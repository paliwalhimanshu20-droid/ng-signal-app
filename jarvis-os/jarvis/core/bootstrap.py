"""
jarvis.core.bootstrap

The JARVIS Core Bootstrap sequence.

Design reference: JARVIS-001 §7 (Bootstrap Process) and §26 (Startup
Sequence, its canonical restatement):

    1. Load and validate the Constitution reference. Halt on failure.
    2. Establish Audit Ledger connectivity.
    3. Initialize the Permission Engine's contract layer.        [Sprint-0: SKIPPED, see note]
    4. Load the Agent Registry.
    5. Run a self-health check across steps 1-4.
    6. Enter ready state.

Per JARVIS-001 §7: "Each step's success is a precondition for the next,"
and steps 3 and 4 have no ordering dependency on each other and MAY run
concurrently — Sprint-0 keeps them sequential for simplicity and because
neither is expensive enough yet to justify the added complexity of
concurrency; this is noted as a legitimate, revisitable Sprint-0
simplification, not a silent deviation from JARVIS-001 §7's stated
ordering freedom.

SPRINT-0 SCOPE NOTE ON STEP 3: this task's explicit scope excludes
"permissions" and "approvals." The Permission Engine's contract layer
does not exist yet. Per JARVIS-001 §7's own ordering logic (constitutional
grounding before audit capability, audit capability before any capability
grants, capability infrastructure before agent awareness), skipping step
3 is safe PRECISELY BECAUSE Sprint-0 also does not issue any real
capability grants — there is nothing for a missing Permission Engine to
fail to gate. This must be revisited the moment a future sprint
introduces real task execution capable of consequential action; step 3
cannot remain skipped once that happens.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from jarvis.audit import AuditLedger, AuditLedgerError
from jarvis.config import JarvisSettings, load_settings
from jarvis.constitution import Constitution, ConstitutionValidationError, load_constitution
from jarvis.health import CoreHealthReport, run_core_health_check
from jarvis.logging_ import configure_logging, get_logger
from jarvis.orchestrator import Orchestrator
from jarvis.registry import AgentRegistry

logger = get_logger(__name__)


class BootstrapError(Exception):
    """
    Raised when Bootstrap cannot complete.

    Per JARVIS-001 §7, this is always fatal — there is no partial or
    degraded ready state. Any caller catching this must not attempt to
    proceed with a partially-initialized JarvisCore.
    """


@dataclass
class JarvisCore:
    """
    Holds every subsystem JARVIS Core needs once Bootstrap has completed.

    This object is only ever constructed by `boot()` below, never
    assembled piecemeal elsewhere — that guarantee is what makes "JARVIS
    Core is running" and "Bootstrap succeeded" the same statement, per
    JARVIS-001 §7's requirement that nothing accept input before all six
    steps have passed.
    """

    settings: JarvisSettings
    constitution: Constitution
    audit_ledger: AuditLedger
    registry: AgentRegistry
    orchestrator: Orchestrator
    ready: bool = False

    def health_check(self) -> CoreHealthReport:
        """Re-run the Core self-health check against the live, running system."""
        return run_core_health_check(
            constitution=self.constitution,
            audit_ledger=self.audit_ledger,
            registry=self.registry,
            orchestrator=self.orchestrator,
        )

    def shutdown(self) -> None:
        """
        Sprint-0 Shutdown Sequence, per JARVIS-001 §27.

        Full §27 sequence requires in-flight task draining and suspended-
        workflow checkpointing, neither of which is meaningful yet since
        Sprint-0's WorkflowEngine has no real in-flight execution. What
        Sprint-0 DOES implement, for real: the final Audit Ledger write
        confirming shutdown, which §27 requires regardless of what else
        was in flight.
        """
        logger.info("JARVIS Core shutdown initiated.")
        self.audit_ledger.record(
            event_type="core.shutdown",
            message="JARVIS Core shutdown completed (Sprint-0: no in-flight workflows to drain).",
        )
        self.ready = False
        logger.info("JARVIS Core shutdown complete.")


def boot() -> JarvisCore:
    """
    Execute the full Bootstrap sequence and return a ready JarvisCore.

    Raises BootstrapError (chained from the originating exception) if any
    step fails. Per JARVIS-001 §7, callers must treat any exception from
    this function as fatal — there is no reduced-functionality fallback.
    """
    settings = load_settings()

    # Logging is configured before anything else logs, but AFTER settings
    # load (since log level itself is operational config) — this is not
    # one of JARVIS-001 §7's six numbered steps, it's the practical
    # precondition for observing the six steps at all.
    configure_logging(level=settings.operational.log_level)
    logger.info("JARVIS Core Bootstrap starting.")

    # --- Step 1: Constitution -------------------------------------------------
    try:
        constitution = load_constitution(settings.constitutional_reference.constitution_path)
    except ConstitutionValidationError as exc:
        logger.critical("Bootstrap Step 1 (Constitution) failed: %s", exc)
        raise BootstrapError(
            "Bootstrap halted at Step 1: Constitution reference invalid or missing. "
            "JARVIS Core cannot run without constitutional grounding."
        ) from exc

    logger.info(
        "Bootstrap Step 1 complete: Constitution v%s loaded, %d/7 Articles present.",
        constitution.version,
        len(constitution.articles),
    )

    # --- Step 2: Audit Ledger ---------------------------------------------------
    audit_ledger = AuditLedger(storage_path=Path("data/audit_ledger.jsonl"))
    try:
        audit_ledger.connect()
    except AuditLedgerError as exc:
        logger.critical("Bootstrap Step 2 (Audit Ledger) failed: %s", exc)
        raise BootstrapError(
            "Bootstrap halted at Step 2: Audit Ledger connectivity could not be "
            "established. Per Article IV, JARVIS Core must not proceed without "
            "guaranteed auditability."
        ) from exc

    audit_ledger.record(
        event_type="core.bootstrap.constitution_loaded",
        message="Constitution reference loaded and structurally validated.",
        details={"constitution_version": constitution.version},
    )
    logger.info("Bootstrap Step 2 complete: Audit Ledger connected.")

    # --- Step 3: Permission Engine contract layer -------------------------------
    # SKIPPED in Sprint-0. See module docstring for why this is safe at
    # Sprint-0's scope and what must change before it can remain skipped.
    logger.info(
        "Bootstrap Step 3 (Permission Engine contract) SKIPPED: out of scope "
        "for Sprint-0 (no capability grants are issued by this sprint)."
    )

    # --- Step 4: Agent Registry --------------------------------------------------
    registry = AgentRegistry()
    logger.info("Bootstrap Step 4 complete: Agent Registry loaded (%d agent(s)).", len(registry))
    audit_ledger.record(
        event_type="core.bootstrap.registry_loaded",
        message="Agent Registry initialized.",
        details={"agent_count": len(registry)},
    )

    # Orchestrator is constructed here, ahead of the Step 5 health check,
    # since the health check needs a real Orchestrator instance to verify
    # pipeline wiring against (JARVIS-001 §22).
    orchestrator = Orchestrator(registry=registry)

    # --- Step 5: Self-health check ------------------------------------------------
    health_report = run_core_health_check(
        constitution=constitution,
        audit_ledger=audit_ledger,
        registry=registry,
        orchestrator=orchestrator,
    )
    logger.info("Bootstrap Step 5 self-health check:\n%s", health_report.summary())

    if not health_report.healthy:
        audit_ledger.record(
            event_type="core.bootstrap.health_check_failed",
            message="Self-health check failed; Bootstrap halted before reaching ready state.",
            details=health_report.detail,
        )
        raise BootstrapError(
            "Bootstrap halted at Step 5: self-health check failed. "
            f"Detail: {health_report.detail}"
        )

    # --- Step 6: Ready state -----------------------------------------------------
    core = JarvisCore(
        settings=settings,
        constitution=constitution,
        audit_ledger=audit_ledger,
        registry=registry,
        orchestrator=orchestrator,
        ready=True,
    )
    audit_ledger.record(
        event_type="core.bootstrap.ready",
        message="JARVIS Core reached ready state. Accepting input.",
    )
    logger.info("Bootstrap Step 6 complete: JARVIS Core is READY.")

    return core
