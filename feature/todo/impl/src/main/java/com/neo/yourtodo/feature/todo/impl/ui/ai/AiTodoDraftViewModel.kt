package com.neo.yourtodo.feature.todo.impl.ui.ai

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.ParseAiTodoDraftsUseCase
import com.neo.yourtodo.core.domain.usecase.SyncTodosUseCase
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoDraft
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.ui.dueTimeTextToMinutes
import com.neo.yourtodo.feature.todo.impl.ui.minutesToDueTimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiTodoDraftViewModel @Inject constructor(
    private val parseAiTodoDraftsUseCase: ParseAiTodoDraftsUseCase,
    private val addTodoUseCase: AddTodoUseCase,
    private val createAssignmentBundleUseCase: CreateAssignmentBundleUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val syncTodosUseCase: SyncTodosUseCase,
    private val calendarWidgetUpdater: CalendarWidgetUpdater
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiTodoDraftUiState())
    val uiState = _uiState

    private val _sideEffects = MutableSharedFlow<AiTodoDraftSideEffect>()
    val sideEffects = _sideEffects.asSharedFlow()

    init {
        refreshPeople()
    }

    fun onPromptChange(value: String) {
        _uiState.update { it.copy(prompt = value, errorMessageRes = null) }
    }

    fun onAnalyze() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.todo_ai_error_prompt_required) }
            return
        }
        viewModelScope.launch {
            val people = ensurePeople(forceRefresh = true)
            _uiState.update { it.copy(isAnalyzing = true, errorMessageRes = null) }
            parseAiTodoDraftsUseCase(prompt, people)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            draftItems = result.items.map { draft -> draft.toUiModel() },
                            modelName = result.model,
                            errorMessageRes = if (result.items.isEmpty()) {
                                R.string.todo_ai_error_no_drafts
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessageRes = R.string.todo_ai_error_analyze_failed
                        )
                    }
                }
        }
    }

    fun onDraftSelected(id: String, selected: Boolean) {
        updateDraft(id) { copy(isSelected = selected) }
    }

    fun onDraftTitleChange(id: String, value: String) {
        updateDraft(id) { copy(title = value, errorMessageRes = null) }
    }

    fun onDraftAssigneeChange(id: String, assigneeId: String?) {
        updateDraft(id) { copy(assigneeId = assigneeId, needsReview = assigneeId == null, errorMessageRes = null) }
    }

    fun onDraftDueDateChange(id: String, value: String) {
        updateDraft(id) { copy(dueDateInput = value, errorMessageRes = null) }
    }

    fun onDraftDueTimeChange(id: String, value: String) {
        updateDraft(id) { copy(dueTimeInput = value, errorMessageRes = null) }
    }

    fun onDraftPriorityChange(id: String, priority: TodoPriority) {
        updateDraft(id) { copy(priority = priority) }
    }

    fun onDraftDelete(id: String) {
        _uiState.update { state ->
            state.copy(draftItems = state.draftItems.filterNot { it.id == id })
        }
    }

    fun onSave() {
        val state = _uiState.value
        val selectedDrafts = state.draftItems.filter { it.isSelected }
        if (selectedDrafts.isEmpty()) {
            _uiState.update { it.copy(errorMessageRes = R.string.todo_ai_error_no_selected_drafts) }
            return
        }
        val validated = selectedDrafts.map { it.toValidatedDraft() }
        if (validated.any { it.errorMessageRes != null }) {
            _uiState.update { current ->
                current.copy(
                    draftItems = current.draftItems.map { draft ->
                        validated.firstOrNull { it.id == draft.id }?.let { validatedDraft ->
                            draft.copy(errorMessageRes = validatedDraft.errorMessageRes)
                        } ?: draft
                    },
                    errorMessageRes = R.string.todo_ai_error_review_required
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
            val selfResult = saveSelfDrafts(validated.filter { it.assigneeId == SELF_ASSIGNEE_ID })
            val friendResult = saveFriendDrafts(validated.filter { it.assigneeId != SELF_ASSIGNEE_ID })
            if (selfResult && friendResult) {
                _uiState.update {
                    it.copy(
                        prompt = "",
                        draftItems = emptyList(),
                        modelName = null,
                        isSaving = false,
                        errorMessageRes = null
                    )
                }
                refreshCalendarWidgetAndSync()
                _sideEffects.emit(AiTodoDraftSideEffect.Saved)
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = R.string.todo_ai_error_save_failed
                    )
                }
            }
        }
    }

    private suspend fun saveSelfDrafts(drafts: List<ValidatedAiTodoDraft>): Boolean {
        drafts.forEach { draft ->
            val result = addTodoUseCase(
                title = draft.title,
                dueDate = draft.dueDate,
                categoryId = null,
                dueTimeMinutes = draft.dueTimeMinutes,
                priority = draft.priority
            )
            if (result.isFailure) return false
        }
        return true
    }

    private suspend fun saveFriendDrafts(drafts: List<ValidatedAiTodoDraft>): Boolean {
        val directAssignableFriends = getFriendsUseCase()
            .getOrDefault(emptyList())
            .asSequence()
            .filter { it.status == FriendshipStatus.ACTIVE }
            .filter { it.directAssignment.canDirectAssignToFriend }
            .map { it.userId }
            .toSet()
        drafts.groupBy { it.assigneeId }.forEach { (receiverUserId, receiverDrafts) ->
            val assignmentMode = if (receiverUserId in directAssignableFriends) {
                AssignmentMode.DIRECT
            } else {
                AssignmentMode.REQUEST
            }
            val result = createAssignmentBundleUseCase(
                receiverUserId = receiverUserId,
                items = receiverDrafts.map {
                    AssignmentDraftItem(
                        title = it.title,
                        description = null,
                        dueDate = it.dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        dueTimeMinutes = it.dueTimeMinutes,
                        priority = it.priority,
                        category = null
                    )
                },
                assignmentMode = assignmentMode
            )
            if (result.isFailure) return false
        }
        return true
    }

    private fun refreshCalendarWidgetAndSync() {
        viewModelScope.launch {
            runCatching { calendarWidgetUpdater.updateCalendarWidgets() }
            syncTodosUseCase()
        }
    }

    private fun refreshPeople() {
        viewModelScope.launch {
            ensurePeople(forceRefresh = true)
        }
    }

    private suspend fun ensurePeople(forceRefresh: Boolean = false): List<AiTodoPerson> {
        val current = _uiState.value.people
        if (!forceRefresh && current.isNotEmpty()) return current

        val session = observeAuthSessionUseCase().firstOrNull()
        val selfName = session?.user?.nickname?.takeIf { it.isNotBlank() } ?: "나"
        val selfPerson = session.toSelfPerson(selfName)
        val friends = getFriendsUseCase().getOrDefault(emptyList())
            .filter { it.status == FriendshipStatus.ACTIVE }
            .filter { friend -> friend.userId != session?.user?.id }
        val people = buildList {
            add(selfPerson)
            friends.forEach { friend ->
                add(
                    AiTodoPerson(
                        id = friend.userId,
                        displayName = friend.nickname,
                        aliases = friend.nickname.aliases(),
                        isSelf = false
                    )
                )
            }
        }
        _uiState.update { it.copy(people = people) }
        return people
    }

    private fun AuthSession?.toSelfPerson(selfName: String): AiTodoPerson =
        AiTodoPerson(
            id = SELF_ASSIGNEE_ID,
            displayName = selfName,
            aliases = (listOf("나", "내", "본인", "저", "제", "me", "self") + selfName.aliases()).distinct(),
            isSelf = true
        )

    private fun String.aliases(): List<String> =
        listOf(this, trim(), lowercase()).filter { it.isNotBlank() }.distinct()

    private fun updateDraft(id: String, transform: AiTodoDraftUiModel.() -> AiTodoDraftUiModel) {
        _uiState.update { state ->
            state.copy(draftItems = state.draftItems.map { if (it.id == id) it.transform() else it })
        }
    }

    private fun AiTodoDraft.toUiModel(): AiTodoDraftUiModel =
        AiTodoDraftUiModel(
            id = UUID.randomUUID().toString(),
            title = title,
            assigneeId = assigneeId,
            dueDateInput = dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE).orEmpty(),
            dueTimeInput = dueTimeMinutes?.let(::minutesToDueTimeText).orEmpty(),
            priority = priority,
            needsReview = needsReview,
            reviewReason = reviewReason
        )

    private companion object {
        private const val SELF_ASSIGNEE_ID = "self"
    }
}

