"""
jarvis.ai_coordination.health

Sprint-5 Part 12 — AI Coordination Health.

Matches the shape and philosophy of every prior sprint's health check.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class AICoordinationHealthReport:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"AI Coordination Layer health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_ai_coordination_health_check(coordinator: "AICoordinator") -> AICoordinationHealthReport:  # type: ignore[name-defined]  # noqa: F821
    """Part 12 requires: AI Coordinator, Capability Registry, Model Registry, Model Selector, Session Manager, Conversation Manager, Prompt Dispatcher, Response Validator, Consensus Engine, Conflict Resolver, Overall Health. All eleven are checked here."""
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    checks["ai_coordinator"] = coordinator is not None
    detail["ai_coordinator"] = "constructed"

    checks["capability_registry"] = coordinator.capability_registry.is_healthy()
    detail["capability_registry"] = f"{len(coordinator.capability_registry.list_all())} capabilities registered"

    checks["model_registry"] = coordinator.model_registry.is_healthy()
    detail["model_registry"] = f"{len(coordinator.model_registry)} provider(s) registered"

    checks["model_selector"] = coordinator.model_selector.is_healthy()
    detail["model_selector"] = "selection rules loaded"

    checks["session_manager"] = coordinator.session_manager.is_healthy()
    detail["session_manager"] = f"{len(coordinator.session_manager)} session(s) tracked"

    checks["conversation_manager"] = coordinator.conversation_manager.is_healthy()
    detail["conversation_manager"] = f"{len(coordinator.conversation_manager)} conversation(s) tracked"

    checks["prompt_dispatcher"] = coordinator.prompt_dispatcher.is_healthy()
    detail["prompt_dispatcher"] = "present"

    checks["response_validator"] = coordinator.response_validator.is_healthy()
    detail["response_validator"] = "validation rules loaded"

    checks["consensus_engine"] = coordinator.consensus_engine.is_healthy()
    detail["consensus_engine"] = "present"

    checks["conflict_resolver"] = coordinator.conflict_resolver.is_healthy()
    detail["conflict_resolver"] = "resolution rules loaded"

    overall_healthy = all(checks.values())
    return AICoordinationHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
