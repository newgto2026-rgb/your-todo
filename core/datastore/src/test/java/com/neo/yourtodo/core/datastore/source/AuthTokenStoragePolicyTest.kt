package com.neo.yourtodo.core.datastore.source

import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import org.junit.Assert.assertThrows
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
    fun readTokensResultFallsBackToLegacyPlaintextAndPreservesEncryptedFailure() {
        val failingPolicy = AuthTokenStoragePolicy(
            FakeAuthTokenCipher(decryptFailure = AuthTokenCipherFailure(
                operation = AuthTokenCipherOperation.DECRYPT,
                type = AuthTokenCipherFailureType.DECRYPTION,
                message = "Stored access token could not be decrypted"
            ))
        )
        val preferences = preferencesOf(
            AUTH_ENCRYPTED_ACCESS_TOKEN to "encrypted:access-token",
            AUTH_ENCRYPTED_REFRESH_TOKEN to "encrypted:refresh-token",
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        val result = failingPolicy.readTokensResult(preferences)

        assertThat(result).isEqualTo(
            AuthTokenReadResult.LegacyFallback(
                tokens = AuthTokenPair(
                    accessToken = "legacy-access-token",
                    refreshToken = "legacy-refresh-token"
                ),
                encryptedFailure = AuthTokenReadFailure(
                    field = AuthTokenField.ACCESS,
                    failure = AuthTokenCipherFailure(
                        operation = AuthTokenCipherOperation.DECRYPT,
                        type = AuthTokenCipherFailureType.DECRYPTION,
                        message = "Stored access token could not be decrypted"
                    )
                )
            )
        )
        assertThat(failingPolicy.readTokens(preferences)).isEqualTo(
            AuthTokenPair(
                accessToken = "legacy-access-token",
                refreshToken = "legacy-refresh-token"
            )
        )
    }

    @Test
    fun readTokensResultReturnsFailureWhenEncryptedTokensCannotDecryptWithoutFallback() {
        val failingPolicy = AuthTokenStoragePolicy(
            FakeAuthTokenCipher(decryptFailure = AuthTokenCipherFailure(
                operation = AuthTokenCipherOperation.DECRYPT,
                type = AuthTokenCipherFailureType.INVALID_FORMAT,
                message = "Stored token format is not recognized"
            ))
        )
        val preferences = preferencesOf(
            AUTH_ENCRYPTED_ACCESS_TOKEN to "not-encrypted",
            AUTH_ENCRYPTED_REFRESH_TOKEN to "encrypted:refresh-token"
        )

        assertThat(failingPolicy.readTokensResult(preferences)).isEqualTo(
            AuthTokenReadResult.Failure(
                AuthTokenReadFailure(
                    field = AuthTokenField.ACCESS,
                    failure = AuthTokenCipherFailure(
                        operation = AuthTokenCipherOperation.DECRYPT,
                        type = AuthTokenCipherFailureType.INVALID_FORMAT,
                        message = "Stored token format is not recognized"
                    )
                )
            )
        )
        assertThat(failingPolicy.readTokens(preferences)).isNull()
    }

    @Test
    fun saveTokensPropagatesTypedEncryptionFailure() {
        val failure = AuthTokenCipherFailure(
            operation = AuthTokenCipherOperation.ENCRYPT,
            type = AuthTokenCipherFailureType.ENCRYPTION,
            message = "Auth token encryption failed"
        )
        val failingPolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher(encryptFailure = failure))
        val preferences = preferencesOf().toMutablePreferences()

        val thrown = assertThrows(AuthTokenCipherException::class.java) {
            failingPolicy.saveTokens(
                preferences = preferences,
                tokens = AuthTokenPair(
                    accessToken = "access-token",
                    refreshToken = "refresh-token"
                )
            )
        }

        assertThat(thrown.failure).isEqualTo(failure)
        assertThat(preferences.toPreferences().asMap()).isEmpty()
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
        val failure = AuthTokenCipherFailure(
            operation = AuthTokenCipherOperation.ENCRYPT,
            type = AuthTokenCipherFailureType.ENCRYPTION,
            message = "Auth token encryption failed"
        )
        val failingPolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher(encryptFailure = failure))
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
    fun migrateLegacyPlaintextTokensResultPreservesEncryptionFailure() {
        val failure = AuthTokenCipherFailure(
            operation = AuthTokenCipherOperation.ENCRYPT,
            type = AuthTokenCipherFailureType.KEY_GENERATION,
            message = "Android Keystore key generation failed for auth token storage"
        )
        val failingPolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher(encryptFailure = failure))
        val preferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )

        val result = failingPolicy.migrateLegacyPlaintextTokensResult(preferences)

        assertThat(result).isEqualTo(
            AuthTokenMigrationResult(
                preferences = preferences,
                failure = failure
            )
        )
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
        private val encryptFailure: AuthTokenCipherFailure? = null,
        private val decryptFailure: AuthTokenCipherFailure? = null
    ) : AuthTokenCipher {
        override fun encrypt(plainText: String): String {
            encryptFailure?.let { throw AuthTokenCipherException(it) }
            return "encrypted:$plainText"
        }

        override fun decrypt(cipherText: String): AuthTokenDecryptResult {
            decryptFailure?.let { return AuthTokenDecryptResult.Failure(it) }
            val plainText = cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
            return if (plainText == null) {
                AuthTokenDecryptResult.Failure(
                    AuthTokenCipherFailure(
                        operation = AuthTokenCipherOperation.DECRYPT,
                        type = AuthTokenCipherFailureType.INVALID_FORMAT,
                        message = "Stored token format is not recognized"
                    )
                )
            } else {
                AuthTokenDecryptResult.Success(plainText)
            }
        }
    }
}
