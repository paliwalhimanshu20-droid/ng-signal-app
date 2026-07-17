package com.jarvis.os.app.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisExpression
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sprint 13 "JARVIS Avatar": the signature element this sprint is
 * judged on ("the owner should immediately feel 'this is JARVIS'").
 * Deliberately not an image asset or a Lottie file -- nothing in this
 * codebase's toolchain can fetch either from this sandbox, and more to
 * the point, a hand-drawn energy core that genuinely responds to real
 * app state (see [JarvisAvatarState]) reads as more alive than a
 * looping animation file that plays regardless of what JARVIS is
 * actually doing. Pure Canvas + gradient brushes + Compose's animation
 * APIs, all stable, well-established primitives -- deliberately
 * avoiding anything exotic given this file could not be compiled
 * against a real Android toolchain while it was written (see this
 * sprint's integration report).
 *
 * No real backdrop blur (RenderEffect) is used anywhere in this
 * avatar's glow -- that API needs Android 12 (API 31), and this app's
 * minSdk is 26. The soft-glow look instead comes from layering several
 * concentric circles at decreasing alpha, which renders identically on
 * every supported OS version.
 *
 * Sprint 14-16 "Theme Engine" + "Live Facial Animation" added two
 * parameters without touching the state machine above: [themeColor]
 * (the Owner's chosen AccentColor.seed, replacing the fixed
 * JarvisBrand.CoreBlue this avatar used before -- see JarvisBrand's own
 * docstring for the full reasoning) and [expression] (an emotional
 * overlay -- Neutral/Happy/Warning/Error -- orthogonal to [state]'s
 * motion). CoreCyan (the hot inner core) and the functional state
 * colors (Thinking's plasma, Speaking's cyan waveform) stay fixed
 * regardless of theme -- only the Idle/Listening/Waiting glow (JARVIS's
 * "at rest" presence, not a specific activity) actually uses
 * [themeColor], and [expression]'s tint, when non-null, overrides both.
 */
/**
 * "JARVIS Living Avatar" sprint: three states added -- Greeting,
 * Confirming, Understanding -- additively, not as a replacement.
 * Idle/Listening/Thinking/Speaking/Working/Waiting are unchanged and
 * every existing call site (HomeViewModel, ChatViewModel,
 * JarvisPresence, LivingBackground) keeps working exactly as before;
 * adding enum entries never breaks a caller that reads this value,
 * only `when` blocks that switch on it without an `else` -- see this
 * file's own breatheDuration block below, the one place that needed a
 * real fix rather than just adding a fallback branch.
 */
enum class JarvisAvatarState { Idle, Listening, Thinking, Speaking, Working, Waiting, Greeting, Confirming, Understanding }

@Composable
fun JarvisAvatar(
    state: JarvisAvatarState,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    themeColor: Color = JarvisBrand.CoreBlue,
    expression: JarvisExpression = JarvisExpression.Neutral,
) {
    val transition = rememberInfiniteTransition(label = "jarvis-avatar")

    // Breathing scale -- present in every state at different amplitude/speed,
    // since a JARVIS that never moves at all reads as a static image, not a
    // presence. Waiting is the quietest (barely perceptible); Listening is
    // the tightest and fastest (alert, attentive).
    val breatheDuration = when (state) {
        JarvisAvatarState.Idle -> 3200
        JarvisAvatarState.Listening -> 900
        JarvisAvatarState.Thinking -> 1800
        JarvisAvatarState.Speaking -> 1100
        JarvisAvatarState.Working -> 1600
        JarvisAvatarState.Waiting -> 4600
        JarvisAvatarState.Greeting -> 2000 // warmer, more present than Idle's resting pace, without Listening's urgency
        JarvisAvatarState.Confirming -> 1400 // brief, settled -- matches a nod's natural pace
        JarvisAvatarState.Understanding -> 2200 // calm, attentive -- similar register to Thinking but without its searching quality
    }
    val breatheAmplitude = when (state) {
        JarvisAvatarState.Waiting -> 0.02f
        JarvisAvatarState.Listening -> 0.09f
        else -> 0.05f
    }
    val breathe by transition.animateFloat(
        initialValue = 1f - breatheAmplitude,
        targetValue = 1f + breatheAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(breatheDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Continuous rotation -- drives Thinking's orbiting particles and
    // Working's sweeping arc. Harmless (simply unused) in states that
    // don't reference it.
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == JarvisAvatarState.Working) 2400 else 5200, easing = LinearEasing),
        ),
        label = "rotation",
    )

    // Outward "ping" ring -- Idle and Listening only, visually distinct
    // from the breathing core itself (expands past the core's own
    // radius and fades, rather than pulsing in place).
    val pingProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == JarvisAvatarState.Listening) 1200 else 2800, easing = LinearEasing),
        ),
        label = "ping",
    )

    // Speaking's waveform -- three bars, independently phased via three
    // separate animateFloat calls rather than one shared value, so they
    // don't all peak in lockstep (which would read as a single pulse,
    // not a waveform).
    val wave1 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave1")
    val wave2 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave2")
    val wave3 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave3")

    val coreAlpha = if (state == JarvisAvatarState.Waiting) 0.55f else 1f

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val coreRadius = (this.size.minDimension / 2f) * 0.42f * breathe

        // Outer soft glow -- concentric circles, decreasing alpha with
        // radius, standing in for a real blur (see this file's docstring).
        // themeColor drives the "at rest" glow (Idle/Listening/Working/Waiting);
        // Thinking/Speaking keep their own fixed functional colors regardless
        // of theme (see this file's docstring for why). expression's tint,
        // when set, overrides all of the above -- an emotional read takes
        // priority over both theme and activity.
        val glowColor = expression.tint ?: when (state) {
            JarvisAvatarState.Thinking -> JarvisBrand.CorePlasma
            JarvisAvatarState.Speaking -> JarvisBrand.CoreCyan
            else -> themeColor
        }
        listOf(3.2f to 0.05f, 2.4f to 0.09f, 1.7f to 0.16f).forEach { (radiusMultiplier, alpha) ->
            drawCircle(color = glowColor.copy(alpha = alpha * coreAlpha), radius = coreRadius * radiusMultiplier, center = center)
        }

        // Ping ring -- Idle/Listening only.
        if (state == JarvisAvatarState.Idle || state == JarvisAvatarState.Listening) {
            val ringRadius = coreRadius * (1.1f + pingProgress * 0.9f)
            val ringAlpha = (1f - pingProgress) * 0.35f * coreAlpha
            drawCircle(
                color = JarvisBrand.CoreCyan.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // The core itself -- a radial gradient from a bright cyan-white
        // hot-center out to the state's identity color.
        val coreBrush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.9f * coreAlpha), JarvisBrand.CoreCyan.copy(alpha = coreAlpha), glowColor.copy(alpha = coreAlpha)),
            center = center,
            radius = coreRadius * 1.05f,
        )
        drawCircle(brush = coreBrush, radius = coreRadius, center = center)

        // Thinking -- three particles orbiting the core at different
        // radii and relative speeds (offsetting each by a fixed phase
        // of the shared rotation value, not three separate animations).
        if (state == JarvisAvatarState.Thinking) {
            listOf(0f to 1.55f, 120f to 1.85f, 240f to 1.7f).forEach { (phaseDeg, orbitMultiplier) ->
                val angle = Math.toRadians((rotation * (if (orbitMultiplier > 1.75f) -1.4 else 1.0) + phaseDeg).toDouble())
                val orbitRadius = coreRadius * orbitMultiplier
                val particleCenter = Offset(
                    x = center.x + (cos(angle) * orbitRadius).toFloat(),
                    y = center.y + (sin(angle) * orbitRadius).toFloat(),
                )
                drawCircle(color = JarvisBrand.CorePlasma.copy(alpha = 0.85f), radius = coreRadius * 0.14f, center = particleCenter)
            }
        }

        // Working -- a sweeping partial arc ring around the core,
        // stylized rather than a literal Material spinner.
        if (state == JarvisAvatarState.Working) {
            val arcRadius = coreRadius * 1.65f
            drawArc(
                color = JarvisBrand.CoreCyan.copy(alpha = 0.8f),
                startAngle = rotation,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Speaking -- three waveform bars beneath the core.
        if (state == JarvisAvatarState.Speaking) {
            val barWidth = coreRadius * 0.22f
            val barGap = coreRadius * 0.35f
            val maxBarHeight = coreRadius * 1.1f
            val baseY = center.y + coreRadius * 1.9f
            listOf(-barGap to wave1, 0f to wave2, barGap to wave3).forEach { (xOffset, magnitude) ->
                val barHeight = maxBarHeight * magnitude
                drawLine(
                    color = JarvisBrand.CoreCyan.copy(alpha = 0.85f),
                    start = Offset(center.x + xOffset, baseY - barHeight / 2f),
                    end = Offset(center.x + xOffset, baseY + barHeight / 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
