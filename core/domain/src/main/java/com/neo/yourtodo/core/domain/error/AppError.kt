package com.neo.yourtodo.core.domain.error

sealed interface AppError {
    data object ValidationError : AppError
    data object NotFound : AppError
    data class Unknown(val message: String? = null) : AppError
}
