package com.neo.yourtodo.feature.friends.impl.ui

import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RespondAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SetDirectAssignmentOptInUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoTerminalReason
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundleStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal fun FakeFriendRepository.createViewModel(
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    assignmentRepository: FakeAssignmentRepository = FakeAssignmentRepository()
): FriendsViewModel {
    val workspaceSyncNotifier = WorkspaceSyncNotifier()
    return FriendsViewModel(
        getFriends = GetFriendsUseCase(this),
        getFriendRequests = GetFriendRequestsUseCase(this),
        sendFriendRequest = SendFriendRequestUseCase(this),
        respondFriendRequest = RespondFriendRequestUseCase(this),
        removeFriend = RemoveFriendUseCase(this),
        createAssignmentBundle = CreateAssignmentBundleUseCase(assignmentRepository),
        setDirectAssignmentOptIn = SetDirectAssignmentOptInUseCase(assignmentRepository),
        getAssignedTodos = GetAssignedTodosUseCase(assignmentRepository),
        respondAssignmentBundle = RespondAssignmentBundleUseCase(assignmentRepository),
        refreshWorkspaceUseCase = RefreshWorkspaceUseCase(
            todoRepository = SuccessfulTodoRepository(),
            friendRepository = this,
            assignmentRepository = assignmentRepository,
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(),
            syncNotifier = workspaceSyncNotifier
        ),
        workspaceSyncNotifier = workspaceSyncNotifier,
        observeAuthSession = ObserveAuthSessionUseCase(authRepository)
    )
}

internal class SuccessfulTodoRepository : TodoItemRepository {
    override fun observeTodos(): Flow<List<TodoItem>> = flowOf(emptyList())

    override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        flowOf(emptyList())

    override suspend fun getTodo(id: Long): TodoItem? = null

    override suspend fun addTodo(
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Long> = Result.failure(UnsupportedOperationException())

    override suspend fun updateTodo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteTodo(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun toggleTodoDone(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun syncTodos(): Result<Unit> = Result.success(Unit)
}

internal class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
    override suspend fun updateCalendarWidgets(): Result<Unit> = Result.success(Unit)
}

internal class FakeAuthRepository : AuthRepository {
    override val authSession = MutableStateFlow<AuthSession?>(null)

    override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
        error("Not used.")

    override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
        error("Not used.")

    override suspend fun signOut() = Unit
}

internal class FakeFriendRepository(
    var getFriendsResult: Result<List<Friend>>? = null,
    var incomingResult: Result<List<FriendRequest>>? = null,
    var outgoingResult: Result<List<FriendRequest>>? = null,
    private val sendResult: Result<Unit> = Result.success(Unit),
    private val acceptResult: Result<Unit> = Result.success(Unit),
    private val removeResult: Result<Unit> = Result.success(Unit)
) : FriendRepository {
    var friends = emptyList<Friend>()
    var incoming = emptyList<FriendRequest>()
    var outgoing = emptyList<FriendRequest>()
    var lastSentNickname: String? = null
    var acceptedRequestId: String? = null

    override suspend fun getFriends(): Result<List<Friend>> =
        getFriendsResult ?: Result.success(friends)

    override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
        incomingResult ?: Result.success(incoming)

    override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
        outgoingResult ?: Result.success(outgoing)

    override suspend fun sendRequest(nickname: String): Result<Unit> {
        lastSentNickname = nickname
        return sendResult
    }

    override suspend fun acceptRequest(requestId: String): Result<Unit> {
        acceptedRequestId = requestId
        return acceptResult
    }

    override suspend fun declineRequest(requestId: String): Result<Unit> = Result.success(Unit)

    override suspend fun removeFriend(friendshipId: String): Result<Unit> = removeResult
}

internal class FakeAssignmentRepository : AssignmentRepository {
    private val sentItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val sentPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val sentHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val receivedItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val receivedPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val receivedHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val friendReceivedItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val friendReceivedPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private val friendReceivedHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
    var sentItems: List<AssignedTodo>
        get() = sentItemsState.value
        set(value) {
            sentItemsState.value = value
        }
    var sentPendingItems: List<AssignedTodo>
        get() = sentPendingItemsState.value
        set(value) {
            sentPendingItemsState.value = value
        }
    var sentHistoryItems: List<AssignedTodo>
        get() = sentHistoryItemsState.value
        set(value) {
            sentHistoryItemsState.value = value
        }
    var receivedItems: List<AssignedTodo>
        get() = receivedItemsState.value
        set(value) {
            receivedItemsState.value = value
            friendReceivedItemsState.value = value
        }
    var receivedPendingItems: List<AssignedTodo>
        get() = receivedPendingItemsState.value
        set(value) {
            receivedPendingItemsState.value = value
            friendReceivedPendingItemsState.value = value
        }
    var receivedHistoryItems: List<AssignedTodo>
        get() = receivedHistoryItemsState.value
        set(value) {
            receivedHistoryItemsState.value = value
            friendReceivedHistoryItemsState.value = value
        }
    var friendReceivedItemsResponse: List<AssignedTodo>? = null
    var friendReceivedPendingItemsResponse: List<AssignedTodo>? = null
    var friendReceivedHistoryItemsResponse: List<AssignedTodo>? = null
    var friendReceivedItemsResponseAfterDecision: List<AssignedTodo>? = null
    var friendReceivedPendingItemsResponseAfterDecision: List<AssignedTodo>? = null
    var friendReceivedHistoryItemsResponseAfterDecision: List<AssignedTodo>? = null
    var workspaceReceivedItems: List<AssignedTodo>? = null
    var workspaceReceivedPendingItems: List<AssignedTodo>? = null
    var workspaceReceivedHistoryItems: List<AssignedTodo>? = null
    var lastReceiverUserId: String? = null
    var lastItems: List<AssignmentDraftItem> = emptyList()
    var lastAssignmentMode: AssignmentMode = AssignmentMode.REQUEST
    var consentSummary = DirectAssignmentConsentSummary()
    val directAssignmentOptInRequests = mutableListOf<Pair<String, Boolean>>()
    var failedBundleIds = emptySet<String>()
    var friendSummaryCalls = 0
    var friendSummaryResult: Result<FriendAssignmentSummary>? = null
    var friendAssignedTodosGate: CompletableDeferred<Unit>? = null
    val decisionsByBundle = mutableMapOf<String, Map<String, AssignmentDecision>>()

    override suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ): Result<AssignmentBundle> {
        lastReceiverUserId = receiverUserId
        lastItems = items
        lastAssignmentMode = AssignmentMode.REQUEST
        return Result.success(assignmentBundle(items))
    }

    override suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>,
        assignmentMode: AssignmentMode
    ): Result<AssignmentBundle> {
        lastReceiverUserId = receiverUserId
        lastItems = items
        lastAssignmentMode = assignmentMode
        return Result.success(assignmentBundle(items))
    }

