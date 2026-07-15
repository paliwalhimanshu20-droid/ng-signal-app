package com.jarvis.os.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/**
 * Sprint 13 "Premium Design System": the glass-panel surface behind
 * Executive Briefing, Mission Control's tiles, and the Executive
 * Timeline -- a translucent surface with a soft gradient-brightened
 * top edge, standing in for true glassmorphism (backdrop blur). Real
 * backdrop blur needs Android 12's RenderEffect (API 31); this app's
 * minSdk is 26 (see build.gradle.kts), so this uses alpha-blended
 * layering instead -- a technique that renders identically on every
 * OS version this app supports, rather than a blur that would silently
 * do nothing (or need a second, unblurred fallback look) on 5 years of
 * still-supported devices.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f)),
                ),
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
    ) {
        Box(modifier = Modifier.padding(JarvisSpacing.lg)) {
            content()
        }
    }
}

/**
 * Sprint 13 "Mission Control": one live operational-status tile
 * ("AI PROVIDERS", "WATCH TOWER", ...) -- the HUD vocabulary Mission
 * Control asks for instead of a settings-page list. `label` renders in
 * [jarvisHudLabelStyle] (wide-tracked small caps); `value` is the one
 * headline fact about that subsystem; `accentColor` tints the status
 * dot and is the one place per-tile color varies, everything else
 * about the tile's chrome is identical across all of Mission Control's
 * tiles so the grid reads as one coherent instrument panel, not seven
 * differently-styled widgets.
 */
@Composable
fun MissionControlTile(
    label: String,
    value: String,
    detail: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, borderColor = accentColor.copy(alpha = 0.18f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).background(accentColor, CircleShape))
                Text(
                    text = label,
                    style = com.jarvis.os.app.designsystem.jarvisHudLabelStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = JarvisSpacing.sm),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = JarvisSpacing.sm),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = JarvisSpacing.xs),
            )
        }
    }
}
