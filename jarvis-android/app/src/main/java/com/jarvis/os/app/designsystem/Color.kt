package com.jarvis.os.app.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Part 15 (JARVIS Branding): "Modern. Premium. Minimal. Professional.
 * Feels like an AI Operating System. Not a chatbot." — the palette below
 * is deliberately restrained (near-black surfaces, a single accent color
 * doing all the visual work, no gradient soup) rather than the bright,
 * rounded, "friendly assistant" look most chat apps reach for. Every
 * screen in this app should read as instrument-panel, not messaging-app.
 */

// Neutral surfaces — shared structure across all three appearance modes
// (Part 11: Dark Mode / Light Mode / AMOLED Mode). AMOLED reuses Dark's
// tokens except Surface, which drops to true black (see Theme.kt) —
// that's the ONLY difference, deliberately, so AMOLED never drifts into
// being a separate, harder-to-maintain third palette.
object JarvisNeutrals {
    val DarkSurface = Color(0xFF121316)
    val DarkSurfaceVariant = Color(0xFF1C1E22)
    val AmoledSurface = Color(0xFF000000)
    val DarkOnSurface = Color(0xFFE7E8EA)
    val DarkOnSurfaceVariant = Color(0xFFA8ABB3)
    val DarkOutline = Color(0xFF34363B)

    val LightSurface = Color(0xFFFAFAFA)
    val LightSurfaceVariant = Color(0xFFEFEFF1)
    val LightOnSurface = Color(0xFF1A1B1E)
    val LightOnSurfaceVariant = Color(0xFF54575F)
    val LightOutline = Color(0xFFD8D9DC)
}

/**
 * Part 11 lets the Owner choose an Accent Color — this is the fixed
 * palette of choices, not an arbitrary color picker, so every accent
 * option is guaranteed to meet contrast requirements against both light
 * and dark surfaces (a genuinely free-form picker would need runtime
 * contrast validation this sprint doesn't build). Extending this list
 * is the one-line change a future sprint would make to add more
 * choices — see SettingsRepository for where the selection persists.
 */
enum class AccentColor(val label: String, val seed: Color) {
    ArcBlue("Arc Blue", Color(0xFF4C8DFF)),
    SignalAmber("Signal Amber", Color(0xFFFFA53C)),
    EmeraldCore("Emerald Core", Color(0xFF35C787)),
    CrimsonAlert("Crimson Alert", Color(0xFFFF5470)),
    VioletMatrix("Violet Matrix", Color(0xFF9B6BFF)),
    SlateMono("Slate Mono", Color(0xFF9AA4B2)),
}

/** Status colors are semantic, never accent-dependent — a connection's HEALTHY dot must stay recognizably green regardless of which AccentColor the Owner picked, since status meaning must never depend on personalization. */
object JarvisStatusColors {
    val Healthy = Color(0xFF35C787)
    val Degraded = Color(0xFFFFA53C)
    val Unhealthy = Color(0xFFFF5470)
    val Unknown = Color(0xFF9AA4B2)
}

/**
 * Sprint 13 "JARVIS Identity": the holographic avatar and its
 * surrounding hero chrome use these FIXED colors, never the Owner's
 * chosen AccentColor -- same reasoning JarvisStatusColors already
 * applies one level up ("status meaning must never depend on
 * personalization"), extended here to "JARVIS's own presence must
 * never depend on personalization." An Owner who picked Crimson Alert
 * as their accent still sees the same recognizably-JARVIS blue/cyan
 * avatar everyone else does -- a brand mark, not themeable chrome.
 * AccentColor continues to govern ordinary UI (buttons, selection
 * states, the rest of MaterialTheme.colorScheme) unchanged.
 *
 * Void is deliberately darker than JarvisNeutrals.DarkSurface/AmoledSurface
 * -- reserved for the specific hero space directly behind the avatar,
 * not swapped in for either of those tokens, so the avatar's own glow
 * has somewhere darker to bloom into without competing with ordinary
 * card surfaces elsewhere on the same screen.
 */
object JarvisBrand {
    val CoreBlue = Color(0xFF2F6BFF)
    val CoreCyan = Color(0xFF5EEAFF)
    val CorePlasma = Color(0xFF8B5CF6)
    val Void = Color(0xFF06070A)
}
