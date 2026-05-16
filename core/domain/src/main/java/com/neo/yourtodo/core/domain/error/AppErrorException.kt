package com.neo.yourtodo.core.domain.error

open class AppErrorException(
    val appError: AppError,
    cause: Throwable? = null
) : RuntimeException(appError.message, cause)

class NetworkFailureException(
    message: String? = null,
    kind: AppError.NetworkFailure.Kind = AppError.NetworkFailure.Kind.UNKNOWN,
    cause: Throwable? = null
) : AppErrorException(
    AppError.NetworkFailure(
        message = message ?: cause?.message,
        kind = kind
    ),
    cause
)

class ServerValidationException(
    message: String? = null,
    fieldErrors: Map<String, List<String>> = emptyMap(),
    cause: Throwable? = null
) : AppErrorException(
    AppError.ServerValidation(
        message = message ?: cause?.message,
        fieldErrors = fieldErrors
    ),
    cause
)

class ConflictException(
    message: String? = null,
    cause: Throwable? = null
) : AppErrorException(
    AppError.Conflict(message = message ?: cause?.message),
    cause
)

class LocalDataMissingException(
    message: String? = null,
    cause: Throwable? = null
) : AppErrorException(
    AppError.LocalDataMissing(message = message ?: cause?.message),
    cause
)
