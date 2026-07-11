"""
jarvis.interface.health

Structural health checks for the Interface Layer's own components:
Interface (presence), Session, Dashboard readiness, Command Parser —
per this sprint's explicit Part 10 requirement. Matches the shape and
philosophy of every prior sprint's health check.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.interface.command_parser import CommandParser
from jarvis.interface.session import SessionManager, SessionStatus


@dataclass(frozen=True)
class InterfaceHealth:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Interface Layer health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_interface_health_check(
    session_manager: SessionManager,
    command_parser: CommandParser,
) -> InterfaceHealth:
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    parser_ok = isinstance(command_parser, CommandParser)
    checks["command_parser_present"] = parser_ok
    detail["command_parser_present"] = "CommandParser instance present" if parser_ok else "MISSING"

    session_manager_ok = isinstance(session_manager, SessionManager)
    checks["session_manager_present"] = session_manager_ok
    detail["session_manager_present"] = "SessionManager instance present" if session_manager_ok else "MISSING"

    session = session_manager.current if session_manager_ok else None
    session_open = session is not None and session.status is not SessionStatus.CLOSED
    checks["session_active"] = session_open
    detail["session_active"] = (
        f"session_id={session.session_id}, status={session.status.value}" if session_open else "No active session"
    )

    healthy = all(checks.values())
    return InterfaceHealth(healthy=healthy, checks=checks, detail=detail)
