package com.jarvis.os.app.core.tools

import com.jarvis.os.app.data.model.ToolDefinition

/**
 * Sprint 10: the seam every tool implements -- same "UI/callers depend
 * only on the interface" swap point ChatProvider established for AI
 * providers (see that file's docstring). ToolRegistry discovers bound
 * Tool instances via Hilt multibinding, ToolRepository is the only
 * caller of execute() (see that file's approval-gating docstring).
 */
interface Tool {
    val definition: ToolDefinition
    suspend fun execute(input: String): ToolResult
}

sealed interface ToolResult {
    data class Success(val output: String) : ToolResult
    data class Failure(val message: String) : ToolResult
}
