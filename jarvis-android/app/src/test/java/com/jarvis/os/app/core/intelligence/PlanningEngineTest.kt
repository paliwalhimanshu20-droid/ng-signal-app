package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanningEngineTest {

    private fun engine() = PlanningEngine(MockToolRepository(setOf(CalculatorTool()), MockApprovalRepository()))

    @Test
    fun `splits a goal on and-or-comma into ordered steps`() {
        val plan = engine().plan("open the approvals screen and review pending items, then notify the owner")
        assertEquals(3, plan.steps.size)
        assertEquals("open the approvals screen", plan.steps[0].description)
    }

    @Test
    fun `a clause mentioning a known tool by name is matched to it`() {
        val plan = engine().plan("use the calculator to total the invoice")
        assertNotNull(plan.steps.first().requiresTool)
        assertEquals("calculator", plan.steps.first().requiresTool)
    }

    @Test
    fun `a clause with no matching tool has requiresTool null`() {
        val plan = engine().plan("summarize the meeting notes")
        assertNull(plan.steps.first().requiresTool)
    }

    @Test
    fun `single-clause goal produces a single step plan`() {
        val plan = engine().plan("draft the weekly report")
        assertEquals(1, plan.steps.size)
    }
}
