package com.jarvis.os.app.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Part 11's "Font Size" setting scales the whole type ramp by a
 * multiplier rather than offering per-style overrides — this keeps
 * every screen's type hierarchy internally consistent at any size the
 * Owner picks (a title is always meaningfully larger than a body at
 * every scale step), instead of risking a font-size picker that only
 * happens to look right at the default.
 */
enum class JarvisFontScale(val label: String, val multiplier: Float) {
    Small("Small", 0.9f),
    Default("Default", 1.0f),
    Large("Large", 1.15f),
    ExtraLarge("Extra Large", 1.3f),
}

/**
 * Part 11's "Fonts" setting — a small, curated set rather than an open
 * font picker, same "curated over arbitrary" reasoning as AccentColor.
 * FontFamily.Default/Serif/Monospace are guaranteed present on every
 * Android device (system fallback fonts), so this list needs no bundled
 * font assets to work correctly on day one.
 */
enum class JarvisFontFamily(val label: String, val family: FontFamily) {
    SansDefault("Sans (Default)", FontFamily.Default),
    Serif("Serif", FontFamily.Serif),
    Monospace("Monospace — Terminal", FontFamily.Monospace),
}

fun jarvisTypography(fontFamily: FontFamily, scale: Float): Typography {
    fun style(size: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (size * scale * 1.35f).sp,
        letterSpacing = tracking.sp,
    )

    return Typography(
        displayLarge = style(34f, FontWeight.SemiBold, -0.5f),
        displayMedium = style(28f, FontWeight.SemiBold),
        headlineLarge = style(24f, FontWeight.SemiBold),
        headlineMedium = style(20f, FontWeight.SemiBold),
        titleLarge = style(18f, FontWeight.Medium),
        titleMedium = style(16f, FontWeight.Medium),
        bodyLarge = style(16f, FontWeight.Normal),
        bodyMedium = style(14f, FontWeight.Normal),
        bodySmall = style(12f, FontWeight.Normal),
        labelLarge = style(14f, FontWeight.Medium, 0.2f),
        labelMedium = style(12f, FontWeight.Medium, 0.2f),
        labelSmall = style(11f, FontWeight.Medium, 0.3f),
    )
}
