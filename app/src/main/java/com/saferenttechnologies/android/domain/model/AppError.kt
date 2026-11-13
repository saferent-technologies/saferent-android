package com.saferenttechnologies.android.domain.model

/** App-wide error model. Pure Kotlin — safe to show [message] to the user. */
sealed interface AppError {
    val message: String

    data class Network(
        override val message: String = "No internet connection. Please check your network.",
    ) : AppError

    data class Timeout(
        override val message: String = "The server took too long to respond. Please try again.",
    ) : AppError

    /** Non-2xx response. [errorCode]/[reason] come from the server error envelope when present. */
    data class Server(
        val httpCode: Int,
        val errorCode: String? = null,
        val reason: String? = null,
        override val message: String = "Something went wrong. Please try again.",
    ) : AppError

    data class Parse(
        override val message: String = "Unexpected response from the server.",
    ) : AppError

    data class Storage(
        override val message: String = "Couldn't save app settings on this device.",
    ) : AppError

    data class Unknown(
        override val message: String = "Something went wrong. Please try again.",
    ) : AppError
}
