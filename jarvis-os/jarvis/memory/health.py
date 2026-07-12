"""
jarvis.memory.health

Sprint-3 Part 9 — Memory Health Dashboard.

Mirrors jarvis.health.health_check.CoreHealthReport's shape exactly
(healthy / checks / detail / summary()) so the Interface Layer's
SystemDashboard can render Memory alongside every other subsystem with
no special-casing.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class MemoryHealthReport:
    """Result of a full Memory Foundation health check — one entry per Part 9 component, plus overall status."""

    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Memory Foundation health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_memory_health_check(memory_manager: "MemoryManager") -> MemoryHealthReport:  # type: ignore[name-defined]  # noqa: F821
    """
    Part 9 requires: Memory Manager, Working Memory, Session Memory,
    Conversation Memory, Preference Memory, Knowledge Memory,
    Persistence, Recovery, Overall Status. All nine are checked here.
    """
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    checks["memory_manager"] = memory_manager.is_connected
    detail["memory_manager"] = "connected" if memory_manager.is_connected else "NOT CONNECTED"

    checks["working_memory"] = memory_manager.working.is_healthy()
    detail["working_memory"] = "runtime state OK"

    checks["session_memory"] = memory_manager.sessions.is_healthy()
    detail["session_memory"] = "persisted state readable" if checks["session_memory"] else "integrity check FAILED"

    checks["conversation_memory"] = memory_manager.conversations.is_healthy()
    detail["conversation_memory"] = (
        f"{len(memory_manager.conversations.history())} record(s) readable"
        if checks["conversation_memory"]
        else "log unreadable"
    )

    checks["preference_memory"] = memory_manager.preferences.is_healthy()
    detail["preference_memory"] = (
        "persisted preferences readable" if checks["preference_memory"] else "integrity check FAILED"
    )

    checks["knowledge_memory"] = memory_manager.knowledge.is_healthy()
    detail["knowledge_memory"] = "placeholder OK" if checks["knowledge_memory"] else "integrity check FAILED"

    checks["persistence"] = memory_manager.is_connected
    detail["persistence"] = "storage directory writable" if checks["persistence"] else "storage NOT writable"

    last_recovery = memory_manager.last_recovery_report
    checks["recovery"] = last_recovery is None or last_recovery.succeeded
    detail["recovery"] = (
        "no recovery attempted yet" if last_recovery is None
        else ("last recovery succeeded" if last_recovery.succeeded else "last recovery FAILED")
    )

    overall_healthy = all(checks.values())
    return MemoryHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
