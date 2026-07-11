"""
jarvis.registry.agent_registry

The Agent Registry: the single source of truth for which agents exist and
what state they're in.

Design reference: JARVIS-002 §17. Per that section, the Registry is
"queried by the Orchestration Layer at routing time (read-heavy, low-
latency)" and "written to only through the lifecycle transitions
(infrequent, governance-gated)". Sprint-0 implements the read/write
mechanics; it does NOT implement the governance gating itself (that
requires the Approval Engine, out of scope) — every transition method
below is written to make that future gating easy to insert without an
interface change, and says so explicitly at the point it's missing.
"""

from __future__ import annotations

from jarvis.logging_ import get_logger
from jarvis.registry.models import AgentLifecycleState, AgentRecord, is_legal_transition

logger = get_logger(__name__)


class RegistryError(Exception):
    """Raised for any invalid Registry operation (duplicate ID, illegal transition, not found)."""


class AgentRegistry:
    """
    In-memory Agent Registry for Sprint-0.

    Sprint-0 scope: no persistence backend yet — the Registry is rebuilt
    fresh from JARVIS-001 §7 Step 4 (Load the Agent Registry) each time
    Core boots. This is intentional and matches JARVIS-001 §6's stateless-
    Core principle: nothing about JARVIS Core's own runtime should assume
    long-lived in-process state survives a restart. A later sprint should
    back this with the same kind of durable, git-committed persistence
    already used elsewhere in this project's broader ecosystem — not
    designed here, since no real agents exist yet to persist.
    """

    def __init__(self) -> None:
        self._agents: dict[str, AgentRecord] = {}

    def register(self, record: AgentRecord) -> None:
        """
        Register a new agent, starting in the PROPOSED lifecycle state
        unless the record explicitly declares otherwise (only intended for
        test/bootstrap seeding — real proposals always start at PROPOSED).
        """
        if record.agent_id in self._agents:
            raise RegistryError(
                f"Agent '{record.agent_id}' is already registered. "
                "Use transition() to change its lifecycle state, not register() again."
            )
        self._agents[record.agent_id] = record
        logger.info(
            "Agent registered: id=%s domain=%s state=%s",
            record.agent_id,
            record.domain,
            record.lifecycle_state.value,
        )

    def get(self, agent_id: str) -> AgentRecord:
        try:
            return self._agents[agent_id]
        except KeyError as exc:
            raise RegistryError(f"No agent registered with id '{agent_id}'.") from exc

    def all(self) -> tuple[AgentRecord, ...]:
        return tuple(self._agents.values())

    def active_agents(self) -> tuple[AgentRecord, ...]:
        """
        Return only agents currently in the ACTIVE lifecycle state — the
        set that should be considered routable. Sprint-0 has no
        Orchestrator to actually route to them yet, but this method is the
        contract a future Orchestrator will call against, per JARVIS-002
        §17's routing/read access pattern.
        """
        return tuple(
            record
            for record in self._agents.values()
            if record.lifecycle_state is AgentLifecycleState.ACTIVE
        )

    def transition(self, agent_id: str, target_state: AgentLifecycleState) -> AgentRecord:
        """
        Move an agent to a new lifecycle state.

        IMPORTANT — Sprint-0 limitation, stated explicitly rather than
        silently omitted: per JARVIS-002 §16, real transitions should be
        gated by Tier 2 governance review (and, for PROVISIONED -> ACTIVE
        specifically, a mandatory probation period). Sprint-0 implements
        only the STRUCTURAL legality check (is this a legal state-machine
        transition at all) — it does NOT implement the governance gate
        itself, because that requires the Approval Engine, which is
        explicitly out of scope for this sprint. Do not treat a successful
        call to this method as constitutionally sufficient on its own
        once the Approval Engine exists — it will need to call into that
        engine before invoking this method, not after.
        """
        record = self.get(agent_id)

        if not is_legal_transition(record.lifecycle_state, target_state):
            raise RegistryError(
                f"Illegal lifecycle transition for agent '{agent_id}': "
                f"{record.lifecycle_state.value} -> {target_state.value}"
            )

        previous_state = record.lifecycle_state
        record.lifecycle_state = target_state
        logger.info(
            "Agent lifecycle transition: id=%s %s -> %s",
            agent_id,
            previous_state.value,
            target_state.value,
        )
        return record

    def __len__(self) -> int:
        return len(self._agents)
