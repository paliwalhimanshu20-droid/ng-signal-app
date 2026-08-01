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

/**
 * Sprint 13 "JARVIS Identity": one deliberately oversized, ultra-light
 * style reserved for the Home screen's greeting only ("Good Morning.")
 * -- not one of Material3's fixed Typography slots above (which top
 * out at displayLarge/34sp), because the greeting needs to read
 * distinctly larger and lighter than anything else in the app to
 * carry the "JARVIS is alive and already working" moment Sprint 13
 * asks for; reusing displayLarge here would make the greeting look
 * like every other headline, not a signature moment.
 *
 * @Composable and derived from MaterialTheme.typography.displayLarge
 * (already-resolved for the Owner's font family AND font-size scale)
 * rather than taking fontFamily/scale parameters directly -- passing
 * those in separately would double-apply the Owner's Font Size setting
 * on top of what jarvisTypography already baked in, silently making
 * every scale step wrong for this one element.
 */
@androidx.compose.runtime.Composable
fun jarvisHeroStyle(): TextStyle {
    val base = androidx.compose.material3.MaterialTheme.typography.displayLarge
    return base.copy(
        fontWeight = FontWeight.Light,
        fontSize = base.fontSize * 1.3f,
        lineHeight = base.lineHeight * 1.3f,
        letterSpacing = (-1f).sp,
    )
}

/**
 * Sprint 13: the small-caps, wide-tracked label style used throughout
 * Mission Control's HUD tiles ("AI PROVIDERS", "WATCH TOWER") --
 * distinct from labelMedium/labelSmall above, which are used for
 * ordinary UI chrome (status pills, form field captions). Same
 * derive-from-resolved-theme reasoning as jarvisHeroStyle above.
 */
@androidx.compose.runtime.Composable
fun jarvisHudLabelStyle(): TextStyle {
    val base = androidx.compose.material3.MaterialTheme.typography.labelSmall
    return base.copy(letterSpacing = 1.5f.sp)
}
