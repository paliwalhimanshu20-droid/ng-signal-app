"""
jarvis.connections.ai_dispatch

Sprint-6 Part 10 — AI Coordinator Integration.

    Connection Manager -> Provider Adapter -> Response -> Validator -> Consensus

Wires Sprint-5's AICoordinator to real dispatch WITHOUT AICoordinator (or
anything in jarvis.ai_coordination) importing anything from
jarvis.connections — the dependency runs one way only, so Sprint-5's own
package remains exactly as it was, untouched, per "do not modify
previous sprint functionality unless a genuine architectural defect
exists." This module is the seam: it depends on both packages'
already-public APIs and composes them.

Sprint-5's ModelRegistry seeds illustrative provider_ids suffixed
'-example' (e.g. 'provider-openai-example') — deliberately, since Sprint-5
was explicitly forbidden from naming anything real (its Part 3: "Do NOT
register real APIs"). `MODEL_REGISTRY_TO_CONNECTION_PROVIDER_ID` below is
the one explicit, small table mapping those illustrative ids to this
sprint's real Connection provider_ids ('provider-openai') — not a guess,
not a string-transform, an auditable table.
"""

from __future__ import annotations

from jarvis.ai_coordination.ai_coordinator import AICoordinator
from jarvis.ai_coordination.models import AIRecommendation, RiskLevel
from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionManager
from jarvis.connections.connection_service import ConnectionService
from jarvis.connections.models import Connection, ConnectionStatus

MODEL_REGISTRY_TO_CONNECTION_PROVIDER_ID: dict[str, str] = {
    "provider-openai-example": "provider-openai",
    "provider-anthropic-example": "provider-anthropic",
    "provider-google-example": "provider-google",
    "provider-future-example": "provider-future",
}


class NoConnectedProviderError(Exception):
    """Raised when AICoordinator selected a provider that has no CONNECTED, trusted Connection — dispatch never falls back to a different provider silently; the caller must see this and decide (request a connection, choose another capability, etc.)."""


class AIConnectionDispatcher:
    """
    The Part 11 pipeline's remaining steps, given an AIRecommendation
    already produced by AICoordinator.coordinate() (Sprint-5's own
    Capability Selection -> Provider Selection -> Session Creation ->
    Prompt Dispatch, unchanged): find the real, Owner-approved Connection
    for the selected provider, send the already-prepared ProviderRequest
    through it, and hand the real ProviderResponse back to
    AICoordinator.finalize_with_responses() for Validation and Consensus
    — exactly Sprint-5's own existing logic, now fed a real response
    instead of a test-constructed one.
    """

    def __init__(
        self,
        ai_coordinator: AICoordinator,
        connection_manager: ConnectionManager,
        connection_service: ConnectionService,
        audit_ledger: AuditLedger,
    ) -> None:
        self._ai_coordinator = ai_coordinator
        self._connections = connection_manager
        self._connection_service = connection_service
        self._audit = audit_ledger

    def dispatch(self, recommendation: AIRecommendation, risk: RiskLevel) -> AIRecommendation:
        if recommendation.selected_provider is None or recommendation.provider_request is None:
            raise NoConnectedProviderError(
                "AICoordinator.coordinate() selected no provider or prepared no request; "
                "nothing to dispatch."
            )

        connection_provider_id = MODEL_REGISTRY_TO_CONNECTION_PROVIDER_ID.get(
            recommendation.selected_provider.provider_id, recommendation.selected_provider.provider_id
        )
        connection = self._find_connected(connection_provider_id)
        if connection is None:
            self._audit.record(
                event_type="ai_coordination.dispatch_blocked",
                message=f"No CONNECTED connection for provider '{connection_provider_id}'; dispatch refused.",
                details={"provider_id": connection_provider_id, "recommendation_id": recommendation.recommendation_id},
            )
            raise NoConnectedProviderError(
                f"No CONNECTED, Owner-approved connection exists for provider "
                f"'{connection_provider_id}'. Owner approval and a live connection are "
                f"required before any prompt can be sent (Owner Sovereignty, Part 3)."
            )

        response = self._connection_service.send_prompt(connection.connection_id, recommendation.provider_request)

        return self._ai_coordinator.finalize_with_responses(
            recommendation.session_id, (response,), risk=risk
        )

    def _find_connected(self, provider_id: str) -> "Connection | None":
        for connection in self._connections.list_all():
            if connection.provider_id == provider_id and connection.status is ConnectionStatus.CONNECTED:
                return connection
        return None

    def is_healthy(self) -> bool:
        return self._connection_service.is_healthy()
