package com.jarvis.os.app.designsystem.avatar

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
 * known Android anti-pattern). Every entry is honestly [JarvisAvatarAsset.None]
 * right now: no real avatar asset exists in this codebase as of this
 * sprint (per the sprint's own instruction -- "do not attempt to
 * recreate the photorealistic face procedurally," and no commissioned
 * asset has been provided yet either). [JarvisAvatarEngine] renders a
 * real, working fallback for [JarvisAvatarAsset.None] -- this is not a
 * broken or incomplete state, it's the honest, correct answer today.
 *
 * THE ENTIRE POINT OF THIS CLASS: once a real asset exists (a Lottie
 * .json dropped into res/raw, or a PNG/WebP into res/drawable), turning
 * it on is a one-line change to the matching branch below -- e.g.
 * `JarvisAvatarState.Idle -> JarvisAvatarAsset.Lottie(R.raw.avatar_idle)`
 * -- and nothing else in this app needs to change. No other file reads
 * resource IDs directly; everything downstream (JarvisAvatarEngine,
 * HomeScreen, ChatScreen) only ever sees the resulting [JarvisAvatarAsset].
 */
@Singleton
class DrawableAvatarAssetProvider @Inject constructor() : AvatarAssetProvider {
    override fun assetFor(state: JarvisAvatarState): JarvisAvatarAsset = when (state) {
        // TODO(avatar-assets): JarvisAvatarState.Idle -> JarvisAvatarAsset.Lottie(R.raw.avatar_idle)
        JarvisAvatarState.Idle -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Greeting -> JarvisAvatarAsset.Lottie(R.raw.avatar_greeting)
        JarvisAvatarState.Greeting -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Listening -> JarvisAvatarAsset.Lottie(R.raw.avatar_listening)
        JarvisAvatarState.Listening -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Thinking -> JarvisAvatarAsset.Lottie(R.raw.avatar_thinking)
        JarvisAvatarState.Thinking -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Speaking -> JarvisAvatarAsset.Lottie(R.raw.avatar_speaking)
        JarvisAvatarState.Speaking -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Confirming -> JarvisAvatarAsset.Lottie(R.raw.avatar_confirming)
        JarvisAvatarState.Confirming -> JarvisAvatarAsset.None
        // TODO(avatar-assets): JarvisAvatarState.Understanding -> JarvisAvatarAsset.Lottie(R.raw.avatar_understanding)
        JarvisAvatarState.Understanding -> JarvisAvatarAsset.None
        // Working/Waiting predate this sprint's 7-state design and have
        // no reference-image counterpart -- honestly None, same as
        // every other not-yet-provided state, not silently mapped to a
        // nearby state's asset (that would show the wrong expression
        // for what JARVIS is actually doing).
        JarvisAvatarState.Working -> JarvisAvatarAsset.None
        JarvisAvatarState.Waiting -> JarvisAvatarAsset.None
    }
}
