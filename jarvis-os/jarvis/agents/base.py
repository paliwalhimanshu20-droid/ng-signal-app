"""
jarvis.agents.base

BaseAgent — the abstract contract every specialist agent must satisfy to
be registrable in the Agent Registry.

Design reference: JARVIS-002 §15 (Base Agent Specification). That section
requires every agent to declare: a single domain position, an enumerable
capability set, its evidence-sourcing behavior, its own escalation
behavior under uncertainty, and a declared trust tier default.

Sprint-0 scope: this is an ABSTRACT class only. It defines the shape and
raises NotImplementedError for every behavioral method — no reasoning, no
evidence grading, no actual task execution. A concrete agent (Engineering
Agent, GitHub Agent, etc., per JARVIS-003 Part I) is a future sprint's
work, built by subclassing this.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class AgentTask:
    """
    Placeholder task shape for Sprint-0.

    A real Task, once the Orchestrator's Task Planner exists (JARVIS-001
    §11), will carry dependencies, tier classification, and routing
    metadata. This minimal shape exists only so BaseAgent's method
    signatures are meaningful today without inventing task-graph
    machinery that belongs to a later sprint.
    """

    task_id: str
    description: str
    payload: dict[str, Any]


@dataclass(frozen=True)
class AgentResult:
    """
    Placeholder result shape for Sprint-0.

    A real result, once the Evidence and Confidence Frameworks exist
    (JARVIS-002 §22-23), will carry evidence grade, confidence score, and
    the six Blueprint Principle 12 fields for any recommendation. This
    minimal shape is a structural stand-in only.
    """

    task_id: str
    success: bool
    summary: str


class BaseAgent(ABC):
    """
    Abstract base class every specialist agent must extend.

    Per JARVIS-002 §15, a concrete agent must declare its domain, its
    capabilities, and its trust tier default at construction time, and
    must implement `handle_task` and `escalate` — the two behavioral
    methods every agent needs regardless of domain. Sprint-0 provides no
    concrete implementation of either; both raise NotImplementedError by
    design, since implementing them without evidence/confidence/approval
    machinery in place would produce an agent that could act without the
    governance this entire project exists to enforce.
    """

    def __init__(
        self,
        agent_id: str,
        domain: str,
        capabilities: tuple[str, ...],
        trust_tier_default: str = "provisional",
    ) -> None:
        self.agent_id = agent_id
        self.domain = domain
        self.capabilities = capabilities
        self.trust_tier_default = trust_tier_default

    @abstractmethod
    def handle_task(self, task: AgentTask) -> AgentResult:
        """
        Handle a single delegated task.

        A concrete agent implementing this method must, per JARVIS-002
        §15 and §24: source and grade its own evidence, populate the six
        Blueprint Principle 12 fields for any recommendation, and never
        execute a Tier 2/3-equivalent action without having first passed
        through the (not-yet-implemented) Approval Engine's gate.

        Sprint-0 leaves this unimplemented. Do not implement a concrete
        agent whose `handle_task` bypasses evidence grading or approval
        gating just to produce runnable output faster than the governed
        version — an ungoverned agent is not a smaller version of a
        governed one, it's a different, non-compliant thing entirely.
        """
        raise NotImplementedError(
            "BaseAgent.handle_task is abstract. Sprint-0 scope explicitly "
            "excludes concrete agent business logic — see jarvis.agents "
            "module docstring."
        )

    @abstractmethod
    def escalate(self, task: AgentTask, reason: str) -> None:
        """
        Escalate a task the agent is uncertain about, rather than guessing.

        Per JARVIS-002 §15's requirement that every agent declare its own
        escalation behavior, and JARVIS-001 §10's Core-level ambiguity
        pattern applied at the agent level: an agent that cannot meet a
        task's evidence bar must escalate, never proceed on its best
        guess. Sprint-0 leaves the actual escalation transport (routing
        back through the Orchestrator) unimplemented, since the
        Orchestrator's real request lifecycle doesn't exist yet either.
        """
        raise NotImplementedError(
            "BaseAgent.escalate is abstract. Sprint-0 scope explicitly "
            "excludes concrete agent business logic — see jarvis.agents "
            "module docstring."
        )
