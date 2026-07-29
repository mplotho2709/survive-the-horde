package com.triathlonplanner.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    BottomTab(Route.Today, "Today", Icons.Filled.Home),
    BottomTab(Route.Plan, "Plan", Icons.Filled.DateRange),
    BottomTab(Route.Progress, "Progress", Icons.Filled.Info),
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
            composable(Route.Plan.route) {
                PlanScreen(onWorkoutClick = { workoutId -> navController.navigate(Route.TodayWorkoutDetail.buildRoute(workoutId)) })
            }
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

    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route.route,
                onClick = {
                    navController.navigate(tab.route.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
