package com.neo.yourtodo.feature.calendar.impl.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.ui.YourTodoAppHeader
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.calendar.impl.R
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarAction
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarSideEffect
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarUiState
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarViewModel
import com.neo.yourtodo.feature.calendar.impl.ui.initialCalendarUiState
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarAgendaSection
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarMonthGrid
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarTopHeader
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CalendarRouteScreen(
    initialSelectedDate: String?,
    onNavigateToTodoEdit: (Long) -> Unit,
    onNavigateToAssignedTodoEdit: (String) -> Unit,
    onNavigateToTodoAdd: (LocalDate) -> Unit,
    workspaceSyncState: StateFlow<WorkspaceSyncUiState> = MutableStateFlow(WorkspaceSyncUiState()),
    launchRouteState: StateFlow<NavKey?> = MutableStateFlow(null),
    onWorkspaceSyncClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val launchRoute by launchRouteState.collectAsStateWithLifecycle()
    val launchSelectedDate = (launchRoute as? CalendarDateRoute)?.selectedDate
    val routeDate = remember(initialSelectedDate, launchSelectedDate) {
        (launchSelectedDate ?: initialSelectedDate)?.let { rawDate ->
            runCatching { LocalDate.parse(rawDate) }.getOrNull()
        }
    }
    LaunchedEffect(routeDate) {
        routeDate?.let { selectedDate ->
            viewModel.selectRouteDate(selectedDate.toString())
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.CREATED
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isWaitingForRouteDate by remember(routeDate) {
        mutableStateOf(routeDate != null)
    }
    LaunchedEffect(routeDate, uiState.currentMonth, uiState.selectedDate) {
        if (
            routeDate == null ||
            uiState.currentMonth == YearMonth.from(routeDate) &&
            uiState.selectedDate == routeDate
        ) {
            isWaitingForRouteDate = false
        }
    }
    val displayedUiState = if (
        routeDate != null &&
        isWaitingForRouteDate
    ) {
        initialCalendarUiState(
            currentMonth = YearMonth.from(routeDate),
            selectedDate = routeDate
        )
    } else {
        uiState
    }
    val syncUiState by workspaceSyncState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is CalendarSideEffect.NavigateToTodoEdit -> onNavigateToTodoEdit(sideEffect.todoId)
                is CalendarSideEffect.NavigateToAssignedTodoEdit ->
                    onNavigateToAssignedTodoEdit(sideEffect.assignedTodoId)

                is CalendarSideEffect.NavigateToTodoAdd -> onNavigateToTodoAdd(sideEffect.dueDate)
                is CalendarSideEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(context.getString(sideEffect.messageRes))
            }
        }
    }

    CalendarScreen(
        uiState = displayedUiState,
        isSyncing = syncUiState.isSyncing,
        onAction = { action ->
            isWaitingForRouteDate = false
            viewModel.onAction(action)
        },
        onSyncClick = onWorkspaceSyncClick,
        onProfileClick = onProfileClick,
        snackbarHostState = snackbarHostState
    )
}

@Composable
private fun CalendarScreen(
    uiState: CalendarUiState,
    isSyncing: Boolean,
    onAction: (CalendarAction) -> Unit,
    onSyncClick: () -> Unit,
    onProfileClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    YourTodoScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0.dp)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            ) {
            Spacer(modifier = Modifier.height(10.dp))

            YourTodoAppHeader(
                wordmarkContentDescription = stringResource(R.string.calendar_app_header_title),
                profileContentDescription = stringResource(R.string.calendar_header_profile_icon),
                syncContentDescription = stringResource(R.string.calendar_sync_action),
                profileInitial = uiState.profileInitial,
                isSyncing = isSyncing,
                onSyncClick = onSyncClick,
                onProfileClick = onProfileClick,
                syncTestTag = "calendar_sync"
            )

            Spacer(modifier = Modifier.height(10.dp))

            CalendarTopHeader(
                currentMonth = uiState.currentMonth,
                todayCount = uiState.todayTaskCount,
                isMonthExpanded = uiState.isMonthExpanded,
                onPreviousMonthClick = { onAction(CalendarAction.OnPreviousMonthClick) },
                onNextMonthClick = { onAction(CalendarAction.OnNextMonthClick) },
                onToggleMonthExpansion = { onAction(CalendarAction.OnToggleMonthExpansion) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.fillMaxWidth()
            ) {
                CalendarMonthGrid(
                    days = if (uiState.isMonthExpanded) uiState.days else uiState.selectedWeekDays,
                    onDateClick = { date -> onAction(CalendarAction.OnDateClick(date)) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            CalendarAgendaSection(
                selectedDate = uiState.selectedDate,
                selectedDateTodos = uiState.selectedDateTodos,
                selectedDateTodoSections = uiState.selectedDateTodoSections,
                onTodoClick = { todo ->
                    onAction(CalendarAction.OnTodoClick(todo.id, todo.assignedTodoId))
                },
                onToggleTodoDone = { todo ->
                    onAction(CalendarAction.OnToggleTodoDone(todo.id, todo.assignedTodoId))
                },
                onToggleFriendTodosExpanded = {
                    onAction(CalendarAction.OnToggleFriendTodosExpanded)
                },
                onAddTodoClick = { onAction(CalendarAction.OnAddTodoClick) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        }
    }
}
