package com.neo.yourtodo.core.datastore.source

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidKeyStoreAuthTokenCipherTest {

    @Test
    fun encryptRegeneratesKeyWhenKeyStoreLookupFailsAndKeepsSessionReadable() {
        val generatedKey = secretKey(seed = 1)
        val keyStore = FakeAuthSecretKeyStore(
            storedKey = null,
            firstGetFailure = UnrecoverableKeyException("Keystore entry is not recoverable")
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

    @Test
    fun decryptReturnsNullWhenKeyStoreLookupThrowsRecoverablePlatformFailure() {
        val recoverableFailures = listOf(
            IOException("AndroidKeyStore load failed"),
            ProviderException("AndroidKeyStore provider unavailable")
        )

        recoverableFailures.forEachIndexed { index, failure ->
            val encryptedWithOldKey = createCipher(
                keyStore = FakeAuthSecretKeyStore(storedKey = secretKey(seed = 10 + index)),
                keyGenerator = FakeAuthSecretKeyGenerator(generatedKey = null)
            ).encrypt("refresh-token")
            val keyStore = FakeAuthSecretKeyStore(
                storedKey = null,
                firstGetFailure = failure
            )
            val keyGenerator = FakeAuthSecretKeyGenerator(secretKey(seed = 20 + index))
            val cipher = createCipher(keyStore, keyGenerator)

            assertThat(cipher.decrypt(encryptedWithOldKey)).isNull()
            assertThat(keyStore.getSecretKeyCalls).isEqualTo(1)
            assertThat(keyStore.clearKeyCalls).isEqualTo(1)
            assertThat(keyGenerator.generateCalls).isEqualTo(1)
        }
    }

    @Test
    fun encryptPropagatesUnexpectedRuntimeKeyStoreLookupFailure() {
        val keyStore = FakeAuthSecretKeyStore(
            storedKey = null,
            firstGetFailure = IllegalStateException("Fake store is misconfigured")
        )
        val keyGenerator = FakeAuthSecretKeyGenerator(secretKey(seed = 3))
        val cipher = createCipher(keyStore, keyGenerator)

        assertThrows(IllegalStateException::class.java) {
            cipher.encrypt("access-token")
        }
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
        private val firstGetFailure: Throwable? = null
    ) : AuthSecretKeyStore {
        var getSecretKeyCalls = 0
            private set
        var clearKeyCalls = 0
            private set

        override fun getSecretKey(alias: String): SecretKey? {
            getSecretKeyCalls += 1
            if (firstGetFailure != null && getSecretKeyCalls == 1) {
                throw firstGetFailure
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
