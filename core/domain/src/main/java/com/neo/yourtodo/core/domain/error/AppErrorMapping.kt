package com.neo.yourtodo.core.domain.error

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

fun Throwable.toAppError(): AppError {
    causeChain()
        .firstNotNullOfOrNull { throwable ->
            (throwable as? AppErrorException)?.appError
        }
        ?.let { return it }

    causeChain()
        .firstOrNull { throwable ->
            throwable is SocketTimeoutException || throwable is InterruptedIOException
        }
        ?.let { return AppError.NetworkFailure(message = it.message, kind = AppError.NetworkFailure.Kind.TIMEOUT) }

    causeChain()
        .firstOrNull { throwable ->
            throwable is UnknownHostException ||
                throwable is ConnectException ||
                throwable is NoRouteToHostException
        }
        ?.let { return AppError.NetworkFailure(message = it.message, kind = AppError.NetworkFailure.Kind.CONNECTIVITY) }

    causeChain()
        .firstOrNull { throwable -> throwable is IOException }
        ?.let { return AppError.NetworkFailure(message = it.message) }

    causeChain()
        .firstOrNull { throwable ->
            throwable is NoSuchElementException ||
                (throwable is IllegalStateException && throwable.message.looksLikeLocalDataMissing())
        }
        ?.let { return AppError.LocalDataMissing(message = it.message) }

    causeChain()
        .firstOrNull { throwable -> throwable is IllegalArgumentException }
        ?.let { return AppError.ValidationError(message = it.message) }

    return AppError.Unknown(message = message ?: causeChain().lastOrNull()?.message)
}

fun Throwable.toAppErrorException(): AppErrorException =
    if (this is AppErrorException) {
        this
    } else {
        toAppError().toException(this)
    }

fun AppError.toException(cause: Throwable? = null): AppErrorException =
    when (this) {
        is AppError.AuthRequired -> AuthRequiredException(message = message, cause = cause)
        is AppError.NetworkFailure -> NetworkFailureException(message = message, kind = kind, cause = cause)
        is AppError.ServerValidation -> ServerValidationException(
            message = message,
            fieldErrors = fieldErrors,
            cause = cause
        )
        is AppError.Conflict -> ConflictException(message = message, cause = cause)
        is AppError.LocalDataMissing -> LocalDataMissingException(message = message, cause = cause)
        else -> AppErrorException(this, cause)
    }

fun <T> Result<T>.mapFailureToAppError(): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable ->
            if (throwable is CancellationException) {
                Result.failure(throwable)
            } else {
                Result.failure(throwable.toAppErrorException())
            }
        }
    )

fun <T> Result<T>.appErrorOrNull(): AppError? =
    exceptionOrNull()?.toAppError()

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { throwable -> throwable.cause }
        .take(MAX_CAUSE_DEPTH)

private fun String?.looksLikeLocalDataMissing(): Boolean {
    val normalized = this?.lowercase() ?: return false
    return "not found" in normalized || "missing" in normalized
}

private const val MAX_CAUSE_DEPTH = 32
