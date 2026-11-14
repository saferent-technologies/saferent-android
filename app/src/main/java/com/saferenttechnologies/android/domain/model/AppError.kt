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
        /** Raw JSON of the error envelope's "details" (parsed by the feature that needs it). */
        val details: String? = null,
    ) : AppError

    /** 400 VALIDATION_ERROR — the server listed which profile fields are missing / invalid. */
    data class Validation(
        override val message: String,
        val missingFields: List<String> = emptyList(),
    ) : AppError

    /** 403 ACCOUNT_DELETED / ACCOUNT_DISABLED — from any signed-in API. */
    data class AccountBlocked(
        val info: AccountBlockedInfo,
        override val message: String = info.message.ifBlank { "This account can no longer be used." },
    ) : AppError

    /** 409 EMAIL_IN_USE — another account already uses this email. */
    data class EmailInUse(
        override val message: String = "This email is already used by another account.",
    ) : AppError

    /** LOGIN_METHOD_DISABLED — phone sign-in switched off on the server. */
    data class LoginMethodDisabled(
        override val message: String = "Phone sign-in is turned off right now",
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
