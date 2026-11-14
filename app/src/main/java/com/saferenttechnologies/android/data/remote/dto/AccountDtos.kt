package com.saferenttechnologies.android.data.remote.dto

import kotlinx.serialization.Serializable

/** Body of POST auth/phone/complete and PUT devices/fcm-token. */
@Serializable
data class FcmTokenBody(
    val fcmToken: String,
    val platform: String = PLATFORM_ANDROID,
    val appVersion: String? = null,
)

/** Body of POST auth/logout. */
@Serializable
data class LogoutBody(val fcmToken: String? = null)

/** POST api/v1/auth/phone/complete → data */
@Serializable
data class PhoneCompleteDto(
    /** ACTIVE | SUSPENDED | DEACTIVATED | DELETED — missing for a new user. */
    val accountStatus: String? = null,
    val ok: Boolean = true,
    val registrationRequired: Boolean = false,
    val mustChangePassword: Boolean = false,
    val isProfileCompleted: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val termsAccepted: Boolean = false,
    val fcmTokenSaved: Boolean = false,
)

/** PUT api/v1/devices/fcm-token → data */
@Serializable
data class FcmTokenResultDto(
    val ok: Boolean = false,
    val fcmTokenSaved: Boolean = false,
)

/** Backend user (auth/me → data.user, userProfile → data.user). Unknown keys are ignored. */
@Serializable
data class UserDto(
    val id: String? = null,
    val phone: String? = null,
    val fullName: String? = null,
    val role: String? = null,
    val roles: List<String> = emptyList(),
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val city: String? = null,
    val state: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val isProfileCompleted: Boolean = false,
)

/** GET api/v1/auth/me → data */
@Serializable
data class MeDto(
    val user: UserDto? = null,
    val termsAccepted: Boolean = false,
)

/** POST api/v1/userProfile → data (201 new account / 200 existing). */
@Serializable
data class UserProfileResultDto(
    val user: UserDto? = null,
    val isProfileCompleted: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val role: String? = null,
    val roles: List<String> = emptyList(),
    val termsAccepted: Boolean = false,
    val consents: ConsentStatusDto = ConsentStatusDto(),
)

/** 403 ACCOUNT_DELETED / ACCOUNT_DISABLED → error "details". Every field may be null. */
@Serializable
data class AccountBlockedDetailsDto(
    val accountStatus: String? = null,
    val title: String? = null,
    val reason: String? = null,
    /** ISO-8601 UTC, e.g. "2026-11-14T10:32:05.120Z" */
    val at: String? = null,
    val support: SupportContactDto? = null,
)

@Serializable
data class SupportContactDto(
    val email: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val helpCenterUrl: String? = null,
)

const val PLATFORM_ANDROID = "ANDROID"
