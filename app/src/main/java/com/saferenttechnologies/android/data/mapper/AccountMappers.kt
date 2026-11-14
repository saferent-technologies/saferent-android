package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.dto.AcceptedDto
import com.saferenttechnologies.android.data.remote.dto.ConsentStatusDto
import com.saferenttechnologies.android.data.remote.dto.MeDto
import com.saferenttechnologies.android.data.remote.dto.PLATFORM_ANDROID
import com.saferenttechnologies.android.data.remote.dto.PhoneCompleteDto
import com.saferenttechnologies.android.data.remote.dto.UserDto
import com.saferenttechnologies.android.data.remote.dto.UserProfileResultDto
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.ConsentStatus
import com.saferenttechnologies.android.domain.model.Gender
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.PolicyAcceptance
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.UserProfileResult
import com.saferenttechnologies.android.domain.model.WhatsappConsent
import com.saferenttechnologies.android.domain.model.userRoleOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

fun PhoneCompleteDto.toDomain() = PhoneCompletion(
    ok = ok,
    registrationRequired = registrationRequired,
    mustChangePassword = mustChangePassword,
    isProfileCompleted = isProfileCompleted,
    missingFields = missingFields,
    termsAccepted = termsAccepted,
    fcmTokenSaved = fcmTokenSaved,
)

fun UserDto.toDomain() = AccountUser(
    id = id.orEmpty(),
    phone = phone,
    fullName = fullName.orEmpty(),
    role = userRoleOf(role),
    roles = roles.mapNotNull(::userRoleOf).distinct(),
    gender = Gender.of(gender),
    dateOfBirth = dateOfBirth.orEmpty().take(10), // "1998-04-12T00:00:00Z" → "1998-04-12"
    city = city.orEmpty(),
    state = state.orEmpty(),
    email = email.orEmpty(),
    occupation = occupation.orEmpty(),
    isProfileCompleted = isProfileCompleted,
)

private fun AcceptedDto.toDomain() = PolicyAcceptance(accepted, acceptedVersion, acceptedAt)

fun ConsentStatusDto.toDomain() = ConsentStatus(
    terms = terms.toDomain(),
    privacy = privacy.toDomain(),
    whatsapp = WhatsappConsent(whatsapp.granted, whatsapp.updatedAt),
)

fun UserProfileResultDto.toDomain(httpCode: Int): UserProfileResult {
    val role = userRoleOf(role) ?: userRoleOf(user?.role)
    val roles = roles.mapNotNull(::userRoleOf).distinct()
    val mappedUser = (user ?: UserDto()).toDomain().let { u ->
        u.copy(
            role = u.role ?: role,
            roles = u.roles.ifEmpty { roles },
            isProfileCompleted = isProfileCompleted,
            termsAccepted = termsAccepted,
        )
    }
    return UserProfileResult(
        user = mappedUser,
        isProfileCompleted = isProfileCompleted,
        missingFields = missingFields,
        role = role,
        roles = roles.ifEmpty { mappedUser.allRoles },
        termsAccepted = termsAccepted,
        consents = consents.toDomain(),
        created = httpCode == 201,
    )
}

/** auth/me data: `{ "user": {...}, "termsAccepted": true }` or the user object itself. */
fun JsonElement.toMeUser(json: Json): AccountUser? {
    val obj = this as? JsonObject ?: return null
    return runCatching {
        if (obj["user"] is JsonObject) {
            val me = json.decodeFromJsonElement(MeDto.serializer(), obj)
            me.user?.toDomain()?.copy(termsAccepted = me.termsAccepted)
        } else {
            json.decodeFromJsonElement(UserDto.serializer(), obj).toDomain()
        }
    }.getOrNull()
}

/**
 * Body for POST userProfile; null values are sent as JSON null.
 * Skip (isProfileCompleted = false) sends only isProfileCompleted + role.
 */
fun UserProfileRequest.toJson(appVersion: String): JsonObject = buildJsonObject {
    put("isProfileCompleted", isProfileCompleted)
    put("role", role?.name)
    if (!isProfileCompleted) return@buildJsonObject
    put("fullName", fullName)
    put("gender", gender?.name)
    put("dateOfBirth", dateOfBirth)
    put("city", city)
    put("state", state)
    put("email", email)
    put("occupation", occupation)
    put("referralCode", referralCode)
    consents?.let { items ->
        putJsonArray("consents") {
            items.forEach { c ->
                addJsonObject {
                    put("type", c.type.name)
                    put("version", c.version)
                    put("granted", c.granted)
                }
            }
        }
        put("clientAcceptedAt", clientAcceptedAt)
    }
    put("source", PLATFORM_ANDROID)
    put("appVersion", appVersion)
}

/**
 * Account-specific server errors:
 * - LOGIN_METHOD_DISABLED → [AppError.LoginMethodDisabled]
 * - 409 EMAIL_IN_USE → [AppError.EmailInUse]
 * - 400 (VALIDATION_ERROR) → [AppError.Validation] with details.missingFields
 */
fun AppError.toAccountError(json: Json): AppError {
    if (this !is AppError.Server) return this
    return when {
        errorCode == "LOGIN_METHOD_DISABLED" -> AppError.LoginMethodDisabled()
        errorCode == "EMAIL_IN_USE" || (httpCode == 409 && reason == "EMAIL_IN_USE") ->
            AppError.EmailInUse(message.takeIf { it.isNotBlank() && !it.startsWith("Server error") } ?: AppError.EmailInUse().message)
        httpCode == 400 -> AppError.Validation(message, missingFieldsOf(details, json))
        else -> this
    }
}

private fun missingFieldsOf(details: String?, json: Json): List<String> {
    if (details.isNullOrBlank()) return emptyList()
    val element = runCatching { json.parseToJsonElement(details) }.getOrNull() ?: return emptyList()
    val array = when (element) {
        is JsonObject -> element["missingFields"] as? JsonArray
        is JsonArray -> element
        else -> null
    } ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
