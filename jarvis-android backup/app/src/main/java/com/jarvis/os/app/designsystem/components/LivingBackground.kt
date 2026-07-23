package com.jarvis.os.app.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisMotionIntensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sprint 14-16 "Living Animated Background": "soft moving particles,
 * ambient holographic glow, smooth motion, nothing static" -- the
 * layer Home sits on top of instead of a flat color. Same
 * Canvas-plus-gradient-brushes approach JarvisAvatar already
 * established (no image assets, no Lottie, nothing this sandbox can't
 * verify compiles), scaled up from one avatar's glow to a whole
 * screen's worth of drifting particles and slow-moving ambient blobs.
 *
 * [accentColor] is the Owner's chosen AccentColor.seed (Theme Engine --
 * every named theme changes what color drifts across the screen).
 * [motionIntensity] controls particle count, glow strength, and
 * animation speed independently of color (see JarvisMotionIntensity's
 * own docstring) -- "architecture must support adding future themes
 * easily" is satisfied by both being simple enums a future theme can
 * extend without touching this file at all.
 *
 * Particle positions are computed from a small, fixed number of shared
 * animated values (drift, pulse) plus a per-particle deterministic seed
 * (index-based Random, not per-particle animateFloat state) -- the same
 * "few animated values, many derived positions" technique
 * JarvisAvatar's Thinking-state particles already use, scaled from 3
 * particles to dozens without a proportional increase in actual
 * animation objects.
 */
@Composable
fun LivingBackground(
    accentColor: Color,
    motionIntensity: JarvisMotionIntensity,
    modifier: Modifier = Modifier,
    avatarState: JarvisAvatarState = JarvisAvatarState.Idle,
) {
    val transition = rememberInfiniteTransition(label = "living-background")

    // Sprint 12 "Background should react to avatar state": a smooth
    // (not infinitely-looping) boost applied on top of motionIntensity's
    // own baseline -- animateFloatAsState settles to a new target
    // whenever avatarState changes, rather than the infiniteRepeatable
    // animations above which loop forever regardless of state. Thinking
    // and Working read as the most "alive" moments (JARVIS visibly
    // working), Listening a smaller lift (attentive, not frantic),
    // Idle/Waiting/Speaking stay at baseline -- Speaking's own energy
    // already comes from the avatar's waveform, doubling it here would
    // compete rather than complement.
    val stateBoost by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when (avatarState) {
            JarvisAvatarState.Thinking -> 1.6f
            JarvisAvatarState.Working -> 1.5f
            JarvisAvatarState.Listening -> 1.2f
            else -> 1f
        },
        label = "background-state-boost",
    )
    val effectiveGlow = motionIntensity.glowIntensity * stateBoost
    val effectiveSpeed = motionIntensity.speedMultiplier * stateBoost

    val driftDurationMs = (22000f / effectiveSpeed).toInt().coerceAtLeast(2000)
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(driftDurationMs, easing = LinearEasing)),
        label = "drift",
    )

    val pulseDurationMs = (7000f / effectiveSpeed).toInt().coerceAtLeast(1000)
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val particles = remember(motionIntensity.particleCount) {
        List(motionIntensity.particleCount) { index -> LivingParticle(index) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = JarvisBrand.Void)

        val driftAngle = Math.toRadians((drift * 360.0))

        val blob1Center = Offset(
            x = size.width * (0.3f + 0.15f * cos(driftAngle).toFloat()),
            y = size.height * (0.28f + 0.1f * sin(driftAngle).toFloat()),
        )
        val blob1Radius = size.minDimension * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.14f * effectiveGlow), Color.Transparent),
                center = blob1Center,
                radius = blob1Radius,
            ),
            radius = blob1Radius,
            center = blob1Center,
        )

        val blob2Center = Offset(
            x = size.width * (0.72f - 0.12f * sin(driftAngle).toFloat()),
            y = size.height * (0.75f + 0.08f * cos(driftAngle).toFloat()),
        )
        val blob2Radius = size.minDimension * 0.5f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JarvisBrand.CorePlasma.copy(alpha = 0.09f * effectiveGlow), Color.Transparent),
                center = blob2Center,
                radius = blob2Radius,
            ),
            radius = blob2Radius,
            center = blob2Center,
        )

        particles.forEach { particle ->
            val angle = Math.toRadians((particle.baseAngleDeg + drift * 360f * particle.speedFactor).toDouble())
            val orbitRadius = size.minDimension * particle.radiusFraction
            val cx = size.width / 2f + (cos(angle) * orbitRadius).toFloat()
            val verticalWobble = particle.verticalDrift * sin(Math.toRadians((drift * 360.0 + particle.baseAngleDeg))).toFloat()
            val cy = size.height / 2f + (sin(angle) * orbitRadius).toFloat() + verticalWobble
            drawCircle(
                color = accentColor.copy(alpha = particle.alpha * effectiveGlow),
                radius = particle.sizePx,
                center = Offset(cx, cy),
            )
        }
    }
}

/** Deterministic per-particle seed (index-based, not random-per-recomposition) so a particle's identity is stable across recompositions of the same [LivingBackground] instance. */
private class LivingParticle(index: Int) {
    private val random = Random(index * 7919 + 13)
    val baseAngleDeg: Float = random.nextFloat() * 360f
    val radiusFraction: Float = 0.12f + random.nextFloat() * 0.75f
    val speedFactor: Float = 0.3f + random.nextFloat() * 1.1f
    val sizePx: Float = 1.5f + random.nextFloat() * 3.5f
    val alpha: Float = 0.12f + random.nextFloat() * 0.3f
    val verticalDrift: Float = 10f + random.nextFloat() * 30f
}
