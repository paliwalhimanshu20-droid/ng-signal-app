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
 *
 * Sprint 14-16 "Theme Engine": labels renamed to match that sprint's
 * five named themes (JARVIS Blue / Amber / Emerald / Crimson / Purple)
 * -- the underlying enum, persistence key (`AccentColor.name`, unchanged:
 * ArcBlue/SignalAmber/EmeraldCore/CrimsonAlert/VioletMatrix/SlateMono),
 * and hex values are untouched, only the user-facing `label` strings
 * changed, so no migration is needed for anyone who already has a
 * choice persisted. SlateMono is kept as a sixth option beyond the five
 * named ones, deliberately, as a live example that "add a theme" is a
 * one-enum-entry change, not a redesign -- exactly what this sprint's
 * "architecture must support adding future themes easily" asks for.
 */
enum class AccentColor(val label: String, val seed: Color) {
    ArcBlue("JARVIS Blue", Color(0xFF4C8DFF)),
    SignalAmber("Amber", Color(0xFFFFA53C)),
    EmeraldCore("Emerald", Color(0xFF35C787)),
    CrimsonAlert("Crimson", Color(0xFFFF5470)),
    VioletMatrix("Purple", Color(0xFF9B6BFF)),
    SlateMono("Slate Mono", Color(0xFF9AA4B2)),
}

/**
 * Sprint 14-16 "Theme Engine": the second, independent axis the brief
 * asks for -- "glow intensity, particle density, animation intensity"
 * are NOT tied to which color the Owner picked (a calm Owner and an
 * energetic Owner might both want JARVIS Blue, at different intensity),
 * so this is its own curated enum, same "3-5 named choices, not an
 * open slider" pattern as AccentColor/JarvisFontScale before it. Named
 * for what the Owner controls (how alive JARVIS's background feels),
 * not for the numbers underneath.
 */
enum class JarvisMotionIntensity(
    val label: String,
    val particleCount: Int,
    val glowIntensity: Float,
    val speedMultiplier: Float,
) {
    Calm("Calm", particleCount = 10, glowIntensity = 0.7f, speedMultiplier = 0.6f),
    Standard("Standard", particleCount = 18, glowIntensity = 1.0f, speedMultiplier = 1.0f),
    Vivid("Vivid", particleCount = 30, glowIntensity = 1.35f, speedMultiplier = 1.5f),
}

/**
 * "JARVIS Personality & Experience Bible": "Base Language... Default:
 * Hinglish... Changing language NEVER changes personality." A curated
 * enum, same pattern as every other Owner-facing choice in this file --
 * the Bible's own three named options (Hinglish, English, Hindi) plus
 * room for more later without touching anything that reads this value
 * (JarvisPersona's system prompt and every deterministic template in
 * this app switch on this same enum).
 */
enum class JarvisLanguage(val label: String) {
    Hinglish("Hinglish"),
    English("English"),
    Hindi("Hindi"),
}

/**
 * Sprint 14-16 "Live Facial Animation": an emotional overlay,
 * orthogonal to [com.jarvis.os.app.designsystem.components.JarvisAvatarState]
 * (which governs MOTION -- is JARVIS idle, thinking, speaking) rather
 * than replacing it. Neutral/Happy/Warning/Error tint the avatar's
 * glow using the SAME semantic colors [JarvisStatusColors] already
 * uses everywhere else in this app (a connection's HEALTHY dot and a
 * Happy JARVIS are the same green, deliberately, for one consistent
 * visual vocabulary) -- not a fourth color system invented just for
 * the avatar.
 */
enum class JarvisExpression {
    Neutral, Happy, Warning, Error;

    val tint: Color?
        get() = when (this) {
            Neutral -> null // no override -- avatar uses its normal theme/state color
            Happy -> JarvisStatusColors.Healthy
            Warning -> JarvisStatusColors.Degraded
            Error -> JarvisStatusColors.Unhealthy
        }
}

/** Status colors are semantic, never accent-dependent — a connection's HEALTHY dot must stay recognizably green regardless of which AccentColor the Owner picked, since status meaning must never depend on personalization. */
object JarvisStatusColors {
    val Healthy = Color(0xFF35C787)
    val Degraded = Color(0xFFFFA53C)
    val Unhealthy = Color(0xFFFF5470)
    val Unknown = Color(0xFF9AA4B2)
}

/**
 * Sprint 13 "JARVIS Identity" introduced these as FIXED colors,
 * independent of the Owner's chosen AccentColor. Sprint 14-16's Theme
 * Engine deliberately supersedes that for one specific piece: the
 * avatar's outer glow/aura and the Living Background now use the
 * Owner's chosen AccentColor.seed (passed in as a parameter -- see
 * JarvisAvatar's `themeColor` and LivingBackground's `accentColor`),
 * since a theme engine whose named themes are literally "JARVIS Blue,
 * Emerald, Crimson, Purple, Amber" is explicitly asking the avatar
 * itself to be themeable, not just app chrome around it.
 *
 * What stays fixed, deliberately: CoreCyan remains the hot-white/cyan
 * INNER core highlight in every theme (every theme's avatar still has
 * the same recognizable bright center -- "JARVIS is always JARVIS,"
 * just wearing a different aura), and the functional state colors
 * (Thinking's plasma particles, Speaking's waveform) stay their own
 * fixed colors regardless of theme, so what JARVIS is DOING stays
 * legible independent of which theme is active -- see JarvisAvatar's
 * own docstring for the full reasoning. Void (the hero background
 * behind the avatar) also stays fixed -- a neutral canvas, not
 * identity.
 */
object JarvisBrand {
    val CoreBlue = Color(0xFF2F6BFF)
    val CoreCyan = Color(0xFF5EEAFF)
    val CorePlasma = Color(0xFF8B5CF6)
    val Void = Color(0xFF06070A)
}
