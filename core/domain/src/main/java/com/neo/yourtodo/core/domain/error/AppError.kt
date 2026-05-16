package com.neo.yourtodo.core.domain.error

sealed interface AppError {
    val message: String?
    val retryable: Boolean
        get() = false

    data class AuthRequired(
        override val message: String? = null
    ) : AppError

    data class ValidationError(
        override val message: String? = null
    ) : AppError

    data class ServerValidation(
        override val message: String? = null,
        val fieldErrors: Map<String, List<String>> = emptyMap()
    ) : AppError

    data class NetworkFailure(
        override val message: String? = null,
        val kind: Kind = Kind.UNKNOWN
    ) : AppError {
        override val retryable: Boolean = true

        enum class Kind {
            CONNECTIVITY,
            TIMEOUT,
            UNKNOWN
        }
    }

    data class Conflict(
        override val message: String? = null
    ) : AppError

    data class LocalDataMissing(
        override val message: String? = null
    ) : AppError

    data class NotFound(
        override val message: String? = null
    ) : AppError

    data class Unknown(
        override val message: String? = null
    ) : AppError
}
