package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AssignmentRepository {
    suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ): Result<AssignmentBundle> = createBundle(receiverUserId, items, AssignmentMode.REQUEST)

    suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>,
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST
    ): Result<AssignmentBundle>

    suspend fun setDirectAssignmentOptIn(
        friendUserId: String,
        enabled: Boolean
    ): Result<DirectAssignmentConsentSummary> =
        Result.failure(UnsupportedOperationException())

    suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary>

    suspend fun getFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Result<List<AssignedTodo>>

    suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>>

    suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>>

    fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>>

    fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>>

    fun observeFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Flow<List<AssignedTodo>>

    fun observeFeedCacheFreshness(feed: AssignmentFeedCacheKey): Flow<AssignmentFeedCacheFreshness> =
        flowOf(AssignmentFeedCacheFreshness(feed = feed, lastUpdatedAtEpochMillis = null))

    suspend fun decideBundleItems(
        bundleId: String,
        decisions: Map<String, AssignmentDecision>
    ): Result<AssignmentBundle>

    suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit>

    suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo>

    suspend fun upsertAssignedTodoReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ): Result<Unit>

    suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit>
}

enum class AssignmentDirection {
    SENT,
    RECEIVED
}

enum class AssignmentFeedStatus(val wireValue: String) {
    ACTIVE("active"),
    PENDING("pending"),
    HISTORY("history")
}

data class AssignmentFeedCacheKey(
    val direction: AssignmentDirection,
    val status: AssignmentFeedStatus,
    val friendUserId: String? = null
)

/**
 * Freshness metadata for an assigned todo feed cache.
 *
 * `lastUpdatedAtEpochMillis` is the latest known feed refresh time when the
 * repository observed one, otherwise the oldest retained row `cacheUpdatedAt`
 * for this feed. Empty feeds can therefore be fresh within the current process
 * without requiring a storage schema change.
 */
data class AssignmentFeedCacheFreshness(
    val feed: AssignmentFeedCacheKey,
    val lastUpdatedAtEpochMillis: Long?,
    val staleAfterMillis: Long = AssignmentFeedCachePolicy.STALE_AFTER_MILLIS
) {
    fun isStale(nowEpochMillis: Long): Boolean =
        lastUpdatedAtEpochMillis?.let { nowEpochMillis - it >= staleAfterMillis } ?: true

    fun shouldForceRefresh(nowEpochMillis: Long): Boolean =
        isStale(nowEpochMillis)
}

object AssignmentFeedCachePolicy {
    const val STALE_AFTER_MILLIS: Long = 5 * 60 * 1000L
}
