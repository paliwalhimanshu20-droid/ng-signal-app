package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PR4: capability-based routing on top of Sprint 8.1's manual
 * switchProvider. `active` (manual pick) is unchanged and still what
 * ChatRepository.sendMessage uses by default -- routeFor is additive,
 * used by callers that care WHAT a message needs rather than which
 * provider happens to be selected right now (MultiAiCoordinator in
 * Sprint 11 is the first real caller).
 *
 * Scoring is deliberately simple and fully deterministic (no ML, no
 * heuristics beyond set overlap) per this codebase's "boring
 * technology" preference: count how many of the requested capabilities
 * each bound provider declares, pick the highest count, break ties by
 * declared-provider order (stable iteration over the injected Set).
 * `null` (no requirement) or an empty requirement set both mean "any
 * provider is fine" and return the currently active one, so routeFor
 * is a safe default even for capability-agnostic callers.
 */
@Singleton
class AiRouter @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards ChatProvider>,
) {
    val available: List<ChatProvider> get() = providers.toList()

    private val _activeProviderId = MutableStateFlow(
        providers.firstOrNull()?.id ?: error("No ChatProvider bound -- check ChatProviderModule"),
    )
    val activeProviderId: StateFlow<String> = _activeProviderId

    val active: ChatProvider
        get() = providers.firstOrNull { it.id == _activeProviderId.value } ?: providers.first()

    /** Returns false and leaves the active provider unchanged if providerId isn't bound. */
    fun switchProvider(providerId: String): Boolean {
        val found = providers.any { it.id == providerId }
        if (found) _activeProviderId.value = providerId
        return found
    }

    /**
     * PR4: pick the best-fit bound provider for a set of required
     * capabilities without changing `active`/`activeProviderId` --
     * this is a per-call routing decision, not a standing switch. A
     * provider that declares zero of the requested capabilities is
     * still eligible (score 0) rather than excluded, so this never
     * returns null while any provider is bound; a caller that truly
     * requires a capability nothing declares should check
     * `providersFor(capability).isNotEmpty()` first rather than rely on
     * this silently falling back.
     */
    fun routeFor(required: Set<AiCapability>): ChatProvider {
        if (required.isEmpty()) return active
        return providers.maxByOrNull { provider -> (provider.capabilities intersect required).size } ?: active
    }

    fun routeForAny(vararg required: AiCapability): ChatProvider = routeFor(required.toSet())

    /** All bound providers that declare a given capability, in stable iteration order -- used by a future provider-picker UI and by MultiAiCoordinator's eligibility checks. */
    fun providersFor(capability: AiCapability): List<ChatProvider> =
        providers.filter { capability in it.capabilities }.toList()
}
