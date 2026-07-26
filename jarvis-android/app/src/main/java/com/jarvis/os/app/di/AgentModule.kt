package com.jarvis.os.app.di

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

/**
 * The swap point [Agent]'s own docstring promises: "AgentRegistry discovers bound instances via
 * Hilt multibinding, a real agent later is one class plus one @Binds @IntoSet line." Also binds
 * the two Sprint 11 coordination interfaces ([AgentRegistry], [MultiAiCoordinator]) that sit on
 * top of the Agent set — WatchTowerOrchestrator needs MultiAiCoordinator,
 * ExecutiveBriefingEngine needs AgentRegistry, and both were unbuildable without this module.
 *
 * The 8 WatchTowerAgents (Batman/Flash/IronMan/DoctorStrange/CaptainAmerica/SpiderMan/NickFury/
 * ProfessorX) plus the 2 BuiltInAgents (Research/Code) are exactly the Watch Tower roster this
 * milestone's "preserve Watch Tower architecture" instruction refers to — nothing about their
 * shape changed, they were just never wired in.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAgentRegistry(impl: MockAgentRegistry): AgentRegistry

    @Binds
    @Singleton
    abstract fun bindMultiAiCoordinator(impl: DefaultMultiAiCoordinator): MultiAiCoordinator

    // --- Sprint 11 built-in agents ---

    @Binds
    @IntoSet
    abstract fun bindResearchAgent(impl: ResearchAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindCodeAgent(impl: CodeAgent): Agent

    // --- Watch Tower roster ---

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
}
