package com.jarvis.os.app.data.settings

/**
 * Part 4's dashboard cards. A fixed enum (not a free-form string list)
 * so DashboardLayoutState can never persist a reference to a card that
 * no longer exists in the app — adding a new card is a one-line enum
 * addition plus a default-position entry in `DEFAULT_ORDER` below,
 * mirroring the "extend by addition, not by loosening the type" pattern
 * already used throughout the Python backend (Capability, GoalCategory,
 * SpecialistDomain, DeviceType).
 */
enum class DashboardCardId {
    TODAYS_BRIEFING, TODAYS_TASKS, PROJECTS, NG_SIGNAL_PRO, CALENDAR,
    WEATHER, AI_STATUS, MEMORY_SUMMARY, CONNECTIONS, APPROVALS, QUICK_ACTIONS,
}

data class DashboardCardState(val id: DashboardCardId, val visible: Boolean)

/**
 * The full dashboard layout: an ORDERED list of card states. Order in
 * the list IS the display order — there is no separate `position: Int`
 * field to drift out of sync with actual list order, which is exactly
 * the kind of dual-source-of-truth bug a reorder feature is prone to.
 */
data class DashboardLayout(val cards: List<DashboardCardState>) {

    /** Part 4: "Cards must support Reorder." Pure, index-based move — the same operation Compose's drag gesture handler and a settings screen's "move up/down" button both call, so there is exactly one reorder implementation in this codebase, not two that could drift apart. */
    fun moved(fromIndex: Int, toIndex: Int): DashboardLayout {
        if (fromIndex == toIndex || fromIndex !in cards.indices || toIndex !in cards.indices) return this
        val mutable = cards.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return DashboardLayout(mutable)
    }

    /** Part 4: "Cards must support Hide / Show." */
    fun withVisibility(id: DashboardCardId, visible: Boolean): DashboardLayout =
        DashboardLayout(cards.map { if (it.id == id) it.copy(visible = visible) else it })

    fun visibleCards(): List<DashboardCardId> = cards.filter { it.visible }.map { it.id }

    /** Serialization for DataStore: a compact "ID:visible,ID:visible,..." string preserving order — chosen over a JSON library dependency this sprint doesn't otherwise need, per the same "boring technology" preference the Python backend has followed since Sprint-0. */
    fun serialize(): String = cards.joinToString(",") { "${it.id.name}:${it.visible}" }

    companion object {
        val DEFAULT_ORDER: List<DashboardCardId> = listOf(
            DashboardCardId.TODAYS_BRIEFING,
            DashboardCardId.TODAYS_TASKS,
            DashboardCardId.PROJECTS,
            DashboardCardId.NG_SIGNAL_PRO,
            DashboardCardId.AI_STATUS,
            DashboardCardId.CONNECTIONS,
            DashboardCardId.APPROVALS,
            DashboardCardId.MEMORY_SUMMARY,
            DashboardCardId.CALENDAR,
            DashboardCardId.WEATHER,
            DashboardCardId.QUICK_ACTIONS,
        )

        fun default(): DashboardLayout =
            DashboardLayout(DEFAULT_ORDER.map { DashboardCardState(it, visible = true) })

        fun deserialize(raw: String): DashboardLayout {
            if (raw.isBlank()) return default()
            val parsed = raw.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@mapNotNull null
                val id = runCatching { DashboardCardId.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                DashboardCardState(id, parts[1].toBooleanStrictOrNull() ?: true)
            }
            // Any card added to the enum SINCE this layout was saved is
            // appended, visible by default, rather than silently
            // disappearing from a persisted layout saved by an older
            // app version.
            val missing = DashboardCardId.values().filter { id -> parsed.none { it.id == id } }
                .map { DashboardCardState(it, visible = true) }
            val combined = parsed + missing
            return if (combined.isNotEmpty()) DashboardLayout(combined) else default()
        }
    }
}
