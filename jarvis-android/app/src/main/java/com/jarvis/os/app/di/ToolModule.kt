package com.jarvis.os.app.di

import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.core.tools.GitHubStatusTool
import com.jarvis.os.app.core.tools.GoogleCalendarTool
import com.jarvis.os.app.core.tools.GoogleDriveTool
import com.jarvis.os.app.core.tools.GoogleGmailTool
import com.jarvis.os.app.core.tools.GoogleWorkspaceHealthTool
import com.jarvis.os.app.core.tools.NgSignalProStatusTool
import com.jarvis.os.app.core.tools.ProjectNoteTool
import com.jarvis.os.app.core.tools.StreamlitStatusTool
import com.jarvis.os.app.core.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * The swap point [Tool]'s own docstring promises: "ToolRegistry discovers bound Tool instances
 * via Hilt multibinding." MockToolRepository injects `Set<Tool>` (see ToolRepository.kt) —
 * without this module that set has no contributors and MockToolRepository (and therefore
 * ToolRepository, and therefore JarvisDecisionEngine/SystemHealthMonitor/JarvisCore) fails to
 * build. Documented in BuiltInTools.kt's own docstring but never actually created.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {

    @Binds
    @IntoSet
    abstract fun bindCalculatorTool(impl: CalculatorTool): Tool

    @Binds
    @IntoSet
    abstract fun bindProjectNoteTool(impl: ProjectNoteTool): Tool

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
    abstract fun bindGoogleCalendarTool(impl: GoogleCalendarTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGoogleGmailTool(impl: GoogleGmailTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGoogleDriveTool(impl: GoogleDriveTool): Tool

    @Binds
    @IntoSet
    abstract fun bindGoogleWorkspaceHealthTool(impl: GoogleWorkspaceHealthTool): Tool
}
