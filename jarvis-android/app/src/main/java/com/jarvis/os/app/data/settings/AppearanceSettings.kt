package com.jarvis.os.app.data.settings

import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.designsystem.JarvisMotionIntensity

/** Part 11's full "Appearance" settings group, minus Wallpaper/Background Image asset storage (a real image picker + file storage is a meaningfully separate concern from the rest of this settings group — see delivery notes for why it's deferred rather than half-built). */
data class AppearanceSettings(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val accentColor: AccentColor = AccentColor.ArcBlue,
    val fontFamily: JarvisFontFamily = JarvisFontFamily.SansDefault,
    val fontScale: JarvisFontScale = JarvisFontScale.Default,
    val backgroundColorHex: String? = null, // null = use theme surface color; Owner-set solid color overrides it
    /** Sprint 14-16 "Theme Engine": particle density / glow intensity / animation speed for the Living Background and avatar -- independent of accentColor, see JarvisMotionIntensity's own docstring for why. */
    val motionIntensity: JarvisMotionIntensity = JarvisMotionIntensity.Standard,
    /** Sprint 14-16 "Live Speech Synthesis": whether JARVIS speaks replies aloud via TextToSpeech. Defaults on -- "voice becomes the primary interaction" is this phase's own framing -- but stays a real, persisted Owner choice, not a forced always-on behavior. */
    val voiceOutputEnabled: Boolean = true,
    /** "JARVIS Personality & Experience Bible": "Base Language: Hinglish (Default)." Drives JarvisPersona's system prompt (real AI provider) and every deterministic template's own phrasing (Executive Briefing, Watch Tower idle lines) -- see JarvisLanguage's own docstring. */
    val language: JarvisLanguage = JarvisLanguage.Hinglish,
    /**
     * JARVIS-002 "NOVA Integration" scope: the ONLY change this milestone makes toward a
     * user-facing conversational name. Null (the default) means "JARVIS" -- the platform,
     * class names, and Constitution are unchanged either way, per that section's explicit "no
     * rename" instruction. When set (e.g. "Nova"), [com.jarvis.os.app.core.chat.JarvisPersona]
     * uses it as the name presented to the Owner in conversation; nothing else in this codebase
     * reads or branches on this value.
     */
    val personaDisplayName: String? = null,
)
