package com.jarvis.os.app.di

import com.jarvis.os.app.core.intelligence.localintent.AnalyticsLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.ConnectedSystemsLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.ConversationLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.DatasetStatusLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.DefaultLocalIntentRouter
import com.jarvis.os.app.core.intelligence.localintent.DeviceActionLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.DiagnosticsLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.EvidenceValidationLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.GreetingLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.HelpLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.KnowledgeBaseLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.LocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.LocalIntentRouter
import com.jarvis.os.app.core.intelligence.localintent.MissionControlLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.SettingsLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.SignalsLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.SystemStatusLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.TidbLocalIntentHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * "OS First" Local Intent Router: the swap point [LocalIntentHandler]'s own docstring promises,
 * mirroring [ToolModule]/[AgentModule]/[ChatProviderModule]'s exact `@Binds @IntoSet` shape --
 * DefaultLocalIntentRouter injects `Set<LocalIntentHandler>`; without this module that set has
 * no contributors and every message would fall straight through to an AI provider, defeating the
 * whole point of this router. A new local capability is a new handler class implementing
 * [LocalIntentHandler] plus one `@Binds` line here -- JarvisCore never needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalIntentHandlerModule {

    @Binds
    abstract fun bindLocalIntentRouter(impl: DefaultLocalIntentRouter): LocalIntentRouter

    @Binds
    @IntoSet
    abstract fun bindTidbLocalIntentHandler(impl: TidbLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindSignalsLocalIntentHandler(impl: SignalsLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindAnalyticsLocalIntentHandler(impl: AnalyticsLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindMissionControlLocalIntentHandler(impl: MissionControlLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindConnectedSystemsLocalIntentHandler(impl: ConnectedSystemsLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindDiagnosticsLocalIntentHandler(impl: DiagnosticsLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindSettingsLocalIntentHandler(impl: SettingsLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindGreetingLocalIntentHandler(impl: GreetingLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindHelpLocalIntentHandler(impl: HelpLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindConversationLocalIntentHandler(impl: ConversationLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindKnowledgeBaseLocalIntentHandler(impl: KnowledgeBaseLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindDeviceActionLocalIntentHandler(impl: DeviceActionLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindEvidenceValidationLocalIntentHandler(impl: EvidenceValidationLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindSystemStatusLocalIntentHandler(impl: SystemStatusLocalIntentHandler): LocalIntentHandler

    @Binds
    @IntoSet
    abstract fun bindDatasetStatusLocalIntentHandler(impl: DatasetStatusLocalIntentHandler): LocalIntentHandler
}
