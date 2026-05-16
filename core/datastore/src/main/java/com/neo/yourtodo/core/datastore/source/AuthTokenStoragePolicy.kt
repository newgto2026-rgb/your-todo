package com.neo.yourtodo.core.datastore.source

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import javax.inject.Inject
import javax.inject.Singleton

data class AuthTokenPair(
    val accessToken: String,
    val refreshToken: String
)

sealed interface AuthTokenReadResult {
    data class Encrypted(val tokens: AuthTokenPair) : AuthTokenReadResult

    data class LegacyFallback(
        val tokens: AuthTokenPair,
        val encryptedFailure: AuthTokenReadFailure?
    ) : AuthTokenReadResult

    data class Failure(val failure: AuthTokenReadFailure) : AuthTokenReadResult

    data object NoTokens : AuthTokenReadResult
}

data class AuthTokenReadFailure(
    val field: AuthTokenField,
    val failure: AuthTokenCipherFailure
)

data class AuthTokenMigrationResult(
    val preferences: Preferences,
    val failure: AuthTokenCipherFailure?
)

enum class AuthTokenField {
    ACCESS,
    REFRESH
}

@Singleton
class AuthTokenStoragePolicy @Inject constructor(
    private val cipher: AuthTokenCipher
) {

    fun readTokens(preferences: Preferences): AuthTokenPair? =
        readTokensResult(
            encryptedAccessToken = preferences[AUTH_ENCRYPTED_ACCESS_TOKEN],
            encryptedRefreshToken = preferences[AUTH_ENCRYPTED_REFRESH_TOKEN],
            legacyAccessToken = preferences[AUTH_ACCESS_TOKEN],
            legacyRefreshToken = preferences[AUTH_REFRESH_TOKEN]
        ).tokensOrNull()

    fun readTokens(
        encryptedAccessToken: String?,
        encryptedRefreshToken: String?,
        legacyAccessToken: String?,
        legacyRefreshToken: String?
    ): AuthTokenPair? =
        readTokensResult(
            encryptedAccessToken = encryptedAccessToken,
            encryptedRefreshToken = encryptedRefreshToken,
            legacyAccessToken = legacyAccessToken,
            legacyRefreshToken = legacyRefreshToken
        ).tokensOrNull()

    fun readTokensResult(preferences: Preferences): AuthTokenReadResult =
        readTokensResult(
            encryptedAccessToken = preferences[AUTH_ENCRYPTED_ACCESS_TOKEN],
            encryptedRefreshToken = preferences[AUTH_ENCRYPTED_REFRESH_TOKEN],
            legacyAccessToken = preferences[AUTH_ACCESS_TOKEN],
            legacyRefreshToken = preferences[AUTH_REFRESH_TOKEN]
        )

    fun readTokensResult(
        encryptedAccessToken: String?,
        encryptedRefreshToken: String?,
        legacyAccessToken: String?,
        legacyRefreshToken: String?
    ): AuthTokenReadResult {
        val encryptedResult = readEncryptedTokens(encryptedAccessToken, encryptedRefreshToken)
        if (encryptedResult is AuthTokenReadResult.Encrypted) {
            return encryptedResult
        }

        val legacyTokens = readLegacyPlaintextTokens(legacyAccessToken, legacyRefreshToken)
        if (legacyTokens != null) {
            val encryptedFailure = (encryptedResult as? AuthTokenReadResult.Failure)?.failure
            return AuthTokenReadResult.LegacyFallback(
                tokens = legacyTokens,
                encryptedFailure = encryptedFailure
            )
        }

        return encryptedResult
    }

    fun saveTokens(preferences: MutablePreferences, tokens: AuthTokenPair) {
        val encryptedAccessToken = cipher.encrypt(tokens.accessToken)
        val encryptedRefreshToken = cipher.encrypt(tokens.refreshToken)

        preferences[AUTH_ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
        preferences[AUTH_ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
        clearLegacyPlaintextTokens(preferences)
    }

    fun clearTokens(preferences: MutablePreferences) {
        preferences.remove(AUTH_ENCRYPTED_ACCESS_TOKEN)
        preferences.remove(AUTH_ENCRYPTED_REFRESH_TOKEN)
        clearLegacyPlaintextTokens(preferences)
    }

    fun shouldMigrateLegacyPlaintextTokens(preferences: Preferences): Boolean =
        hasLegacyPlaintextToken(preferences)

    fun migrateLegacyPlaintextTokens(preferences: Preferences): Preferences {
        return migrateLegacyPlaintextTokensResult(preferences).preferences
    }

    fun migrateLegacyPlaintextTokensResult(preferences: Preferences): AuthTokenMigrationResult {
        val migrated = preferences.toMutablePreferences()
        val encryptedTokens = readEncryptedTokens(preferences)
        if (encryptedTokens is AuthTokenReadResult.Encrypted) {
            clearLegacyPlaintextTokens(migrated)
            return AuthTokenMigrationResult(
                preferences = migrated.toPreferences(),
                failure = null
            )
        }

        val legacyTokens = readLegacyPlaintextTokens(preferences)
        if (legacyTokens == null) {
            clearLegacyPlaintextTokens(migrated)
            return AuthTokenMigrationResult(
                preferences = migrated.toPreferences(),
                failure = null
            )
        }

        return runCatching {
            saveTokens(migrated, legacyTokens)
            AuthTokenMigrationResult(
                preferences = migrated.toPreferences(),
                failure = null
            )
        }.getOrElse { exception ->
            AuthTokenMigrationResult(
                preferences = preferences,
                failure = (exception as? AuthTokenCipherException)?.failure
                    ?: AuthTokenCipherFailure(
                        operation = AuthTokenCipherOperation.ENCRYPT,
                        type = AuthTokenCipherFailureType.ENCRYPTION,
                        message = "Legacy auth token migration failed",
                        cause = exception
                    )
            )
        }
    }

    private fun readEncryptedTokens(preferences: Preferences): AuthTokenReadResult {
        val encryptedAccessToken = preferences[AUTH_ENCRYPTED_ACCESS_TOKEN]
        val encryptedRefreshToken = preferences[AUTH_ENCRYPTED_REFRESH_TOKEN]
        return readEncryptedTokens(encryptedAccessToken, encryptedRefreshToken)
    }

    private fun readEncryptedTokens(
        encryptedAccessToken: String?,
        encryptedRefreshToken: String?
    ): AuthTokenReadResult {
        if (encryptedAccessToken.isNullOrBlank() || encryptedRefreshToken.isNullOrBlank()) {
            return AuthTokenReadResult.NoTokens
        }

        val accessToken = when (
            val result = decryptToken(AuthTokenField.ACCESS, encryptedAccessToken)
        ) {
            is AuthTokenDecryptValue.Success -> result.plainText
            is AuthTokenDecryptValue.Failure -> return AuthTokenReadResult.Failure(result.failure)
        }
        val refreshToken = when (
            val result = decryptToken(AuthTokenField.REFRESH, encryptedRefreshToken)
        ) {
            is AuthTokenDecryptValue.Success -> result.plainText
            is AuthTokenDecryptValue.Failure -> return AuthTokenReadResult.Failure(result.failure)
        }
        return AuthTokenReadResult.Encrypted(AuthTokenPair(accessToken, refreshToken))
    }

    private fun decryptToken(field: AuthTokenField, encryptedToken: String): AuthTokenDecryptValue =
        when (val result = cipher.decrypt(encryptedToken)) {
            is AuthTokenDecryptResult.Success -> {
                if (result.plainText.isBlank()) {
                    AuthTokenDecryptValue.Failure(
                        AuthTokenReadFailure(
                            field = field,
                            failure = AuthTokenCipherFailure(
                                operation = AuthTokenCipherOperation.DECRYPT,
                                type = AuthTokenCipherFailureType.DECRYPTION,
                                message = "Stored auth token decrypted to a blank value"
                            )
                        )
                    )
                } else {
                    AuthTokenDecryptValue.Success(result.plainText)
                }
            }

            is AuthTokenDecryptResult.Failure -> {
                AuthTokenDecryptValue.Failure(
                    AuthTokenReadFailure(field = field, failure = result.failure)
                )
            }
        }

    private fun readLegacyPlaintextTokens(preferences: Preferences): AuthTokenPair? {
        val accessToken = preferences[AUTH_ACCESS_TOKEN]
        val refreshToken = preferences[AUTH_REFRESH_TOKEN]
        return readLegacyPlaintextTokens(accessToken, refreshToken)
    }

    private fun readLegacyPlaintextTokens(
        accessToken: String?,
        refreshToken: String?
    ): AuthTokenPair? {
        return if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            null
        } else {
            AuthTokenPair(accessToken, refreshToken)
        }
    }

    private fun hasLegacyPlaintextToken(preferences: Preferences): Boolean =
        !preferences[AUTH_ACCESS_TOKEN].isNullOrBlank() ||
            !preferences[AUTH_REFRESH_TOKEN].isNullOrBlank()

    private fun clearLegacyPlaintextTokens(preferences: MutablePreferences) {
        preferences.remove(AUTH_ACCESS_TOKEN)
        preferences.remove(AUTH_REFRESH_TOKEN)
    }

    private fun AuthTokenReadResult.tokensOrNull(): AuthTokenPair? =
        when (this) {
            is AuthTokenReadResult.Encrypted -> tokens
            is AuthTokenReadResult.LegacyFallback -> tokens
            is AuthTokenReadResult.Failure,
            AuthTokenReadResult.NoTokens -> null
        }
}

private sealed interface AuthTokenDecryptValue {
    data class Success(val plainText: String) : AuthTokenDecryptValue

    data class Failure(val failure: AuthTokenReadFailure) : AuthTokenDecryptValue
}
