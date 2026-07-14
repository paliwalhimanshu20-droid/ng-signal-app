package com.jarvis.os.app.core.agents.di

import com.jarvis.os.app.core.agents.Agent
import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.core.agents.CodeAgent
import com.jarvis.os.app.core.agents.DefaultMultiAiCoordinator
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.agents.MultiAiCoordinator
import com.jarvis.os.app.core.agents.ResearchAgent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Sprint 11: same swap point as ChatProviderModule/ToolModule -- a new Agent is one class plus one @Binds @IntoSet line. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds
    @IntoSet
    abstract fun bindResearchAgent(impl: ResearchAgent): Agent

    @Binds
    @IntoSet
    abstract fun bindCodeAgent(impl: CodeAgent): Agent

    @Binds
    @Singleton
    abstract fun bindAgentRegistry(impl: MockAgentRegistry): AgentRegistry

    @Binds
    @Singleton
    abstract fun bindMultiAiCoordinator(impl: DefaultMultiAiCoordinator): MultiAiCoordinator
}
