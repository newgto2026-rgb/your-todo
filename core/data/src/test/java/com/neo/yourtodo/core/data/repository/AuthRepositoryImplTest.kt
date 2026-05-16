package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoWithChecklist
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthRepositoryImplTest {

    @Test
    fun signInWithGoogleSavesNetworkSession() = runTest {
        val network = FakeAuthNetworkDataSource()
        val preferences = FakeUserPreferencesDataSource()
        val repository = repository(network, preferences)

        val result = repository.signInWithGoogle("google-token")

        assertThat(result.isSuccess).isTrue()
        assertThat(network.lastIdToken).isEqualTo("google-token")
        assertThat(repository.authSession.first()?.accessToken).isEqualTo("access-token")
        assertThat(repository.authSession.first()?.user?.email).isEqualTo("neo@example.com")
    }

    @Test
    fun signOutClearsSavedSession() = runTest {
        val repository = AuthRepositoryImpl(
            networkDataSource = FakeAuthNetworkDataSource(),
            preferencesDataSource = FakeUserPreferencesDataSource(),
            todoDao = FakeTodoDao(),
            todoOutboxDao = FakeTodoOutboxDao(),
            assignedTodoDao = FakeAssignedTodoDao(),
            assignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
        )
        repository.signInWithGoogle("google-token")

        repository.signOut()

        assertThat(repository.authSession.first()).isNull()
    }

    @Test
    fun signOutClearsUserScopedLocalDataAndSyncState() = runTest {
        val preferences = FakeUserPreferencesDataSource()
        val todoDao = FakeTodoDao()
        val todoOutboxDao = FakeTodoOutboxDao()
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(
            preferences = preferences,
            todoDao = todoDao,
            todoOutboxDao = todoOutboxDao,
            assignedTodoDao = assignedTodoDao,
            assignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
        )
        repository.signInWithGoogle("google-token")
        preferences.setTodoSyncCursor("3")
        preferences.setTodoSyncHaltReason("AUTH_REQUIRED")

        repository.signOut()

        assertThat(todoOutboxDao.deletedOwnerUserId).isEqualTo("user-id")
        assertThat(todoDao.deletedOwnerUserId).isEqualTo("user-id")
        assertThat(assignedTodoDao.deletedOwnerUserId).isEqualTo("user-id")
        assertThat(preferences.todoSyncCursor.first()).isNull()
        assertThat(preferences.todoSyncHaltReason.first()).isNull()
        assertThat(repository.authSession.first()).isNull()
    }

    @Test
    fun signOutDeletesLocalOnlyTodosAndPreventsPreviousUserDataLeak() = runTest {
        val preferences = FakeUserPreferencesDataSource()
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 1L,
                    title = "user a local",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    ownerUserId = "user-a",
                    syncStatus = "LOCAL_ONLY"
                ),
                TodoEntity(
                    id = 2L,
                    title = "user a server",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    ownerUserId = "user-a",
                    syncStatus = "SYNCED",
                    serverId = "server-a"
                ),
                TodoEntity(
                    id = 3L,
                    title = "user b server",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    ownerUserId = "user-b",
                    syncStatus = "SYNCED",
                    serverId = "server-b"
                )
            )
        }
        val todoOutboxDao = FakeTodoOutboxDao().apply {
            seed(
                TodoOutboxEntity(
                    id = 1L,
                    ownerUserId = "user-a",
                    clientMutationId = "mutation-a",
                    todoLocalId = 2L,
                    serverId = "server-a",
                    clientId = "client-a",
                    type = "UPDATE",
                    payloadJson = "{}",
                    createdAt = 1L
                ),
                TodoOutboxEntity(
                    id = 2L,
                    ownerUserId = "user-b",
                    clientMutationId = "mutation-b",
                    todoLocalId = 3L,
                    serverId = "server-b",
                    clientId = "client-b",
                    type = "UPDATE",
                    payloadJson = "{}",
                    createdAt = 1L
                )
            )
        }
        val repository = repository(
            preferences = preferences,
            todoDao = todoDao,
            todoOutboxDao = todoOutboxDao
        )
        preferences.saveAuthSession(
            AuthSessionData(
                accessToken = "access-a",
                refreshToken = "refresh-a",
                userId = "user-a",
                nickname = "neo-a",
                email = "a@example.com",
                onboardingRequired = false
            )
        )
        preferences.setTodoSyncCursor("10")

        repository.signOut()

        assertThat(todoDao.items.map { it.title }).containsExactly("user b server")
        assertThat(todoOutboxDao.items.map { it.ownerUserId }).containsExactly("user-b")
        assertThat(preferences.todoSyncCursor.first()).isNull()
    }

    @Test
    fun signInTokenStorageFailureClearsOnlyAuthSessionAndPreservesLocalData() = runTest {
        val preferences = FakeUserPreferencesDataSource(failSaveAuthSession = true).apply {
            saveAuthSessionIgnoringFailure(
                AuthSessionData(
                    accessToken = "old-access",
                    refreshToken = "old-refresh",
                    userId = "user-a",
                    nickname = "neo-a",
                    email = "a@example.com",
                    onboardingRequired = false
                )
            )
            setTodoSyncCursor("cursor-a")
            setTodoSyncHaltReason("AUTH_REQUIRED")
        }
        val todoDao = FakeTodoDao().apply {
            seed(
                TodoEntity(
                    id = 1L,
                    title = "user a local",
                    isDone = false,
                    dueDateEpochDay = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    categoryId = null,
                    ownerUserId = "user-a",
                    syncStatus = "LOCAL_ONLY"
                )
            )
        }
        val todoOutboxDao = FakeTodoOutboxDao().apply {
            seed(
                TodoOutboxEntity(
                    id = 1L,
                    ownerUserId = "user-a",
                    clientMutationId = "mutation-a",
                    todoLocalId = 1L,
                    serverId = null,
                    clientId = "client-a",
                    type = "CREATE",
                    payloadJson = "{}",
                    createdAt = 1L
                )
            )
        }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(
            preferences = preferences,
            todoDao = todoDao,
            todoOutboxDao = todoOutboxDao,
            assignedTodoDao = assignedTodoDao
        )

        val result = repository.signInWithGoogle("google-token")

        assertThat(result.isFailure).isTrue()
        assertThat(preferences.authSession.first()).isNull()
        assertThat(preferences.todoSyncCursor.first()).isEqualTo("cursor-a")
        assertThat(preferences.todoSyncHaltReason.first()).isEqualTo("AUTH_REQUIRED")
        assertThat(todoDao.items.map { it.title }).containsExactly("user a local")
        assertThat(todoOutboxDao.items.map { it.clientMutationId }).containsExactly("mutation-a")
        assertThat(assignedTodoDao.deletedOwnerUserId).isNull()
    }

    @Test
    fun completeNicknameOnboardingUpdatesSavedSessionUser() = runTest {
        val network = FakeAuthNetworkDataSource()
        val preferences = FakeUserPreferencesDataSource()
        val repository = repository(network, preferences)
        repository.signInWithGoogle("google-token")

        val result = repository.completeNicknameOnboarding("태윤")

        assertThat(result.isSuccess).isTrue()
        assertThat(network.lastAccessToken).isEqualTo("access-token")
        assertThat(network.lastNickname).isEqualTo("태윤")
        assertThat(repository.authSession.first()?.accessToken).isEqualTo("access-token")
        assertThat(repository.authSession.first()?.refreshToken).isEqualTo("refresh-token")
        assertThat(repository.authSession.first()?.user?.nickname).isEqualTo("태윤")
        assertThat(repository.authSession.first()?.user?.onboardingRequired).isFalse()
    }

    private fun repository(
        network: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource(),
        preferences: FakeUserPreferencesDataSource = FakeUserPreferencesDataSource(),
        todoDao: FakeTodoDao = FakeTodoDao(),
        todoOutboxDao: FakeTodoOutboxDao = FakeTodoOutboxDao(),
        assignedTodoDao: FakeAssignedTodoDao = FakeAssignedTodoDao(),
        assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
    ): AuthRepositoryImpl =
        AuthRepositoryImpl(
            networkDataSource = network,
            preferencesDataSource = preferences,
            todoDao = todoDao,
            todoOutboxDao = todoOutboxDao,
            assignedTodoDao = assignedTodoDao,
            assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker
        )

    private class FakeAuthNetworkDataSource : AuthNetworkDataSource {
        var lastIdToken: String? = null
        var lastAccessToken: String? = null
        var lastNickname: String? = null

        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession {
            lastIdToken = idToken
            return NetworkAuthSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = null,
                    email = "neo@example.com",
                    onboardingRequired = true
                )
            )
        }

        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession =
            NetworkAuthSession(
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = "neo",
                    email = "neo@example.com",
                    onboardingRequired = false
                )
            )

        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ): NetworkAuthUserResponse {
            lastAccessToken = accessToken
            lastNickname = nickname
            return NetworkAuthUserResponse(
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = nickname,
                    email = "neo@example.com",
                    onboardingRequired = false
                )
            )
        }
    }

    private class FakeUserPreferencesDataSource(
        private val failSaveAuthSession: Boolean = false
    ) : UserPreferencesDataSource {
        private val savedAuthSession = MutableStateFlow<AuthSessionData?>(null)
        private val syncCursor = MutableStateFlow<String?>(null)
        private val syncHaltReason = MutableStateFlow<String?>(null)

        override val authSession: Flow<AuthSessionData?> = savedAuthSession
        override val selectedTodoFilter: Flow<TodoFilter> = flowOf(TodoFilter.ALL)
        override val selectedTodoCategoryFilter: Flow<Long?> = flowOf(null)
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
            flowOf(TodoPriorityFilter.ALL)
        override val todoSyncCursor: Flow<String?> = syncCursor
        override val todoSyncHaltReason: Flow<String?> = syncHaltReason

        override suspend fun saveAuthSession(session: AuthSessionData) {
            if (failSaveAuthSession) error("Auth token storage failed")
            savedAuthSession.value = session
        }

        fun saveAuthSessionIgnoringFailure(session: AuthSessionData) {
            savedAuthSession.value = session
        }

        override suspend fun clearAuthSession() {
            savedAuthSession.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) = Unit
        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) = Unit
        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) = Unit
        override suspend fun setTodoSyncCursor(cursor: String?) {
            syncCursor.value = cursor
        }

        override suspend fun setTodoSyncHaltReason(reason: String?) {
            syncHaltReason.value = reason
        }

        override suspend fun clearTodoSyncState() {
            syncCursor.value = null
            syncHaltReason.value = null
        }
    }

    private class FakeTodoDao : TodoDao {
        var deletedOwnerUserId: String? = null
        val items = mutableListOf<TodoEntity>()

        fun seed(vararg todos: TodoEntity) {
            items.clear()
            items.addAll(todos)
        }

        override fun observeTodos(): Flow<List<TodoEntity>> = flowOf(items)
        override fun observeTodosByDueDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TodoEntity>> =
            flowOf(emptyList())

        override suspend fun insert(todo: TodoEntity): Long {
            items.add(todo)
            return todo.id
        }

        override suspend fun update(todo: TodoEntity) {
            items.replaceAll { if (it.id == todo.id) todo else it }
        }

        override suspend fun delete(todo: TodoEntity) {
            items.removeAll { it.id == todo.id }
        }

        override suspend fun getTodoById(id: Long): TodoEntity? = items.firstOrNull { it.id == id }
        override suspend fun getTodosByIds(ids: List<Long>): List<TodoEntity> = items.filter { it.id in ids }
        override suspend fun getTodoByServerId(ownerUserId: String, serverId: String): TodoEntity? = null
        override suspend fun getTodoByClientId(ownerUserId: String, clientId: String): TodoEntity? = null
        override suspend fun deleteSyncedTodosByOwner(ownerUserId: String) {
            deletedOwnerUserId = ownerUserId
            items.removeAll { it.ownerUserId == ownerUserId && it.syncStatus != "LOCAL_ONLY" }
        }
        override suspend fun deleteByOwner(ownerUserId: String) {
            deletedOwnerUserId = ownerUserId
            items.removeAll { it.ownerUserId == ownerUserId }
        }
        override suspend fun getTodosWithActiveReminder(): List<TodoEntity> = emptyList()
    }

    private class FakeTodoOutboxDao : TodoOutboxDao {
        var deletedOwnerUserId: String? = null
        val items = mutableListOf<TodoOutboxEntity>()

        fun seed(vararg outbox: TodoOutboxEntity) {
            items.clear()
            items.addAll(outbox)
        }

        override suspend fun getPendingMutations(ownerUserId: String): List<TodoOutboxEntity> =
            items.filter { it.ownerUserId == ownerUserId }

        override suspend fun getByTodoLocalId(todoLocalId: Long): TodoOutboxEntity? =
            items.firstOrNull { it.todoLocalId == todoLocalId }

        override suspend fun insert(outbox: TodoOutboxEntity): Long {
            items.add(outbox)
            return outbox.id
        }

        override suspend fun update(outbox: TodoOutboxEntity) {
            items.replaceAll { if (it.id == outbox.id) outbox else it }
        }

        override suspend fun delete(outbox: TodoOutboxEntity) {
            items.removeAll { it.id == outbox.id }
        }

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
        }

        override suspend fun deleteByTodoLocalId(todoLocalId: Long) {
            items.removeAll { it.todoLocalId == todoLocalId }
        }

        override suspend fun deleteByOwner(ownerUserId: String) {
            deletedOwnerUserId = ownerUserId
            items.removeAll { it.ownerUserId == ownerUserId }
        }
    }

    private class FakeAssignedTodoDao : AssignedTodoDao {
        var deletedOwnerUserId: String? = null

        override fun observeReceivedAssignedTodos(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> = flowOf(emptyList())

        override fun observeSentAssignedTodos(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> = flowOf(emptyList())

        override fun observeSentAssignedTodosByFriend(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> = flowOf(emptyList())

        override fun observeReceivedAssignedTodosByFriend(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> = flowOf(emptyList())

        override suspend fun getAssignedTodoById(ownerUserId: String, id: String): AssignedTodoEntity? = null

        override fun observeReceivedFeedCacheUpdatedAt(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<Long?> = flowOf(null)

        override fun observeSentFeedCacheUpdatedAt(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<Long?> = flowOf(null)

        override fun observeSentFriendFeedCacheUpdatedAt(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<Long?> = flowOf(null)

        override fun observeReceivedFriendFeedCacheUpdatedAt(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<Long?> = flowOf(null)

        override suspend fun deleteByOwner(ownerUserId: String) {
            deletedOwnerUserId = ownerUserId
        }

        override suspend fun upsertAssignedTodos(items: List<AssignedTodoEntity>) = Unit
        override suspend fun upsertChecklistItems(items: List<AssignedTodoChecklistItemEntity>) = Unit
        override suspend fun deleteChecklistItems(assignedTodoCacheKeys: List<String>) = Unit
        override suspend fun deleteReceivedByStatuses(ownerUserId: String, statuses: List<String>) = Unit
        override suspend fun hideReceivedByStatuses(ownerUserId: String, statuses: List<String>) = Unit
        override suspend fun hideReceivedFromTaskSurface(ownerUserId: String, id: String) = Unit
        override suspend fun deleteReceivedByStatusesExcept(
            ownerUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) = Unit
        override suspend fun hideReceivedByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>) =
            Unit
        override suspend fun deleteSentByStatuses(ownerUserId: String, statuses: List<String>) = Unit
        override suspend fun deleteSentByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>) =
            Unit
        override suspend fun deleteSentByFriendAndStatuses(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ) = Unit
        override suspend fun deleteSentByFriendAndStatusesExcept(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) = Unit
        override suspend fun deleteReceivedByFriendAndStatuses(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ) = Unit
        override suspend fun deleteReceivedByFriendAndStatusesExcept(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) = Unit
    }
}
