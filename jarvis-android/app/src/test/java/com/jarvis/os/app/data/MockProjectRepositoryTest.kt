package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.ProjectStatus
import com.jarvis.os.app.data.repository.MockProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 10 ProjectOS: proves mutation methods actually mutate `projects` and `dashboard` stays in sync -- not just that they compile. */
class MockProjectRepositoryTest {

    @Test
    fun `addTask appends to the right project only`() {
        val repo = MockProjectRepository()
        val task = repo.addTask("jarvis-os", "New task")
        assertNotNull(task)
        val project = repo.projects.value.first { it.projectId == "jarvis-os" }
        assertTrue(project.pendingTasks.any { it.taskId == task!!.taskId })
        val other = repo.projects.value.first { it.projectId == "ng-signal-pro" }
        assertTrue(other.pendingTasks.none { it.taskId == task!!.taskId })
    }

    @Test
    fun `addTask on unknown project returns null and mutates nothing`() {
        val repo = MockProjectRepository()
        val before = repo.projects.value
        val result = repo.addTask("does-not-exist", "x")
        assertEquals(null, result)
        assertEquals(before, repo.projects.value)
    }

    @Test
    fun `completeTask flips done and dashboard openTaskCount decreases`() {
        val repo = MockProjectRepository()
        val before = repo.dashboard.value.openTaskCount
        val taskId = repo.projects.value.first { it.projectId == "jarvis-os" }.pendingTasks.first().taskId
        repo.completeTask("jarvis-os", taskId)
        assertEquals(before - 1, repo.dashboard.value.openTaskCount)
    }

    @Test
    fun `reachMilestone updates dashboard reachedMilestoneCount`() {
        val repo = MockProjectRepository()
        val milestoneId = repo.projects.value.first { it.projectId == "jarvis-os" }.milestones.first().milestoneId
        assertEquals(0, repo.dashboard.value.reachedMilestoneCount)
        repo.reachMilestone("jarvis-os", milestoneId)
        assertEquals(1, repo.dashboard.value.reachedMilestoneCount)
    }

    @Test
    fun `updateStatus changes dashboard blockedProjects`() {
        val repo = MockProjectRepository()
        assertEquals(1, repo.dashboard.value.blockedProjects)
        repo.updateStatus("projectos", ProjectStatus.ACTIVE)
        assertEquals(0, repo.dashboard.value.blockedProjects)
    }

    @Test
    fun `recordEvidence and addSprint attach to the correct project`() {
        val repo = MockProjectRepository()
        val evidence = repo.recordEvidence("jarvis-os", "PR4 tests passing", "AiRouterTest.kt")
        val sprint = repo.addSprint("jarvis-os", "Sprint 12")
        val project = repo.projects.value.first { it.projectId == "jarvis-os" }
        assertTrue(project.evidence.any { it.evidenceId == evidence!!.evidenceId })
        assertTrue(project.sprints.any { it.sprintId == sprint!!.sprintId })
    }
}
