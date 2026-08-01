package com.jarvis.os.app.home

import com.jarvis.os.app.data.settings.DashboardCardId
import com.jarvis.os.app.data.settings.DashboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance Scenario 5: "Owner rearranges dashboard widgets. Order
 * persists after restart." Persistence itself is DataStore (exercised
 * only by SettingsRepository, which needs Android instrumentation to
 * test directly) — what's tested here, as a plain JVM test, is the pure
 * logic a restart-persisted layout depends on: reorder, hide/show, and
 * round-tripping through the exact string format DataStore will store.
 */
class DashboardLayoutTest {

    @Test
    fun `default layout contains every card exactly once, all visible`() {
        val layout = DashboardLayout.default()
        assertEquals(DashboardCardId.values().size, layout.cards.size)
        assertEquals(DashboardCardId.values().toSet(), layout.cards.map { it.id }.toSet())
        assertTrue(layout.cards.all { it.visible })
    }

    @Test
    fun `moved reorders without losing or duplicating cards`() {
        val layout = DashboardLayout.default()
        val moved = layout.moved(fromIndex = 0, toIndex = 3)

        assertEquals(layout.cards.size, moved.cards.size)
        assertEquals(layout.cards.map { it.id }.toSet(), moved.cards.map { it.id }.toSet())
        assertEquals(layout.cards[0].id, moved.cards[3].id) // the moved card landed exactly where requested
    }

    @Test
    fun `moved with out-of-range index is a no-op`() {
        val layout = DashboardLayout.default()
        assertEquals(layout, layout.moved(fromIndex = -1, toIndex = 2))
        assertEquals(layout, layout.moved(fromIndex = 0, toIndex = 999))
    }

    @Test
    fun `withVisibility hides only the targeted card`() {
        val layout = DashboardLayout.default()
        val hidden = layout.withVisibility(DashboardCardId.WEATHER, visible = false)

        assertFalse(hidden.cards.first { it.id == DashboardCardId.WEATHER }.visible)
        assertTrue(hidden.cards.filter { it.id != DashboardCardId.WEATHER }.all { it.visible })
        assertFalse(DashboardCardId.WEATHER in hidden.visibleCards())
    }

    @Test
    fun `serialize then deserialize round-trips an arbitrary layout exactly`() {
        val layout = DashboardLayout.default()
            .moved(fromIndex = 0, toIndex = 5)
            .withVisibility(DashboardCardId.CALENDAR, visible = false)
            .withVisibility(DashboardCardId.WEATHER, visible = false)

        val restored = DashboardLayout.deserialize(layout.serialize())

        assertEquals(layout.cards.map { it.id }, restored.cards.map { it.id })
        assertEquals(layout.cards.map { it.visible }, restored.cards.map { it.visible })
    }

    @Test
    fun `deserialize of blank string falls back to default`() {
        assertEquals(DashboardLayout.default(), DashboardLayout.deserialize(""))
    }

    @Test
    fun `deserialize tolerates a corrupted entry without losing the rest`() {
        val restored = DashboardLayout.deserialize("TODAYS_BRIEFING:true,GARBAGE,PROJECTS:false")
        assertTrue(restored.cards.any { it.id == DashboardCardId.TODAYS_BRIEFING && it.visible })
        assertTrue(restored.cards.any { it.id == DashboardCardId.PROJECTS && !it.visible })
    }
}
