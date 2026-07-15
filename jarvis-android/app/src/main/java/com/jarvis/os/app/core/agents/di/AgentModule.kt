package com.jarvis.os.app.core.agents.di

import com.jarvis.os.app.core.agents.Agent
import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.core.agents.BatmanAgent
import com.jarvis.os.app.core.agents.CaptainAmericaAgent
import com.jarvis.os.app.core.agents.CodeAgent
import com.jarvis.os.app.core.agents.DefaultMultiAiCoordinator
import com.jarvis.os.app.core.agents.DoctorStrangeAgent
import com.jarvis.os.app.core.agents.FlashAgent
import com.jarvis.os.app.core.agents.IronManAgent
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.agents.MultiAiCoordinator
import com.jarvis.os.app.core.agents.NickFuryAgent
import com.jarvis.os.app.core.agents.ProfessorXAgent
import com.jarvis.os.app.core.agents.ResearchAgent
import com.jarvis.os.app.core.agents.SpiderManAgent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Sprint 11 (extended Sprint 12): same swap point as ChatProviderModule/ToolModule -- a new Agent is one class plus one @Binds @IntoSet line. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds
    @IntoSet
    abstract fun bindResearchAgent(impl: ResearchAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindCodeAgent(impl: CodeAgent): Agent

    // Sprint 12 "Watch Tower Orchestration" -- see WatchTowerAgents.kt's docstring.
    @Binds
    @IntoSet
    abstract fun bindBatmanAgent(impl: BatmanAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindFlashAgent(impl: FlashAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindIronManAgent(impl: IronManAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindDoctorStrangeAgent(impl: DoctorStrangeAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindCaptainAmericaAgent(impl: CaptainAmericaAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindSpiderManAgent(impl: SpiderManAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindNickFuryAgent(impl: NickFuryAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindProfessorXAgent(impl: ProfessorXAgent): Agent

    @Binds
    @Singleton
    abstract fun bindAgentRegistry(impl: MockAgentRegistry): AgentRegistry

    @Binds
    @Singleton
    abstract fun bindMultiAiCoordinator(impl: DefaultMultiAiCoordinator): MultiAiCoordinator
}
