package com.neo.yourtodo.core.domain.error

class AuthRequiredException(
    message: String? = null,
    cause: Throwable? = null
) : AppErrorException(
    AppError.AuthRequired(message = message ?: cause?.message),
    cause
)