    override suspend fun setDirectAssignmentOptIn(
        friendUserId: String,
        enabled: Boolean
    ): Result<DirectAssignmentConsentSummary> {
        directAssignmentOptInRequests += friendUserId to enabled
        return Result.success(consentSummary)
    }

    override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
        (friendSummaryResult ?: Result.success(
            FriendAssignmentSummary(
                friendUserId = friendUserId,
                sent = assignmentSummary(totalCount = sentItems.size + sentPendingItems.size),
                received = assignmentSummary(totalCount = receivedItems.size + receivedPendingItems.size)
            )
        )).also { friendSummaryCalls += 1 }

    override suspend fun getFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Result<List<AssignedTodo>> {
        friendAssignedTodosGate?.await()
        val items = when (direction) {
            AssignmentDirection.SENT -> when (status) {
                AssignmentFeedStatus.ACTIVE -> sentItems
                AssignmentFeedStatus.PENDING -> sentPendingItems
                AssignmentFeedStatus.HISTORY -> sentHistoryItems
            }
            AssignmentDirection.RECEIVED -> when (status) {
                AssignmentFeedStatus.ACTIVE -> friendReceivedItemsResponse ?: receivedItems
                AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsResponse ?: receivedPendingItems
                AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsResponse ?: receivedHistoryItems
            }
        }
        if (direction == AssignmentDirection.RECEIVED) {
            when (status) {
                AssignmentFeedStatus.ACTIVE -> friendReceivedItemsState.value = items
                AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsState.value = items
                AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsState.value = items
            }
        }
        return Result.success(items)
    }

    override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> {
        val items = when (status) {
            AssignmentFeedStatus.ACTIVE -> workspaceReceivedItems ?: receivedItems
            AssignmentFeedStatus.PENDING -> workspaceReceivedPendingItems ?: receivedPendingItems
            AssignmentFeedStatus.HISTORY -> workspaceReceivedHistoryItems ?: receivedHistoryItems
        }
        when (status) {
            AssignmentFeedStatus.ACTIVE -> receivedItemsState.value = items
            AssignmentFeedStatus.PENDING -> receivedPendingItemsState.value = items
            AssignmentFeedStatus.HISTORY -> receivedHistoryItemsState.value = items
        }
        return Result.success(items)
    }

    override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
        Result.success(if (status == AssignmentFeedStatus.PENDING) sentPendingItems else sentItems)

    override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
        when (status) {
            AssignmentFeedStatus.ACTIVE -> receivedItemsState
            AssignmentFeedStatus.PENDING -> receivedPendingItemsState
            AssignmentFeedStatus.HISTORY -> receivedHistoryItemsState
        }

    override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
        when (status) {
            AssignmentFeedStatus.ACTIVE -> sentItemsState
            AssignmentFeedStatus.PENDING -> sentPendingItemsState
            AssignmentFeedStatus.HISTORY -> sentHistoryItemsState
        }

