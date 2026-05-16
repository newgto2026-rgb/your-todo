package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AuthSessionRefresher @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val authNetworkDataSource: AuthNetworkDataSource,
    private val assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(refreshToken: String): AuthSessionData? =
        refreshMutex.withLock {
            val currentSession = userPreferencesDataSource.authSession.first()
            if (currentSession == null) {
                assignmentFeedFreshnessTracker.clear()
                userPreferencesDataSource.clearAssignmentFeedRefreshTimes()
                return@withLock null
            }
            if (currentSession.refreshToken != refreshToken) {
                return@withLock currentSession
            }

            try {
                authNetworkDataSource.refreshSession(refreshToken)
                    .toAuthSessionData()
                    .also { userPreferencesDataSource.saveAuthSession(it) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                assignmentFeedFreshnessTracker.clear()
                userPreferencesDataSource.clearAssignmentFeedRefreshTimes()
                userPreferencesDataSource.clearAuthSession()
                null
            }
        }

    private fun NetworkAuthSession.toAuthSessionData() =
        AuthSessionData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            nickname = user.nickname,
            email = user.email,
            onboardingRequired = user.onboardingRequired
        )
}
