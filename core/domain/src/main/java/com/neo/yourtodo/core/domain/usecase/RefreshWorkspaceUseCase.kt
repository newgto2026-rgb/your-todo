package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RefreshWorkspaceUseCase @Inject constructor(
    private val todoRepository: TodoItemRepository,
    private val friendRepository: FriendRepository,
    private val assignmentRepository: AssignmentRepository,
    private val calendarWidgetUpdater: CalendarWidgetUpdater,
    private val syncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier()
) {
    suspend operator fun invoke(): Result<WorkspaceRefreshSnapshot> = coroutineScope {
        val todoSyncResult = todoRepository.syncTodos()
        val friends = async { friendRepository.getFriends() }
        val incomingRequests = async { friendRepository.getIncomingRequests() }
        val outgoingRequests = async { friendRepository.getOutgoingRequests() }
        val pendingReceivedAssignedTodos = async {
            assignmentRepository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING)
        }
        val activeReceivedAssignedTodos = async {
            assignmentRepository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)
        }
        val historyReceivedAssignedTodos = async {
            assignmentRepository.getReceivedAssignedTodos(AssignmentFeedStatus.HISTORY)
        }

        val friendsResult = friends.await()
        val incomingRequestsResult = incomingRequests.await()
        val outgoingRequestsResult = outgoingRequests.await()
        val pendingReceivedResult = pendingReceivedAssignedTodos.await()
        val activeReceivedResult = activeReceivedAssignedTodos.await()
        val historyReceivedResult = historyReceivedAssignedTodos.await()
        val requiredFailure = listOf(
            friendsResult,
            incomingRequestsResult,
            outgoingRequestsResult
        ).firstOrNull { it.isFailure }?.exceptionOrNull()
        if (requiredFailure != null) {
            return@coroutineScope Result.failure(requiredFailure)
        }
        val isFullySynced = listOf(
            todoSyncResult,
            friendsResult,
            incomingRequestsResult,
            outgoingRequestsResult,
            pendingReceivedResult,
            activeReceivedResult,
            historyReceivedResult
        ).all { it.isSuccess }

        val snapshot = WorkspaceRefreshSnapshot(
            isFullySynced = isFullySynced,
            friends = friendsResult.getOrDefault(emptyList()),
            incomingRequests = incomingRequestsResult.getOrDefault(emptyList()),
            outgoingRequests = outgoingRequestsResult.getOrDefault(emptyList()),
            visibleReceivedAssignedTodos = visibleTaskSurfaceAssignedTodos(
                activeReceivedResult.getOrDefault(emptyList()),
                historyReceivedResult.getOrDefault(emptyList())
            )
        )
        syncNotifier.publish(snapshot)
        calendarWidgetUpdater.updateCalendarWidgets()
        Result.success(snapshot)
    }
}

data class WorkspaceRefreshSnapshot(
    val isFullySynced: Boolean,
    val friends: List<Friend>,
    val incomingRequests: List<FriendRequest>,
    val outgoingRequests: List<FriendRequest>,
    val visibleReceivedAssignedTodos: List<AssignedTodo>
)
