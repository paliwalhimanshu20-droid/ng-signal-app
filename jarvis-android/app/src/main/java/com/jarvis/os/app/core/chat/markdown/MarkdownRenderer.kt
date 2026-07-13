package com.jarvis.os.app.core.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.os.app.designsystem.JarvisSpacing

/**
 * Sprint-8: renders MessageContentKind.MARKDOWN content. Parsing
 * happens in MarkdownParser (pure, no Compose dependency, independently
 * verifiable); this file is presentation only, matching this codebase's
 * "clean separation between UI and business logic" requirement.
 */
@Composable
fun MarkdownText(raw: String, modifier: Modifier = Modifier) {
    val blocks = remember(raw) { MarkdownParser.parse(raw) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(modifier = Modifier.padding(top = JarvisSpacing.xs))
            when (block) {
                is MarkdownBlock.Paragraph -> Text(parseInlineSpans(block.text), style = MaterialTheme.typography.bodyMedium)
                is MarkdownBlock.BulletList -> Column {
                    block.items.forEach { item ->
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium)
                            Text(parseInlineSpans(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> CodeBlockView(block)
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(JarvisSpacing.sm),
    ) {
        if (block.language != null) {
            Text(
                block.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = JarvisSpacing.xs),
            )
        }
        Text(
            block.code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Single-pass scan, not sequential regex substitution — a common bug
 * source for **bold**/*italic* is regex for one pattern also matching
 * inside the other. Checking "**" before "*" in this `when`'s branch
 * order (Kotlin evaluates in order, first match wins) is what makes
 * that ambiguity a non-issue here.
 */
private fun parseInlineSpans(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Gray.copy(alpha = 0.2f),
                        ),
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
