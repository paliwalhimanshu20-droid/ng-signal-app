"""
jarvis.connections.health

Sprint-6 Part 12 — Connection Management Health.

Matches the shape and philosophy of every prior sprint's health check.
Adapter health here is STRUCTURAL (is the adapter registered and
instantiable) rather than LIVE (a real network call) — a live check
requires real credentials this sandbox/test environment never holds;
per-connection live health is what jarvis.connections.connection_manager's
`record_health()` and each adapter's own `health()` method are for, run
against a real, CONNECTED connection when one exists.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.connections.models import ConnectionHealthStatus, ConnectionStatus


@dataclass(frozen=True)
class ConnectionSystemHealthReport:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Connection Management health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_connection_health_check(
    connection_manager,
    provider_registry,
    profile_manager,
) -> ConnectionSystemHealthReport:
    """Part 12 requires: Connection Manager, Provider Registry, OpenAI Adapter, Anthropic Adapter, Profiles, Connection Health, Overall Status. All seven are checked here."""
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    checks["connection_manager"] = connection_manager.is_healthy()
    detail["connection_manager"] = f"{len(connection_manager)} connection(s) tracked"

    checks["provider_registry"] = provider_registry.is_healthy()
    detail["provider_registry"] = f"{len(provider_registry.list_provider_ids())} adapter(s) registered"

    checks["openai_adapter"] = provider_registry.is_registered("provider-openai")
    detail["openai_adapter"] = "registered" if checks["openai_adapter"] else "NOT REGISTERED"

    checks["anthropic_adapter"] = provider_registry.is_registered("provider-anthropic")
    detail["anthropic_adapter"] = "registered" if checks["anthropic_adapter"] else "NOT REGISTERED"

    checks["profiles"] = profile_manager.is_healthy()
    detail["profiles"] = (
        f"active profile: {profile_manager.active_profile.value}" if profile_manager.active_profile else "no profile active"
    )

    connected = [c for c in connection_manager.list_all() if c.status is ConnectionStatus.CONNECTED]
    unhealthy_connected = [c for c in connected if c.health is ConnectionHealthStatus.UNHEALTHY]
    checks["connection_health"] = len(unhealthy_connected) == 0
    detail["connection_health"] = (
        f"{len(connected)} connected, {len(unhealthy_connected)} unhealthy"
    )

    overall_healthy = all(checks.values())
    return ConnectionSystemHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
