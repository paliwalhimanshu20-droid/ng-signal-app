"""
jarvis.intelligence.goal_manager

Sprint-4 Part 3 — Goal Manager.

In-memory only, matching jarvis.registry.AgentRegistry's own Sprint-0
precedent for the same reason: goal tracking's persistence needs (across
a restart) are a real, but separate, question from the Intelligence
Layer's reasoning logic this sprint scopes — Sprint-4's brief does not
list Goal persistence among Part 3's requirements the way Sprint-3
explicitly required Session persistence. Deferred explicitly, not
silently, to Sprint-5 (see delivery notes).
"""

from __future__ import annotations

from typing import Optional

from jarvis.audit import AuditLedger
from jarvis.intelligence.models import Goal, GoalCategory, GoalStatus, TaskPriority, utc_now_iso


class GoalError(Exception):
    """Raised for any invalid Goal operation (unknown goal_id, illegal status transition)."""


class GoalManager:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger
        self._goals: dict[str, Goal] = {}

    def create(
        self,
        title: str,
        description: str,
        category: GoalCategory,
        confidence: float,
        confidence_reason: str,
        priority: TaskPriority = TaskPriority.NORMAL,
        parent_goal: Optional[str] = None,
    ) -> Goal:
        if parent_goal is not None and parent_goal not in self._goals:
            raise GoalError(f"Cannot create sub-goal: parent goal '{parent_goal}' does not exist.")

        goal = Goal.new(
            title=title,
            description=description,
            category=category,
            confidence=confidence,
            confidence_reason=confidence_reason,
            priority=priority,
            parent_goal=parent_goal,
        )
        self._goals[goal.goal_id] = goal

        if parent_goal is not None:
            self._goals[parent_goal].sub_goals.append(goal.goal_id)
            self._goals[parent_goal].updated_at = utc_now_iso()

        self._audit.record(
            event_type="intelligence.goal_created",
            message=f"Goal created: {title}",
            details={"goal_id": goal.goal_id, "category": category.value, "parent_goal": parent_goal},
        )
        return goal

    def get(self, goal_id: str) -> Goal:
        try:
            return self._goals[goal_id]
        except KeyError as exc:
            raise GoalError(f"No goal found with id '{goal_id}'.") from exc

    def update(self, goal_id: str, **fields) -> Goal:
        """Update arbitrary mutable fields (title, description, priority, status). Unknown field names raise, rather than being silently ignored."""
        goal = self.get(goal_id)
        allowed = {"title", "description", "priority", "status"}
        unknown = set(fields) - allowed
        if unknown:
            raise GoalError(f"Cannot update unknown Goal field(s): {sorted(unknown)}")

        for name, value in fields.items():
            setattr(goal, name, value)
        goal.updated_at = utc_now_iso()

        self._audit.record(
            event_type="intelligence.goal_updated",
            message=f"Goal updated: {goal.title}",
            details={"goal_id": goal_id, "fields": sorted(fields)},
        )
        return goal

    def add_dependency(self, goal_id: str, depends_on_goal_id: str) -> Goal:
        goal = self.get(goal_id)
        self.get(depends_on_goal_id)  # validates the dependency target actually exists
        if depends_on_goal_id not in goal.dependencies:
            goal.dependencies.append(depends_on_goal_id)
            goal.updated_at = utc_now_iso()
        return goal

    def complete(self, goal_id: str) -> Goal:
        goal = self.get(goal_id)
        if goal.status is GoalStatus.CANCELLED:
            raise GoalError(f"Cannot complete goal '{goal_id}': it was already cancelled.")
        goal.status = GoalStatus.COMPLETED
        goal.updated_at = utc_now_iso()
        self._audit.record(
            event_type="intelligence.goal_completed",
            message=f"Goal completed: {goal.title}",
            details={"goal_id": goal_id},
        )
        return goal

    def cancel(self, goal_id: str, reason: str) -> Goal:
        goal = self.get(goal_id)
        if goal.status is GoalStatus.COMPLETED:
            raise GoalError(f"Cannot cancel goal '{goal_id}': it was already completed.")
        goal.status = GoalStatus.CANCELLED
        goal.updated_at = utc_now_iso()
        self._audit.record(
            event_type="intelligence.goal_cancelled",
            message=f"Goal cancelled: {goal.title}",
            details={"goal_id": goal_id, "reason": reason},
        )
        return goal

    def block(self, goal_id: str, reason: str) -> Goal:
        goal = self.get(goal_id)
        goal.status = GoalStatus.BLOCKED
        goal.updated_at = utc_now_iso()
        self._audit.record(
            event_type="intelligence.goal_blocked",
            message=f"Goal blocked: {goal.title}",
            details={"goal_id": goal_id, "reason": reason},
        )
        return goal

    def list_by_status(self, status: GoalStatus) -> list[Goal]:
        return [g for g in self._goals.values() if g.status is status]

    def is_healthy(self) -> bool:
        return isinstance(self._goals, dict)

    def __len__(self) -> int:
        return len(self._goals)
