package com.jarvis.os.app.designsystem.avatar

import com.jarvis.os.app.R
import com.jarvis.os.app.designsystem.components.JarvisAvatarState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "JARVIS Living Avatar" sprint: "make the avatar engine modular so
 * the underlying images or animations can later be replaced with
 * higher-quality Lottie, Rive, or other assets without changing the
 * surrounding code." This interface is that modularity boundary --
 * same interface-plus-swappable-implementation shape this codebase
 * uses for every other pluggable concern (ChatProvider,
 * SpeechSynthesizer, ApiKeyStore). [JarvisAvatarEngine] depends only on
 * this interface, never on where an asset actually comes from or what
 * format it's in.
 */
interface AvatarAssetProvider {
    fun assetFor(state: JarvisAvatarState): JarvisAvatarAsset
}

/**
 * The real, default implementation -- an explicit, compile-time-safe
 * mapping table, not a runtime resource-name lookup
 * (`resources.getIdentifier`, which is reflection-based, slower, and a
 * known Android anti-pattern).
 *
 * "Avatar V1" (this sprint): every state now points at
 * `res/drawable/avatar_v1.jpg` -- the Owner's approved reference image,
 * cropped down to just the holographic face/neck/glow-ring artwork
 * (the original was a full app-mockup screenshot with status bar,
 * side-thumbnail previews, and briefing cards around it; registering
 * that whole screenshot as "the avatar" would have rendered a
 * screenshot-within-a-screenshot inside the real running app, so it
 * was cropped to isolate just the actual avatar artwork before being
 * added as a resource). One static image standing in for all states
 * on purpose, exactly as instructed -- [JarvisAvatarEngine]'s existing
 * breathing/drift/glow/crossfade layer is what makes a single static
 * image still read as "alive" and still visibly different across
 * states (see that file for how).
 *
 * THE ENTIRE POINT OF THIS CLASS remains unchanged: replacing Avatar V1
 * with a real per-state Lottie file later is a one-line change to the
 * matching branch below, e.g.
 * `JarvisAvatarState.Idle -> JarvisAvatarAsset.Lottie(R.raw.avatar_idle)`
 * -- and nothing else in this app needs to change. No other file reads
 * resource IDs directly; everything downstream (JarvisAvatarEngine,
 * HomeScreen, ChatScreen) only ever sees the resulting [JarvisAvatarAsset].
 */
@Singleton
class DrawableAvatarAssetProvider @Inject constructor() : AvatarAssetProvider {

    /** Avatar V1 -- see this class's own docstring for provenance. Referenced once, not repeated at each branch below, so a future re-crop or replacement image is also a one-line change. */
    private val avatarV1 = JarvisAvatarAsset.StaticImage(R.drawable.avatar_v1)

    override fun assetFor(state: JarvisAvatarState): JarvisAvatarAsset = when (state) {
        // TODO(avatar-assets): replace with a dedicated per-state Lottie file, e.g. JarvisAvatarState.Idle -> JarvisAvatarAsset.Lottie(R.raw.avatar_idle)
        JarvisAvatarState.Idle -> avatarV1
        JarvisAvatarState.Greeting -> avatarV1
        JarvisAvatarState.Listening -> avatarV1
        JarvisAvatarState.Thinking -> avatarV1
        JarvisAvatarState.Speaking -> avatarV1
        JarvisAvatarState.Confirming -> avatarV1
        JarvisAvatarState.Understanding -> avatarV1
        // Working/Waiting predate this sprint's 7-state design and have
        // no reference-image counterpart in the Owner's approved
        // reference, but also use Avatar V1 rather than reverting to
        // the procedural orb -- "no regressions," and a JARVIS that
        // suddenly changes identity between states would read as more
        // broken than simply reusing the same portrait everywhere,
        // consistent with how every other state above is handled today.
        JarvisAvatarState.Working -> avatarV1
        JarvisAvatarState.Waiting -> avatarV1
    }
}
