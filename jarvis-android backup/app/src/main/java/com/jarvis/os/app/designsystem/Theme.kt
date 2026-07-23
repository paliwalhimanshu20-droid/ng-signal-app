package com.jarvis.os.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Part 11: Dark Mode / Light Mode / AMOLED Mode — three named modes, not a boolean, since AMOLED is not simply "extra dark," it changes exactly one token (Surface) for real power savings on OLED screens. */
enum class AppearanceMode { Light, Dark, Amoled }

private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private fun colorSchemeFor(mode: AppearanceMode, accent: AccentColor) = when (mode) {
    AppearanceMode.Light -> lightColorScheme(
        primary = accent.seed,
        onPrimary = onColorFor(accent.seed),
        secondary = accent.seed.copy(alpha = 0.7f),
        surface = JarvisNeutrals.LightSurface,
        onSurface = JarvisNeutrals.LightOnSurface,
        surfaceVariant = JarvisNeutrals.LightSurfaceVariant,
        onSurfaceVariant = JarvisNeutrals.LightOnSurfaceVariant,
        outline = JarvisNeutrals.LightOutline,
        background = JarvisNeutrals.LightSurface,
        onBackground = JarvisNeutrals.LightOnSurface,
    )

    AppearanceMode.Dark -> darkColorScheme(
        primary = accent.seed,
        onPrimary = onColorFor(accent.seed),
        secondary = accent.seed.copy(alpha = 0.7f),
        surface = JarvisNeutrals.DarkSurface,
        onSurface = JarvisNeutrals.DarkOnSurface,
        surfaceVariant = JarvisNeutrals.DarkSurfaceVariant,
        onSurfaceVariant = JarvisNeutrals.DarkOnSurfaceVariant,
        outline = JarvisNeutrals.DarkOutline,
        background = JarvisNeutrals.DarkSurface,
        onBackground = JarvisNeutrals.DarkOnSurface,
    )

    // AMOLED: identical to Dark except true-black Surface/Background —
    // deliberately not its own palette (see Color.kt's object docstring).
    AppearanceMode.Amoled -> darkColorScheme(
        primary = accent.seed,
        onPrimary = onColorFor(accent.seed),
        secondary = accent.seed.copy(alpha = 0.7f),
        surface = JarvisNeutrals.AmoledSurface,
        onSurface = JarvisNeutrals.DarkOnSurface,
        surfaceVariant = JarvisNeutrals.DarkSurfaceVariant,
        onSurfaceVariant = JarvisNeutrals.DarkOnSurfaceVariant,
        outline = JarvisNeutrals.DarkOutline,
        background = JarvisNeutrals.AmoledSurface,
        onBackground = JarvisNeutrals.DarkOnSurface,
    )
}

@Composable
fun JarvisTheme(
    appearanceMode: AppearanceMode,
    accentColor: AccentColor,
    fontFamily: JarvisFontFamily,
    fontScale: JarvisFontScale,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(appearanceMode, accentColor)
    val typography = jarvisTypography(fontFamily.family, fontScale.multiplier)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = JarvisShapes,
        content = content,
    )
}
