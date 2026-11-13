package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.ApiErrorDto
import com.saferenttechnologies.android.domain.model.AppError
import kotlinx.serialization.json.Json
import okhttp3.Headers
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Result of any SafeRent API call. Never an exception. */
sealed interface ApiResult<out T> {
    /** 2xx with "success": true. [data] is the envelope's "data" (may be null). */
    data class Success<T>(
        val data: T?,
        val message: String,
        val code: Int,
        val headers: Headers,
    ) : ApiResult<T>

    /** 304 — server says the cached copy is still valid (empty body). */
    data class NotModified(val headers: Headers) : ApiResult<Nothing>

    /** Anything that went wrong, mapped to a user-safe [AppError]. */
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

/**
 * Wraps every Retrofit call that returns `Response<ApiEnvelope<T>>`:
 * - never throws (except coroutine cancellation),
 * - maps offline / timeout / HTTP errors / bad JSON to [AppError],
 * - logs every outcome (debug builds only) with endpoint, URL, HTTP code,
 *   server errorCode/reason and the exception stacktrace — so errors are visible in Logcat.
 *
 * Usage:
 * ```
 * when (val r = safeApiCall("GET app/config") { api.config(etag) }) {
 *     is ApiResult.Success -> r.data
 *     is ApiResult.NotModified -> …
 *     is ApiResult.Failure -> r.error
 * }
 * ```
 */
@Singleton
class SafeApiCall @Inject constructor(
    private val json: Json,
    private val logger: AppLogger,
) {

    suspend operator fun <T> invoke(
        name: String,
        call: suspend () -> Response<ApiEnvelope<T>>,
    ): ApiResult<T> {
        val response = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toAppError()
            logger.w(TAG, "✗ $name → ${e.describe()} → $error", e)
            return ApiResult.Failure(error)
        }

        val code = response.code()
        val url = response.raw().request.url

        if (code == HTTP_NOT_MODIFIED) {
            logger.d(TAG, "✓ $name → 304 Not Modified ($url)")
            return ApiResult.NotModified(response.headers())
        }

        if (!response.isSuccessful) {
            val error = response.toServerError(json)
            logger.w(
                TAG,
                "✗ $name → HTTP $code ($url) errorCode=${error.errorCode} " +
                    "reason=${error.reason} message=\"${error.message}\"",
            )
            return ApiResult.Failure(error)
        }

        val envelope = response.body()
        if (envelope == null) {
            logger.w(TAG, "✗ $name → HTTP $code ($url) but the body is empty")
            return ApiResult.Failure(AppError.Parse())
        }

        if (!envelope.success) {
            val error = AppError.Server(
                httpCode = code,
                message = envelope.message.ifBlank { AppError.Server(code).message },
            )
            logger.w(TAG, "✗ $name → HTTP $code ($url) success=false message=\"${envelope.message}\"")
            return ApiResult.Failure(error)
        }

        logger.d(TAG, "✓ $name → HTTP $code ($url)")
        return ApiResult.Success(
            data = envelope.data,
            message = envelope.message,
            code = code,
            headers = response.headers(),
        )
    }

    private fun Throwable.describe(): String = "${this::class.java.simpleName}: ${message ?: "-"}"

    private companion object {
        const val TAG = "SafeRent-Api"
        const val HTTP_NOT_MODIFIED = 304
    }
}

/** Maps a thrown exception (no HTTP response) to [AppError]. */
internal fun Throwable.toAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout()
    is UnknownHostException, is ConnectException -> AppError.Network()
    is IOException -> AppError.Network()
    // kotlinx SerializationException extends IllegalArgumentException (bad / unexpected JSON).
    is IllegalArgumentException -> AppError.Parse()
    else -> AppError.Unknown()
}

/** Maps a non-2xx response using the server error envelope when it can be read. */
internal fun Response<*>.toServerError(json: Json): AppError.Server {
    val raw = try {
        errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    val dto = raw?.takeIf { it.isNotBlank() }?.let {
        try {
            json.decodeFromString(ApiErrorDto.serializer(), it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    return AppError.Server(
        httpCode = code(),
        errorCode = dto?.errorCode,
        reason = dto?.reason,
        message = dto?.message?.takeIf { it.isNotBlank() }
            ?: "Server error (${code()}). Please try again.",
    )
}
