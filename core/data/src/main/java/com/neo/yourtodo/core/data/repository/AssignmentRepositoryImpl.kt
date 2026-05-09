package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoChecklistItem
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoReminder
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoTerminalReason
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundleStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.network.assignments.AssignmentAuthRequiredException
import com.neo.yourtodo.core.network.assignments.AssignmentNetworkDataSource
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodo
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoChecklistItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoReminder
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundleResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentDecision
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentSummary
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentUser
import com.neo.yourtodo.core.network.assignments.NetworkCreateAssignmentBundleRequest
import com.neo.yourtodo.core.network.assignments.NetworkCreateAssignmentItem
import com.neo.yourtodo.core.network.assignments.NetworkDecideAssignmentItemsRequest
import com.neo.yourtodo.core.network.assignments.NetworkUpsertAssignedTodoReminderRequest
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class AssignmentRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val assignmentNetworkDataSource: AssignmentNetworkDataSource,
    authNetworkDataSource: AuthNetworkDataSource,
    private val authSessionRefresher: AuthSessionRefresher =
        AuthSessionRefresher(userPreferencesDataSource, authNetworkDataSource)
) : AssignmentRepository {
    override suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ): Result<AssignmentBundle> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.createBundle(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                request = NetworkCreateAssignmentBundleRequest(
                    receiverUserId = receiverUserId,
                    items = items.map { item ->
                        NetworkCreateAssignmentItem(
                            clientItemId = UUID.randomUUID().toString(),
                            title = item.title,
                            description = item.description,
                            dueDate = item.dueDate,
                            dueTimeMinutes = item.dueTimeMinutes,
                            priority = item.priority.name,
                            category = item.category
                        )
                    }
                )
            ).toDomain()
        }

    override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getFriendSummary(accessToken, friendUserId).let {
                FriendAssignmentSummary(
                    friendUserId = it.friendUserId,
                    sent = it.sent.toDomain(),
                    received = it.received.toDomain()
                )
            }
        }

    override suspend fun getFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Result<List<AssignedTodo>> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getFriendAssignedTodos(
                accessToken = accessToken,
                friendUserId = friendUserId,
                direction = direction.name.lowercase(Locale.US),
                status = status.wireValue
            ).items.map { it.toDomain() }
        }

    override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getReceivedAssignedTodos(accessToken, status.wireValue)
                .items.map { it.toDomain() }
        }

    override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getSentAssignedTodos(accessToken, status.wireValue)
                .items.map { it.toDomain() }
        }

    override suspend fun decideBundleItems(
        bundleId: String,
        decisions: Map<String, AssignmentDecision>
    ): Result<AssignmentBundle> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.decideItems(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                bundleId = bundleId,
                request = NetworkDecideAssignmentItemsRequest(
                    decisions = decisions.map { (assignedTodoId, decision) ->
                        NetworkAssignmentDecision(
                            assignedTodoId = assignedTodoId,
                            decision = decision.name
                        )
                    }
                )
            ).toDomain()
        }

    override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.completeAssignedTodo(accessToken, assignedTodoId)
                .item.toDomain()
        }

    override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.reopenAssignedTodo(accessToken, assignedTodoId)
                .item.toDomain()
        }

    override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.deleteReceivedAssignedTodo(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                assignedTodoId = assignedTodoId
            ).item.toDomain()
        }

    override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.cancelAssignedTodo(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                assignedTodoId = assignedTodoId
            ).item.toDomain()
        }

    override suspend fun upsertAssignedTodoReminder(
        assignedTodoId: String,
        reminderAt: String,
        enabled: Boolean
    ): Result<Unit> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.upsertAssignedTodoReminder(
                accessToken = accessToken,
                assignedTodoId = assignedTodoId,
                request = NetworkUpsertAssignedTodoReminderRequest(
                    reminderAt = reminderAt,
                    enabled = enabled
                )
            )
            Unit
        }

    override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.deleteAssignedTodoReminder(
                accessToken = accessToken,
                assignedTodoId = assignedTodoId
            )
            Unit
        }

    private suspend fun <T> authenticatedRequest(block: suspend (String) -> T): Result<T> =
        runCatching {
            val session = currentSession() ?: throw AuthRequiredException()
            try {
                block(session.accessToken)
            } catch (throwable: AssignmentAuthRequiredException) {
                val refreshedSession = authSessionRefresher.refresh(session.refreshToken)
                    ?: authRequired()
                try {
                    block(refreshedSession.accessToken)
                } catch (retryThrowable: AssignmentAuthRequiredException) {
                    authRequired()
                }
            }
        }

    private suspend fun currentSession() =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }

    private suspend fun authRequired(): Nothing {
        userPreferencesDataSource.clearAuthSession()
        throw AuthRequiredException()
    }

    private fun NetworkAssignmentBundleResponse.toDomain(): AssignmentBundle =
        AssignmentBundle(
            id = bundle.id,
            sender = bundle.sender.toDomain(),
            receiver = bundle.receiver.toDomain(),
            status = enumValueOf(bundle.status),
            summary = bundle.summary.toDomain(),
            items = items.map { it.toDomain(bundle.id) }
        )

    private fun NetworkAssignedTodo.toDomain(fallbackBundleId: String? = null): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = bundleId ?: fallbackBundleId,
            title = title,
            description = description,
            dueDate = dueDate.toLocalDateOrNull(),
            dueTimeMinutes = dueTimeMinutes,
            priority = enumValueOrDefault(priority, TodoPriority.MEDIUM),
            category = category,
            status = enumValueOf(status),
            terminalReason = terminalReason?.let {
                enumValueOf<AssignedTodoTerminalReason>(it)
            },
            progressPercent = progressPercent,
            sender = sender?.toDomain(),
            receiver = receiver?.toDomain(),
            reminder = reminder?.toDomain(),
            checklist = checklist.map { it.toDomain() },
            createdAt = createdAt.toInstantOrNull(),
            completedAt = completedAt.toInstantOrNull()
        )

    private fun NetworkAssignedTodoChecklistItem.toDomain() =
        AssignedTodoChecklistItem(id = id, title = title, completed = completed)

    private fun NetworkAssignedTodoReminder.toDomain() =
        AssignedTodoReminder(reminderAt = reminderAt, enabled = enabled)

    private fun NetworkAssignmentUser.toDomain() =
        AssignedTodoUser(id = id, nickname = nickname)

    private fun NetworkAssignmentSummary.toDomain() =
        AssignmentSummary(
            totalCount = totalCount,
            pendingCount = pendingCount,
            acceptedCount = acceptedCount,
            inProgressCount = inProgressCount,
            doneCount = doneCount,
            rejectedCount = rejectedCount,
            canceledCount = canceledCount,
            progressPercent = progressPercent
        )

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private inline fun <reified T : Enum<T>> enumValueOf(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown enum value: $value")

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
