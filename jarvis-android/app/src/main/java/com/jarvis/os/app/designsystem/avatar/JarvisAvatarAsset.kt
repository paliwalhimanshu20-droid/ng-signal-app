package com.jarvis.os.app.designsystem.avatar

/**
 * "JARVIS Living Avatar" sprint: the one type every real visual asset
 * for the avatar gets expressed as, regardless of what format it
 * actually is. This is the actual swap point the sprint asks for --
 * "the underlying images or animations can later be replaced... without
 * changing the surrounding code" is true specifically because
 * [JarvisAvatarEngine] only ever depends on this sealed type, never on
 * a concrete image format directly.
 *
 * As of "Avatar V1," [DrawableAvatarAssetProvider] returns
 * [StaticImage] for every state, not [None] -- see that class's own
 * docstring for the real asset now registered. [None] remains a real,
 * meaningful variant of this type (not dead code) for any future state
 * that doesn't yet have a matching asset.
 *
 * resId/rawResId are plain Int, not @DrawableRes/@RawRes-annotated --
 * those are compile-time lint hints only (no runtime behavior), and
 * this file deliberately avoids depending on androidx.annotation
 * directly rather than assume it's transitively available without a
 * way to verify that here.
 */
sealed interface JarvisAvatarAsset {
    /**
     * The honest fallback when no asset is configured for a given
     * state -- not a placeholder meaning "broken." [JarvisAvatarEngine]
     * renders a real, working procedural avatar (Sprint 13's original
     * holographic orb) when it sees this, never a blank or error state.
     * As of "Avatar V1" this is no longer what any state in
     * [DrawableAvatarAssetProvider] actually returns -- every state has
     * a real image now -- but the type stays available for any future
     * state that doesn't have a matching asset yet.
     */
    data object None : JarvisAvatarAsset

    /** A static image (PNG/WebP/etc.) in res/drawable -- the simplest real asset type, useful even before a full animated file exists for every state. */
    data class StaticImage(val resId: Int) : JarvisAvatarAsset

    /**
     * A Lottie animation (a .json file exported from After Effects via
     * the Bodymovin/Lottie plugin) in res/raw -- the primary format
     * this sprint's Avatar Engine is built to render richly (crossfades,
     * loops, internal keyframed detail like blinking or lip movement
     * living inside the file itself). See AvatarAssetProvider's
     * docstring for why Lottie was chosen over Rive for this sprint.
     */
    data class Lottie(val rawResId: Int) : JarvisAvatarAsset
}
