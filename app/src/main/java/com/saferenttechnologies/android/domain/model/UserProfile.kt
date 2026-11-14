package com.saferenttechnologies.android.domain.model

enum class UserProfileField(val serverKey: String) {
    FULL_NAME("fullName"),
    ROLE("role"),
    GENDER("gender"),
    DATE_OF_BIRTH("dateOfBirth"),
    CITY("city"),
    STATE("state"),
    EMAIL("email"),
    OCCUPATION("occupation"),
    REFERRAL_CODE("referralCode"),

    /** Terms + Privacy missing (ticked on the login screen, not here). */
    CONSENTS("consents"),
    ;

    companion object {
        fun fromServer(key: String): UserProfileField? = entries.firstOrNull { it.serverKey.equals(key.trim(), true) }
    }
}

/** What the user typed on the user profile screen. */
data class UserProfileForm(
    val fullName: String = "",
    val role: UserRole? = null,
    val gender: Gender? = null,
    /** "YYYY-MM-DD" */
    val dateOfBirth: String = "",
    val city: String = "",
    val state: String = "",
    val email: String = "",
    val occupation: String = "",
    val referralCode: String = "",
) {
    companion object {
        fun from(user: AccountUser?) = UserProfileForm(
            fullName = user?.fullName.orEmpty(),
            role = user?.role,
            gender = user?.gender,
            dateOfBirth = user?.dateOfBirth.orEmpty(),
            city = user?.city.orEmpty(),
            state = user?.state.orEmpty(),
            email = user?.email.orEmpty(),
            occupation = user?.occupation.orEmpty(),
        )
    }
}

/** Simple calendar date (minSdk 24 → no java.time). */
data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int =
        compareValuesBy(this, other, SimpleDate::year, SimpleDate::month, SimpleDate::day)

    /** "YYYY-MM-DD" */
    fun iso(): String = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day)

    companion object {
        private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

        /** Parses "YYYY-MM-DD"; null when malformed or not a real date. */
        fun parse(value: String): SimpleDate? {
            val m = ISO.matchEntire(value.trim()) ?: return null
            val (y, mo, d) = m.destructured.toList().map(String::toInt)
            if (mo !in 1..12 || d < 1 || d > daysIn(y, mo)) return null
            return SimpleDate(y, mo, d)
        }

        private fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

object UserProfileValidator {
    const val MIN_AGE = 18
    private val EMAIL = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")

    /** Full years between [dob] and [today]. */
    fun age(dob: SimpleDate, today: SimpleDate): Int {
        val beforeBirthday = today.month < dob.month || (today.month == dob.month && today.day < dob.day)
        return today.year - dob.year - if (beforeBirthday) 1 else 0
    }

    /** Field → message. Empty map = valid. */
    fun validate(form: UserProfileForm, today: SimpleDate): Map<UserProfileField, String> = buildMap {
        if (form.fullName.isBlank()) put(UserProfileField.FULL_NAME, "Enter your full name")
        if (form.role == null) put(UserProfileField.ROLE, "Choose landlord or tenant")
        if (form.gender == null) put(UserProfileField.GENDER, "Select your gender")
        val dob = SimpleDate.parse(form.dateOfBirth)
        when {
            form.dateOfBirth.isBlank() -> put(UserProfileField.DATE_OF_BIRTH, "Select your date of birth")
            dob == null || dob > today -> put(UserProfileField.DATE_OF_BIRTH, "Enter a valid date of birth")
            age(dob, today) < MIN_AGE -> put(UserProfileField.DATE_OF_BIRTH, "You must be at least $MIN_AGE years old")
        }
        if (form.city.isBlank()) put(UserProfileField.CITY, "Enter your city")
        if (form.state.isBlank()) put(UserProfileField.STATE, "Enter your state")
        val email = form.email.trim()
        if (email.isNotEmpty() && !EMAIL.matches(email)) put(UserProfileField.EMAIL, "Enter a valid email")
    }
}
