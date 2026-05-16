package com.neo.yourtodo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.usecase.ObserveSelectedTodoFilterUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateSelectedTodoFilterUseCase
import com.neo.yourtodo.core.model.TodoFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppNavigationPreferencesViewModel @Inject constructor(
    observeSelectedTodoFilterUseCase: ObserveSelectedTodoFilterUseCase,
    private val updateSelectedTodoFilterUseCase: UpdateSelectedTodoFilterUseCase
) : ViewModel() {
    private var rememberSelectedTodoFilterJob: Job? = null

    val selectedTodoFilter: StateFlow<TodoFilter?> = observeSelectedTodoFilterUseCase()
        .map { filter -> filter as TodoFilter? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun rememberSelectedTodoFilter(filter: TodoFilter) {
        if (selectedTodoFilter.value == filter) return
        rememberSelectedTodoFilterJob?.cancel()
        rememberSelectedTodoFilterJob = viewModelScope.launch {
            updateSelectedTodoFilterUseCase(filter)
        }
    }
}
