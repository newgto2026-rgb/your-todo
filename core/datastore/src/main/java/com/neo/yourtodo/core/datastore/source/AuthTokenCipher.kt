package com.neo.yourtodo.core.datastore.source

interface AuthTokenCipher {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): AuthTokenDecryptResult
}

sealed interface AuthTokenDecryptResult {
    data class Success(val plainText: String) : AuthTokenDecryptResult

    data class Failure(val failure: AuthTokenCipherFailure) : AuthTokenDecryptResult
}

data class AuthTokenCipherFailure(
    val operation: AuthTokenCipherOperation,
    val type: AuthTokenCipherFailureType,
    val message: String,
    val cause: Throwable? = null
)

enum class AuthTokenCipherOperation {
    ENCRYPT,
    DECRYPT,
    KEY_LOOKUP,
    KEY_GENERATION
}

enum class AuthTokenCipherFailureType {
    INVALID_FORMAT,
    KEY_LOOKUP,
    KEY_GENERATION,
    ENCRYPTION,
    DECRYPTION,
    ENCODING
}

class AuthTokenCipherException(
    val failure: AuthTokenCipherFailure
) : RuntimeException(failure.message, failure.cause)
