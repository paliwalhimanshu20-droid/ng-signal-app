package com.jarvis.os.app.core.tools.di

import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.core.tools.GitHubStatusTool
import com.jarvis.os.app.core.tools.GoogleWorkspaceTool
import com.jarvis.os.app.core.tools.NgSignalProStatusTool
import com.jarvis.os.app.core.tools.ProjectNoteTool
import com.jarvis.os.app.core.tools.StreamlitStatusTool
import com.jarvis.os.app.core.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Sprint 10: the same swap point ChatProviderModule established for AI providers, for tools -- a new tool is one class plus one line here. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {
    @Binds
    @IntoSet
    abstract fun bindCalculatorTool(impl: CalculatorTool): Tool

    @Binds
    @IntoSet
    abstract fun bindProjectNoteTool(impl: ProjectNoteTool): Tool

    // --- Sprint 13 "Connected Intelligence Platform": one binding per connector, same shape as above ---

    @Binds
    @IntoSet
    abstract fun bindGitHubStatusTool(impl: GitHubStatusTool): Tool

    @Binds
    @IntoSet
    abstract fun bindNgSignalProStatusTool(impl: NgSignalProStatusTool): Tool

    @Binds
    @IntoSet
    abstract fun bindStreamlitStatusTool(impl: StreamlitStatusTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGoogleWorkspaceTool(impl: GoogleWorkspaceTool): Tool
}
