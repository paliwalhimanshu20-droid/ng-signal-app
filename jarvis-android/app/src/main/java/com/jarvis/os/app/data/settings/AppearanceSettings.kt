package com.jarvis.os.app.data.settings

import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale

/** Part 11's full "Appearance" settings group, minus Wallpaper/Background Image asset storage (a real image picker + file storage is a meaningfully separate concern from the rest of this settings group — see delivery notes for why it's deferred rather than half-built). */
data class AppearanceSettings(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val accentColor: AccentColor = AccentColor.ArcBlue,
    val fontFamily: JarvisFontFamily = JarvisFontFamily.SansDefault,
    val fontScale: JarvisFontScale = JarvisFontScale.Default,
    val backgroundColorHex: String? = null, // null = use theme surface color; Owner-set solid color overrides it
)
