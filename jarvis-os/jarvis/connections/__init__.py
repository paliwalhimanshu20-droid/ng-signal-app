"""
jarvis.connections

Sprint-6: the Connection Management Framework and AI Provider Adapter
Layer. Public surface: ConnectionManager (state/governance),
ConnectionService (the only holder of live adapters), ProviderRegistry
(adapter plugin registry), ProfileManager, AIConnectionDispatcher (the
Sprint-5 integration seam), plus the read-only data types other layers
need.

Owner Sovereignty (Part 3) is this package's organizing constraint —
every mutating ConnectionManager method traces back to an explicit
caller action, never a timer, retry loop, or health-check side effect.
"""

from __future__ import annotations

from jarvis.connections.ai_dispatch import AIConnectionDispatcher, NoConnectedProviderError
from jarvis.connections.connection_manager import ConnectionError_, ConnectionManager
from jarvis.connections.connection_service import ConnectionService, ConnectionServiceError
from jarvis.connections.connection_system import ConnectionSystem
from jarvis.connections.credentials import ConnectionCredentials, MissingCredentialsError, from_env
from jarvis.connections.health import ConnectionSystemHealthReport, run_connection_health_check
from jarvis.connections.models import (
    Connection,
    ConnectionHealthStatus,
    ConnectionStatus,
    PermissionScope,
    TrustLevel,
)
from jarvis.connections.profiles import ConnectionProfile, ProfileManager
from jarvis.connections.provider_adapter import AdapterHealthStatus, ProviderAdapter, ProviderAdapterError
from jarvis.connections.provider_registry import ProviderRegistry, ProviderRegistryError

__all__ = [
    "AIConnectionDispatcher",
    "AdapterHealthStatus",
    "Connection",
    "ConnectionCredentials",
    "ConnectionError_",
    "ConnectionHealthStatus",
    "ConnectionManager",
    "ConnectionProfile",
    "ConnectionService",
    "ConnectionServiceError",
    "ConnectionStatus",
    "ConnectionSystem",
    "ConnectionSystemHealthReport",
    "MissingCredentialsError",
    "NoConnectedProviderError",
    "PermissionScope",
    "ProfileManager",
    "ProviderAdapter",
    "ProviderAdapterError",
    "ProviderRegistry",
    "ProviderRegistryError",
    "TrustLevel",
    "from_env",
    "run_connection_health_check",
]
