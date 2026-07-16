package com.jarvis.os.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.core.JarvisPresence
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisTheme
import com.jarvis.os.app.designsystem.components.JarvisAvatar
import com.jarvis.os.app.designsystem.components.LivingBackground
import com.jarvis.os.app.feature.notifications.NotificationsViewModel
import com.jarvis.os.app.navigation.JarvisDestination
import com.jarvis.os.app.navigation.JarvisNavHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 8.1: JarvisCore.navigationRequests had no consumer since
 * Sprint-8 -- this is that consumer, kept as a thin ViewModel rather
 * than injecting JarvisCore straight into the JarvisApp composable, per
 * that sprint's "no business logic inside Composables" requirement.
 *
 * "JARVIS Experience Transformation" (Phase 1 + Phase 2): also exposes
 * [appearance] (so the app-root Living Background has the Owner's
 * theme/motion settings without every individual screen needing its
 * own SettingsRepository read) and [presence] (JarvisPresence's shared
 * cross-screen avatar state, see that class's docstring) -- both real
 * data this app already had, just not previously available at the one
 * place ("behind every screen") that needed them.
 */
@HiltViewModel
class JarvisAppViewModel @Inject constructor(
    core: JarvisCore,
    settingsRepository: SettingsRepository,
    presence: JarvisPresence,
) : ViewModel() {
    val navigationRequests: SharedFlow<String> = core.navigationRequests
    val appearance: StateFlow<AppearanceSettings> = settingsRepository.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())
    val presenceState = presence.state
}

/**
 * "JARVIS Experience Transformation": app chrome is no longer a
 * separate white/Material shell that individual screens occasionally
 * paint over with their own dark background -- [LivingBackground] now
 * renders exactly once, here, behind the entire ModalNavigationDrawer
 * and Scaffold tree, so every screen (Mission Control, Watch Tower,
 * Memory, Connections, Daily Focus, Conversation) sits inside the same
 * continuous holographic environment by construction, not by each
 * screen remembering to paint its own copy (the previous approach --
 * see this delivery's own gap analysis for why that produced
 * inconsistent light/dark screens depending on whether a given screen
 * had been given that treatment yet).
 *
 * The TopAppBar carries a small persistent [JarvisAvatar] (Phase 2:
 * "everything should orbit around JARVIS," "the owner's eye should
 * immediately go to JARVIS, not to cards") reflecting
 * [JarvisAppViewModel.presenceState] -- real state (Listening/Thinking/
 * Speaking when Home or Chat are actually doing something, Idle
 * otherwise), not a decorative loop that ignores what's actually
 * happening.
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
        val liveAppearance by appViewModel.appearance.collectAsState()
        val presenceState by appViewModel.presenceState.collectAsState()

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

        Box(modifier = Modifier.fillMaxSize()) {
            LivingBackground(
                accentColor = liveAppearance.accentColor.seed,
                motionIntensity = liveAppearance.motionIntensity,
                modifier = Modifier.fillMaxSize(),
                avatarState = presenceState,
            )

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
                            actions = {
                                // Phase 2: JARVIS's presence follows the Owner
                                // across every screen, not just Home/Chat --
                                // small, quiet, and real (see this file's
                                // docstring) rather than a full-size avatar
                                // competing with each screen's own content.
                                JarvisAvatar(
                                    state = presenceState,
                                    size = 32.dp,
                                    themeColor = liveAppearance.accentColor.seed,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        )
                    },
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            containerColor = JarvisBrand.Void.copy(alpha = 0.92f),
                            tonalElevation = 0.dp,
                        ) {
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
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = liveAppearance.accentColor.seed,
                                        selectedTextColor = liveAppearance.accentColor.seed,
                                        indicatorColor = liveAppearance.accentColor.seed.copy(alpha = 0.18f),
                                    ),
                                )
                            }
                        }
                    },
                ) { contentPadding ->
                    Box(modifier = Modifier.padding(contentPadding)) {
                        JarvisNavHost(navController = navController)
                    }
                }
            }
        }
    }
}
