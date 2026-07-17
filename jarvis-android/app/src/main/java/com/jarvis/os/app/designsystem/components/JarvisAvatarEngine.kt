package com.jarvis.os.app.designsystem.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisExpression
import com.jarvis.os.app.designsystem.avatar.AvatarAssetProvider
import com.jarvis.os.app.designsystem.avatar.DrawableAvatarAssetProvider
import com.jarvis.os.app.designsystem.avatar.JarvisAvatarAsset
import kotlin.math.cos
import kotlin.math.sin

/**
 * "JARVIS Living Avatar" sprint: the permanent, image-based Avatar
 * Engine -- built around real visual assets (see JarvisAvatarAsset),
 * not a procedural recreation of the reference photo (explicitly
 * ruled out for this sprint). Renders whichever [JarvisAvatarAsset]
 * [AvatarAssetProvider] returns for the current [state], crossfading
 * between assets on state change, with a real breathing/drift overlay
 * and a glow backdrop applied on top of whatever asset renders
 * beneath -- so a future Lottie file gets extra "alive" motion layered
 * on top of whatever animation already lives inside the file itself,
 * the same way a real actor's subtle sway reads as more alive than a
 * perfectly still shot even when their face is doing all the acting.
 *
 * [JarvisAvatarAsset.None] (the honest answer for every state today --
 * no asset has been commissioned yet) falls back to [JarvisAvatar], the
 * existing procedural Canvas orb from Sprint 13 -- a real, working,
 * already-shipped presence, not an error state or a blank box. This is
 * the one place old and new avatar code meet: [JarvisAvatarEngine] is
 * the new permanent surface every screen should call going forward;
 * [JarvisAvatar] becomes its fallback renderer rather than being
 * deleted, since deleting still-useful working code the moment a real
 * asset doesn't exist yet would leave the app with nothing to show.
 *
 * IMPLEMENTATION CHOICE -- Lottie over Rive: both are real, viable
 * Android animation formats. Lottie was chosen for this sprint because
 * (1) its Compose integration (`rememberLottieComposition` +
 * `LottieAnimation`) is smaller, more stable API surface with years of
 * production use across the Android ecosystem, lower integration risk
 * for a file that could not be compiled against a real toolchain while
 * being written; (2) Lottie is the more common deliverable format from
 * motion designers and AI-assisted animation tools, likely closer to
 * what a commissioned asset would actually arrive as; (3) the
 * [JarvisAvatarAsset] sealed interface can grow a `Rive` variant later
 * without touching this file's structure if a specific asset ever
 * needs it -- this is a default, not a lock-in.
 *
 * HONESTY NOTE ON WHAT COULD BE VERIFIED: the Lottie rendering branch
 * below is real, complete code written against the documented
 * `lottie-compose` API, but since [DrawableAvatarAssetProvider] never
 * actually returns a `Lottie` asset yet (no real .json file exists in
 * this codebase), that code path has never actually executed. The
 * fallback path ([JarvisAvatarAsset.None] -> [JarvisAvatar]) is the
 * only one currently exercised by a running app.
 */
@Composable
fun JarvisAvatarEngine(
    state: JarvisAvatarState,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    themeColor: Color = JarvisBrand.CoreBlue,
    expression: JarvisExpression = JarvisExpression.Neutral,
    assetProvider: AvatarAssetProvider = DrawableAvatarAssetProvider(),
) {
    val asset = assetProvider.assetFor(state)

    val transition = rememberInfiniteTransition(label = "avatar-engine")

    // Same breathing rhythm table as JarvisAvatar's own -- a real asset
    // rendered through this engine breathes at the same pace the
    // procedural fallback already does, so switching from None to a
    // real asset later doesn't change the avatar's basic sense of pace.
    val breatheDurationMs = when (state) {
        JarvisAvatarState.Idle -> 3200
        JarvisAvatarState.Listening -> 900
        JarvisAvatarState.Thinking -> 1800
        JarvisAvatarState.Speaking -> 1100
        JarvisAvatarState.Working -> 1600
        JarvisAvatarState.Waiting -> 4600
        JarvisAvatarState.Greeting -> 2000
        JarvisAvatarState.Confirming -> 1400
        JarvisAvatarState.Understanding -> 2200
    }

    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(breatheDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val driftY by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween((breatheDurationMs * 1.4f).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        AvatarGlowBackdrop(themeColor = themeColor, size = size, state = state)

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                    translationY = driftY
                },
        ) {
            Crossfade(targetState = asset, animationSpec = tween(420), label = "avatar-asset-crossfade") { currentAsset ->
                when (currentAsset) {
                    is JarvisAvatarAsset.None -> JarvisAvatar(
                        state = state,
                        size = size,
                        themeColor = themeColor,
                        expression = expression,
                    )
                    is JarvisAvatarAsset.StaticImage -> Image(
                        painter = painterResource(id = currentAsset.resId),
                        contentDescription = "JARVIS avatar",
                        modifier = Modifier.fillMaxSize(),
                    )
                    is JarvisAvatarAsset.Lottie -> {
                        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(currentAsset.rawResId))
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Glow/particle backdrop scoped to the avatar's own bounds -- same
 * concentric-alpha-circle and orbiting-particle technique
 * LivingBackground already uses for the whole screen, sized down to
 * sit directly behind whatever asset [JarvisAvatarEngine] renders,
 * rather than duplicating LivingBackground's full-screen version.
 */
@Composable
private fun AvatarGlowBackdrop(themeColor: Color, size: Dp, state: JarvisAvatarState) {
    val transition = rememberInfiniteTransition(label = "avatar-glow")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-pulse",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(9000)),
        label = "glow-orbit",
    )

    val intensity = when (state) {
        JarvisAvatarState.Thinking, JarvisAvatarState.Working -> 1.4f
        JarvisAvatarState.Listening -> 1.2f
        else -> 1f
    }

    Canvas(modifier = Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension / 2f) * 0.85f * pulse

        listOf(1.0f to 0.06f, 0.75f to 0.10f, 0.5f to 0.16f).forEach { (radiusMultiplier, alpha) ->
            drawCircle(
                color = themeColor.copy(alpha = alpha * intensity),
                radius = baseRadius * radiusMultiplier,
                center = center,
            )
        }

        val particleCount = 6
        repeat(particleCount) { index ->
            val angleDeg = orbit + (360f / particleCount) * index
            val angle = Math.toRadians(angleDeg.toDouble())
            val orbitRadius = baseRadius * 1.15f
            val px = center.x + (cos(angle) * orbitRadius).toFloat()
            val py = center.y + (sin(angle) * orbitRadius).toFloat()
            drawCircle(
                color = JarvisBrand.CoreCyan.copy(alpha = 0.4f * intensity),
                radius = 2.5.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}
