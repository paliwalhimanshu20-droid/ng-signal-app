"""
jarvis.connections.connection_service

High-level orchestrator tying ConnectionManager (state/governance),
ProviderRegistry (adapter factory), and real ProviderAdapter instances
together. This is the ONLY place a live ProviderAdapter instance is held
in memory — ConnectionManager itself never holds one (Part 1's
responsibilities — create/remove/suspend/restore/health/permission
verification/trust verification/audit — are all STATE operations, not
network I/O), which is what keeps "no module may directly connect to
any provider" (Part 1) literally true: this class and ProviderRegistry
are the only two objects in the codebase that ever touch a
ProviderAdapter, and this class never acts without first checking
ConnectionManager's state.
"""

from __future__ import annotations

from typing import Optional

from jarvis.ai_coordination.models import ProviderRequest, ProviderResponse
from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionManager
from jarvis.connections.credentials import ConnectionCredentials
from jarvis.connections.models import Connection, ConnectionHealthStatus, ConnectionStatus, utc_now_iso
from jarvis.connections.provider_adapter import AdapterHealthStatus, ProviderAdapter, ProviderAdapterError
from jarvis.connections.provider_registry import ProviderRegistry


class ConnectionServiceError(Exception):
    """Raised for any invalid Connection Service operation (dispatching to a non-connected connection, establishing a non-approved one)."""


_HEALTH_MAP = {
    AdapterHealthStatus.HEALTHY: ConnectionHealthStatus.HEALTHY,
    AdapterHealthStatus.DEGRADED: ConnectionHealthStatus.DEGRADED,
    AdapterHealthStatus.UNHEALTHY: ConnectionHealthStatus.UNHEALTHY,
}


class ConnectionService:
    def __init__(self, connection_manager: ConnectionManager, provider_registry: ProviderRegistry, audit_ledger: AuditLedger) -> None:
        self._connections = connection_manager
        self._provider_registry = provider_registry
        self._audit = audit_ledger
        self._adapters: dict[str, ProviderAdapter] = {}

    def establish(self, connection_id: str, credentials: ConnectionCredentials) -> Connection:
        """The one real network call in the connection-establishment flow: given an APPROVED connection, actually connect() the real adapter, and only then mark it CONNECTED. If the real call fails, the connection is marked FAILED, not silently left APPROVED."""
        connection = self._connections.get(connection_id)
        if connection.status is not ConnectionStatus.APPROVED:
            raise ConnectionServiceError(
                f"Cannot establish connection '{connection_id}': status is "
                f"'{connection.status.value}', expected 'approved'."
            )

        adapter = self._provider_registry.create(connection.provider_id)
        try:
            adapter.connect(credentials)
        except ProviderAdapterError as exc:
            self._connections.mark_failed(connection_id, reason=str(exc))
            raise

        self._adapters[connection_id] = adapter
        return self._connections.mark_connected(connection_id)

    def send_prompt(self, connection_id: str, request: ProviderRequest) -> ProviderResponse:
        if not self._connections.verify_trust(connection_id):
            raise ConnectionServiceError(
                f"Connection '{connection_id}' is not currently connected/trusted; refusing to dispatch."
            )
        adapter = self._adapters.get(connection_id)
        if adapter is None:
            raise ConnectionServiceError(
                f"No live adapter held for connection '{connection_id}' — establish() must succeed first."
            )

        try:
            response = adapter.send_prompt(request)
        except ProviderAdapterError as exc:
            self._connections.record_health(connection_id, ConnectionHealthStatus.UNHEALTHY)
            raise ConnectionServiceError(f"Prompt dispatch failed: {exc}") from exc

        self._connections.record_health(connection_id, ConnectionHealthStatus.HEALTHY, sync_timestamp=utc_now_iso())
        return response

    def check_health(self, connection_id: str) -> ConnectionHealthStatus:
        adapter = self._adapters.get(connection_id)
        if adapter is None:
            self._connections.record_health(connection_id, ConnectionHealthStatus.UNKNOWN)
            return ConnectionHealthStatus.UNKNOWN

        status = adapter.health()
        mapped = _HEALTH_MAP.get(status, ConnectionHealthStatus.UNKNOWN)
        self._connections.record_health(connection_id, mapped, sync_timestamp=utc_now_iso())
        return mapped

    def disconnect(self, connection_id: str, reason: Optional[str] = None) -> Connection:
        adapter = self._adapters.pop(connection_id, None)
        if adapter is not None:
            adapter.disconnect()
        return self._connections.disconnect(connection_id, reason=reason)

    def is_healthy(self) -> bool:
        return isinstance(self._adapters, dict)
