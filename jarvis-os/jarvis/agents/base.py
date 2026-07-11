"""
jarvis.agents.base

BaseAgent — the abstract contract every specialist agent must satisfy.

SPRINT-1B UPGRADE NOTE: Sprint-0's version of this file defined
`handle_task`/`escalate` against placeholder `AgentTask`/`AgentResult`
shapes, with a docstring explicitly calling them a stand-in "until the
Orchestrator's Task Planner exists." Sprint-1A's TaskPlanner now exists,
and Sprint-1B needs agents to execute against the real `jarvis.intake.Task`
type and return a real, structured `ExecutionResult` — so this file is
replaced outright rather than extended alongside the placeholders. This
is a deliberate, sanctioned exception to "don't modify Sprint-0," per
this sprint's own brief ("unless a genuine architectural defect exists"):
Sprint-0's own docstring already named this exact upgrade as the expected
next step, and nothing in Sprint-0 or Sprint-1A ever called
handle_task/escalate/AgentTask/AgentResult, so nothing breaks.

Design reference: JARVIS-002 §15 (Base Agent Specification — domain,
capabilities, evidence-sourcing behavior, escalation behavior, trust tier
default — all still required; only the method shapes changed).
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from jarvis.agents.models import AgentHealthStatus, ExecutionResult
from jarvis.intake.models import Task


class BaseAgent(ABC):
    """
    Abstract base class every specialist agent must extend.

    Required by Sprint-1B: initialize(), health(), can_execute(task),
    execute(task), shutdown(), capabilities(), metadata(), version().

    `capabilities()`, `metadata()`, and `version()` are concrete —
    they return values fixed at construction time, and a subclass has no
    legitimate reason to override how they're reported (only what values
    they hold). `initialize()`, `shutdown()`, and `health()` have sensible
    concrete defaults (no-op / no-op / always-healthy) since not every
    agent needs real setup, teardown, or custom health logic — but every
    default is overridable. `can_execute()` has a real, working default
    implementation (domain/capability match against the Task's
    ExecutionPlan candidate_agents) rather than being abstract, because
    that matching logic is the same for every agent and duplicating it in
    every subclass would violate this sprint's "no duplicate logic"
    requirement. `execute()` remains abstract — it is inherently
    agent-specific and this base class has no way to provide a
    default without fabricating behavior it doesn't have (Article III).
    """

    def __init__(
        self,
        agent_id: str,
        domain: str,
        capabilities: tuple[str, ...],
        version: str = "0.1.0",
        display_name: str | None = None,
    ) -> None:
        self.agent_id = agent_id
        self.domain = domain
        self._capabilities = capabilities
        self._version = version
        self._display_name = display_name or agent_id

    def initialize(self) -> None:
        """
        Prepare the agent for execution. Default: no-op.

        Overridden by agents that need real setup (e.g. warming a cache,
        opening a connection) once such agents exist — none do yet in
        Sprint-1B's placeholder scope.
        """
        return None

    def shutdown(self) -> None:
        """Release any resources the agent holds. Default: no-op, symmetric with initialize()."""
        return None

    def health(self) -> AgentHealthStatus:
        """
        Report current health. Default: always healthy.

        A concrete agent with real failure modes (a connection that can
        drop, a rate limit that can be hit) should override this with a
        genuine check — returning a fabricated "healthy" from an agent
        that can't actually verify it would violate Article III, which is
        exactly why this default is documented as a *default*, not a
        universal truth every agent inherits blindly.
        """
        return AgentHealthStatus(healthy=True, detail=f"{self._display_name}: no health issues reported.")

    def can_execute(self, task: Task) -> bool:
        """
        Default capability-match check: does this agent's domain or any
        declared capability appear among the Task's ExecutionPlan
        candidate_agents (Sprint-1A's domain-hint list)?

        Returns False, honestly, if the task has no execution_plan yet or
        an empty candidate list — per this sprint's "never fabricate an
        available agent" requirement, applied at the single-agent level:
        an agent must never claim it can handle a task it has no
        real basis to believe matches its declared scope.
        """
        plan = task.execution_plan
        if plan is None or not plan.candidate_agents:
            return False
        if self.domain in plan.candidate_agents:
            return True
        return any(capability in plan.candidate_agents for capability in self._capabilities)

    @abstractmethod
    def execute(self, task: Task) -> ExecutionResult:
        """
        Execute the given task and return a structured ExecutionResult.

        Every concrete implementation must: check can_execute(task) itself
        before doing any real work (never assume the Router already did,
        since this method may be called directly in tests or future
        contexts); return ExecutionStatus.FAILED with at least one entry
        in `errors` rather than raising, for any expected failure mode
        (per ExecutionResult's own validation — a FAILED result with no
        errors is rejected at construction); and never return a raw
        string in place of an ExecutionResult, per this sprint's explicit
        requirement.
        """
        raise NotImplementedError

    def capabilities(self) -> tuple[str, ...]:
        return self._capabilities

    def metadata(self) -> dict[str, str]:
        return {
            "agent_id": self.agent_id,
            "domain": self.domain,
            "display_name": self._display_name,
            "version": self._version,
        }

    def version(self) -> str:
        return self._version
