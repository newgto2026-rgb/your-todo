package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.dao.PersonVisibilityDao
import com.neo.yourtodo.core.database.entity.ObservedSyncStateEntity
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.core.database.entity.VisibilityGrantEntity
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkCreateVisibilityGrantRequest
import com.neo.yourtodo.core.network.personvisibility.NetworkDeletedObservedTodo
import com.neo.yourtodo.core.network.personvisibility.NetworkObservedTodo
import com.neo.yourtodo.core.network.personvisibility.NetworkObservedTodoOwner
import com.neo.yourtodo.core.network.personvisibility.NetworkObservedTodoSyncResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkRevokeVisibilityGrantResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkVisibilityGrant
import com.neo.yourtodo.core.network.personvisibility.NetworkVisibilityGrantsResponse
import com.neo.yourtodo.core.network.personvisibility.NetworkVisibilityUser
import com.neo.yourtodo.core.network.personvisibility.PersonVisibilityNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PersonVisibilityRepositoryImplTest {
    @Test
    fun setVisibilityGrantCreatesServerGrantAndCachesIt() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val dao = FakePersonVisibilityDao()
        val network = FakePersonVisibilityNetworkDataSource()
        val repository = repository(prefs = prefs, dao = dao, network = network)

        val grant = repository.setVisibilityGrant("friend-id").getOrThrow()

        assertThat(network.createTokens).containsExactly("access-token")
        assertThat(network.lastCreateRequest).isEqualTo(NetworkCreateVisibilityGrantRequest("friend-id"))
        assertThat(grant.id).isEqualTo("grant-id")
        assertThat(grant.observerUserId).isEqualTo("friend-id")
        assertThat(dao.observeVisibilityGrants("user-id").first().map { it.grantId })
            .containsExactly("grant-id")
    }

    @Test
    fun revokeVisibilityGrantUsesGrantIdAndPurgesObservedCache() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val dao = FakePersonVisibilityDao().apply {
            upsertVisibilityGrants(
                listOf(
                    visibilityGrant(
                        currentUserId = "user-id",
                        grantId = "grant-id",
                        ownerUserId = "user-id",
                        viewerUserId = "friend-id"
                    )
                )
            )
            upsertObservedTodos(
                listOf(
                    observedTodo(currentUserId = "user-id", observedTodoId = "purged", grantId = "grant-id"),
                    observedTodo(currentUserId = "user-id", observedTodoId = "kept", grantId = "other-grant")
                )
            )
        }
        val network = FakePersonVisibilityNetworkDataSource()
        val repository = repository(prefs = prefs, dao = dao, network = network)

        repository.revokeVisibilityGrant("friend-id").getOrThrow()

        assertThat(network.revokedGrantIds).containsExactly("grant-id")
        assertThat(dao.observeObservedTodos("user-id").first().map { it.observedTodoId })
            .containsExactly("kept")
        assertThat(dao.observeVisibilityGrants("user-id").first().single().status)
            .isEqualTo("REVOKED")
    }

    @Test
    fun syncObservedTodosCachesUpsertsDeletesPurgesAndCursor() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val dao = FakePersonVisibilityDao().apply {
            upsertObservedTodos(
                listOf(
                    observedTodo(currentUserId = "user-id", observedTodoId = "deleted", grantId = "grant-id"),
                    observedTodo(currentUserId = "user-id", observedTodoId = "purged", grantId = "purged-grant")
                )
            )
            upsertObservedSyncState(
                ObservedSyncStateEntity(
                    currentUserId = "user-id",
                    cursor = "cursor-1",
                    syncedAtEpochMillis = 100L
                )
            )
        }
        val network = FakePersonVisibilityNetworkDataSource()
        val repository = repository(prefs = prefs, dao = dao, network = network)

        repository.syncObservedTodos().getOrThrow()

        assertThat(network.lastSyncCursor).isEqualTo("cursor-1")
        assertThat(dao.observeObservedTodos("user-id").first().map { it.observedTodoId })
            .containsExactly("fresh")
        assertThat(dao.getObservedSyncState("user-id")?.cursor).isEqualTo("cursor-2")
        assertThat(repository.observeObservedTodos().first().single().todos.single().title)
            .isEqualTo("fresh todo")
    }

    private fun repository(
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        dao: FakePersonVisibilityDao = FakePersonVisibilityDao(),
        network: FakePersonVisibilityNetworkDataSource = FakePersonVisibilityNetworkDataSource()
    ) = PersonVisibilityRepositoryImpl(
        userPreferencesDataSource = prefs,
        personVisibilityNetworkDataSource = network,
        personVisibilityDao = dao,
        authSessionRefresher = AuthSessionRefresher(
            userPreferencesDataSource = prefs,
            authNetworkDataSource = FakeAuthNetworkDataSource()
        ),
        timeProvider = PersonVisibilityTimeProvider { 123_456L }
    )

    private fun authSession() = AuthSessionData(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        userId = "user-id",
        nickname = "neo",
        email = "neo@example.com",
        onboardingRequired = false
    )

    private class FakePreferencesDataSource : UserPreferencesDataSource {
        private val authSessionFlow = MutableStateFlow<AuthSessionData?>(null)
        override val authSession: Flow<AuthSessionData?> = authSessionFlow.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = MutableStateFlow(TodoFilter.ALL).asStateFlow()
        override val selectedTodoCategoryFilter: Flow<Long?> = MutableStateFlow<Long?>(null).asStateFlow()
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
            MutableStateFlow(TodoPriorityFilter.ALL).asStateFlow()
        override val todoSyncCursor: Flow<String?> = MutableStateFlow<String?>(null).asStateFlow()
        override val todoSyncHaltReason: Flow<String?> = MutableStateFlow<String?>(null).asStateFlow()

        override suspend fun saveAuthSession(session: AuthSessionData) {
            authSessionFlow.value = session
        }

        override suspend fun clearAuthSession() {
            authSessionFlow.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) = Unit
        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) = Unit
        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) = Unit
        override suspend fun setTodoSyncCursor(cursor: String?) = Unit
        override suspend fun setTodoSyncHaltReason(reason: String?) = Unit
        override suspend fun clearTodoSyncState() = Unit
    }

    private class FakeAuthNetworkDataSource : AuthNetworkDataSource {
        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession = session()
        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession = session()
        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ): NetworkAuthUserResponse = NetworkAuthUserResponse(session().user.copy(nickname = nickname))

        private fun session() = NetworkAuthSession(
            accessToken = "refreshed-access-token",
            refreshToken = "refreshed-refresh-token",
            user = NetworkAuthUser(
                id = "user-id",
                nickname = "neo",
                email = "neo@example.com",
                onboardingRequired = false
            )
        )
    }

    private class FakePersonVisibilityNetworkDataSource : PersonVisibilityNetworkDataSource {
        val createTokens = mutableListOf<String>()
        val revokedGrantIds = mutableListOf<String>()
        var lastCreateRequest: NetworkCreateVisibilityGrantRequest? = null
        var lastSyncCursor: String? = null

        override suspend fun getVisibilityGrants(accessToken: String): NetworkVisibilityGrantsResponse =
            NetworkVisibilityGrantsResponse(
                given = listOf(
                    networkGrant(
                        id = "grant-id",
                        ownerUserId = "user-id",
                        viewerUserId = "friend-id"
                    )
                )
            )

        override suspend fun createVisibilityGrant(
            accessToken: String,
            idempotencyKey: String,
            request: NetworkCreateVisibilityGrantRequest
        ): NetworkVisibilityGrant {
            createTokens += accessToken
            lastCreateRequest = request
            return networkGrant(id = "grant-id", ownerUserId = "user-id", viewerUserId = request.viewerUserId)
        }

        override suspend fun revokeVisibilityGrant(
            accessToken: String,
            idempotencyKey: String,
            grantId: String
        ): NetworkRevokeVisibilityGrantResponse {
            revokedGrantIds += grantId
            return NetworkRevokeVisibilityGrantResponse(
                grantId = grantId,
                status = "REVOKED",
                version = 2,
                revokedAt = "2026-05-17T00:01:00Z"
            )
        }

        override suspend fun syncObservedTodos(
            accessToken: String,
            cursor: String?,
            windowStart: String?,
            windowEnd: String?
        ): NetworkObservedTodoSyncResponse {
            lastSyncCursor = cursor
            return NetworkObservedTodoSyncResponse(
                items = listOf(
                    NetworkObservedTodo(
                        observedTodoId = "fresh",
                        sourceTodoId = "source-fresh",
                        grantId = "grant-id",
                        owner = NetworkObservedTodoOwner(id = "owner-id", nickname = "owner"),
                        title = "fresh todo",
                        dueDate = "2026-05-20",
                        status = "ACTIVE",
                        revision = "2",
                        updatedAt = "2026-05-17T00:00:00Z"
                    )
                ),
                deleted = listOf(NetworkDeletedObservedTodo("deleted", 2)),
                purgedGrantIds = listOf("purged-grant"),
                nextCursor = "cursor-2"
            )
        }

        private fun networkGrant(
            id: String,
            ownerUserId: String,
            viewerUserId: String,
            status: String = "ACTIVE"
        ) = NetworkVisibilityGrant(
            id = id,
            owner = NetworkVisibilityUser(id = ownerUserId, nickname = "owner"),
            viewer = NetworkVisibilityUser(id = viewerUserId, nickname = "viewer"),
            status = status,
            createdAt = "2026-05-17T00:00:00Z",
            updatedAt = "2026-05-17T00:00:00Z",
            version = 1
        )
    }

    private class FakePersonVisibilityDao : PersonVisibilityDao {
        private val grants = MutableStateFlow<List<VisibilityGrantEntity>>(emptyList())
        private val todos = MutableStateFlow<List<ObservedTodoEntity>>(emptyList())
        private var syncState: ObservedSyncStateEntity? = null

        override fun observeVisibilityGrants(currentUserId: String): Flow<List<VisibilityGrantEntity>> =
            grants.map { rows -> rows.filter { it.currentUserId == currentUserId } }

        override suspend fun getActiveGrant(
            currentUserId: String,
            ownerUserId: String,
            viewerUserId: String
        ): VisibilityGrantEntity? =
            grants.value.firstOrNull {
                it.currentUserId == currentUserId &&
                    it.ownerUserId == ownerUserId &&
                    it.viewerUserId == viewerUserId &&
                    it.status == "ACTIVE"
            }

        override fun observeObservedTodos(currentUserId: String): Flow<List<ObservedTodoEntity>> =
            todos.map { rows -> rows.filter { it.currentUserId == currentUserId } }

        override suspend fun getObservedSyncState(currentUserId: String): ObservedSyncStateEntity? =
            syncState?.takeIf { it.currentUserId == currentUserId }

        override suspend fun upsertVisibilityGrants(grants: List<VisibilityGrantEntity>) {
            this.grants.value = this.grants.value
                .filterNot { existing -> grants.any { it.currentUserId == existing.currentUserId && it.grantId == existing.grantId } } +
                grants
        }

        override suspend fun upsertObservedTodos(todos: List<ObservedTodoEntity>) {
            this.todos.value = this.todos.value
                .filterNot { existing ->
                    todos.any { it.currentUserId == existing.currentUserId && it.observedTodoId == existing.observedTodoId }
                } + todos
        }

        override suspend fun upsertObservedSyncState(state: ObservedSyncStateEntity) {
            syncState = state
        }

        override suspend fun deleteObservedTodos(currentUserId: String, observedTodoIds: List<String>) {
            todos.value = todos.value.filterNot {
                it.currentUserId == currentUserId && it.observedTodoId in observedTodoIds
            }
        }

        override suspend fun purgeObservedTodosByGrantId(currentUserId: String, grantId: String) {
            todos.value = todos.value.filterNot {
                it.currentUserId == currentUserId && it.grantId == grantId
            }
        }

        override suspend fun purgeObservedTodosByCurrentUser(currentUserId: String) {
            todos.value = todos.value.filterNot { it.currentUserId == currentUserId }
        }

        override suspend fun deleteVisibilityGrantsByCurrentUser(currentUserId: String) {
            grants.value = grants.value.filterNot { it.currentUserId == currentUserId }
        }
    }

    private fun visibilityGrant(
        currentUserId: String,
        grantId: String,
        ownerUserId: String,
        viewerUserId: String,
        status: String = "ACTIVE"
    ) = VisibilityGrantEntity(
        currentUserId = currentUserId,
        grantId = grantId,
        ownerUserId = ownerUserId,
        viewerUserId = viewerUserId,
        status = status,
        version = 1,
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 200L,
        revokedAtEpochMillis = null
    )

    private fun observedTodo(
        currentUserId: String,
        observedTodoId: String,
        grantId: String
    ) = ObservedTodoEntity(
        currentUserId = currentUserId,
        observedTodoId = observedTodoId,
        sourceTodoId = "source-$observedTodoId",
        grantId = grantId,
        ownerUserId = "owner-id",
        ownerNickname = "owner",
        ownerAvatarUrl = null,
        title = observedTodoId,
        dueDateEpochDay = null,
        dueTimeMinutes = null,
        isDone = false,
        recurrenceOccurrenceId = null,
        projectionVersion = 1,
        updatedAtEpochMillis = 100L,
        cacheUpdatedAtEpochMillis = 200L
    )
}
