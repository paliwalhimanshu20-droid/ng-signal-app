package com.jarvis.os.app.core.chat

/**
 * "AI Provider Stabilization & Truthfulness Audit": "Settings, Talk to
 * JARVIS, and Mission Control must all use one shared connection
 * state. No screen should invent its own interpretation."
 *
 * THE ACTUAL BUG this fixes: it was never that different screens held
 * different *data* -- hasStoredKey and lastSuccessAt both already came
 * from the same singleton KeyStore, so every screen already agreed on
 * the underlying facts. The bug was that each screen applied its own
 * ad-hoc *interpretation* of those facts. AIProviderScreen's
 * `ProviderCard` treated `hasStoredKey` alone as "Connected" -- exactly
 * the mistake this sprint's Requirement 1 names ("Saving an API key is
 * NOT a successful connection"). Mission Control separately checked
 * only whether the active provider's id was one of the three real
 * ones, never whether it had actually succeeded. Two different
 * booleans, two different stories, both wrong in different ways.
 *
 * The fix is this one enum plus one pure function every screen calls
 * with the same inputs, rather than a new repository or new state flow
 * -- no new architecture, just one shared interpretation replacing
 * three inconsistent ones.
 */
enum class ProviderConnectionState {
    /** No API key saved for this provider at all. */
    NOT_CONFIGURED,

    /** A key is saved, but no real successful reply has ever been recorded. Requirement 1's own words: this is NOT "Connected." */
    CONFIGURED,

    /** At least one real successful reply exists on record (lastSuccessAt != null), but nothing has been attempted this session to confirm it still works. */
    CONNECTED,

    /** The most recent attempt in this session (a real message or Test Connection) actually succeeded -- the freshest, highest-confidence state. */
    VERIFIED,

    /** The most recent attempt failed specifically due to rate limiting or quota (HTTP 429 or the provider's own rate-limit error type). */
    RATE_LIMITED,

    /** The most recent attempt failed for any other reason (auth, invalid request, network, unparseable response). */
    ERROR,

    /** Not a real provider -- one of the honest offline Mock fallbacks. */
    OFFLINE,
    ;

    /** The one place display text for this state is decided -- every screen reads this rather than writing its own label. */
    val label: String
        get() = when (this) {
            NOT_CONFIGURED -> "Not Connected"
            CONFIGURED -> "Configured — not yet tested"
            CONNECTED -> "Connected"
            VERIFIED -> "Verified"
            RATE_LIMITED -> "Rate Limited"
            ERROR -> "Error"
            OFFLINE -> "Offline"
        }

    companion object {
        /**
         * [latestOutcome] is derived from the most recent real attempt
         * this session (a chat message or an explicit Test Connection),
         * if any -- null means nothing has been attempted yet this
         * session, which is exactly the distinction between CONNECTED
         * (worked before, unconfirmed now) and VERIFIED (just confirmed).
         */
        fun compute(
            hasStoredKey: Boolean,
            lastSuccessAt: Long?,
            latestOutcome: AttemptOutcome?,
        ): ProviderConnectionState {
            if (!hasStoredKey) return NOT_CONFIGURED
            return when (latestOutcome) {
                AttemptOutcome.RATE_LIMITED -> RATE_LIMITED
                AttemptOutcome.FAILED -> ERROR
                AttemptOutcome.SUCCEEDED -> VERIFIED
                null -> if (lastSuccessAt != null) CONNECTED else CONFIGURED
            }
        }
    }

    enum class AttemptOutcome { SUCCEEDED, RATE_LIMITED, FAILED }
}
