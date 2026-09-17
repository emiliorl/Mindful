package com.mindshield.app.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindshield.app.screens.AppsScreen
import com.mindshield.app.screens.HomeScreen
import com.mindshield.app.screens.IntentPickerScreen
import com.mindshield.app.screens.NotificationsScreen
import com.mindshield.app.screens.RoutinesScreen
import com.mindshield.app.screens.SettingsScreen
import com.mindshield.app.screens.StatsScreen
import com.mindshield.app.service.ZoneManagerService

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────

private object Routes {
    const val INTENT_PICKER = "intent_picker"
    const val HOME          = "home"
    const val APPS          = "apps"
    const val NOTIFICATIONS = "notifications"
    const val STATS         = "stats"
    const val ROUTINES      = "routines"
    const val SETTINGS      = "settings"
}

private sealed class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home          : Tab(Routes.HOME,          "Home",    Icons.Outlined.Home)
    object Apps          : Tab(Routes.APPS,          "Apps",    Icons.Outlined.Apps)
    object Notifications : Tab(Routes.NOTIFICATIONS, "Silence", Icons.Outlined.NotificationsOff)
    object Stats         : Tab(Routes.STATS,         "Stats",   Icons.Outlined.BarChart)
    object Routines      : Tab(Routes.ROUTINES,      "Routines",Icons.Outlined.WbTwilight)
    object Settings      : Tab(Routes.SETTINGS,      "Settings",Icons.Outlined.Settings)

    companion object {
        val all = listOf(Home, Apps, Notifications, Stats, Routines, Settings)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AppShell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Read start destination once at launch — must NOT be reactive.
    // If startDestination changed on every session change, NavHost would recreate
    // its graph and force-navigate to the new start on every session start/stop.
    val startDestination = remember {
        if (ZoneManagerService.sessionState.value == null) Routes.INTENT_PICKER else Routes.HOME
    }

    // Hide the bottom nav on the picker screen
    val showBottomBar = currentRoute != Routes.INTENT_PICKER

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.all.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.INTENT_PICKER) {
                IntentPickerScreen(
                    onSessionStarted = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.INTENT_PICKER) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onChangeIntent = {
                        navController.navigate(Routes.INTENT_PICKER) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.APPS) {
                AppsScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.STATS)         { StatsScreen() }
            composable(Routes.ROUTINES)      { RoutinesScreen() }
            composable(Routes.SETTINGS)      { SettingsScreen() }
        }
    }
}
