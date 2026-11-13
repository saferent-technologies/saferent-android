package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiErrorDto
import com.saferenttechnologies.android.domain.model.AppError
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** Maps a thrown exception (no HTTP response) to [AppError]. */
internal fun Throwable.toAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout()
    is IOException -> AppError.Network()
    // kotlinx SerializationException extends IllegalArgumentException.
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
