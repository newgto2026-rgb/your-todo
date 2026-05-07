package com.example.myfirstapp.app

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import com.example.myfirstapp.app.navigation.ImmediateNavDisplay
import com.example.myfirstapp.core.ui.navigation.AppFeatureEntry
import com.example.myfirstapp.core.ui.navigation.AppRouteActions
import com.example.myfirstapp.feature.todo.api.TodoEditorRoute

@Composable
fun AppNavHost(entries: Set<@JvmSuppressWildcards AppFeatureEntry>) {
    val context = LocalContext.current
    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val navigationGraph = remember(entries) { buildAppNavigationGraph(entries) }
    val orderedTopLevelRoutes = navigationGraph.topLevelRoutes
    val navigationState = rememberAppNavigationState(
        startRoute = navigationGraph.startRoute,
        topLevelRoutes = orderedTopLevelRoutes
    )
    val navigator = remember(navigationState, navigationGraph.transientRouteTypes) {
        AppNavigator(
            state = navigationState,
            transientRouteTypes = navigationGraph.transientRouteTypes
        )
    }
    val routeActions = remember(navigator) {
        object : AppRouteActions {
            override fun openTodoEdit(todoId: Long) {
                navigator.navigate(TodoEditorRoute(todoId = todoId, editOnly = true))
            }

            override fun openTodoAdd(dueDate: String) {
                navigator.navigate(TodoEditorRoute(dueDate = dueDate, editOnly = true))
            }

            override fun closeCurrentEntry() {
                navigator.closeCurrentEntry()
            }

            override fun setBackBlocked(blocked: Boolean) {
                navigator.setBackBlocked(blocked)
            }
        }
    }
    val currentTopLevelRoute = navigationState.topLevelRoute
    val currentTab = AppTabDestination.fromRoute(currentTopLevelRoute)
    val appEntryProvider = remember(navigationGraph.featureEntries, routeActions) {
        entryProvider {
            navigationGraph.featureEntries.forEach { entry ->
                entry.register(this, routeActions)
            }
        }
    }
    val navEntries = navigationState.toEntries(
        entryProvider = appEntryProvider
    )
    Scaffold(
        bottomBar = {
            AppBottomBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    navigator.navigate(tab.route)
                }
            )
        }
    ) { innerPadding ->
        ImmediateNavDisplay(
            entries = navEntries,
            activeContentKey = navigationState.currentStack.last().toString(),
            onBack = {
                if (!navigator.goBack()) {
                    (context as? android.app.Activity)?.finish()
                        ?: backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: AppTabDestination?,
    onTabSelected: (AppTabDestination) -> Unit
) {
    NavigationBar {
        AppTabDestination.tabs.forEach { tab ->
            val icon = when (tab) {
                AppTabDestination.ALL -> Icons.Default.GridView
                AppTabDestination.TODAY -> Icons.Default.Today
                AppTabDestination.COMPLETED -> Icons.Default.CheckCircle
                AppTabDestination.CALENDAR -> Icons.Default.DateRange
            }
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) },
                modifier = Modifier.testTag("app_tab_${tab.name.lowercase()}")
            )
        }
    }
}
