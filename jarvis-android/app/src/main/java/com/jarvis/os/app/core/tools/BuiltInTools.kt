package com.jarvis.os.app.core.tools

import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.model.ToolDefinition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 10: two real, deterministic, no-network tools -- enough to
 * exercise discovery, LOW-risk (no approval) and MODERATE-risk
 * (approval-gated) paths through ToolRepository.execute for real,
 * without wiring an actual network-calling tool this sandbox can't
 * verify end to end (same "boring technology, verify what you can
 * actually run" preference Sprint 9's flow tests already established).
 * A real WebSearchTool or GitHubTool later is: implement Tool, one
 * @Binds @IntoSet line in ToolModule -- identical swap point to
 * ChatProvider/AiRouter.
 */
@Singleton
class CalculatorTool @Inject constructor() : Tool {
    override val definition = ToolDefinition(
        toolId = "calculator",
        name = "Calculator",
        description = "Evaluates a simple arithmetic expression: digits, + - * / and parentheses only.",
        riskLevel = RiskLevel.LOW,
    )

    override suspend fun execute(input: String): ToolResult =
        try {
            ToolResult.Success(evaluate(input).toString())
        } catch (e: IllegalArgumentException) {
            ToolResult.Failure(e.message ?: "Invalid expression")
        }

    /**
     * Minimal recursive-descent evaluator -- no third-party expression
     * library, per this codebase's stdlib-first preference (see this
     * file's class docstring). Supports + - * / and parentheses;
     * rejects anything else.
     *
     * parseExpr/parseTerm/parseFactor are class-level private members,
     * not functions local to `evaluate`, because they're mutually
     * recursive (parseFactor calls back into parseExpr for a
     * parenthesized sub-expression) -- Kotlin resolves member
     * functions regardless of declaration order, but local functions
     * only see local functions already declared earlier in the same
     * block, so a local-function version of this cycle fails to
     * compile no matter which of the three is written first.
     * `sanitized`/`pos` are threaded through as parameters instead of
     * captured by closure since these are no longer nested functions.
     */
    private fun evaluate(expression: String): Double {
        val sanitized = expression.filter { !it.isWhitespace() }
        require(sanitized.isNotEmpty()) { "Empty expression" }
        require(sanitized.all { it.isDigit() || it in "+-*/()." }) { "Unsupported character in expression" }
        val pos = intArrayOf(0)
        val result = parseExpr(sanitized, pos)
        require(pos[0] == sanitized.length) { "Unexpected trailing characters" }
        return result
    }

    private fun parseExpr(sanitized: String, pos: IntArray): Double {
        var value = parseTerm(sanitized, pos)
        while (pos[0] < sanitized.length && sanitized[pos[0]] in "+-") {
            val op = sanitized[pos[0]++]
            val rhs = parseTerm(sanitized, pos)
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }

    private fun parseTerm(sanitized: String, pos: IntArray): Double {
        var value = parseFactor(sanitized, pos)
        while (pos[0] < sanitized.length && sanitized[pos[0]] in "*/") {
            val op = sanitized[pos[0]++]
            val rhs = parseFactor(sanitized, pos)
            value = if (op == '*') value * rhs else value / rhs
        }
        return value
    }

    private fun parseFactor(sanitized: String, pos: IntArray): Double {
        if (pos[0] < sanitized.length && sanitized[pos[0]] == '(') {
            pos[0]++
            val value = parseExpr(sanitized, pos)
            require(pos[0] < sanitized.length && sanitized[pos[0]] == ')') { "Missing closing parenthesis" }
            pos[0]++
            return value
        }
        val start = pos[0]
        while (pos[0] < sanitized.length && (sanitized[pos[0]].isDigit() || sanitized[pos[0]] == '.')) pos[0]++
        require(pos[0] > start) { "Expected a number at position $start" }
        return sanitized.substring(start, pos[0]).toDouble()
    }
}

/**
 * Deliberately MODERATE risk (unlike CalculatorTool) even though it
 * still does nothing real -- exists to give ToolRepository's approval
 * gate a genuine second candidate to gate, not just documentation of
 * what gating would do. A real ProjectFileWriteTool later inherits
 * this risk tier for the same reason: it mutates something outside
 * the running process.
 */
@Singleton
class ProjectNoteTool @Inject constructor() : Tool {
    override val definition = ToolDefinition(
        toolId = "project_note",
        name = "Project Note Writer",
        description = "Appends a note to a project's record. Mutates project state, so it requires owner approval.",
        riskLevel = RiskLevel.MODERATE,
    )

    override suspend fun execute(input: String): ToolResult =
        if (input.isBlank()) ToolResult.Failure("Note text cannot be blank")
        else ToolResult.Success("Recorded note (${input.length} chars): \"${input.take(80)}\"")
}
