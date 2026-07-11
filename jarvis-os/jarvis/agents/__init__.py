"""
jarvis.agents

Base Agent Specification (JARVIS-002 §15) and concrete specialist agents.

SPRINT-1B UPGRADE: BaseAgent's method contract changed (see base.py's
module docstring for the full reasoning) — this package now also exports
the execution-result models (ExecutionResult, ExecutionStatus,
AgentHealthStatus) and the first concrete agent, EngineeringAgent.
"""

from jarvis.agents.base import BaseAgent
from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.agents.models import AgentHealthStatus, ExecutionResult, ExecutionStatus

__all__ = [
    "AgentHealthStatus",
    "BaseAgent",
    "EngineeringAgent",
    "ExecutionResult",
    "ExecutionStatus",
]
