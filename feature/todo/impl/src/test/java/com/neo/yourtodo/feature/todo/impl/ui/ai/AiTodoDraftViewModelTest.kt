package com.neo.yourtodo.feature.todo.impl.ui.ai

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AiTodoDraftRepository
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.ParseAiTodoDraftsUseCase
import com.neo.yourtodo.core.domain.usecase.SyncTodosUseCase
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoDraft
import com.neo.yourtodo.core.model.aitodo.AiTodoDraftResult
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiTodoDraftViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun analyzeExcludesSelfFromFriendCandidatesByUserIdAndNickname() = runTest {
        val aiRepository = RecordingAiTodoDraftRepository()
        val authRepository = FakeAuthRepository(
            session = testAuthSession(userId = "user-tee", nickname = "tee")
        )
        val friendRepository = FakeFriendRepository(
            friends = listOf(
                testFriend(userId = "user-tee", nickname = "tee"),
                testFriend(userId = "friend-duplicate", nickname = "TEE"),
                testFriend(userId = "friend-neo", nickname = "neo")
            )
        )
        val viewModel = createViewModel(
            aiRepository = aiRepository,
            authRepository = authRepository,
            friendRepository = friendRepository
        )

        viewModel.onPromptChange("tee 내일 아이등원7시 빨래 청소 설겆이 다해놔")
        viewModel.onAnalyze()
        advanceUntilIdle()

        assertThat(aiRepository.people.map { it.id }).containsExactly("self", "friend-neo").inOrder()
        assertThat(aiRepository.people.first { it.isSelf }.aliases).contains("tee")
        assertThat(viewModel.uiState.value.people.map { it.id }).containsExactly("self", "friend-neo").inOrder()
    }

    @Test
    fun saveSuccessClearsDraftsAndAllowsNextPromptToAnalyze() = runTest {
        val aiRepository = RecordingAiTodoDraftRepository(
            result = AiTodoDraftResult(
                items = listOf(testDraft(title = "빨래하기")),
                model = "test-model",
                fallbackUsed = false
            )
        )
        val viewModel = createViewModel(
            aiRepository = aiRepository,
            authRepository = FakeAuthRepository(testAuthSession(userId = "user-tee", nickname = "tee")),
            friendRepository = FakeFriendRepository(emptyList())
        )

        viewModel.onPromptChange("tee 오늘 빨래해")
        viewModel.onAnalyze()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.prompt).isEmpty()
        assertThat(viewModel.uiState.value.draftItems).isEmpty()
        assertThat(viewModel.uiState.value.isSaving).isFalse()

        aiRepository.result = AiTodoDraftResult(
            items = listOf(testDraft(title = "청소하기")),
            model = "test-model",
            fallbackUsed = false
        )
        viewModel.onPromptChange("tee 오늘 청소해")
        viewModel.onAnalyze()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.draftItems.map { it.title }).containsExactly("청소하기")
        assertThat(viewModel.uiState.value.isAnalyzing).isFalse()
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun saveSuccessClearsSavingStateBeforePostSaveSyncCompletes() = runTest {
        val aiRepository = RecordingAiTodoDraftRepository(
            result = AiTodoDraftResult(
                items = listOf(testDraft(title = "분리수거")),
                model = "test-model",
                fallbackUsed = false
            )
        )
        val todoRepository = DelayingSyncTodoRepository()
        val viewModel = createViewModel(
            aiRepository = aiRepository,
            authRepository = FakeAuthRepository(testAuthSession(userId = "user-tee", nickname = "tee")),
            friendRepository = FakeFriendRepository(emptyList()),
            todoRepository = todoRepository
        )

        viewModel.onPromptChange("tee 오늘 분리수거해")
        viewModel.onAnalyze()
        advanceUntilIdle()

        viewModel.onSave()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.uiState.value.prompt).isEmpty()
        assertThat(viewModel.uiState.value.draftItems).isEmpty()
        assertThat(viewModel.uiState.value.isSaving).isFalse()
        assertThat(todoRepository.syncStarted).isTrue()

        todoRepository.completeSync()
        advanceUntilIdle()
    }

    private fun createViewModel(
        aiRepository: RecordingAiTodoDraftRepository,
        authRepository: FakeAuthRepository,
        friendRepository: FakeFriendRepository,
        todoRepository: TodoItemRepository = FakeTodoRepository()
    ): AiTodoDraftViewModel {
        return AiTodoDraftViewModel(
            parseAiTodoDraftsUseCase = ParseAiTodoDraftsUseCase(aiRepository),
            addTodoUseCase = AddTodoUseCase(todoRepository),
            createAssignmentBundleUseCase = CreateAssignmentBundleUseCase(FakeAssignmentRepository()),
            getFriendsUseCase = GetFriendsUseCase(friendRepository),
            observeAuthSessionUseCase = ObserveAuthSessionUseCase(authRepository),
            syncTodosUseCase = SyncTodosUseCase(todoRepository),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater()
        )
    }

    private class RecordingAiTodoDraftRepository(
        var result: AiTodoDraftResult = AiTodoDraftResult(
            items = emptyList(),
            model = "test-model",
            fallbackUsed = false
        )
    ) : AiTodoDraftRepository {
        var people: List<AiTodoPerson> = emptyList()
            private set

        override suspend fun parseTodoDrafts(
            text: String,
            now: Instant,
            zoneId: ZoneId,
            people: List<AiTodoPerson>
        ): Result<AiTodoDraftResult> {
            this.people = people
            return Result.success(result)
        }
    }

    private class DelayingSyncTodoRepository(
        private val delegate: FakeTodoRepository = FakeTodoRepository()
    ) : TodoItemRepository by delegate {
        private val syncAllowed = CompletableDeferred<Unit>()
        var syncStarted: Boolean = false
            private set

        override suspend fun syncTodos(): Result<Unit> {
            syncStarted = true
            syncAllowed.await()
            return Result.success(Unit)
        }

        fun completeSync() {
            syncAllowed.complete(Unit)
        }
    }

    private class FakeAuthRepository(session: AuthSession) : AuthRepository {
        override val authSession = MutableStateFlow<AuthSession?>(session)

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut() = Unit
    }

    private class FakeFriendRepository(
        private val friends: List<Friend>
    ) : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(friends)

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())

        override suspend fun sendRequest(nickname: String): Result<Unit> = Result.success(Unit)

        override suspend fun acceptRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun declineRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun removeFriend(friendshipId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeAssignmentRepository : AssignmentRepository {
        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = Result.success(emptyList())

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(emptyList())

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(emptyList())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(emptyList())

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(emptyList())

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = MutableStateFlow(emptyList())

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> = Result.success(Unit)
    }

    private fun testAuthSession(userId: String, nickname: String): AuthSession =
        AuthSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = AuthUser(
                id = userId,
                nickname = nickname,
                email = "$nickname@example.com",
                onboardingRequired = false
            )
        )

    private fun testDraft(title: String): AiTodoDraft =
        AiTodoDraft(
            title = title,
            assigneeId = "self",
            dueDate = LocalDate.of(2026, 5, 11),
            dueTimeMinutes = null,
            priority = TodoPriority.MEDIUM,
            needsReview = false,
            reviewReason = null
        )

    private fun testFriend(userId: String, nickname: String): Friend =
        Friend(
            friendshipId = "friendship-$userId",
            userId = userId,
            nickname = nickname,
            status = FriendshipStatus.ACTIVE,
            createdAt = "2026-05-11T00:00:00Z",
            removedAt = null
        )
}
