package com.jarvis.os.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.designsystem.JarvisTheme
import com.jarvis.os.app.navigation.JarvisDestination
import com.jarvis.os.app.navigation.JarvisNavHost
import kotlinx.coroutines.launch

/**
 * Part 15 (Branding): app chrome is intentionally quiet — a single
 * TopAppBar with a drawer trigger and the current screen's label, never
 * a decorated hero header. The "feels like an operating system, not a
 * chatbot" brief is mostly a restraint decision, not an added-feature
 * decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp(appearance: AppearanceSettings) {
    JarvisTheme(
        appearanceMode = appearance.mode,
        accentColor = appearance.accentColor,
        fontFamily = appearance.fontFamily,
        fontScale = appearance.fontScale,
    ) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    JarvisDestination.all.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(destination.label) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(JarvisDestination.all.firstOrNull { it.route == currentRoute?.route }?.label ?: "JARVIS")
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open navigation drawer")
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        JarvisDestination.bottomBarItems.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
            ) { contentPadding ->
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(contentPadding)) {
                    JarvisNavHost(navController = navController)
                }
            }
        }
    }
}
