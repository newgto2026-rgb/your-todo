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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.example.myfirstapp.app.navigation.BottomSheetSceneStrategy
import com.example.myfirstapp.core.ui.navigation.AppFeatureEntry
import com.example.myfirstapp.core.ui.navigation.AppRouteActions
import com.example.myfirstapp.feature.todo.api.TodoEditorRoute
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavHost(entries: Set<@JvmSuppressWildcards AppFeatureEntry>) {
    val context = LocalContext.current
    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val sortedEntries = remember(entries) { entries.sortedBy { entry -> entry.route.toString() } }
    val startDestination = sortedEntries
        .firstOrNull(AppFeatureEntry::isStartDestination)
        ?.route
        ?: AppTabDestination.ALL.route
    val topLevelRoutes = remember { AppTabDestination.tabs.map(AppTabDestination::route).toSet() }
    val navigationState = rememberAppNavigationState(
        startRoute = startDestination,
        topLevelRoutes = topLevelRoutes
    )
    val navigator = remember(navigationState) { AppNavigator(navigationState) }
    val routeActions = remember(navigator) {
        object : AppRouteActions {
            override fun openTodoEdit(todoId: Long) {
                navigator.navigate(TodoEditorRoute(todoId = todoId, editOnly = true))
            }

            override fun openTodoAdd(dueDate: String) {
                navigator.navigate(TodoEditorRoute(dueDate = dueDate, editOnly = true))
            }

            override fun closeCurrentEntry() {
                navigator.goBack()
            }

            override fun setBackBlocked(blocked: Boolean) {
                navigator.setBackBlocked(blocked)
            }
        }
    }
    val currentTab = AppTabDestination.fromRoute(navigationState.topLevelRoute)
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>()
    )
    val appEntryProvider = remember(sortedEntries, routeActions) {
        entryProvider {
            sortedEntries.forEach { entry ->
                entry.register(this, routeActions)
            }
        }
    }
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }

    Scaffold(
        bottomBar = {
            AppBottomBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    if (tab == currentTab) return@AppBottomBar
                    navigator.navigate(tab.route)
                }
            )
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = navigationState.currentStack,
            entryDecorators = entryDecorators,
            entryProvider = appEntryProvider,
            sceneStrategies = listOf(bottomSheetStrategy),
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
