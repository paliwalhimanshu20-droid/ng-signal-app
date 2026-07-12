package com.jarvis.os.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Part 3's full navigation set. `bottomBarItems` is the subset dense
 * enough for a bottom bar (Material 3 guidance: 3-5 items before it
 * gets cramped); everything else lives in the navigation drawer (Part 2:
 * both Bottom Navigation AND Navigation Drawer are required, not an
 * either/or) — this split, not the existence of two nav patterns, is
 * this sprint's actual UI design decision.
 */
sealed class JarvisDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : JarvisDestination("home", "Home", Icons.Filled.Home)
    data object Chat : JarvisDestination("chat", "Chat", Icons.Filled.Chat)
    data object Projects : JarvisDestination("projects", "Projects", Icons.Filled.AccountTree)
    data object Memory : JarvisDestination("memory", "Memory", Icons.Filled.Memory)
    data object Connections : JarvisDestination("connections", "Connections", Icons.Filled.Link)
    data object Approvals : JarvisDestination("approvals", "Approval Center", Icons.Filled.CheckCircle)
    data object Notifications : JarvisDestination("notifications", "Notifications", Icons.Filled.Notifications)
    data object Settings : JarvisDestination("settings", "Settings", Icons.Filled.Settings)
    data object HomeAutomation : JarvisDestination("home_automation", "Home Automation", Icons.Filled.Home)

    companion object {
        val bottomBarItems = listOf(Home, Chat, Projects, Connections)
        val drawerOnlyItems = listOf(Memory, Approvals, Notifications, HomeAutomation, Settings)
        val all = bottomBarItems + drawerOnlyItems
    }
}
