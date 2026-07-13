package com.jarvis.os.app.core.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 8.1: the AI Router requested to live inside Core. Replaces
 * Sprint-8's ChatProviderRegistry -- same underlying responsibility
 * (choosing among bound ChatProvider implementations), renamed and
 * extended with real switching state rather than a fixed
 * first-available pick. delete ChatProviderRegistry.kt when applying
 * this sprint; nothing else references it.
 *
 * Today exactly one provider (MockChatProvider) is bound via Hilt's
 * multibinding (see ChatProviderModule), so switchProvider has nothing
 * real to switch between yet -- the state and method are real and
 * exercised by ChatRepository, just with one candidate. Adding a
 * second provider is one class implementing ChatProvider plus one
 * @Binds @IntoSet line; nothing here or above this class changes.
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
}
