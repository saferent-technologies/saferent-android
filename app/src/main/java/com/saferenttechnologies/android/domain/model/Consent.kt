package com.saferenttechnologies.android.domain.model

enum class ConsentType { TERMS, PRIVACY, WHATSAPP }

/** One consent sent with POST userProfile. [version] is "" for WHATSAPP. */
data class ConsentItem(val type: ConsentType, val version: String, val granted: Boolean)

/**
 * What the user ticked on the login screen. Sent later with the user profile.
 * [consentTimeMillis] = when Terms was ticked (System.currentTimeMillis()).
 */
data class LoginConsent(
    val termsAccepted: Boolean,
    val whatsappOptIn: Boolean,
    val consentTimeMillis: Long,
)

data class PolicyAcceptance(
    val accepted: Boolean = false,
    val acceptedVersion: String? = null,
    /** ISO-8601 server time, null if never accepted. */
    val acceptedAt: String? = null,
)

data class WhatsappConsent(
    val granted: Boolean = false,
    /** ISO-8601 server time of the last change. */
    val updatedAt: String? = null,
)

/** userProfile response → consents. */
data class ConsentStatus(
    val terms: PolicyAcceptance = PolicyAcceptance(),
    val privacy: PolicyAcceptance = PolicyAcceptance(),
    val whatsapp: WhatsappConsent = WhatsappConsent(),
)
