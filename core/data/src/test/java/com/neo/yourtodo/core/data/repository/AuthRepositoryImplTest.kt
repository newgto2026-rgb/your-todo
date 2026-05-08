package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
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
        val repository = AuthRepositoryImpl(network, preferences)

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
            preferencesDataSource = FakeUserPreferencesDataSource()
        )
        repository.signInWithGoogle("google-token")

        repository.signOut()

        assertThat(repository.authSession.first()).isNull()
    }

    @Test
    fun completeNicknameOnboardingUpdatesSavedSessionUser() = runTest {
        val network = FakeAuthNetworkDataSource()
        val preferences = FakeUserPreferencesDataSource()
        val repository = AuthRepositoryImpl(network, preferences)
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

    private class FakeUserPreferencesDataSource : UserPreferencesDataSource {
        private val savedAuthSession = MutableStateFlow<AuthSessionData?>(null)

        override val authSession: Flow<AuthSessionData?> = savedAuthSession
        override val selectedTodoFilter: Flow<TodoFilter> = flowOf(TodoFilter.ALL)
        override val selectedTodoCategoryFilter: Flow<Long?> = flowOf(null)
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
            flowOf(TodoPriorityFilter.ALL)

        override suspend fun saveAuthSession(session: AuthSessionData) {
            savedAuthSession.value = session
        }

        override suspend fun clearAuthSession() {
            savedAuthSession.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) = Unit
        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) = Unit
        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) = Unit
    }
}
