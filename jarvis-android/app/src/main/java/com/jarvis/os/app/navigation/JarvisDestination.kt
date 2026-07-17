package com.jarvis.os.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sprint 13 "Natural Language" (section 9): every label here is what
 * the Owner would call the thing, not the underlying implementation --
 * "Mission Control" replaces "Executive Dashboard"/"Dashboard",
 * "Daily Focus" replaces the old "Home" label (the route/composable is
 * still named Home internally; only the label a person actually reads
 * changed), "Approvals" and "Connected Systems" stay close to their
 * Sprint 9/Part-2 names since those already read naturally. Route
 * strings (used only internally by NavHost, never shown to the Owner)
 * are unchanged from prior sprints on purpose -- renaming them would
 * be a pure-risk change with no Owner-visible benefit.
 */
sealed class JarvisDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : JarvisDestination("home", "Daily Focus", Icons.Filled.Home)
    data object Chat : JarvisDestination("chat", "Talk to JARVIS", Icons.Filled.Chat)
    data object Projects : JarvisDestination("projects", "Projects", Icons.Filled.AccountTree)
    data object Memory : JarvisDestination("memory", "Memory", Icons.Filled.Memory)
    data object Connections : JarvisDestination("connections", "Connected Systems", Icons.Filled.Link)
    data object Approvals : JarvisDestination("approvals", "Approvals", Icons.Filled.CheckCircle)
    data object Notifications : JarvisDestination("notifications", "Notifications", Icons.Filled.Notifications)
    data object Settings : JarvisDestination("settings", "Settings", Icons.Filled.Settings)
    data object HomeAutomation : JarvisDestination("home_automation", "Home Automation", Icons.Filled.Home)
    data object MissionControl : JarvisDestination("dashboard", "Mission Control", Icons.Filled.Dashboard)
    data object WatchTower : JarvisDestination("watch_tower", "Watch Tower", Icons.Filled.Groups)
    // "JARVIS Goes Live": reached only by tapping the AI Provider card
    // inside Settings -- deliberately not added to drawerOnlyItems/all
    // below, since it isn't meant to be a separately-discoverable
    // top-level destination.
    data object AIProvider : JarvisDestination("ai_provider", "AI Provider", Icons.Filled.Settings)

    companion object {
        val bottomBarItems = listOf(Home, Chat, MissionControl, Connections)
        val drawerOnlyItems = listOf(Projects, Memory, Approvals, Notifications, HomeAutomation, Settings, WatchTower)
        val all = bottomBarItems + drawerOnlyItems
    }
}
