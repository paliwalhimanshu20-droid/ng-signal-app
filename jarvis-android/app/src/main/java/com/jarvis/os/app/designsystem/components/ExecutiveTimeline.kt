package com.jarvis.os.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sprint 13 "Executive Timeline": a live activity feed, built directly
 * from AuditRepository.entries (Sprint 11) -- the exact same source
 * ExecutiveDashboardScreen's "Recent Audit" list already reads, given a
 * connected-dot visual treatment instead of a stack of identical cards.
 * No new data source, no invented events -- every line on this timeline
 * is a real AuditEntry that was actually recorded, matching this
 * sprint's "do not invent fake intelligence" rule exactly.
 *
 * Entries are expected newest-first (callers pass `entries.takeLast(N).reversed()`,
 * matching ExecutiveDashboardScreen's existing convention) so the most
 * recent moment reads at the top, same order a human reading a feed expects.
 */
@Composable
fun ExecutiveTimeline(entries: List<AuditEntry>, modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Column(modifier = modifier) {
        entries.forEachIndexed { index, entry ->
            TimelineRow(entry = entry, formatter = formatter, isLast = index == entries.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(entry: AuditEntry, formatter: DateTimeFormatter, isLast: Boolean) {
    Row(modifier = Modifier.padding(bottom = if (isLast) 0.dp else JarvisSpacing.md)) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
            Text(
                text = formatter.format(entry.timestamp.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(horizontal = JarvisSpacing.sm)) {
            Box(modifier = Modifier.size(10.dp).background(JarvisBrand.CoreCyan, CircleShape))
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = JarvisSpacing.xs)
                        .width(2.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
            }
        }
        Column(modifier = Modifier.padding(start = JarvisSpacing.xs)) {
            Text(entry.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
