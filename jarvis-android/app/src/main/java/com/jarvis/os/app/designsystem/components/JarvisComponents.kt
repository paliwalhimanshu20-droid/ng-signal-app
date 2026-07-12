package com.jarvis.os.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.os.app.designsystem.JarvisSpacing

/**
 * The one card shape every dashboard/list/detail screen in this app
 * uses (Part 2: "Cards" is a design-system concern, not something each
 * feature reinvents). Elevation is intentionally minimal — Part 15's
 * "Minimal, Professional, not a chatbot" brief reads as flat surfaces
 * differentiated by tone, not drop-shadow-heavy card stacks.
 */
@Composable
fun JarvisCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(JarvisSpacing.md)) {
            content()
        }
    }
}

/**
 * A small colored dot + label used for connection/approval/health
 * status everywhere (Connections, Approval Center, Home Automation).
 * Color is passed explicitly rather than derived from a string, so
 * callers can't accidentally introduce a status this component doesn't
 * recognize.
 */
@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = JarvisSpacing.sm, vertical = JarvisSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = JarvisSpacing.xs)
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
