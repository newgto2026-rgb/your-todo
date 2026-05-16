package com.neo.yourtodo.core.domain.error

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

fun Throwable.toAppError(): AppError {
    val chain = causeChain().toList()

    (chain.firstOrNull { throwable -> throwable is CancellationException } as? CancellationException)
        ?.let { throwable -> throw throwable }

    chain
        .firstNotNullOfOrNull { throwable ->
            (throwable as? AppErrorException)?.appError
        }
        ?.let { return it }

    chain
        .firstOrNull { throwable ->
            throwable is SocketTimeoutException || throwable is InterruptedIOException
        }
        ?.let { return AppError.NetworkFailure(message = it.message, kind = AppError.NetworkFailure.Kind.TIMEOUT) }

    chain
        .firstOrNull { throwable ->
            throwable is UnknownHostException ||
                throwable is ConnectException ||
                throwable is NoRouteToHostException
        }
        ?.let { return AppError.NetworkFailure(message = it.message, kind = AppError.NetworkFailure.Kind.CONNECTIVITY) }

    chain
        .firstOrNull { throwable -> throwable is IOException }
        ?.let { return AppError.NetworkFailure(message = it.message) }

    chain
        .firstOrNull { throwable ->
            throwable is NoSuchElementException ||
                (throwable is IllegalStateException && throwable.message.looksLikeLocalDataMissing()) ||
                (throwable is IllegalArgumentException && throwable.message.looksLikeLocalDataMissing())
        }
        ?.let { return AppError.LocalDataMissing(message = it.message) }

    chain
        .firstOrNull { throwable -> throwable is IllegalArgumentException }
        ?.let { return AppError.ValidationError(message = it.message) }

    return AppError.Unknown(message = message ?: chain.lastOrNull()?.message)
}

fun Throwable.toAppErrorException(): AppErrorException =
    when (this) {
        is CancellationException -> throw this
        is AppErrorException -> this
        else -> toAppError().toException(this)
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
                throw throwable
            } else {
                Result.failure(throwable.toAppErrorException())
            }
        }
    )

fun <T> Result<T>.appErrorOrNull(): AppError? {
    val throwable = exceptionOrNull() ?: return null
    if (throwable is CancellationException) {
        throw throwable
    }
    return throwable.toAppError()
}

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { throwable -> throwable.cause }
        .take(MAX_CAUSE_DEPTH)

private fun String?.looksLikeLocalDataMissing(): Boolean {
    val value = this ?: return false
    val isKnownMissingMessage = KNOWN_LOCAL_DATA_MISSING_MESSAGES.any { message ->
        value.equals(message, ignoreCase = true)
    }
    val isKnownLocalNotFound =
        value.contains("not found", ignoreCase = true) &&
            KNOWN_LOCAL_DATA_NAMES.any { name -> value.contains(name, ignoreCase = true) }
    val isContextualMissing =
        value.contains("missing", ignoreCase = true) &&
            LOCAL_DATA_CONTEXTS.any { context -> value.contains(context, ignoreCase = true) }
    val isStaleLocalReference =
        value.contains("stale", ignoreCase = true) &&
            KNOWN_LOCAL_DATA_NAMES.any { name -> value.contains(name, ignoreCase = true) } &&
            LOCAL_DATA_REFERENCE_MARKERS.any { marker -> value.containsToken(marker) }
    return isKnownMissingMessage || isKnownLocalNotFound || isContextualMissing || isStaleLocalReference
}

private fun String.containsToken(token: String): Boolean {
    val tokenPattern = Regex(
        pattern = "(?<![A-Za-z0-9_])${Regex.escape(token)}(?![A-Za-z0-9_])",
        option = RegexOption.IGNORE_CASE
    )
    return tokenPattern.containsMatchIn(this)
}

private val KNOWN_LOCAL_DATA_MISSING_MESSAGES = listOf(
    "Todo not found",
    "Category not found",
    "Reminder not found"
)

private val KNOWN_LOCAL_DATA_NAMES = listOf(
    "todo",
    "category",
    "reminder"
)

private val LOCAL_DATA_CONTEXTS = listOf(
    "local",
    "cache"
)

private val LOCAL_DATA_REFERENCE_MARKERS = listOf(
    "id",
    "reference"
)

private const val MAX_CAUSE_DEPTH = 32
