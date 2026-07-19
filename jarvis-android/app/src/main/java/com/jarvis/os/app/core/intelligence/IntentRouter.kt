package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.data.repository.ToolRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 14 introduced this to close the gap between chat and the
 * connector tools -- see the class docstring history for that
 * diagnosis. Sprint 15 "Executive Intelligence Completion" Phase 3 + 8
 * rewrote it once already (dropping the old ChatIntent enum and
 * hardcoded per-connector keyword table in favor of
 * ToolDefinition.triggerKeywords, owned by each tool). This second
 * pass, from the sprint's own Executive Integration Audit item 1,
 * changes ONE more thing: classify() returned only the FIRST matching
 * tool, so "Do I have meetings today and any important unread emails?"
 * silently ran GoogleCalendarTool and never even attempted Gmail --
 * the compound question's second half was dropped with no signal to
 * the owner or the LLM that anything was skipped. classifyAll() below
 * returns EVERY matching tool; JarvisCore.buildToolBackedContextHint
 * now runs all of them and folds every result into the same context
 * hint, so a compound question gets a compound, honest answer instead
 * of a silently partial one.
 *
 * DELIBERATELY NARROWER THAN JarvisDecisionEngine.matchedTool, still
 * not a replacement for it -- that reasoning (calculator-style tools
 * can't have their input reliably extracted from a sentence, so they're
 * only named, never run) is untouched by this rewrite. A tool only
 * ends up here, auto-runnable, if IT chose to set triggerKeywords.
 *
 * PROVIDER-AGNOSTIC BY CONSTRUCTION, same as before: this runs before
 * ChatRepository/AiRouter/any ChatProvider is touched. A future native
 * function-calling implementation of this interface (asking the model
 * itself which tool(s) to call, via an OpenAI-style tools schema) can
 * replace KeywordIntentRouter entirely without JarvisCore.sendChatMessage
 * changing, because it only ever depends on the IntentRouter interface
 * -- multi-tool selection is exactly the kind of decision a real
 * function-calling model is naturally good at, so this interface shape
 * (return a LIST) was chosen specifically to still fit that future
 * swap, not just today's keyword matching.
 */
data class IntentClassification(
    val toolId: String?,
    /** Which of the tool's own triggerKeywords actually matched -- useful for logging/debugging a routing decision, not used for control flow anywhere. */
    val matchedKeyword: String? = null,
)

interface IntentRouter {
    /** All tools whose triggerKeywords match this message, in ToolRepository.discover() order. Empty list means no tool applies -- the message goes to the LLM exactly as it does today (buildConversationalContextHint). */
    fun classifyAll(text: String): List<IntentClassification>
}

@Singleton
class KeywordIntentRouter @Inject constructor(
    private val tools: ToolRepository,
) : IntentRouter {
    override fun classifyAll(text: String): List<IntentClassification> {
        val lower = text.lowercase()
        return tools.discover().mapNotNull { tool ->
            tool.triggerKeywords.firstOrNull { it in lower }?.let { hit -> IntentClassification(tool.toolId, hit) }
        }
    }
}
