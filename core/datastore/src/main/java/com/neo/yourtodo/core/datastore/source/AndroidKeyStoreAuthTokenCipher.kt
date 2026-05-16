package com.neo.yourtodo.core.datastore.source

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeyStoreAuthTokenCipher internal constructor(
    private val secretKeyStore: AuthSecretKeyStore,
    private val secretKeyGenerator: AuthSecretKeyGenerator,
    private val base64Codec: AuthTokenBase64Codec
) : AuthTokenCipher {

    @Inject
    constructor() : this(
        secretKeyStore = AndroidKeyStoreAuthSecretKeyStore(),
        secretKeyGenerator = AndroidKeyStoreAuthSecretKeyGenerator(),
        base64Codec = AndroidAuthTokenBase64Codec
    )

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey().key)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            listOf(
                FORMAT_VERSION,
                encode(cipher.iv),
                encode(cipherText)
            ).joinToString(separator = SEPARATOR)
        } catch (exception: AuthTokenCipherException) {
            throw exception
        } catch (exception: GeneralSecurityException) {
            throw AuthTokenCipherException(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.ENCRYPT,
                    type = AuthTokenCipherFailureType.ENCRYPTION,
                    message = "Auth token encryption failed",
                    cause = exception
                )
            )
        }
    }

    override fun decrypt(cipherText: String): AuthTokenDecryptResult {
        val parts = cipherText.split(SEPARATOR)
        if (parts.size != ENCRYPTED_PARTS || parts[0] != FORMAT_VERSION) {
            return AuthTokenDecryptResult.Failure(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.DECRYPT,
                    type = AuthTokenCipherFailureType.INVALID_FORMAT,
                    message = "Stored auth token does not match the encrypted token format"
                )
            )
        }

        return try {
            val iv = decode(parts[1])
            val encryptedBytes = decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keyResult = getOrCreateSecretKey()
            if (keyResult.recoveredLookupFailure != null) {
                return AuthTokenDecryptResult.Failure(keyResult.recoveredLookupFailure)
            }
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyResult.key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            AuthTokenDecryptResult.Success(String(cipher.doFinal(encryptedBytes), Charsets.UTF_8))
        } catch (exception: AuthTokenCipherException) {
            AuthTokenDecryptResult.Failure(exception.failure)
        } catch (exception: GeneralSecurityException) {
            AuthTokenDecryptResult.Failure(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.DECRYPT,
                    type = AuthTokenCipherFailureType.DECRYPTION,
                    message = "Stored auth token could not be decrypted",
                    cause = exception
                )
            )
        }
    }

    private fun getOrCreateSecretKey(): SecretKeyResult {
        cachedKey?.let { return SecretKeyResult(key = it) }

        return synchronized(this) {
            cachedKey?.let { return@synchronized SecretKeyResult(key = it) }

            val existingKeyResult = readExistingSecretKey()
            val key = existingKeyResult.key ?: generateSecretKey()
            cachedKey = key
            SecretKeyResult(
                key = key,
                recoveredLookupFailure = existingKeyResult.recoveredLookupFailure
            )
        }
    }

    private fun readExistingSecretKey(): SecretKeyLookupResult =
        try {
            SecretKeyLookupResult(key = secretKeyStore.getSecretKey(KEY_ALIAS))
        } catch (exception: GeneralSecurityException) {
            handleRecoverableKeyStoreLookupFailure(exception)
        } catch (exception: IOException) {
            handleRecoverableKeyStoreLookupFailure(exception)
        } catch (exception: ProviderException) {
            handleRecoverableKeyStoreLookupFailure(exception)
        }

    private fun handleRecoverableKeyStoreLookupFailure(cause: Throwable): SecretKeyLookupResult {
        secretKeyStore.clearKey(KEY_ALIAS)
        return SecretKeyLookupResult(
            key = null,
            recoveredLookupFailure = AuthTokenCipherFailure(
                operation = AuthTokenCipherOperation.KEY_LOOKUP,
                type = AuthTokenCipherFailureType.KEY_LOOKUP,
                message = "Android Keystore key lookup failed for auth token storage",
                cause = cause
            )
        )
    }

    private fun generateSecretKey(): SecretKey =
        try {
            secretKeyGenerator.generate(KEY_ALIAS)
        } catch (exception: GeneralSecurityException) {
            throw AuthTokenCipherException(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.KEY_GENERATION,
                    type = AuthTokenCipherFailureType.KEY_GENERATION,
                    message = "Android Keystore key generation failed for auth token storage",
                    cause = exception
                )
            )
        }

    private fun encode(bytes: ByteArray): String =
        try {
            base64Codec.encode(bytes)
        } catch (exception: IllegalArgumentException) {
            throw AuthTokenCipherException(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.ENCRYPT,
                    type = AuthTokenCipherFailureType.ENCODING,
                    message = "Auth token encryption output could not be encoded",
                    cause = exception
                )
            )
        }

    private fun decode(encoded: String): ByteArray =
        try {
            base64Codec.decode(encoded)
        } catch (exception: IllegalArgumentException) {
            throw AuthTokenCipherException(
                AuthTokenCipherFailure(
                    operation = AuthTokenCipherOperation.DECRYPT,
                    type = AuthTokenCipherFailureType.ENCODING,
                    message = "Stored auth token encoding is invalid",
                    cause = exception
                )
            )
        }

    private companion object {
        const val KEY_ALIAS = "yourtodo_auth_token_storage_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "ks1"
        const val SEPARATOR = ":"
        const val ENCRYPTED_PARTS = 3
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

private data class SecretKeyResult(
    val key: SecretKey,
    val recoveredLookupFailure: AuthTokenCipherFailure? = null
)

private data class SecretKeyLookupResult(
    val key: SecretKey?,
    val recoveredLookupFailure: AuthTokenCipherFailure? = null
)

internal interface AuthSecretKeyStore {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getSecretKey(alias: String): SecretKey?

    fun clearKey(alias: String)
}

private class AndroidKeyStoreAuthSecretKeyStore : AuthSecretKeyStore {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    }

    override fun getSecretKey(alias: String): SecretKey? =
        keyStore.getKey(alias, null) as? SecretKey

    override fun clearKey(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}

internal interface AuthSecretKeyGenerator {
    fun generate(alias: String): SecretKey
}

private class AndroidKeyStoreAuthSecretKeyGenerator : AuthSecretKeyGenerator {
    override fun generate(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}

internal interface AuthTokenBase64Codec {
    fun encode(bytes: ByteArray): String

    fun decode(encoded: String): ByteArray
}

private object AndroidAuthTokenBase64Codec : AuthTokenBase64Codec {
    override fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    override fun decode(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}
