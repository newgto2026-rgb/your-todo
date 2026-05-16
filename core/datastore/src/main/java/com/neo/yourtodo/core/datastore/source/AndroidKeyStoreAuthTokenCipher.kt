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
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            base64Codec.encode(cipher.iv),
            base64Codec.encode(cipherText)
        ).joinToString(separator = SEPARATOR)
    }

    override fun decrypt(cipherText: String): String? {
        val parts = cipherText.split(SEPARATOR)
        if (parts.size != ENCRYPTED_PARTS || parts[0] != FORMAT_VERSION) return null

        return try {
            val iv = base64Codec.decode(parts[1])
            val encryptedBytes = base64Codec.decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (exception: GeneralSecurityException) {
            null
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        cachedKey?.let { return it }

        return synchronized(this) {
            cachedKey?.let { return@synchronized it }

            val key = readExistingSecretKey()
                ?: secretKeyGenerator.generate(KEY_ALIAS)
            cachedKey = key
            key
        }
    }

    private fun readExistingSecretKey(): SecretKey? =
        try {
            secretKeyStore.getSecretKey(KEY_ALIAS)
        } catch (exception: GeneralSecurityException) {
            handleRecoverableKeyStoreLookupFailure()
        } catch (exception: IOException) {
            handleRecoverableKeyStoreLookupFailure()
        } catch (exception: ProviderException) {
            handleRecoverableKeyStoreLookupFailure()
        }

    private fun handleRecoverableKeyStoreLookupFailure(): SecretKey? {
        secretKeyStore.clearKey(KEY_ALIAS)
        return null
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
