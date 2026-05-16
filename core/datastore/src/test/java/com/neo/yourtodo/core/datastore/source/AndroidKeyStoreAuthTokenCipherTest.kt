package com.neo.yourtodo.core.datastore.source

import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Test

class AndroidKeyStoreAuthTokenCipherTest {

    @Test
    fun encryptRegeneratesKeyWhenKeyStoreLookupFailsAndKeepsSessionReadable() {
        val generatedKey = secretKey(seed = 1)
        val keyStore = FakeAuthSecretKeyStore(
            storedKey = null,
            failFirstGet = true
        )
        val keyGenerator = FakeAuthSecretKeyGenerator(generatedKey)
        val cipher = createCipher(keyStore, keyGenerator)

        val encrypted = cipher.encrypt("refresh-token")

        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token")
        assertThat(keyStore.getSecretKeyCalls).isEqualTo(1)
        assertThat(keyStore.clearKeyCalls).isEqualTo(1)
        assertThat(keyGenerator.generateCalls).isEqualTo(1)

        cipher.encrypt("next-token")

        assertThat(keyStore.getSecretKeyCalls).isEqualTo(1)
        assertThat(keyGenerator.generateCalls).isEqualTo(1)
    }

    @Test
    fun encryptCachesKeyLoadedFromKeyStore() {
        val storedKey = secretKey(seed = 2)
        val keyStore = FakeAuthSecretKeyStore(storedKey = storedKey)
        val keyGenerator = FakeAuthSecretKeyGenerator(generatedKey = null)
        val cipher = createCipher(keyStore, keyGenerator)

        val encrypted = cipher.encrypt("access-token")

        assertThat(cipher.decrypt(encrypted)).isEqualTo("access-token")
        cipher.encrypt("next-token")

        assertThat(keyStore.getSecretKeyCalls).isEqualTo(1)
        assertThat(keyStore.clearKeyCalls).isEqualTo(0)
        assertThat(keyGenerator.generateCalls).isEqualTo(0)
    }

    private fun createCipher(
        keyStore: AuthSecretKeyStore,
        keyGenerator: AuthSecretKeyGenerator
    ): AndroidKeyStoreAuthTokenCipher =
        AndroidKeyStoreAuthTokenCipher(
            secretKeyStore = keyStore,
            secretKeyGenerator = keyGenerator,
            base64Codec = JavaBase64Codec
        )

    private fun secretKey(seed: Int): SecretKey =
        SecretKeySpec(ByteArray(KEY_SIZE_BYTES) { index -> (seed + index).toByte() }, "AES")

    private class FakeAuthSecretKeyStore(
        private val storedKey: SecretKey?,
        private val failFirstGet: Boolean = false
    ) : AuthSecretKeyStore {
        var getSecretKeyCalls = 0
            private set
        var clearKeyCalls = 0
            private set

        override fun getSecretKey(alias: String): SecretKey? {
            getSecretKeyCalls += 1
            if (failFirstGet && getSecretKeyCalls == 1) {
                throw UnrecoverableKeyException("Keystore entry is not recoverable")
            }
            return storedKey
        }

        override fun clearKey(alias: String) {
            clearKeyCalls += 1
        }
    }

    private class FakeAuthSecretKeyGenerator(
        private val generatedKey: SecretKey?
    ) : AuthSecretKeyGenerator {
        var generateCalls = 0
            private set

        override fun generate(alias: String): SecretKey {
            generateCalls += 1
            return generatedKey ?: throw GeneralSecurityException("No generated key configured")
        }
    }

    private object JavaBase64Codec : AuthTokenBase64Codec {
        override fun encode(bytes: ByteArray): String =
            Base64.getEncoder().encodeToString(bytes)

        override fun decode(encoded: String): ByteArray =
            Base64.getDecoder().decode(encoded)
    }

    private companion object {
        const val KEY_SIZE_BYTES = 32
    }
}
