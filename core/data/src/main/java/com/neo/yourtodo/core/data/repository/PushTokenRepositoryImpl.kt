package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.push.NetworkDeletePushTokenRequest
import com.neo.yourtodo.core.network.push.NetworkPushTokenRequest
import com.neo.yourtodo.core.network.push.PushAuthRequiredException
import com.neo.yourtodo.core.network.push.PushNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class PushTokenRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val pushNetworkDataSource: PushNetworkDataSource,
    authNetworkDataSource: AuthNetworkDataSource,
    private val authSessionRefresher: AuthSessionRefresher =
        AuthSessionRefresher(userPreferencesDataSource, authNetworkDataSource),
    private val assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker =
        AssignmentFeedFreshnessTracker()
) : PushTokenRepository {
    override suspend fun saveCurrentToken(token: String): Result<Unit> =
        runCatching {
            userPreferencesDataSource.setPushCurrentToken(token)
        }

    override suspend fun registerCurrentToken(): Result<Unit> =
        runCatching {
            val token = userPreferencesDataSource.pushCurrentToken.first()
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatching
            val session = currentSession() ?: return@runCatching

            authenticatedRequest(session) { accessToken ->
                pushNetworkDataSource.upsertPushToken(
                    accessToken = accessToken,
                    request = NetworkPushTokenRequest(
                        platform = ANDROID_PLATFORM,
                        token = token
                    )
                )
            }
            userPreferencesDataSource.setPushRegisteredToken(token)
        }

    override suspend fun deleteRegisteredToken(): Result<Unit> =
        runCatching {
            val token = userPreferencesDataSource.pushRegisteredToken.first()
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatching
            val session = userPreferencesDataSource.authSession.first()
                ?: return@runCatching userPreferencesDataSource.setPushRegisteredToken(null)

            runCatching {
                authenticatedRequest(session) { accessToken ->
                    pushNetworkDataSource.deletePushToken(
                        accessToken = accessToken,
                        request = NetworkDeletePushTokenRequest(token = token)
                    )
                }
            }
            userPreferencesDataSource.setPushRegisteredToken(null)
        }

    private suspend fun currentSession() =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }

    private suspend fun <T> authenticatedRequest(
        session: AuthSessionData,
        block: suspend (String) -> T
    ): T =
        try {
            block(session.accessToken)
        } catch (throwable: PushAuthRequiredException) {
            val refreshedSession = authSessionRefresher.refresh(session.refreshToken)
                ?: authRequired()
            try {
                block(refreshedSession.accessToken)
            } catch (retryThrowable: PushAuthRequiredException) {
                authRequired()
            }
        }

    private suspend fun authRequired(): Nothing {
        assignmentFeedFreshnessTracker.clear()
        userPreferencesDataSource.clearAuthSession()
        throw AuthRequiredException()
    }

    private companion object {
        const val ANDROID_PLATFORM = "ANDROID"
    }
}
