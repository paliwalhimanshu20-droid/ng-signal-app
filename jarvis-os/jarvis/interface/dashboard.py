"""
jarvis.interface.dashboard

SystemDashboard: the console dashboard this sprint's Part 7 requires —
JARVIS Version, Core, Workflow, Permission Engine, Approval Engine,
Audit, Registry, Engineering Agent, Session, Overall Status.

Composes existing health checks (Sprint-0 Core health, Kernel's bundled
health reports) plus session and registry state — introduces no new
health logic of its own beyond what Interface health already covers.
"""

from __future__ import annotations

from dataclasses import dataclass

import jarvis
from jarvis.core.bootstrap import JarvisCore
from jarvis.interface.health import InterfaceHealth
from jarvis.kernel.kernel import ExecutiveKernel, KernelHealthReports
from jarvis.interface.session import SessionManager


@dataclass(frozen=True)
class DashboardSnapshot:
    """Everything the dashboard needs to render, gathered in one place."""

    version: str
    core_healthy: bool
    kernel_health: KernelHealthReports
    interface_health: InterfaceHealth
    agent_count: int
    session_id: str | None
    session_status: str | None
    overall_healthy: bool


class SystemDashboard:
    def snapshot(self, core: JarvisCore, kernel: ExecutiveKernel, interface_health: InterfaceHealth, session_manager: SessionManager) -> DashboardSnapshot:
        core_health = core.health_check()
        kernel_health = kernel.health_reports()
        session = session_manager.current

        overall_healthy = core_health.healthy and kernel_health.healthy and interface_health.healthy

        return DashboardSnapshot(
            version=jarvis.__version__,
            core_healthy=core_health.healthy,
            kernel_health=kernel_health,
            interface_health=interface_health,
            agent_count=len(kernel.registry),
            session_id=session.session_id if session else None,
            session_status=session.status.value if session else None,
            overall_healthy=overall_healthy,
        )

    def render(self, snapshot: DashboardSnapshot) -> str:
        lines = [
            "=" * 70,
            "JARVIS SYSTEM DASHBOARD",
            "=" * 70,
            f"Version:            {snapshot.version}",
            f"Core:               {'HEALTHY' if snapshot.core_healthy else 'UNHEALTHY'}",
            f"Workflow:           {'HEALTHY' if snapshot.kernel_health.execution.healthy else 'UNHEALTHY'}",
            f"Permission Engine:  {'HEALTHY' if snapshot.kernel_health.permission.healthy else 'UNHEALTHY'}",
            f"Approval Engine:    {'HEALTHY' if snapshot.kernel_health.approval.healthy else 'UNHEALTHY'}",
            f"Audit:              connected",
            f"Registry:           {snapshot.agent_count} agent(s) registered",
            f"Engineering Agent:  {'HEALTHY' if snapshot.kernel_health.execution.checks.get('engineering_agent_healthy') else 'UNHEALTHY'}",
            f"Session:            {snapshot.session_id or '(none)'} [{snapshot.session_status or 'closed'}]",
            "-" * 70,
            f"OVERALL STATUS:     {'HEALTHY' if snapshot.overall_healthy else 'UNHEALTHY'}",
            "=" * 70,
        ]
        return "\n".join(lines)
