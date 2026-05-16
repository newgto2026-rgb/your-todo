package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val networkDataSource: AuthNetworkDataSource,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val todoDao: TodoDao,
    private val todoOutboxDao: TodoOutboxDao,
    private val assignedTodoDao: AssignedTodoDao,
    private val assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker =
        AssignmentFeedFreshnessTracker()
) : AuthRepository {

    override val authSession: Flow<AuthSession?> =
        preferencesDataSource.authSession.map { session -> session?.toDomain() }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
        runCatching {
            val networkSession = networkDataSource.signInWithGoogle(idToken)
            val authSession = networkSession.toDomain()
            preferencesDataSource.saveAuthSession(authSession.toData())
            authSession
        }

    override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
        runCatching {
            val currentSession = preferencesDataSource.authSession.first()
                ?: throw IllegalStateException("Auth session is required.")
            val networkUser = networkDataSource
                .completeNicknameOnboarding(
                    accessToken = currentSession.accessToken,
                    nickname = nickname
                )
                .user
            val authSession = currentSession
                .copy(
                    userId = networkUser.id,
                    nickname = networkUser.nickname,
                    email = networkUser.email,
                    onboardingRequired = networkUser.onboardingRequired
                )
                .toDomain()
            preferencesDataSource.saveAuthSession(authSession.toData())
            authSession
        }

    override suspend fun signOut() {
        preferencesDataSource.authSession.first()?.let { session ->
            todoOutboxDao.deleteByOwner(session.userId)
            todoDao.deleteSyncedTodosByOwner(session.userId)
            assignedTodoDao.deleteByOwner(session.userId)
        }
        preferencesDataSource.clearTodoSyncState()
        assignmentFeedFreshnessTracker.clear()
        preferencesDataSource.clearAuthSession()
    }

    private fun NetworkAuthSession.toDomain(): AuthSession =
        AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = AuthUser(
                id = user.id,
                nickname = user.nickname,
                email = user.email,
                onboardingRequired = user.onboardingRequired
            )
        )

    private fun AuthSessionData.toDomain(): AuthSession =
        AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = AuthUser(
                id = userId,
                nickname = nickname,
                email = email,
                onboardingRequired = onboardingRequired
            )
        )

    private fun AuthSession.toData(): AuthSessionData =
        AuthSessionData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            nickname = user.nickname,
            email = user.email,
            onboardingRequired = user.onboardingRequired
        )
}
