package com.jarvis.os.app.data.settings

import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
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
)
