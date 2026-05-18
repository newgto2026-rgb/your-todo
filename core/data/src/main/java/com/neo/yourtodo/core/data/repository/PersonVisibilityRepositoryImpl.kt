package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.PersonVisibilityDao
import com.neo.yourtodo.core.database.entity.ObservedSyncStateEntity
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.core.database.entity.VisibilityGrantEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.PersonVisibilityRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.ObservedTodo
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrant
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrantState
import com.neo.yourtodo.core.network.personvisibility.NetworkCreateVisibilityGrantRequest
import com.neo.yourtodo.core.network.personvisibility.NetworkObservedTodo
import com.neo.yourtodo.core.network.personvisibility.NetworkObservedTodoSyncResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkRevokeVisibilityGrantResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkVisibilityGrant
import com.neo.yourtodo.core.network.personvisibility.PersonVisibilityAuthRequiredException
import com.neo.yourtodo.core.network.personvisibility.PersonVisibilityNetworkDataSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PersonVisibilityRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val personVisibilityNetworkDataSource: PersonVisibilityNetworkDataSource,
    private val personVisibilityDao: PersonVisibilityDao,
    private val authSessionRefresher: AuthSessionRefresher,
    private val timeProvider: PersonVisibilityTimeProvider
) : PersonVisibilityRepository {
    override fun observeVisibilityGrants(): Flow<List<PersonVisibilityGrant>> =
        userPreferencesDataSource.authSession.distinctUntilChanged().flatMapLatest { session ->
            val currentUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            personVisibilityDao.observeVisibilityGrants(currentUserId)
        }.map { grants -> grants.map { it.toDomain() } }

    override suspend fun setVisibilityGrant(friendUserId: String): Result<PersonVisibilityGrant> =
        authenticatedRequest { accessToken, currentUserId ->
            personVisibilityNetworkDataSource.createVisibilityGrant(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                request = NetworkCreateVisibilityGrantRequest(viewerUserId = friendUserId)
            ).also { grant ->
                personVisibilityDao.upsertVisibilityGrants(listOf(grant.toEntity(currentUserId)))
            }.toDomain()
        }

    override suspend fun revokeVisibilityGrant(friendUserId: String): Result<Unit> =
        authenticatedRequest { accessToken, currentUserId ->
            val grant = activeGivenGrant(currentUserId, friendUserId, accessToken)
                ?: return@authenticatedRequest
            val response = personVisibilityNetworkDataSource.revokeVisibilityGrant(
                accessToken = accessToken,
                idempotencyKey = UUID.randomUUID().toString(),
                grantId = grant.grantId
            )
            personVisibilityDao.upsertVisibilityGrants(listOf(response.toEntity(grant)))
            personVisibilityDao.purgeObservedTodosByGrantId(currentUserId, grant.grantId)
        }

    override fun observeObservedTodos(): Flow<List<ObservedPersonTodos>> =
        userPreferencesDataSource.authSession.distinctUntilChanged().flatMapLatest { session ->
            val currentUserId = session?.takeUnless { it.onboardingRequired }?.userId
                ?: return@flatMapLatest flowOf(emptyList())
            personVisibilityDao.observeObservedTodos(currentUserId)
        }.map { todos ->
            todos.groupBy { it.ownerUserId }
                .map { (ownerUserId, ownerTodos) ->
                    ObservedPersonTodos(
                        ownerUserId = ownerUserId,
                        todos = ownerTodos.map { it.toDomain() }
                    )
                }
        }

    override suspend fun refreshVisibilityGrants(): Result<List<PersonVisibilityGrant>> =
        authenticatedRequest { accessToken, currentUserId ->
            val grants = personVisibilityNetworkDataSource.getVisibilityGrants(accessToken)
                .let { it.given + it.received }
            personVisibilityDao.replaceVisibilityGrantsAndPruneObservedTodos(
                currentUserId = currentUserId,
                grants = grants.map { it.toEntity(currentUserId) },
                activeObservedGrantIds = grants.activeObservedGrantIds(currentUserId)
            )
            grants.map { it.toDomain() }
        }

    override suspend fun syncObservedTodos(
        windowStart: LocalDate?,
        windowEnd: LocalDate?
    ): Result<Unit> =
        authenticatedRequest { accessToken, currentUserId ->
            val cursor = personVisibilityDao.getObservedSyncState(currentUserId)?.cursor
            personVisibilityNetworkDataSource.syncObservedTodos(
                accessToken = accessToken,
                cursor = cursor,
                windowStart = windowStart?.toString(),
                windowEnd = windowEnd?.toString()
            ).cache(currentUserId)
        }

    private suspend fun activeGivenGrant(
        currentUserId: String,
        friendUserId: String,
        accessToken: String
    ): VisibilityGrantEntity? =
        personVisibilityDao.getActiveGrant(
            currentUserId = currentUserId,
            ownerUserId = currentUserId,
            viewerUserId = friendUserId
        ) ?: personVisibilityNetworkDataSource.getVisibilityGrants(accessToken).let { response ->
            val grants = response.given + response.received
            personVisibilityDao.replaceVisibilityGrantsAndPruneObservedTodos(
                currentUserId = currentUserId,
                grants = grants.map { it.toEntity(currentUserId) },
                activeObservedGrantIds = grants.activeObservedGrantIds(currentUserId)
            )
            grants.firstOrNull {
                it.owner.id == currentUserId &&
                    it.viewer.id == friendUserId &&
                    it.status == PersonVisibilityGrantState.ACTIVE.name
            }?.toEntity(currentUserId)
        }

    private suspend fun NetworkObservedTodoSyncResponse.cache(currentUserId: String) {
        val now = timeProvider.currentTimeMillis()
        personVisibilityDao.applyObservedTodoSync(
            currentUserId = currentUserId,
            upserts = items.map { it.toEntity(currentUserId, now) },
            deletedObservedTodoIds = deleted.map { it.observedTodoId },
            purgedGrantIds = purgedGrantIds,
            state = ObservedSyncStateEntity(
                currentUserId = currentUserId,
                cursor = nextCursor,
                syncedAtEpochMillis = now
            )
        )
    }

    private suspend fun <T> authenticatedRequest(block: suspend (String, String) -> T): Result<T> =
        runCatching {
            val session = currentSession() ?: authRequired(clearPersistedSession = false)
            try {
                block(session.accessToken, session.userId)
            } catch (throwable: PersonVisibilityAuthRequiredException) {
                val refreshedSession = authSessionRefresher.refresh(session.refreshToken)
                    ?: authRequired()
                try {
                    block(refreshedSession.accessToken, refreshedSession.userId)
                } catch (retryThrowable: PersonVisibilityAuthRequiredException) {
                    authRequired()
                }
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }

    private suspend fun currentSession() =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }

    private suspend fun authRequired(clearPersistedSession: Boolean = true): Nothing {
        if (clearPersistedSession) {
            userPreferencesDataSource.clearAuthSession()
        }
        throw AuthRequiredException()
    }

    private fun NetworkVisibilityGrant.toEntity(currentUserId: String): VisibilityGrantEntity {
        val createdAtMillis = createdAt.toEpochMillisOrDefault()
        val updatedAtMillis = updatedAt.toEpochMillisOrNull()
            ?: revokedAt.toEpochMillisOrNull()
            ?: createdAtMillis
        return VisibilityGrantEntity(
            currentUserId = currentUserId,
            grantId = id,
            ownerUserId = owner.id,
            viewerUserId = viewer.id,
            status = status,
            version = version,
            createdAtEpochMillis = createdAtMillis,
            updatedAtEpochMillis = updatedAtMillis,
            revokedAtEpochMillis = revokedAt.toEpochMillisOrNull()
        )
    }

    private fun NetworkVisibilityGrant.toDomain(): PersonVisibilityGrant =
        toEntity(currentUserId = "").toDomain()

    private fun List<NetworkVisibilityGrant>.activeObservedGrantIds(currentUserId: String): List<String> =
        filter {
            it.viewer.id == currentUserId &&
                it.status == PersonVisibilityGrantState.ACTIVE.name
        }.map { it.id }

    private fun NetworkRevokeVisibilityGrantResponse.toEntity(existing: VisibilityGrantEntity): VisibilityGrantEntity {
        val revokedAtMillis = revokedAt.toEpochMillisOrNull() ?: timeProvider.currentTimeMillis()
        return existing.copy(
            status = status,
            version = version,
            updatedAtEpochMillis = revokedAtMillis,
            revokedAtEpochMillis = revokedAtMillis
        )
    }

    private fun VisibilityGrantEntity.toDomain(): PersonVisibilityGrant =
        PersonVisibilityGrant(
            id = grantId,
            ownerUserId = ownerUserId,
            observerUserId = viewerUserId,
            state = enumValueOrDefault(status, PersonVisibilityGrantState.NONE),
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString(),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis).toString(),
            revokedAt = revokedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() }
        )

    private fun NetworkObservedTodo.toEntity(
        currentUserId: String,
        cacheUpdatedAtEpochMillis: Long
    ): ObservedTodoEntity =
        ObservedTodoEntity(
            currentUserId = currentUserId,
            observedTodoId = observedTodoId,
            sourceTodoId = sourceTodoId,
            grantId = grantId,
            ownerUserId = owner.id,
            ownerNickname = owner.nickname,
            ownerAvatarUrl = owner.avatarUrl,
            title = title,
            dueDateEpochDay = dueDate.toLocalDateOrNull()?.toEpochDay(),
            dueTimeMinutes = dueTime.toTimeMinutesOrNull(),
            isDone = isDone ?: status == "COMPLETED",
            recurrenceOccurrenceId = recurrenceOccurrenceId,
            projectionVersion = revision?.toLongOrNull() ?: projectionVersion,
            updatedAtEpochMillis = updatedAt.toEpochMillisOrDefault(),
            cacheUpdatedAtEpochMillis = cacheUpdatedAtEpochMillis
        )

    private fun ObservedTodoEntity.toDomain(): ObservedTodo =
        ObservedTodo(
            id = observedTodoId,
            ownerUserId = ownerUserId,
            sourceTodoId = sourceTodoId,
            grantId = grantId,
            ownerNickname = ownerNickname,
            ownerAvatarUrl = ownerAvatarUrl,
            title = title,
            isDone = isDone,
            dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
            dueTimeMinutes = dueTimeMinutes,
            priority = TodoPriority.MEDIUM,
            categoryName = null,
            projectionVersion = projectionVersion,
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis).toString()
        )

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun String.toEpochMillisOrDefault(): Long =
        toEpochMillisOrNull() ?: 0L

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun String?.toTimeMinutesOrNull(): Int? =
        this?.split(":")
            ?.takeIf { it.size >= 2 }
            ?.let { parts ->
                val hour = parts[0].toIntOrNull() ?: return@let null
                val minute = parts[1].toIntOrNull() ?: return@let null
                hour * 60 + minute
            }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