data class AiTodoDraftUiState(
    val prompt: String = "",
    val people: List<AiTodoPerson> = emptyList(),
    val draftItems: List<AiTodoDraftUiModel> = emptyList(),
    val modelName: String? = null,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val errorMessageRes: Int? = null
)

data class AiTodoDraftUiModel(
    val id: String,
    val title: String,
    val assigneeId: String?,
    val dueDateInput: String,
    val dueTimeInput: String,
    val priority: TodoPriority,
    val needsReview: Boolean,
    val reviewReason: String?,
    val isSelected: Boolean = true,
    @StringRes val errorMessageRes: Int? = null
)

sealed interface AiTodoDraftSideEffect {
    data object Saved : AiTodoDraftSideEffect
}

internal data class ValidatedAiTodoDraft(
    val id: String,
    val title: String,
    val assigneeId: String,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int?,
    val priority: TodoPriority,
    @StringRes val errorMessageRes: Int?
)

internal fun AiTodoDraftUiModel.toValidatedDraft(): ValidatedAiTodoDraft {
    val normalizedTitle = title.trim()
    val parsedDueDate = if (dueDateInput.isBlank()) {
        null
    } else {
        runCatching { LocalDate.parse(dueDateInput.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    val parsedDueTime = dueTimeTextToMinutes(dueTimeInput)
    val error = when {
        normalizedTitle.isBlank() -> R.string.todo_error_title_required
        assigneeId == null -> R.string.todo_ai_error_assignee_required
        dueDateInput.isNotBlank() && parsedDueDate == null -> R.string.todo_error_due_date_format
        dueTimeInput.isNotBlank() && parsedDueTime == null -> R.string.todo_error_due_time_format
        dueTimeInput.isNotBlank() && parsedDueDate == null -> R.string.todo_error_due_time_requires_due_date
        else -> null
    }
    return ValidatedAiTodoDraft(
        id = id,
        title = normalizedTitle,
        assigneeId = assigneeId.orEmpty(),
        dueDate = parsedDueDate,
        dueTimeMinutes = parsedDueTime,
        priority = priority,
        errorMessageRes = error
    )
}
