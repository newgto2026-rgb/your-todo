package com.neo.yourtodo.app

import androidx.annotation.StringRes
import com.neo.yourtodo.R
import com.neo.yourtodo.feature.calendar.api.CalendarRoute
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import com.neo.yourtodo.feature.todo.api.TodoAllRoute
import com.neo.yourtodo.feature.todo.api.TodoCompletedRoute
import com.neo.yourtodo.feature.todo.api.TodoTodayRoute
import androidx.navigation3.runtime.NavKey

enum class AppTabDestination(
    val route: NavKey,
    @StringRes val labelRes: Int
) {
    ALL(
        route = TodoAllRoute,
        labelRes = R.string.tab_all
    ),
    TODAY(
        route = TodoTodayRoute,
        labelRes = R.string.tab_today
    ),
    COMPLETED(
        route = TodoCompletedRoute,
        labelRes = R.string.tab_completed
    ),
    CALENDAR(
        route = CalendarRoute,
        labelRes = R.string.tab_calendar
    ),
    FRIENDS(
        route = FriendsRoute,
        labelRes = R.string.tab_friends
    );

    companion object {
        val tabs: List<AppTabDestination> = entries

        fun fromRoute(route: NavKey?): AppTabDestination? =
            tabs.firstOrNull { tab -> tab.route == route }
    }
}
