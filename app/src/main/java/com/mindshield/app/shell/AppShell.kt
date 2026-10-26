package com.mindshield.app.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindshield.app.screens.HomeScreen
import com.mindshield.app.screens.InsightsScreen
import com.mindshield.app.screens.IntentPickerScreen
import com.mindshield.app.screens.RoutinesScreen
import com.mindshield.app.screens.SettingsScreen
import com.mindshield.app.screens.ShieldScreen
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.viewmodel.RoutinesViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────

private object Routes {
    const val INTENT_PICKER = "intent_picker"
    const val FOCUS         = "focus"
    const val SHIELD        = "shield"
    const val INSIGHTS      = "insights"
    const val SETTINGS      = "settings"
    const val ROUTINES      = "routines"
}

private sealed class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Focus    : Tab(Routes.FOCUS,    "Focus",    Icons.Outlined.SelfImprovement)
    object Shield   : Tab(Routes.SHIELD,   "Shield",   Icons.Outlined.Shield)
    object Insights : Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights)

    companion object {
        val all = listOf(Focus, Shield, Insights)
    }
}

/** Title for each non-picker route shown in the top bar. */
private fun titleFor(route: String?): String = when (route) {
    Routes.FOCUS    -> "Focus"
    Routes.SHIELD   -> "Shield"
    Routes.INSIGHTS -> "Insights"
    Routes.SETTINGS -> "Settings"
    Routes.ROUTINES -> "Daily routines"
    else            -> "MindShield"
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AppShell
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Read start destination once at launch — must NOT be reactive.
    // If startDestination changed on every session change, NavHost would recreate
    // its graph and force-navigate to the new start on every session start/stop.
    val startDestination = remember {
        if (ZoneManagerService.sessionState.value == null) Routes.INTENT_PICKER else Routes.FOCUS
    }

    val isTabRoute = Tab.all.any { it.route == currentRoute }
    val isPushedRoute = currentRoute == Routes.SETTINGS || currentRoute == Routes.ROUTINES
    val showTopBar = isTabRoute || isPushedRoute
    val showBottomBar = isTabRoute

    val routinesVm: RoutinesViewModel = viewModel()
    val morningStreak by routinesVm.morningStreak.collectAsStateWithLifecycle()
    val windDownStreak by routinesVm.windDownStreak.collectAsStateWithLifecycle()
    val streak = maxOf(morningStreak, windDownStreak)

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = titleFor(currentRoute),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (isPushedRoute) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (isTabRoute) {
                            if (streak > 0) StreakPill(streak)
                            IconButton(onClick = {
                                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.all.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(imageVector = tab.icon, contentDescription = null) },
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
                        navController.navigate(Routes.FOCUS) {
                            popUpTo(Routes.INTENT_PICKER) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.FOCUS) {
                HomeScreen(
                    onChangeIntent = {
                        navController.navigate(Routes.INTENT_PICKER) { launchSingleTop = true }
                    },
                    onOpenInsights = { navController.navigateToTab(Routes.INSIGHTS) }
                )
            }
            composable(Routes.SHIELD) {
                ShieldScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenRoutines = {
                        navController.navigate(Routes.ROUTINES) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.ROUTINES) { RoutinesScreen() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-bar streak pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreakPill(days: Int) {
    val label = if (days == 1) "1 day" else "$days days"
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.semantics { contentDescription = "Routine streak: $label" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$days",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
