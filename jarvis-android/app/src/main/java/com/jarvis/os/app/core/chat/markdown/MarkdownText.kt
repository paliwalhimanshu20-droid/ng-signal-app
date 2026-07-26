package com.jarvis.os.app.core.chat.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal, dependency-free Markdown renderer for chat messages.
 *
 * Deliberately not a full CommonMark implementation — chat responses only need the common
 * subset: headers, bullet/numbered lists, fenced code blocks, and inline bold/italic/code/links.
 * If a message needs richer rendering later, swap this file's internals for a real markdown
 * library without touching the call site in ChatScreen.kt (`MarkdownText(message.content)`).
 *
 * Supported:
 *  - `# `.."###### ` headers
 *  - `- `, `* `, `+ ` bullet list items
 *  - `1. ` numbered list items
 *  - ``` fenced code blocks (monospace, unparsed inside)
 *  - inline `**bold**`, `*italic*`/`_italic_`, `` `code` ``, `[text](url)` (styled, not clickable)
 *
 * Anything not recognized is rendered as plain paragraph text — this never throws on
 * malformed/partial markdown (e.g. a streaming response mid-token), it just under-styles it.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val baseColor = LocalContentColor.current

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = style.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                is MarkdownBlock.Header -> {
                    Text(
                        text = renderInline(block.text, baseColor),
                        style = style.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = style.fontSize * headerScale(block.level),
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Text(
                        text = buildAnnotatedString {
                            append(block.prefix)
                            append(renderInline(block.text, baseColor))
                        },
                        style = style,
                        modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInline(block.text, baseColor),
                        style = style,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun headerScale(level: Int): Float = when (level) {
    1 -> 1.4f
    2 -> 1.25f
    3 -> 1.15f
    else -> 1.05f
}

private sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class ListItem(val prefix: String, val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.split("\n")

    var i = 0
    var orderedIndex = 1
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.toString().trim()))
            paragraphBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                orderedIndex = 1
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(code.toString().trimEnd('\n')))
                // i currently points at the closing fence (or EOF); loop's i++ advances past it
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                orderedIndex = 1
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val text = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Header(level, text))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushParagraph()
                orderedIndex = 1
                blocks.add(MarkdownBlock.ListItem("•  ", trimmed.drop(2).trim()))
            }

            Regex("""^\d+\.\s+""").containsMatchIn(trimmed) -> {
                flushParagraph()
                val rest = trimmed.replaceFirst(Regex("""^\d+\.\s+"""), "")
                blocks.add(MarkdownBlock.ListItem("$orderedIndex.  ", rest.trim()))
                orderedIndex++
            }

            trimmed.isBlank() -> {
                flushParagraph()
                orderedIndex = 1
            }

            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(" ")
                paragraphBuffer.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()

    return blocks
}

/** Inline styling: bold (**text**), italic (*text* or _text_), code (`text`), links [text](url). Single pass, left to right, no nesting. */
private fun renderInline(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    val pattern = Regex(
        """(\*\*(.+?)\*\*)|(\*(.+?)\*)|(_(.+?)_)|(`(.+?)`)|(\[(.+?)]\((.+?)\))"""
    )
    var lastIndex = 0
    for (match in pattern.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        when {
            match.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groups[2]!!.value)
            }
            match.groups[4] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(match.groups[4]!!.value)
            }
            match.groups[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(match.groups[6]!!.value)
            }
            match.groups[8] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f))
            ) {
                append(match.groups[8]!!.value)
            }
            match.groups[10] != null -> withStyle(
                SpanStyle(color = baseColor.copy(alpha = 0.85f), textDecoration = TextDecoration.Underline)
            ) {
                append(match.groups[10]!!.value)
            }
            else -> append(match.value)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}
