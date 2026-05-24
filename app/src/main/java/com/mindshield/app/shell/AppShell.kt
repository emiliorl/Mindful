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
import com.mindshield.app.screens.NotificationsScreen
import com.mindshield.app.screens.RoutinesScreen

// ─────────────────────────────────────────────────────────────────────────────
// Tab definitions
// ─────────────────────────────────────────────────────────────────────────────

private sealed class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home          : Tab("home",          "Home",          Icons.Outlined.Home)
    object Apps          : Tab("apps",          "Apps",          Icons.Outlined.Apps)
    object Notifications : Tab("notifications", "Silence",       Icons.Outlined.NotificationsOff)
    object Routines      : Tab("routines",      "Routines",      Icons.Outlined.WbTwilight)

    companion object {
        val all = listOf(Home, Apps, Notifications, Routines)
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.all.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                // Pop up to the start destination on re-select to avoid
                                // building up a large back stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Tab.Home.route)          { HomeScreen() }
            composable(Tab.Apps.route)          { AppsScreen() }
            composable(Tab.Notifications.route) { NotificationsScreen() }
            composable(Tab.Routines.route)      { RoutinesScreen() }
        }
    }
}
