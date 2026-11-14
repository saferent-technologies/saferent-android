package com.saferenttechnologies.android.domain.model

/** Server role string ("LANDLORD" / "tenant") → [UserRole], unknown → null. */
fun userRoleOf(value: String?): UserRole? =
    value?.trim()?.uppercase()?.let { v -> UserRole.entries.firstOrNull { it.name == v } }

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    PREFER_NOT_TO_SAY("Prefer not to say"),
    ;

    companion object {
        fun of(value: String?): Gender? =
            value?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } }
    }
}

/** The signed-in SafeRent account (GET auth/me → user). */
data class AccountUser(
    val id: String = "",
    val phone: String? = null,
    val fullName: String = "",
    val role: UserRole? = null,
    val roles: List<UserRole> = emptyList(),
    val gender: Gender? = null,
    /** "YYYY-MM-DD" */
    val dateOfBirth: String = "",
    val city: String = "",
    val state: String = "",
    val email: String = "",
    val occupation: String = "",
    val isProfileCompleted: Boolean = false,
    /** auth/me top-level "termsAccepted" — display / diagnostics only. */
    val termsAccepted: Boolean = false,
) {
    /** All roles of the account; falls back to [role] when the server sends no list. */
    val allRoles: List<UserRole>
        get() = roles.ifEmpty { listOfNotNull(role) }
}

/** POST auth/phone/complete (right after Firebase OTP). */
data class PhoneCompletion(
    val ok: Boolean = true,
    val registrationRequired: Boolean = false,
    val mustChangePassword: Boolean = false,
    val isProfileCompleted: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val termsAccepted: Boolean = false,
    val fcmTokenSaved: Boolean = false,
)

/** POST userProfile body. Null = sent as null. Skip sends only [isProfileCompleted] + [role]. */
data class UserProfileRequest(
    val isProfileCompleted: Boolean,
    val role: UserRole?,
    val fullName: String? = null,
    val gender: Gender? = null,
    val dateOfBirth: String? = null,
    val city: String? = null,
    val state: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val referralCode: String? = null,
    /** Login-screen checkboxes; null = not sent. */
    val consents: List<ConsentItem>? = null,
    val clientAcceptedAt: Long? = null,
)

/** POST userProfile response. [created] = 201 (new account) vs 200 (existing). */
data class UserProfileResult(
    val user: AccountUser,
    val isProfileCompleted: Boolean,
    val missingFields: List<String>,
    val role: UserRole?,
    val roles: List<UserRole>,
    val termsAccepted: Boolean,
    val consents: ConsentStatus,
    val created: Boolean,
)

/** Which role's dashboard to open: saved choice if the account still has it, else user.role. */
object ActiveRole {
    fun resolve(saved: UserRole?, user: AccountUser): UserRole? =
        saved?.takeIf { it in user.allRoles } ?: user.role ?: user.allRoles.firstOrNull()
}

/** Where to go after sign-in (or app start while signed in). */
sealed interface PostLoginDestination {
    data class Dashboard(val user: AccountUser, val activeRole: UserRole?) : PostLoginDestination

    data class UserProfile(
        /** Prefill (null when the account doesn't exist yet). */
        val user: AccountUser?,
        val registrationRequired: Boolean,
        val skippable: Boolean,
    ) : PostLoginDestination
}
