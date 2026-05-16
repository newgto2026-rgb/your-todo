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

@Singleton
class AuthTokenStoragePolicy @Inject constructor(
    private val cipher: AuthTokenCipher
) {

    fun readTokens(preferences: Preferences): AuthTokenPair? =
        readEncryptedTokens(preferences) ?: readLegacyPlaintextTokens(preferences)

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
        val migrated = preferences.toMutablePreferences()
        val encryptedTokens = readEncryptedTokens(preferences)
        if (encryptedTokens != null) {
            clearLegacyPlaintextTokens(migrated)
            return migrated.toPreferences()
        }

        val legacyTokens = readLegacyPlaintextTokens(preferences)
        if (legacyTokens == null) {
            clearLegacyPlaintextTokens(migrated)
            return migrated.toPreferences()
        }

        return runCatching {
            saveTokens(migrated, legacyTokens)
            migrated.toPreferences()
        }.getOrElse {
            preferences
        }
    }

    private fun readEncryptedTokens(preferences: Preferences): AuthTokenPair? {
        val encryptedAccessToken = preferences[AUTH_ENCRYPTED_ACCESS_TOKEN]
        val encryptedRefreshToken = preferences[AUTH_ENCRYPTED_REFRESH_TOKEN]
        if (encryptedAccessToken.isNullOrBlank() || encryptedRefreshToken.isNullOrBlank()) {
            return null
        }

        val accessToken = cipher.decrypt(encryptedAccessToken)
        val refreshToken = cipher.decrypt(encryptedRefreshToken)
        return if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            null
        } else {
            AuthTokenPair(accessToken, refreshToken)
        }
    }

    private fun readLegacyPlaintextTokens(preferences: Preferences): AuthTokenPair? {
        val accessToken = preferences[AUTH_ACCESS_TOKEN]
        val refreshToken = preferences[AUTH_REFRESH_TOKEN]
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
}
