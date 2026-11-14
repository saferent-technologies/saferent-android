package com.saferenttechnologies.android.domain.model

/** Who is signing up — sent with the profile later. */
enum class UserRole { TENANT, LANDLORD }

data class AuthUser(
    val uid: String,
    val phoneNumber: String?,
)

/** Indian mobile number rules (+91). */
object IndianPhone {
    const val COUNTRY_CODE = "+91"
    const val LENGTH = 10

    /** Keeps digits only, max 10 (also drops a pasted +91 / leading 0). */
    fun sanitize(input: String): String {
        var digits = input.filter(Char::isDigit)
        if (digits.length > LENGTH && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length > LENGTH && digits.startsWith("0")) digits = digits.drop(1)
        return digits.take(LENGTH)
    }

    /** 10 digits, starting 6–9 (all Indian mobile numbers). */
    fun isValid(digits: String): Boolean = digits.length == LENGTH && digits.first() in '6'..'9'

    fun toE164(digits: String): String = "$COUNTRY_CODE$digits"

    /** "98490 12345" */
    fun format(digits: String): String =
        if (digits.length <= 5) digits else digits.take(5) + " " + digits.drop(5)
}

/** Phone sign-in failures, with a message that is safe to show. */
sealed interface AuthError {
    val message: String

    data class InvalidPhone(override val message: String = "Please enter a valid mobile number.") : AuthError
    data class InvalidCode(override val message: String = "That code is incorrect. Please try again.") : AuthError
    data class CodeExpired(override val message: String = "This code has expired. Please request a new one.") : AuthError
    data class TooManyRequests(
        override val message: String = "Too many attempts. Please wait a while and try again.",
    ) : AuthError
    data class Network(override val message: String = "No internet connection. Please check your network.") : AuthError
    data class Unknown(override val message: String = "Something went wrong. Please try again.") : AuthError
}

/** Events while an OTP is being sent / auto-detected (Firebase phone auth). */
sealed interface PhoneAuthEvent {
    /** SMS sent — the user can type the code. */
    data class CodeSent(val verificationId: String) : PhoneAuthEvent

    /** Android read the SMS automatically — fill the boxes with this code. */
    data class CodeAutoRetrieved(val code: String) : PhoneAuthEvent

    /** Auto-detection window ended — the user must type the code. */
    data object AutoRetrievalTimedOut : PhoneAuthEvent

    /** Signed in without typing (instant verification / auto-retrieval). */
    data class SignedIn(val user: AuthUser) : PhoneAuthEvent

    data class Failed(val error: AuthError) : PhoneAuthEvent
}

sealed interface VerifyOtpResult {
    data class Success(val user: AuthUser) : VerifyOtpResult
    data class Failed(val error: AuthError) : VerifyOtpResult
}
