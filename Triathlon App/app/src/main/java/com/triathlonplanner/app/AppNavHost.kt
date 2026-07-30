package com.triathlonplanner.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.triathlonplanner.feature.onboarding.OnboardingScreen
import com.triathlonplanner.feature.plan.PlanScreen
import com.triathlonplanner.feature.profile.ProfileScreen
import com.triathlonplanner.feature.progress.ProgressScreen
import com.triathlonplanner.feature.today.TodayScreen
import com.triathlonplanner.feature.today.TodayWorkoutDetailScreen

private data class BottomTab(val route: Route, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Route.Today, "Today", Icons.Filled.Today),
    BottomTab(Route.Plan, "Plan", Icons.Filled.CalendarMonth),
    BottomTab(Route.Progress, "Progress", Icons.Filled.Insights),
    BottomTab(Route.Profile, "Profile", Icons.Filled.Person),
)

@Composable
fun AppNavHost(rootViewModel: AppRootViewModel = hiltViewModel()) {
    val hasActivePlan by rootViewModel.hasActivePlan.collectAsStateWithLifecycle()

    when (hasActivePlan) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> OnboardingScreen(onFinished = {})
        true -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Today.route) {
                TodayScreen(onWorkoutClick = { workoutId -> navController.navigate(Route.TodayWorkoutDetail.buildRoute(workoutId)) })
            }
            composable(Route.Plan.route) { PlanScreen() }
            composable(Route.Progress.route) { ProgressScreen() }
            composable(Route.Profile.route) { ProfileScreen() }
            composable(
                Route.TodayWorkoutDetail.route,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
            ) {
                TodayWorkoutDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Surface-coloured rather than the tinted Material default, so the bar reads as a quiet frame
    // around the content instead of a second coloured band competing with each screen's header.
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation,
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
