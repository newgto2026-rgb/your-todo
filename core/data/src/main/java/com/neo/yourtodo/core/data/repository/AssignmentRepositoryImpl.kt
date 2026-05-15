package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoWithChecklist
import com.neo.yourtodo.core.database.entity.assignedTodoCacheKey
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
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.network.assignments.AssignmentAuthRequiredException
import com.neo.yourtodo.core.network.assignments.AssignmentNetworkDataSource
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodo
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoChecklistItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoMutationItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoReminder
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundleResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentDecision
import com.neo.yourtodo.core.network.assignments.NetworkDirectAssignmentConsentSummary
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val assignmentNetworkDataSource: AssignmentNetworkDataSource,
    private val assignedTodoDao: AssignedTodoDao,
    authNetworkDataSource: AuthNetworkDataSource,
    private val authSessionRefresher: AuthSessionRefresher =
        AuthSessionRefresher(userPreferencesDataSource, authNetworkDataSource)
) : AssignmentRepository {
    override suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>
    ): Result<AssignmentBundle> =
        createBundle(receiverUserId, items, AssignmentMode.REQUEST)

    override suspend fun createBundle(
        receiverUserId: String,
        items: List<AssignmentDraftItem>,
        assignmentMode: AssignmentMode
    ): Result<AssignmentBundle> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.createBundle(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                request = NetworkCreateAssignmentBundleRequest(
                    receiverUserId = receiverUserId,
                    assignmentMode = assignmentMode.name,
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
                .also { bundle ->
                    cacheAssignedTodos(
                        items = bundle.items,
                        direction = AssignmentDirection.SENT,
                        status = AssignmentFeedStatus.PENDING,
                        friendUserId = receiverUserId,
                        replaceStale = false
                    )
                }
        }

    override suspend fun requestDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.requestDirectAssignmentConsent(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                friendUserId = friendUserId
            ).directAssignment.toDomain()
        }

    override suspend fun acceptDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.acceptDirectAssignmentConsent(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                friendUserId = friendUserId
            ).directAssignment.toDomain()
        }

    override suspend fun rejectDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.rejectDirectAssignmentConsent(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                friendUserId = friendUserId
            ).directAssignment.toDomain()
        }

    override suspend fun revokeDirectAssignmentConsent(friendUserId: String): Result<DirectAssignmentConsentSummary> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.revokeDirectAssignmentConsent(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                friendUserId = friendUserId
            ).directAssignment.toDomain()
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
                .also {
                    cacheAssignedTodos(
                        items = it,
                        direction = direction,
                        status = status,
                        friendUserId = friendUserId
                    )
                }
        }

    override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getReceivedAssignedTodos(accessToken, status.wireValue)
                .items.map { it.toDomain() }
                .also {
                    cacheAssignedTodos(
                        items = it,
                        direction = AssignmentDirection.RECEIVED,
                        status = status
                    )
                }
        }

    override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
        authenticatedRequest { accessToken ->
            assignmentNetworkDataSource.getSentAssignedTodos(accessToken, status.wireValue)
                .items.map { it.toDomain() }
                .also {
                    cacheAssignedTodos(
                        items = it,
                        direction = AssignmentDirection.SENT,
                        status = status
                    )
                }
        }

    override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
        userPreferencesDataSource.authSession.flatMapLatest { session ->
            val ownerUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            assignedTodoDao.observeReceivedAssignedTodos(ownerUserId, status.cacheStatuses())
        }.map { items -> items.map { it.toDomain() } }

    override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
        userPreferencesDataSource.authSession.flatMapLatest { session ->
            val ownerUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            assignedTodoDao.observeSentAssignedTodos(ownerUserId, status.cacheStatuses())
        }.map { items -> items.map { it.toDomain() } }

    override fun observeFriendAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ): Flow<List<AssignedTodo>> =
        userPreferencesDataSource.authSession.flatMapLatest { session ->
            val ownerUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            when (direction) {
                AssignmentDirection.SENT -> assignedTodoDao.observeSentAssignedTodosByFriend(
                    ownerUserId = ownerUserId,
                    friendUserId = friendUserId,
                    statuses = status.cacheStatuses()
                )

                AssignmentDirection.RECEIVED -> assignedTodoDao.observeReceivedAssignedTodosByFriend(
                    ownerUserId = ownerUserId,
                    friendUserId = friendUserId,
                    statuses = status.cacheStatuses()
                )
            }
        }.map { items -> items.map { it.toDomain() } }

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
                .also { bundle ->
                    cacheAssignedTodos(
                        items = bundle.items,
                        direction = AssignmentDirection.RECEIVED,
                        status = AssignmentFeedStatus.PENDING,
                        replaceStale = false
                    )
                }
        }

    override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        mutateAssignedTodoOptimistically(
            assignedTodoId = assignedTodoId,
            optimistic = { item ->
                item.copy(
                    status = AssignedTodoStatus.DONE.name,
                    progressPercent = 100,
                    completedAtEpochMillis = Instant.now().toEpochMilli()
                )
            }
        ) {
            authenticatedRequest { accessToken ->
                assignmentNetworkDataSource.completeAssignedTodo(accessToken, assignedTodoId)
                    .item
                    .let { cacheAssignedTodoMutation(it, AssignmentDirection.RECEIVED) }
            }
        }

    override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        mutateAssignedTodoOptimistically(
            assignedTodoId = assignedTodoId,
            optimistic = { item ->
                item.copy(
                    status = AssignedTodoStatus.ACCEPTED.name,
                    progressPercent = 0,
                    completedAtEpochMillis = null
                )
            }
        ) {
            authenticatedRequest { accessToken ->
                assignmentNetworkDataSource.reopenAssignedTodo(accessToken, assignedTodoId)
                    .item
                    .let { cacheAssignedTodoMutation(it, AssignmentDirection.RECEIVED) }
            }
        }

    override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        mutateAssignedTodoOptimistically(
            assignedTodoId = assignedTodoId,
            optimistic = { item ->
                item.copy(
                    status = AssignedTodoStatus.REJECTED.name,
                    terminalReason = AssignedTodoTerminalReason.DELETED_BY_RECEIVER.name
                )
            }
        ) {
            authenticatedRequest { accessToken ->
                assignmentNetworkDataSource.deleteReceivedAssignedTodo(
                    accessToken = accessToken,
                    idempotencyKey = UUID.randomUUID().toString(),
                    assignedTodoId = assignedTodoId
                ).item
                    .let { cacheAssignedTodoMutation(it, AssignmentDirection.RECEIVED) }
            }
        }

    override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
        runCatching {
            val ownerUserId = currentSession()?.userId ?: throw AuthRequiredException()
            assignedTodoDao.hideReceivedFromTaskSurface(ownerUserId, assignedTodoId)
        }

    override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
        mutateAssignedTodoOptimistically(
            assignedTodoId = assignedTodoId,
            optimistic = { item ->
                item.copy(
                    status = AssignedTodoStatus.CANCELED.name,
                    terminalReason = AssignedTodoTerminalReason.CANCELED_BY_SENDER.name
                )
            }
        ) {
            authenticatedRequest { accessToken ->
                assignmentNetworkDataSource.cancelAssignedTodo(
                    accessToken = accessToken,
                    idempotencyKey = UUID.randomUUID().toString(),
                    assignedTodoId = assignedTodoId
                ).item
                    .let { cacheAssignedTodoMutation(it, AssignmentDirection.SENT) }
            }
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

    private suspend fun cacheAssignedTodoMutation(
        item: AssignedTodo,
        direction: AssignmentDirection
    ) {
        cacheAssignedTodos(
            items = listOf(item),
            direction = direction,
            status = item.feedStatus(),
            replaceStale = false
        )
    }

    private suspend fun cacheAssignedTodoMutation(
        item: NetworkAssignedTodoMutationItem,
        direction: AssignmentDirection
    ): AssignedTodo {
        val ownerUserId = currentSession()?.userId
        val existing = ownerUserId?.let { assignedTodoDao.getAssignedTodoById(it, item.id) }
        val merged = item.toDomain(existing)
        if (ownerUserId != null) {
            val entity = merged.toEntity(
                ownerUserId = ownerUserId,
                existing = existing,
                direction = direction,
                cacheUpdatedAt = System.currentTimeMillis()
            )
            assignedTodoDao.upsertAssignedTodos(listOf(entity))
            item.checklist?.let { checklist ->
                val cacheKey = assignedTodoCacheKey(ownerUserId, item.id)
                assignedTodoDao.deleteChecklistItems(listOf(cacheKey))
                assignedTodoDao.upsertChecklistItems(
                    checklist.mapIndexed { index, checklistItem ->
                        AssignedTodoChecklistItemEntity(
                            ownerUserId = ownerUserId,
                            assignedTodoId = item.id,
                            assignedTodoCacheKey = cacheKey,
                            id = checklistItem.id,
                            title = checklistItem.title,
                            completed = checklistItem.completed,
                            sortOrder = index
                        )
                    }
                )
            }
        }
        return merged
    }

    private suspend fun cacheAssignedTodos(
        items: List<AssignedTodo>,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus,
        friendUserId: String? = null,
        replaceStale: Boolean = true
    ) {
        val ownerUserId = currentSession()?.userId ?: return
        val ids = items.map { it.id }
        val now = System.currentTimeMillis()
        val entities = items.map { item ->
            val existing = assignedTodoDao.getAssignedTodoById(ownerUserId, item.id)
            item.toEntity(
                ownerUserId = ownerUserId,
                existing = existing,
                direction = direction,
                cacheUpdatedAt = now
            )
        }
        val checklistItems = items.flatMap { item ->
            val cacheKey = assignedTodoCacheKey(ownerUserId, item.id)
            item.checklist.mapIndexed { index, checklist ->
                AssignedTodoChecklistItemEntity(
                    ownerUserId = ownerUserId,
                    assignedTodoId = item.id,
                    assignedTodoCacheKey = cacheKey,
                    id = checklist.id,
                    title = checklist.title,
                    completed = checklist.completed,
                    sortOrder = index
                )
            }
        }
        if (replaceStale) {
            replaceCachedItems(ownerUserId, direction, status, friendUserId, ids, entities, checklistItems)
        } else {
            assignedTodoDao.upsertAssignedTodoGraph(entities, checklistItems)
        }
    }

    private suspend fun replaceCachedItems(
        ownerUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus,
        friendUserId: String?,
        ids: List<String>,
        entities: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        val statuses = status.cacheStatuses()
        when (direction) {
            AssignmentDirection.RECEIVED -> {
                if (friendUserId == null) {
                    assignedTodoDao.replaceReceivedCache(ownerUserId, statuses, ids, entities, checklistItems)
                } else {
                    assignedTodoDao.replaceReceivedFriendCache(
                        ownerUserId,
                        friendUserId,
                        statuses,
                        ids,
                        entities,
                        checklistItems
                    )
                }
            }

            AssignmentDirection.SENT -> {
                if (friendUserId == null) {
                    assignedTodoDao.replaceSentCache(ownerUserId, statuses, ids, entities, checklistItems)
                } else {
                    assignedTodoDao.replaceSentFriendCache(
                        ownerUserId,
                        friendUserId,
                        statuses,
                        ids,
                        entities,
                        checklistItems
                    )
                }
            }
        }
    }

    private suspend fun <T> mutateAssignedTodoOptimistically(
        assignedTodoId: String,
        optimistic: (AssignedTodoEntity) -> AssignedTodoEntity,
        mutation: suspend () -> Result<T>
    ): Result<T> {
        val ownerUserId = currentSession()?.userId
        val previous = ownerUserId?.let { assignedTodoDao.getAssignedTodoById(it, assignedTodoId) }
        if (previous != null) {
            assignedTodoDao.upsertAssignedTodos(listOf(optimistic(previous)))
        }
        val result = mutation()
        if (result.isFailure && previous != null) {
            assignedTodoDao.upsertAssignedTodos(listOf(previous))
        }
        return result
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
            assignmentMode = assignmentMode?.let { enumValueOrDefault(it, AssignmentMode.REQUEST) }
                ?: AssignmentMode.REQUEST,
            reminder = reminder?.toDomain(),
            checklist = checklist.map { it.toDomain() },
            createdAt = createdAt.toInstantOrNull(),
            completedAt = completedAt.toInstantOrNull()
        )

    private fun NetworkAssignedTodoMutationItem.toDomain(existing: AssignedTodoEntity?): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = bundleId ?: existing?.bundleId,
            title = title ?: existing?.title.orEmpty(),
            description = description ?: existing?.description,
            dueDate = dueDate.toLocalDateOrNull() ?: existing?.dueDateEpochDay?.let(LocalDate::ofEpochDay),
            dueTimeMinutes = dueTimeMinutes ?: existing?.dueTimeMinutes,
            priority = priority?.let { enumValueOrDefault(it, TodoPriority.MEDIUM) }
                ?: existing?.priority?.let { enumValueOrDefault(it, TodoPriority.MEDIUM) }
                ?: TodoPriority.MEDIUM,
            category = category ?: existing?.category,
            status = status?.let { enumValueOf<AssignedTodoStatus>(it) }
                ?: existing?.status?.let { enumValueOf<AssignedTodoStatus>(it) }
                ?: AssignedTodoStatus.ACCEPTED,
            terminalReason = terminalReason?.let {
                enumValueOf<AssignedTodoTerminalReason>(it)
            } ?: existing?.terminalReason?.let {
                enumValueOf<AssignedTodoTerminalReason>(it)
            },
            progressPercent = progressPercent ?: existing?.progressPercent ?: 0,
            sender = sender?.toDomain() ?: userOrNull(existing?.senderUserId, existing?.senderNickname),
            receiver = receiver?.toDomain() ?: userOrNull(existing?.receiverUserId, existing?.receiverNickname),
            assignmentMode = assignmentMode?.let { enumValueOrDefault(it, AssignmentMode.REQUEST) }
                ?: existing?.assignmentMode?.let { enumValueOrDefault(it, AssignmentMode.REQUEST) }
                ?: AssignmentMode.REQUEST,
            reminder = reminder?.toDomain() ?: existing?.reminderAt?.let {
                AssignedTodoReminder(
                    reminderAt = it,
                    enabled = existing.reminderEnabled ?: true
                )
            },
            checklist = checklist?.map { it.toDomain() }.orEmpty(),
            createdAt = createdAt.toInstantOrNull()
                ?: existing?.createdAtEpochMillis?.let(Instant::ofEpochMilli),
            completedAt = completedAt.toInstantOrNull()
                ?: existing?.completedAtEpochMillis?.let(Instant::ofEpochMilli)
        )

    private fun AssignedTodoWithChecklist.toDomain(): AssignedTodo =
        assignedTodo.run {
            AssignedTodo(
                id = id,
                bundleId = bundleId,
                title = title,
                description = description,
                dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
                dueTimeMinutes = dueTimeMinutes,
                priority = enumValueOrDefault(priority, TodoPriority.MEDIUM),
                category = category,
                status = enumValueOf(status),
                terminalReason = terminalReason?.let {
                    enumValueOf<AssignedTodoTerminalReason>(it)
                },
                progressPercent = progressPercent,
                sender = userOrNull(senderUserId, senderNickname),
                receiver = userOrNull(receiverUserId, receiverNickname),
                assignmentMode = enumValueOrDefault(assignmentMode, AssignmentMode.REQUEST),
                reminder = reminderAt?.let {
                    AssignedTodoReminder(
                        reminderAt = it,
                        enabled = reminderEnabled ?: true
                    )
                },
                checklist = checklist
                    .sortedBy { it.sortOrder }
                    .map {
                        AssignedTodoChecklistItem(
                            id = it.id,
                            title = it.title,
                            completed = it.completed
                        )
                    },
                createdAt = createdAtEpochMillis?.let(Instant::ofEpochMilli),
                completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli)
            )
        }

    private fun AssignedTodo.toEntity(
        ownerUserId: String,
        existing: AssignedTodoEntity?,
        direction: AssignmentDirection,
        cacheUpdatedAt: Long
    ): AssignedTodoEntity =
        AssignedTodoEntity(
            ownerUserId = ownerUserId,
            id = id,
            cacheKey = assignedTodoCacheKey(ownerUserId, id),
            bundleId = bundleId,
            title = title,
            description = description,
            dueDateEpochDay = dueDate?.toEpochDay(),
            dueTimeMinutes = dueTimeMinutes,
            priority = priority.name,
            category = category,
            status = status.name,
            terminalReason = terminalReason?.name,
            progressPercent = progressPercent,
            senderUserId = sender?.id ?: existing?.senderUserId,
            senderNickname = sender?.nickname ?: existing?.senderNickname,
            receiverUserId = receiver?.id ?: existing?.receiverUserId,
            receiverNickname = receiver?.nickname ?: existing?.receiverNickname,
            assignmentMode = assignmentMode.name,
            reminderAt = reminder?.reminderAt,
            reminderEnabled = reminder?.enabled,
            createdAtEpochMillis = createdAt?.toEpochMilli(),
            completedAtEpochMillis = completedAt?.toEpochMilli(),
            receivedCached = existing?.receivedCached == true || direction == AssignmentDirection.RECEIVED,
            receivedTaskHidden = existing?.receivedTaskHidden == true,
            sentCached = existing?.sentCached == true || direction == AssignmentDirection.SENT,
            cacheUpdatedAt = cacheUpdatedAt
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

    private fun NetworkDirectAssignmentConsentSummary.toDomain() =
        DirectAssignmentConsentSummary(
            grantedByMe = enumValueOrDefault(grantedByMe, DirectAssignmentConsentState.NONE),
            grantedToMe = enumValueOrDefault(grantedToMe, DirectAssignmentConsentState.NONE)
        )

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private inline fun <reified T : Enum<T>> enumValueOf(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown enum value: $value")

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private fun userOrNull(id: String?, nickname: String?): AssignedTodoUser? =
        if (id != null && nickname != null) {
            AssignedTodoUser(id = id, nickname = nickname)
        } else {
            null
        }

    private fun AssignmentFeedStatus.cacheStatuses(): List<String> = when (this) {
        AssignmentFeedStatus.ACTIVE -> listOf(
            AssignedTodoStatus.ACCEPTED.name,
            AssignedTodoStatus.IN_PROGRESS.name
        )

        AssignmentFeedStatus.PENDING -> listOf(AssignedTodoStatus.PENDING_ACCEPTANCE.name)
        AssignmentFeedStatus.HISTORY -> listOf(
            AssignedTodoStatus.DONE.name,
            AssignedTodoStatus.REJECTED.name,
            AssignedTodoStatus.CANCELED.name
        )
    }

    private fun AssignedTodo.feedStatus(): AssignmentFeedStatus = when (status) {
        AssignedTodoStatus.PENDING_ACCEPTANCE -> AssignmentFeedStatus.PENDING
        AssignedTodoStatus.ACCEPTED,
        AssignedTodoStatus.IN_PROGRESS -> AssignmentFeedStatus.ACTIVE

        AssignedTodoStatus.DONE,
        AssignedTodoStatus.REJECTED,
        AssignedTodoStatus.CANCELED -> AssignmentFeedStatus.HISTORY
    }
}
