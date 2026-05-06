package com.example.myfirstapp.feature.calendar.impl.ui.screen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfirstapp.feature.calendar.impl.ui.CalendarAction
import com.example.myfirstapp.feature.calendar.impl.ui.CalendarUiState
import com.example.myfirstapp.feature.calendar.impl.ui.CalendarViewModel
import com.example.myfirstapp.feature.calendar.impl.ui.components.CalendarAgendaSection
import com.example.myfirstapp.feature.calendar.impl.ui.components.CalendarMonthGrid
import com.example.myfirstapp.feature.calendar.impl.ui.components.CalendarTopHeader

@Composable
fun CalendarRouteScreen(
    onNavigateToTodoEdit: (Long) -> Unit,
    onNavigateToTodoAdd: (java.time.LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onTodoClick = onNavigateToTodoEdit,
        onAddTodoClick = onNavigateToTodoAdd
    )
}

@Composable
private fun CalendarScreen(
    uiState: CalendarUiState,
    onAction: (CalendarAction) -> Unit,
    onTodoClick: (Long) -> Unit,
    onAddTodoClick: (java.time.LocalDate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8F9FC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 30.dp, top = 14.dp, bottom = 12.dp)
        ) {
            CalendarTopHeader(
                currentMonth = uiState.currentMonth,
                todayCount = uiState.todayTaskCount,
                onPreviousMonthClick = { onAction(CalendarAction.OnPreviousMonthClick) },
                onNextMonthClick = { onAction(CalendarAction.OnNextMonthClick) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.fillMaxWidth()
            ) {
                CalendarMonthGrid(
                    days = uiState.days,
                    onDateClick = { date -> onAction(CalendarAction.OnDateClick(date)) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            CalendarAgendaSection(
                selectedDate = uiState.selectedDate,
                selectedDateTodos = uiState.selectedDateTodos,
                onTodoClick = onTodoClick,
                onAddTodoClick = { onAddTodoClick(uiState.selectedDate) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
