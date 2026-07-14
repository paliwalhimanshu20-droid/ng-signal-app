package com.jarvis.os.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jarvis.os.app.feature.approvals.ApprovalCenterScreen
import com.jarvis.os.app.feature.chat.ChatScreen
import com.jarvis.os.app.feature.connections.ConnectionsScreen
import com.jarvis.os.app.feature.dashboard.ExecutiveDashboardScreen
import com.jarvis.os.app.feature.home.HomeScreen
import com.jarvis.os.app.feature.homeautomation.HomeAutomationScreen
import com.jarvis.os.app.feature.memory.MemoryScreen
import com.jarvis.os.app.feature.notifications.NotificationsScreen
import com.jarvis.os.app.feature.projects.ProjectsScreen
import com.jarvis.os.app.feature.settings.SettingsScreen

@Composable
fun JarvisNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = JarvisDestination.Home.route) {
        composable(JarvisDestination.Home.route) { HomeScreen(navController) }
        composable(JarvisDestination.Chat.route) { ChatScreen() }
        composable(JarvisDestination.Projects.route) { ProjectsScreen() }
        composable(JarvisDestination.Memory.route) { MemoryScreen() }
        composable(JarvisDestination.Connections.route) { ConnectionsScreen() }
        composable(JarvisDestination.Approvals.route) { ApprovalCenterScreen() }
        composable(JarvisDestination.Notifications.route) { NotificationsScreen() }
        composable(JarvisDestination.HomeAutomation.route) { HomeAutomationScreen() }
        composable(JarvisDestination.Settings.route) { SettingsScreen() }
        composable(JarvisDestination.Dashboard.route) { ExecutiveDashboardScreen() }
    }
}
