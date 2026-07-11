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

    # =========================================================================
    # SPRINT-1B ADDITIONS BELOW — all additive. Every method above this line
    # is unchanged from Sprint-0, byte-for-byte, and every Sprint-0 test
    # continues to pass unmodified against it.
    # =========================================================================

    def unregister(self, agent_id: str) -> None:
        """
        Remove an agent from the Registry entirely.

        Distinct from transition(..., DEPRECATED): deprecation (JARVIS-002
        §16) is the formal governance end-state and preserves the record
        (and its audit history) in place. unregister() is a lower-level
        operation that removes the entry outright — the mechanism this
        sprint's required "hot-swapping" support is built on (unregister
        the old instance, register a replacement under the same or a new
        agent_id). Raises RegistryError if the agent isn't registered, for
        the same fail-closed reasoning as every other lookup in this class.
        """
        if agent_id not in self._agents:
            raise RegistryError(f"Cannot unregister: no agent registered with id '{agent_id}'.")
        del self._agents[agent_id]
        logger.info("Agent unregistered: id=%s", agent_id)

    def discover_agents(self) -> tuple[AgentRecord, ...]:
        """
        Return every currently ACTIVE, routable agent.

        Deliberately an alias over active_agents() rather than a
        reimplementation — "discovery" and "what's routable" are the same
        question from the Router's point of view (JARVIS-002 §17), and
        this sprint's brief names both "Discover Agents" and the existing
        Sprint-0 active_agents() separately only because Sprint-0 didn't
        yet have a Router that needed the discovery framing. Keeping one
        implementation avoids the two ever silently drifting apart.
        """
        return self.active_agents()

    def lookup_by_capability(self, capability: str) -> tuple[AgentRecord, ...]:
        """
        Return every ACTIVE agent that declares the given capability.

        Per JARVIS-002 §18, capability declarations are a static ceiling
        checked at registration — this method reads that declaration, it
        does not (and must not) infer or expand capabilities dynamically.
        """
        return tuple(
            record for record in self.active_agents() if capability in record.capabilities
        )

    def health_status(self, agent_id: str) -> "AgentHealthStatus":
        """
        Query an agent's live health, per JARVIS-001 §22's per-agent health
        concept, delegated to the agent's own BaseAgent.health().

        Returns an unhealthy status (rather than raising) if the record has
        no live `instance` attached — a registered-but-instance-less record
        is a real, representable state (e.g. mid-provisioning), not an
        error condition this method should refuse to answer about.
        """
        from jarvis.agents.models import AgentHealthStatus  # local import: avoids a hard,

        # module-load-time dependency from jarvis.registry on jarvis.agents;
        # only needed inside this one method's return path.
        record = self.get(agent_id)
        if record.instance is None:
            return AgentHealthStatus(
                healthy=False,
                detail=f"Agent '{agent_id}' has no live instance attached to its Registry record.",
            )
        return record.instance.health()

    def is_available(self, agent_id: str) -> bool:
        """
        An agent is "available" iff it is ACTIVE and reports healthy.

        This is the single predicate the Router (jarvis.routing) uses to
        decide whether a candidate is even worth evaluating for
        capability match — combining lifecycle state and live health into
        one clear, testable question.
        """
        record = self.get(agent_id)
        if record.lifecycle_state is not AgentLifecycleState.ACTIVE:
            return False
        return self.health_status(agent_id).healthy
