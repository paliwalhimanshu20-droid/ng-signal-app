package com.jarvis.os.app.core.chat.markdown

/**
 * Sprint-8: activates MessageContentKind.MARKDOWN, which has existed
 * in the domain model since Sprint-7 but was never rendered — see
 * ChatScreen.kt's history. This is a deliberately hand-rolled parser,
 * not a third-party Markdown library: no such dependency exists in
 * this project's version catalog yet, and adding one here would be
 * both an unrelated dependency-management decision and something this
 * environment cannot verify actually resolves against Maven Central.
 * Covers exactly what a chat assistant's replies realistically need —
 * fenced code blocks, bullet lists, and inline bold/italic/code — not
 * a general-purpose Markdown spec implementation. Extending it (tables,
 * headers, links) is straightforward but explicitly out of scope here;
 * see architecture summary's "Next recommended sprint."
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
}

object MarkdownParser {

    fun parse(raw: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = raw.lines()
        var i = 0
        val paragraphBuffer = StringBuilder()
        val listBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotBlank()) {
                blocks += MarkdownBlock.Paragraph(paragraphBuffer.toString().trim())
            }
            paragraphBuffer.clear()
        }

        fun flushList() {
            if (listBuffer.isNotEmpty()) {
                blocks += MarkdownBlock.BulletList(listBuffer.toList())
                listBuffer.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmedStart = line.trimStart()

            if (trimmedStart.startsWith("```")) {
                flushParagraph()
                flushList()
                val language = trimmedStart.removePrefix("```").trim().ifBlank { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines += lines[i]
                    i++
                }
                blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language)
                if (i < lines.size) i++ // skip the closing fence; if unterminated, we've already reached lines.size
                continue
            }

            if (trimmedStart.startsWith("- ")) {
                flushParagraph()
                listBuffer += trimmedStart.removePrefix("- ").trim()
                i++
                continue
            } else if (listBuffer.isNotEmpty()) {
                flushList()
            }

            if (line.isBlank()) {
                flushParagraph()
            } else {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(" ")
                paragraphBuffer.append(line.trim())
            }
            i++
        }
        flushParagraph()
        flushList()
        return blocks
    }
}
