"""
jarvis.connections.connection_system

ConnectionSystem: a thin facade bundling ConnectionManager,
ProviderRegistry, ConnectionService, and ProfileManager into one object
JarvisCore can hold — matching the single-object `.health_check()`
pattern MemoryManager (Sprint-3), IntelligenceEngine (Sprint-4), and
AICoordinator (Sprint-5) already established for Bootstrap wiring. This
class adds no behavior of its own beyond construction and health
aggregation; every real operation still goes through the four
components' own public methods.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionManager
from jarvis.connections.connection_service import ConnectionService
from jarvis.connections.health import ConnectionSystemHealthReport, run_connection_health_check
from jarvis.connections.profiles import ProfileManager
from jarvis.connections.provider_registry import ProviderRegistry


class ConnectionSystem:
    def __init__(self, audit_ledger: AuditLedger, seed_default_adapters: bool = True) -> None:
        self.connection_manager = ConnectionManager(audit_ledger)
        self.provider_registry = ProviderRegistry(seed_defaults=seed_default_adapters)
        self.connection_service = ConnectionService(self.connection_manager, self.provider_registry, audit_ledger)
        self.profile_manager = ProfileManager(self.connection_manager, audit_ledger)

    def health_check(self) -> ConnectionSystemHealthReport:
        return run_connection_health_check(self.connection_manager, self.provider_registry, self.profile_manager)
