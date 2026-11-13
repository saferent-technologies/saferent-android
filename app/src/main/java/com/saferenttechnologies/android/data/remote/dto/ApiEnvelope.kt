package com.saferenttechnologies.android.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Success envelope: { "success", "message", "data", "meta" }. */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null,
    val meta: JsonElement? = null,
)

/** Error envelope: { "success": false, "message", "errorCode", "reason", "details" }. */
@Serializable
data class ApiErrorDto(
    val success: Boolean = false,
    val message: String = "",
    val errorCode: String? = null,
    val reason: String? = null,
    val details: JsonElement? = null,
)
