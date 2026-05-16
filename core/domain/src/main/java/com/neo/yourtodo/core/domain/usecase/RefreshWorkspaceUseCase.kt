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
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RefreshWorkspaceUseCase @Inject constructor(
    private val todoRepository: TodoItemRepository,
    private val friendRepository: FriendRepository,
    private val assignmentRepository: AssignmentRepository,
    private val calendarWidgetUpdater: CalendarWidgetUpdater,
    private val refreshPolicy: WorkspaceRefreshPolicy,
    private val refreshClock: WorkspaceRefreshClock,
    private val syncNotifier: WorkspaceSyncNotifier
) {
    private val inFlightMutex = Mutex()
    private var inFlightRefresh: CompletableDeferred<Result<WorkspaceRefreshSnapshot>>? = null
    private var lastRefresh: WorkspaceRefreshState? = null

    suspend operator fun invoke(
        forceRefresh: Boolean = false,
        allowCachedResult: Boolean = false
    ): Result<WorkspaceRefreshSnapshot> {
        val (refresh, shouldStart, skippedSnapshot) = inFlightMutex.withLock {
            val activeRefresh = inFlightRefresh
            if (activeRefresh != null) {
                RefreshStart(activeRefresh, shouldStart = false)
            } else {
                val decision = if (allowCachedResult) {
                    refreshPolicy.decide(
                        forceRefresh = forceRefresh,
                        lastRefresh = lastRefresh,
                        nowEpochMillis = refreshClock.nowEpochMillis()
                    )
                } else {
                    WorkspaceRefreshDecision.Refresh
                }
                when (decision) {
                    is WorkspaceRefreshDecision.Refresh -> {
                        val newRefresh = CompletableDeferred<Result<WorkspaceRefreshSnapshot>>()
                        inFlightRefresh = newRefresh
                        RefreshStart(newRefresh, shouldStart = true)
                    }
                    is WorkspaceRefreshDecision.Skip -> RefreshStart(
                        refresh = null,
                        shouldStart = false,
                        skippedSnapshot = decision.snapshot
                    )
                }
            }
        }
        if (skippedSnapshot != null) {
            return Result.success(skippedSnapshot)
        }
        if (!shouldStart) {
            return checkNotNull(refresh).await()
        }

        return try {
            val result = performRefresh()
            inFlightMutex.withLock {
                lastRefresh = result.getOrNull()?.let { snapshot ->
                    WorkspaceRefreshState(
                        snapshot = snapshot,
                        completedAtEpochMillis = refreshClock.nowEpochMillis()
                    )
                }
            }
            checkNotNull(refresh).complete(result)
            result
        } catch (exception: CancellationException) {
            checkNotNull(refresh).completeExceptionally(exception)
            throw exception
        } catch (throwable: Throwable) {
            inFlightMutex.withLock {
                lastRefresh = null
            }
            checkNotNull(refresh).completeExceptionally(throwable)
            throw throwable
        } finally {
            withContext(NonCancellable) {
                inFlightMutex.withLock {
                    if (inFlightRefresh === refresh) {
                        inFlightRefresh = null
                    }
                }
            }
        }
    }

    private suspend fun performRefresh(): Result<WorkspaceRefreshSnapshot> = coroutineScope {
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
        updateCalendarWidgetsBestEffort()
        Result.success(snapshot)
    }

    private suspend fun updateCalendarWidgetsBestEffort() {
        runCatching { calendarWidgetUpdater.updateCalendarWidgets() }
            .onFailure { if (it is CancellationException) throw it }
    }
}

data class WorkspaceRefreshSnapshot(
    val isFullySynced: Boolean,
    val friends: List<Friend>,
    val incomingRequests: List<FriendRequest>,
    val outgoingRequests: List<FriendRequest>,
    val visibleReceivedAssignedTodos: List<AssignedTodo>
)

private data class RefreshStart(
    val refresh: CompletableDeferred<Result<WorkspaceRefreshSnapshot>>?,
    val shouldStart: Boolean,
    val skippedSnapshot: WorkspaceRefreshSnapshot? = null
)

data class WorkspaceRefreshState(
    val snapshot: WorkspaceRefreshSnapshot,
    val completedAtEpochMillis: Long
)

sealed interface WorkspaceRefreshDecision {
    data object Refresh : WorkspaceRefreshDecision

    data class Skip(
        val snapshot: WorkspaceRefreshSnapshot
    ) : WorkspaceRefreshDecision
}

class WorkspaceRefreshPolicy @Inject constructor() {
    fun decide(
        forceRefresh: Boolean,
        lastRefresh: WorkspaceRefreshState?,
        nowEpochMillis: Long
    ): WorkspaceRefreshDecision {
        if (forceRefresh) return WorkspaceRefreshDecision.Refresh
        if (lastRefresh == null) return WorkspaceRefreshDecision.Refresh
        if (!lastRefresh.snapshot.isFullySynced) return WorkspaceRefreshDecision.Refresh

        val ageMillis = nowEpochMillis - lastRefresh.completedAtEpochMillis
        return if (ageMillis in 0 until STALE_THRESHOLD_MILLIS) {
            WorkspaceRefreshDecision.Skip(lastRefresh.snapshot)
        } else {
            WorkspaceRefreshDecision.Refresh
        }
    }

    companion object {
        const val STALE_THRESHOLD_MILLIS: Long = 5 * 60 * 1000
    }
}

open class WorkspaceRefreshClock @Inject constructor() {
    open fun nowEpochMillis(): Long = System.currentTimeMillis()
}
