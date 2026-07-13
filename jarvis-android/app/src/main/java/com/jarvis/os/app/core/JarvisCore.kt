package com.jarvis.os.app.core

import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8: the single coordinating entry point above the repository
 * layer, requested as the "JARVIS Core" that should own task, memory,
 * connection, approval, event, and navigation coordination. Read this
 * docstring before extending this class — what it deliberately does
 * NOT do is as important as what it does:
 *
 * - It does NOT duplicate or replace ConnectionRepository, ApprovalRepository,
 *   MemoryRepository, or ProjectRepository. Each already owns its domain
 *   correctly (verified: Connections' state machine matches the Python
 *   ConnectionManager 1:1, Home Automation's safety allowlist is
 *   enforced twice, DataStore persistence round-trips correctly). Core
 *   exposes them as read-only properties so a caller who wants "the
 *   app's central coordinator" doesn't have to inject five separate
 *   repositories — but the repositories remain the actual owners.
 *   Re-implementing their logic here would be exactly the "duplicate
 *   logic" this sprint's requirements explicitly forbid.
 *
 * - "Task management" is ProjectTask, which already existed inside
 *   ProjectRepository before this sprint. There is no dedicated Task
 *   domain/repository, because none of this app's real requirements
 *   have asked for tasks independent of a project — inventing that
 *   abstraction with nothing to justify it would be premature
 *   abstraction, which this codebase's "boring technology" principle
 *   (stated since Sprint-0 of the Python backend) argues against. If a
 *   real need for project-independent tasks shows up, that is a
 *   scoped, justified addition for a later sprint.
 *
 * - "Navigation coordination" is NOT "Core owns the NavHost." Moving
 *   navigation ownership into Core would mean rewriting JarvisNavHost,
 *   JarvisApp, and every screen's navigation call sites in the same
 *   sprint as several other new subsystems — exactly the kind of
 *   high-risk, everything-at-once change "production quality, no
 *   shortcuts" argues against, and it would risk the working nav shell
 *   this app already has (9 destinations, verified reachable, no dead
 *   ends). Instead, Core exposes a navigation *intent* channel:
 *   [navigationRequests] emits route strings; a collector (added in a
 *   later sprint, once there's a real product decision about which
 *   events should trigger navigation) calls navController.navigate()
 *   in response. The NavHostController itself never touches Core.
 *
 * - Event dispatching ([events]) is the one genuinely new runtime
 *   capability this sprint adds: a shared bus for [CoreEvent]s so
 *   features can react to each other without depending on each other's
 *   ViewModels or Repositories. Nothing currently publishes to it —
 *   wiring real publishers (e.g. ApprovalRepository publishing
 *   ApprovalRequested when a new item arrives) is deliberately left for
 *   the sprint that has a concrete consumer for each event, rather than
 *   wiring speculative producers with no consumer to verify against.
 */
@Singleton
class JarvisCore @Inject constructor(
    val connections: ConnectionRepository,
    val approvals: ApprovalRepository,
    val memory: MemoryRepository,
    val projects: ProjectRepository,
) {
    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CoreEvent> = _events

    private val _navigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Route strings matching JarvisDestination.route values. Core
     * never holds a NavHostController reference — see class docstring.
     */
    val navigationRequests: SharedFlow<String> = _navigationRequests

    suspend fun publish(event: CoreEvent) {
        _events.emit(event)
    }

    suspend fun requestNavigation(route: String) {
        _navigationRequests.emit(route)
    }
}
