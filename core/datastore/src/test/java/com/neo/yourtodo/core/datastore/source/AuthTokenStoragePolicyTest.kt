package com.neo.yourtodo.core.datastore.source

import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import org.junit.Test

class AuthTokenStoragePolicyTest {

    private val cipher = FakeAuthTokenCipher()
    private val policy = AuthTokenStoragePolicy(cipher)

    @Test
    fun saveTokensWritesEncryptedValuesAndClearsLegacyPlaintext() {
        val preferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        ).toMutablePreferences()

        policy.saveTokens(
            preferences = preferences,
            tokens = AuthTokenPair(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )

        val saved = preferences.toPreferences()
        assertThat(saved[AUTH_ENCRYPTED_ACCESS_TOKEN]).isEqualTo("encrypted:access-token")
        assertThat(saved[AUTH_ENCRYPTED_REFRESH_TOKEN]).isEqualTo("encrypted:refresh-token")
        assertThat(saved[AUTH_ACCESS_TOKEN]).isNull()
        assertThat(saved[AUTH_REFRESH_TOKEN]).isNull()
    }

    @Test
    fun readTokensPrefersEncryptedValuesOverLegacyPlaintext() {
        val preferences = preferencesOf(
            AUTH_ENCRYPTED_ACCESS_TOKEN to "encrypted:access-token",
            AUTH_ENCRYPTED_REFRESH_TOKEN to "encrypted:refresh-token",
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        assertThat(policy.readTokens(preferences)).isEqualTo(
            AuthTokenPair(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )
    }

    @Test
    fun readTokensFallsBackToLegacyPlaintextForBackwardCompatibility() {
        val preferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        assertThat(policy.readTokens(preferences)).isEqualTo(
            AuthTokenPair(
                accessToken = "legacy-access-token",
                refreshToken = "legacy-refresh-token"
            )
        )
    }

    @Test
    fun migrateLegacyPlaintextTokensEncryptsAndClearsPlaintext() {
        val preferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        val migrated = policy.migrateLegacyPlaintextTokens(preferences)

        assertThat(migrated[AUTH_ENCRYPTED_ACCESS_TOKEN]).isEqualTo("encrypted:legacy-access-token")
        assertThat(migrated[AUTH_ENCRYPTED_REFRESH_TOKEN]).isEqualTo("encrypted:legacy-refresh-token")
        assertThat(migrated[AUTH_ACCESS_TOKEN]).isNull()
        assertThat(migrated[AUTH_REFRESH_TOKEN]).isNull()
    }

    @Test
    fun migrateLegacyPlaintextTokensKeepsLegacyWhenEncryptionFails() {
        val failingPolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher(failEncrypt = true))
        val preferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        val migrated = failingPolicy.migrateLegacyPlaintextTokens(preferences)

        assertThat(migrated[AUTH_ACCESS_TOKEN]).isEqualTo("legacy-access-token")
        assertThat(migrated[AUTH_REFRESH_TOKEN]).isEqualTo("legacy-refresh-token")
        assertThat(migrated[AUTH_ENCRYPTED_ACCESS_TOKEN]).isNull()
        assertThat(migrated[AUTH_ENCRYPTED_REFRESH_TOKEN]).isNull()
    }

    @Test
    fun clearTokensRemovesEncryptedAndLegacyValues() {
        val preferences = preferencesOf(
            AUTH_ENCRYPTED_ACCESS_TOKEN to "encrypted:access-token",
            AUTH_ENCRYPTED_REFRESH_TOKEN to "encrypted:refresh-token",
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        ).toMutablePreferences()

        policy.clearTokens(preferences)

        val cleared = preferences.toPreferences()
        assertThat(cleared[AUTH_ENCRYPTED_ACCESS_TOKEN]).isNull()
        assertThat(cleared[AUTH_ENCRYPTED_REFRESH_TOKEN]).isNull()
        assertThat(cleared[AUTH_ACCESS_TOKEN]).isNull()
        assertThat(cleared[AUTH_REFRESH_TOKEN]).isNull()
    }

    private class FakeAuthTokenCipher(
        private val failEncrypt: Boolean = false
    ) : AuthTokenCipher {
        override fun encrypt(plainText: String): String {
            if (failEncrypt) error("Encryption failed")
            return "encrypted:$plainText"
        }

        override fun decrypt(cipherText: String): String? =
            cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
    }
}
