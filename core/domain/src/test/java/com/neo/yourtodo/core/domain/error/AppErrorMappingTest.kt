package com.neo.yourtodo.core.domain.error

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.Test

class AppErrorMappingTest {

    @Test
    fun `auth exception maps to auth required error`() {
        val throwable = AuthRequiredException("sign in required")

        val error = throwable.toAppError()

        assertThat(error).isEqualTo(AppError.AuthRequired("sign in required"))
    }

    @Test
    fun `network throwables map to network failure kinds`() {
        val timeout = SocketTimeoutException("slow").toAppError()
        val offline = UnknownHostException("offline").toAppError()
        val ioFailure = IOException("transport failed").toAppError()

        assertThat(timeout).isEqualTo(
            AppError.NetworkFailure(
                message = "slow",
                kind = AppError.NetworkFailure.Kind.TIMEOUT
            )
        )
        assertThat(offline).isEqualTo(
            AppError.NetworkFailure(
                message = "offline",
                kind = AppError.NetworkFailure.Kind.CONNECTIVITY
            )
        )
        assertThat(ioFailure).isEqualTo(AppError.NetworkFailure(message = "transport failed"))
    }

    @Test
    fun `domain exceptions map to their taxonomy entries`() {
        val validation = ServerValidationException(
            message = "invalid nickname",
            fieldErrors = mapOf("nickname" to listOf("already taken"))
        ).toAppError()
        val conflict = ConflictException("stale assignment").toAppError()
        val missing = LocalDataMissingException("todo cache missing").toAppError()

        assertThat(validation).isEqualTo(
            AppError.ServerValidation(
                message = "invalid nickname",
                fieldErrors = mapOf("nickname" to listOf("already taken"))
            )
        )
        assertThat(conflict).isEqualTo(AppError.Conflict("stale assignment"))
        assertThat(missing).isEqualTo(AppError.LocalDataMissing("todo cache missing"))
    }

    @Test
    fun `mapper walks nested causes before falling back to unknown`() {
        val wrappedNetworkError = RuntimeException("wrapper", UnknownHostException("dns"))
        val unknown = RuntimeException("boom")

        assertThat(wrappedNetworkError.toAppError()).isEqualTo(
            AppError.NetworkFailure(
                message = "dns",
                kind = AppError.NetworkFailure.Kind.CONNECTIVITY
            )
        )
        assertThat(unknown.toAppError()).isEqualTo(AppError.Unknown("boom"))
    }

    @Test
    fun `result helper wraps failures with app error exception`() {
        val result = Result.failure<Unit>(UnknownHostException("offline"))
            .mapFailureToAppError()

        val exception = result.exceptionOrNull()

        assertThat(exception).isInstanceOf(AppErrorException::class.java)
        assertThat((exception as AppErrorException).appError).isEqualTo(
            AppError.NetworkFailure(
                message = "offline",
                kind = AppError.NetworkFailure.Kind.CONNECTIVITY
            )
        )
        assertThat(result.appErrorOrNull()).isEqualTo(exception.appError)
    }

    @Test
    fun `result helper preserves cancellation exceptions`() {
        val cancellation = CancellationException("cancelled")

        val result = Result.failure<Unit>(cancellation).mapFailureToAppError()

        assertThat(result.exceptionOrNull()).isSameInstanceAs(cancellation)
    }
}