    override fun observeFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Flow<List<AssignedTodo>> = when (direction) {
        AssignmentDirection.SENT -> observeSentAssignedTodos(status)
        AssignmentDirection.RECEIVED -> when (status) {
            AssignmentFeedStatus.ACTIVE -> friendReceivedItemsState
            AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsState
            AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsState
        }
    }

    override suspend fun decideBundleItems(
        bundleId: String,
        decisions: Map<String, AssignmentDecision>
    ): Result<AssignmentBundle> {
        if (bundleId in failedBundleIds) {
            return Result.failure(IllegalStateException("Bundle decision failed"))
        }
        decisionsByBundle[bundleId] = decisions
        val acceptedIds = decisions.filterValues { it == AssignmentDecision.ACCEPT }.keys
        val rejectedIds = decisions.filterValues { it == AssignmentDecision.REJECT }.keys
        receivedItems = receivedItems + receivedPendingItems
            .filter { it.id in acceptedIds }
            .map { it.copy(status = AssignedTodoStatus.ACCEPTED) }
        receivedPendingItems = receivedPendingItems.filterNot { it.id in acceptedIds || it.id in rejectedIds }
        friendReceivedItemsResponseAfterDecision?.let { friendReceivedItemsResponse = it }
        friendReceivedPendingItemsResponseAfterDecision?.let { friendReceivedPendingItemsResponse = it }
        friendReceivedHistoryItemsResponseAfterDecision?.let { friendReceivedHistoryItemsResponse = it }
        return Result.success(assignmentBundle(emptyList()))
    }

    override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        Result.success(assignedTodo(assignedTodoId))

    override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        Result.success(assignedTodo(assignedTodoId))

    override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        Result.success(assignedTodo(assignedTodoId, status = AssignedTodoStatus.REJECTED))

    override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        Result.success(assignedTodo(assignedTodoId, status = AssignedTodoStatus.CANCELED))

    override suspend fun upsertAssignedTodoReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}
internal fun authSession(nickname: String) = AuthSession(
    accessToken = "access",
    refreshToken = "refresh",
    user = AuthUser(
        id = "me",
        nickname = nickname,
        email = "me@example.com",
        onboardingRequired = false
    )
)

internal fun friend(
    directAssignment: DirectAssignmentConsentSummary = DirectAssignmentConsentSummary()
) = Friend(
    friendshipId = "friendship-1",
    userId = "friend-1",
    nickname = "monday",
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-09T00:00:00Z",
    removedAt = null,
    directAssignment = directAssignment
)

internal fun request(
    id: String,
    requesterId: String = "friend-1",
    receiverId: String = "me"
) = FriendRequest(
    id = id,
    requester = FriendUser(
        id = requesterId,
        nickname = if (requesterId == "me") "tester" else "monday"
    ),
    receiver = FriendUser(
        id = receiverId,
        nickname = if (receiverId == "me") "tester" else "monday"
    ),
    status = FriendRequestStatus.PENDING,
    createdAt = "2026-05-09T00:00:00Z",
    respondedAt = null
)

internal fun assignedTodo(
    id: String = "assigned-1",
    title: String = "Shared todo",
    status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED,
    assignmentMode: AssignmentMode = AssignmentMode.REQUEST,
    terminalReason: AssignedTodoTerminalReason? = null,
    bundleId: String? = "bundle-1",
    createdAt: Instant? = null,
    completedAt: Instant? = null
) = AssignedTodo(
    id = id,
    bundleId = bundleId,
    title = title,
    description = null,
    dueDate = null,
    priority = TodoPriority.MEDIUM,
    category = null,
    status = status,
    terminalReason = terminalReason,
    progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
    sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    receiver = AssignedTodoUser(id = "me", nickname = "tester"),
    assignmentMode = assignmentMode,
    reminder = null,
    checklist = emptyList(),
    createdAt = createdAt,
    completedAt = completedAt
)

internal fun assignmentSummary(totalCount: Int = 0) = AssignmentSummary(
    totalCount = totalCount,
    pendingCount = 0,
    acceptedCount = totalCount,
    inProgressCount = 0,
    doneCount = 0,
    rejectedCount = 0,
    canceledCount = 0,
    progressPercent = 0
)

internal fun assignmentBundle(items: List<AssignmentDraftItem>) = AssignmentBundle(
    id = "bundle-1",
    sender = AssignedTodoUser(id = "me", nickname = "tester"),
    receiver = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    status = AssignmentBundleStatus.SENT,
    summary = assignmentSummary(totalCount = items.size),
    items = items.mapIndexed { index, item ->
        assignedTodo(id = "assigned-$index", title = item.title, status = AssignedTodoStatus.PENDING_ACCEPTANCE)
    }
)
