package com.neo.yourtodo.feature.calendar.impl.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.ui.YourTodoBrandHeader
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.feature.calendar.impl.R
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarAction
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarUiState
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarViewModel
import com.neo.yourtodo.feature.calendar.impl.ui.initialCalendarUiState
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarAgendaSection
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarMonthGrid
import com.neo.yourtodo.feature.calendar.impl.ui.components.CalendarTopHeader
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarRouteScreen(
    initialSelectedDate: String?,
    onNavigateToTodoEdit: (Long) -> Unit,
    onNavigateToTodoAdd: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val routeDate = remember(initialSelectedDate) {
        initialSelectedDate?.let { rawDate ->
            runCatching { LocalDate.parse(rawDate) }.getOrNull()
        }
    }
    val routeUiState = remember(viewModel, initialSelectedDate) {
        initialSelectedDate?.let(viewModel::selectRouteDate)
        viewModel.uiState
    }
    val uiState by routeUiState.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.CREATED
    )
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

    CalendarScreen(
        uiState = displayedUiState,
        onAction = { action ->
            isWaitingForRouteDate = false
            viewModel.onAction(action)
        },
        onTodoClick = onNavigateToTodoEdit,
        onAddTodoClick = onNavigateToTodoAdd
    )
}

@Composable
private fun CalendarScreen(
    uiState: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
    onTodoClick: (Long) -> Unit,
    onAddTodoClick: (LocalDate) -> Unit
) {
    YourTodoScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            YourTodoBrandHeader(
                wordmarkContentDescription = stringResource(R.string.calendar_app_header_title),
                profileContentDescription = stringResource(R.string.calendar_header_profile_icon),
                profileInitial = uiState.profileInitial
            )

            Spacer(modifier = Modifier.height(10.dp))

            CalendarTopHeader(
                currentMonth = uiState.currentMonth,
                todayCount = uiState.todayTaskCount,
                onPreviousMonthClick = { onAction(CalendarAction.OnPreviousMonthClick) },
                onNextMonthClick = { onAction(CalendarAction.OnNextMonthClick) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.fillMaxWidth()
            ) {
                CalendarMonthGrid(
                    days = uiState.days,
                    onDateClick = { date -> onAction(CalendarAction.OnDateClick(date)) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            CalendarAgendaSection(
                selectedDate = uiState.selectedDate,
                selectedDateTodos = uiState.selectedDateTodos,
                onTodoClick = onTodoClick,
                onToggleTodoDone = { todoId -> onAction(CalendarAction.OnToggleTodoDone(todoId)) },
                onAddTodoClick = { onAddTodoClick(uiState.selectedDate) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
