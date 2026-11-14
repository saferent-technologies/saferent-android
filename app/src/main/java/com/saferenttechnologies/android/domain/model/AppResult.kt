package com.saferenttechnologies.android.domain.model

/** Result of a domain operation. Never an exception. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
