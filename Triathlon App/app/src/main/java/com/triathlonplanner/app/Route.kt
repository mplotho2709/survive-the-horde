package com.triathlonplanner.app

sealed class Route(val route: String) {
    data object Onboarding : Route("onboarding")
    data object Today : Route("today")
    data object Plan : Route("plan")
    data object Progress : Route("progress")
    data object Profile : Route("profile")
    data object PlanWeekDetail : Route("plan/week/{weekIndex}") {
        fun buildRoute(weekIndex: Int) = "plan/week/$weekIndex"
    }
    data object TodayWorkoutDetail : Route("today/workout/{workoutId}") {
        fun buildRoute(workoutId: Long) = "today/workout/$workoutId"
    }
}
