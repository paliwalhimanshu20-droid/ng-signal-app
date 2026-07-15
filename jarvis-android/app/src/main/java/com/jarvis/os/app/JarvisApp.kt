package com.jarvis.os.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.designsystem.JarvisTheme
import com.jarvis.os.app.feature.notifications.NotificationsViewModel
import com.jarvis.os.app.navigation.JarvisDestination
import com.jarvis.os.app.navigation.JarvisNavHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 8.1: JarvisCore.navigationRequests had no consumer since
 * Sprint-8 -- this is that consumer, kept as a thin ViewModel rather
 * than injecting JarvisCore straight into the JarvisApp composable, per
 * that sprint's "no business logic inside Composables" requirement.
 *
 * Sprint 13 note: this class existed correctly since Sprint 8.1, but a
 * copy of JarvisApp.kt without it was live at this file's actual
 * package path, while a copy WITH it sat as dead code outside any
 * Gradle source set (jarvis-android/app/src/main/JarvisApp.kt, missing
 * the java/com/jarvis/os/app folder structure entirely -- silently
 * ignored by the build, so this compiled fine while quietly not doing
 * anything). This sprint merges the two back into one correct file at
 * the correct path and deletes the stray one -- restoring
 * "open approvals"-style chat navigation commands, which were
 * consequently dead in the actual running app until now.
 */
@HiltViewModel
class JarvisAppViewModel @Inject constructor(core: JarvisCore) : ViewModel() {
    val navigationRequests: SharedFlow<String> = core.navigationRequests
}

/**
 * Sprint 13 "JARVIS Identity": app chrome stays quiet and out of the
 * way -- the emotional signature this sprint asks for lives on Home
 * (the avatar, the greeting, the briefing), not in a redesigned
 * navigation shell. The TopAppBar/NavigationBar/drawer below are the
 * same well-tested Material3 scaffold every prior sprint has used,
 * given a transparent top bar (so Home's dark background reads as one
 * continuous surface behind it, not a separate bar sitting on top) and
 * the natural-language labels from JarvisDestination -- deliberately
 * not rebuilt as bespoke chrome, since Sprint 13 is explicit that
 * "the goal is not to add functionality," and hand-rolling a full
 * custom nav shell without any way to compile-check it against this
 * sprint's real Android toolchain is exactly the kind of risk not
 * worth taking for a part of the screen this sprint's own success
 * criteria don't hinge on (see this sprint's integration report).
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
        val appViewModel: JarvisAppViewModel = hiltViewModel()

        LaunchedEffect(Unit) {
            appViewModel.navigationRequests.collect { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        // Sprint 9 (PR2): read directly from NotificationsViewModel's
        // unreadCount rather than duplicating a filter over the
        // notification list here -- see NotificationRepository's
        // docstring for why unreadCount is the one source of truth a
        // badge anywhere in the UI should read.
        val notificationsViewModel: NotificationsViewModel = hiltViewModel()
        val unreadCount by notificationsViewModel.unreadCount.collectAsState()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    JarvisDestination.all.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(destination.label) },
                            icon = {
                                if (destination == JarvisDestination.Notifications && unreadCount > 0) {
                                    BadgedBox(badge = { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) {
                                        Icon(destination.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            },
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
                containerColor = Color.Transparent,
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
