package com.jarvis.os.app.core.workflow.di

import com.jarvis.os.app.core.workflow.DefaultWorkflowEngine
import com.jarvis.os.app.core.workflow.WorkflowEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkflowModule {
    @Binds
    @Singleton
    abstract fun bindWorkflowEngine(impl: DefaultWorkflowEngine): WorkflowEngine
}
