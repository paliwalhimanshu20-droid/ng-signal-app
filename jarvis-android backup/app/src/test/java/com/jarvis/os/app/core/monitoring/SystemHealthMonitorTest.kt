package com.jarvis.os.app.core.monitoring

import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.core.workflow.DefaultWorkflowEngine
import com.jarvis.os.app.data.model.SystemHealthLevel
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemHealthMonitorTest {

    @Test
    fun `a fresh system with no failures reports HEALTHY`() {
        val monitor = SystemHealthMonitor(
            MockConnectionRepository(),
            MockToolRepository(setOf(CalculatorTool()), MockApprovalRepository()),
            DefaultWorkflowEngine(),
        )
        val snapshot = monitor.snapshot()
        assertEquals(SystemHealthLevel.HEALTHY, snapshot.level)
        assertEquals(0.0, snapshot.recentWorkflowFailureRate, 0.0)
    }
}
