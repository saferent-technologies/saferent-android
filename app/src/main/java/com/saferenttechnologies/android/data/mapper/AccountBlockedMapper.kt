package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.dto.AccountBlockedDetailsDto
import com.saferenttechnologies.android.data.remote.dto.ApiErrorDto
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.SupportContact
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Codes the server may use for a blocked account (errorCode or reason). */
private val BLOCKED_CODES = setOf(
    "ACCOUNT_DELETED", "ACCOUNT_DISABLED", "ACCOUNT_DEACTIVATED", "ACCOUNT_SUSPENDED", "ACCOUNT_BLOCKED",
)
private val BLOCKED_STATUSES = setOf("DELETED", "DEACTIVATED", "SUSPENDED", "DISABLED", "BLOCKED")

/**
 * Lenient on purpose: any 4xx whose errorCode / reason is an ACCOUNT_* block code, or whose
 * details.accountStatus is not ACTIVE, is a blocked account (spec says 403, servers vary).
 */
fun isAccountBlocked(httpCode: Int, errorCode: String?, reason: String? = null, details: JsonElement? = null): Boolean {
    if (httpCode !in 400..499) return false
    if (errorCode?.uppercase() in BLOCKED_CODES || reason?.uppercase() in BLOCKED_CODES) return true
    val status = ((details as? JsonObject)?.get("accountStatus") as? JsonPrimitive)?.contentOrNull?.uppercase()
    return status in BLOCKED_STATUSES
}

/**
 * 403 ACCOUNT_DELETED / ACCOUNT_DISABLED → [AccountBlockedInfo]. Works without "details"
 * (status from the errorCode, blank title → the UI uses the strings.xml fallback).
 */
fun accountBlockedInfo(json: Json, errorCode: String?, message: String, details: JsonElement?): AccountBlockedInfo {
    val dto = (details as? JsonObject)?.let {
        runCatching { json.decodeFromJsonElement(AccountBlockedDetailsDto.serializer(), it) }.getOrNull()
    }
    val code = errorCode?.uppercase()
    val status = dto?.accountStatus?.let(AccountStatus::fromServer)
        ?.takeIf { it != AccountStatus.ACTIVE } // a blocked response is never "active"
        ?: when (code) {
            "ACCOUNT_DELETED" -> AccountStatus.DELETED
            "ACCOUNT_SUSPENDED" -> AccountStatus.SUSPENDED
            else -> AccountStatus.DEACTIVATED
        }
    return AccountBlockedInfo(
        status = status,
        title = dto?.title?.trim().orEmpty(),
        message = message.trim(),
        reason = dto?.reason?.trim()?.takeIf { it.isNotEmpty() },
        changedAtMillis = parseIsoMillis(dto?.at),
        support = dto?.support?.let {
            SupportContact(
                email = it.email?.trim()?.takeIf(String::isNotEmpty),
                phone = it.phone?.trim()?.takeIf(String::isNotEmpty),
                whatsapp = it.whatsapp?.trim()?.takeIf(String::isNotEmpty),
                helpCenterUrl = it.helpCenterUrl?.trim()?.takeIf(String::isNotEmpty),
            )
        },
    )
}

/** Raw error body (interceptor) → info, or null when it isn't a blocked-account error. */
fun accountBlockedFromBody(json: Json, httpCode: Int, body: String?): AccountBlockedInfo? {
    if (httpCode !in 400..499 || body.isNullOrBlank()) return null
    val dto = runCatching { json.decodeFromString(ApiErrorDto.serializer(), body) }.getOrNull() ?: return null
    if (!isAccountBlocked(httpCode, dto.errorCode, dto.reason, dto.details)) return null
    val code = dto.errorCode?.takeIf { it.uppercase() in BLOCKED_CODES } ?: dto.reason
    return accountBlockedInfo(json, code, dto.message, dto.details)
}

/** Any API error → [AppError.AccountBlocked] when it's a 403 ACCOUNT_*; otherwise unchanged. */
fun AppError.toAccountBlockedOrSelf(json: Json): AppError {
    if (this !is AppError.Server) return this
    val detailsJson = details?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
    if (!isAccountBlocked(httpCode, errorCode, reason, detailsJson)) return this
    val code = errorCode?.takeIf { it.uppercase() in BLOCKED_CODES } ?: reason
    return AppError.AccountBlocked(accountBlockedInfo(json, code, message, detailsJson))
}

/** "2026-11-14T10:32:05.120Z" (with or without millis) → epoch ms, null if missing / bad. */
internal fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso.trim())?.time
        }.getOrNull()
    }
}
