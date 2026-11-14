package com.saferenttechnologies.android.domain.model

/** Backend account status. Missing (new user) → ACTIVE; an unknown value → DEACTIVATED (safe side). */
enum class AccountStatus {
    ACTIVE, SUSPENDED, DEACTIVATED, DELETED;

    companion object {
        fun fromServer(value: String?): AccountStatus {
            val v = value?.trim()?.uppercase()
            if (v.isNullOrEmpty()) return ACTIVE
            return entries.firstOrNull { it.name == v } ?: DEACTIVATED
        }
    }
}

/** Support channels from the blocked-account response. Every field may be missing. */
data class SupportContact(
    val email: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val helpCenterUrl: String? = null,
) {
    val hasAny: Boolean
        get() = listOf(email, phone, whatsapp, helpCenterUrl).any { !it.isNullOrBlank() }
}

/**
 * Why this account can't be used. [title] / [message] may be blank → the UI shows the
 * strings.xml fallback for [status]. [changedAtMillis] = when the status changed (UTC epoch ms).
 * (minSdk 24 → epoch millis instead of java.time.Instant.)
 */
data class AccountBlockedInfo(
    val status: AccountStatus,
    val title: String = "",
    val message: String = "",
    val reason: String? = null,
    val changedAtMillis: Long? = null,
    val support: SupportContact? = null,
)

/** What to do after POST auth/phone/complete. */
sealed interface LoginResult {
    data class GoToDashboard(val completion: PhoneCompletion) : LoginResult
    data class GoToUserProfile(val completion: PhoneCompletion) : LoginResult
    data class Blocked(val info: AccountBlockedInfo) : LoginResult
}
