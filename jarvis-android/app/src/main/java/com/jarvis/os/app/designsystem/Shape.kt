package com.jarvis.os.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Part 2's "Spacing" system — a fixed 4dp-based scale, referenced by
 * name everywhere instead of raw `.dp` literals scattered through
 * feature code. This is what keeps every screen's rhythm consistent
 * without a design review catching drift after the fact.
 */
object JarvisSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
