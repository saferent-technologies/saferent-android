package com.saferenttechnologies.android.domain.model

/**
 * Server-driven app configuration (GET api/v1/app/config → "data").
 *
 * Every field has a safe default: an empty cache or a missing field never crashes and
 * the app keeps working with these values.
 *
 * String "enums" (kept as String so new server values never break old app versions):
 * - [ConvenienceFee.type]: NONE | PERCENT | FLAT
 * - [DocumentSettings.defaultVisibility]: PRIVATE | SHARED_WITH_TENANT
 * - [OnboardingScreen.mediaType]: NONE | IMAGE | VIDEO | LOTTIE
 *
 * Media URLs are always full URLs or "" (empty = use the app's built-in default).
 */
data class AppConfig(
    val configVersion: Int = 0,
    val updatedAt: String? = null,
    val app: AppInfo = AppInfo(),
    val maintenance: Maintenance = Maintenance(),
    val features: Features = Features(),
    val auth: AuthSettings = AuthSettings(),
    val properties: PropertySettings = PropertySettings(),
    val documents: DocumentSettings = DocumentSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val payments: PaymentSettings = PaymentSettings(),
    val referrals: ReferralSettings = ReferralSettings(),
    val support: SupportSettings = SupportSettings(),
    val splash: SplashSettings = SplashSettings(),
    val onboarding: OnboardingSettings = OnboardingSettings(),
    val ui: UiSettings = UiSettings(),
    val legal: LegalSettings = LegalSettings(),
)

data class AppInfo(
    val name: String = "SafeRent",
    val packageName: String = "com.saferenttechnologies.android",
    val latestVersion: String = "",
    val minimumSupportedVersion: String = "",
    val forceUpdate: Boolean = false,
    val updateMessage: String = "",
    val playStoreUrl: String = "",
) {
    /** True when [currentVersion] is below the minimum, or below latest while [forceUpdate] is on. */
    fun isUpdateRequired(currentVersion: String): Boolean {
        val belowMinimum = minimumSupportedVersion.isNotBlank() &&
            compareVersions(currentVersion, minimumSupportedVersion) < 0
        val forcedToLatest = forceUpdate && latestVersion.isNotBlank() &&
            compareVersions(currentVersion, latestVersion) < 0
        return belowMinimum || forcedToLatest
    }

    /** True when a newer version than [currentVersion] exists (optional update). */
    fun isUpdateAvailable(currentVersion: String): Boolean =
        latestVersion.isNotBlank() && compareVersions(currentVersion, latestVersion) < 0

    companion object {
        /** Compares "1.2.10" style versions numerically; "1.0" == "1.0.0"; suffixes like "-beta" are ignored. */
        fun compareVersions(a: String, b: String): Int {
            val left = a.toVersionParts()
            val right = b.toVersionParts()
            for (i in 0 until maxOf(left.size, right.size)) {
                val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }

        private fun String.toVersionParts(): List<Int> =
            trim().substringBefore('-').substringBefore('+').split('.')
                .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}

data class Maintenance(
    val enabled: Boolean = false,
    val title: String = "",
    val message: String = "",
    val startsAt: String? = null,
    val expectedEndAt: String? = null,
)

data class Features(
    val properties: Boolean = true,
    val tenants: Boolean = true,
    val landlords: Boolean = true,
    val rentManagement: Boolean = true,
    val rentPayments: Boolean = true,
    val rentReminders: Boolean = true,
    val documents: Boolean = true,
    val rentalAgreements: Boolean = true,
    val electricityBills: Boolean = true,
    val propertyTaxDocuments: Boolean = true,
    val otherDocuments: Boolean = true,
    val notifications: Boolean = true,
    val referrals: Boolean = true,
    val support: Boolean = true,
    val chat: Boolean = false,
    /** Boolean flags the server sent that this app version doesn't know as fields yet. */
    val extra: Map<String, Boolean> = emptyMap(),
) {
    fun asMap(): Map<String, Boolean> = extra + mapOf(
        "properties" to properties,
        "tenants" to tenants,
        "landlords" to landlords,
        "rentManagement" to rentManagement,
        "rentPayments" to rentPayments,
        "rentReminders" to rentReminders,
        "documents" to documents,
        "rentalAgreements" to rentalAgreements,
        "electricityBills" to electricityBills,
        "propertyTaxDocuments" to propertyTaxDocuments,
        "otherDocuments" to otherDocuments,
        "notifications" to notifications,
        "referrals" to referrals,
        "support" to support,
        "chat" to chat,
    )

    /** Unknown key → true (the server is the one that enforces access). */
    fun isEnabled(key: String): Boolean = asMap()[key] ?: true

    companion object {
        val KNOWN_KEYS: Set<String> = Features().asMap().keys
    }
}

data class AuthSettings(
    val emailLogin: Boolean = true,
    val phoneLogin: Boolean = true,
    val emailOtp: Boolean = true,
    val phoneOtp: Boolean = true,
    val googleLogin: Boolean = false,
    val otpExpiryMinutes: Int = 5,
    val otpResendSeconds: Int = 60,
    val otpMaxAttempts: Int = 5,
)

data class PropertyType(
    val key: String = "",
    val label: String = "",
    val category: String = "",
    val structure: String = "",
)

data class PropertySettings(
    val enabledTypes: List<PropertyType> = emptyList(),
    val maxImages: Int = 40,
    val maxDocuments: Int = 50,
    val maxImageSizeMB: Int = 5,
    val allowedImageFormats: List<String> = listOf("jpg", "jpeg", "png", "webp"),
)

data class DocumentSettings(
    val agreement: Boolean = true,
    val electricityBill: Boolean = true,
    val propertyTax: Boolean = true,
    val otherDocuments: Boolean = true,
    val customNameRequired: Boolean = true,
    val maxFileSizeMB: Int = 10,
    val allowedFormats: List<String> = listOf("pdf", "jpg", "jpeg", "png"),
    val notifyTenantOnUpload: Boolean = true,
    val defaultVisibility: String = "PRIVATE",
)

data class NotificationSettings(
    val pushEnabled: Boolean = true,
    val rentDue: Boolean = true,
    val rentOverdue: Boolean = true,
    val paymentSuccess: Boolean = true,
    val documentUploaded: Boolean = true,
    val agreementUploaded: Boolean = true,
    val propertyAssigned: Boolean = true,
    val maintenance: Boolean = true,
)

data class ConvenienceFee(
    val type: String = "NONE",
    val value: Double = 0.0,
    val maxAmount: Double? = null,
)

data class PaymentSettings(
    val enabled: Boolean = true,
    val razorpay: Boolean = true,
    val upi: Boolean = true,
    val card: Boolean = true,
    val netbanking: Boolean = true,
    val currency: String = "INR",
    val convenienceFee: ConvenienceFee = ConvenienceFee(),
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
)

data class ReferralProgram(
    val type: String = "",
    val rewardAmount: Double? = null,
    val durationMonths: Int? = null,
)

data class ReferralSettings(
    val enabled: Boolean = true,
    val minSuccessfulReferrals: Int = 1,
    val eligibilityRules: String = "",
    val programs: List<ReferralProgram> = emptyList(),
)

data class SupportSettings(
    val email: String = "support@saferenttechnologies.com",
    val phone: String = "",
    val whatsapp: String = "",
    val helpCenterUrl: String = "",
    val faqEnabled: Boolean = false,
)

data class SplashSettings(
    /** Content version (e.g. "1.4"). "" = unknown / built-in default. */
    val version: String = "",
    val enabled: Boolean = true,
    val title: String = "SafeRent",
    val description: String = "",
    val logoUrl: String = "",
    val lottieUrl: String = "",
    val minDurationMs: Long = 1500L,
)

data class OnboardingScreen(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val mediaType: String = "NONE",
    val mediaUrl: String = "",
)

data class OnboardingSettings(
    /** Content version (e.g. "2.0"). "" = unknown / built-in default. */
    val version: String = "",
    val enabled: Boolean = false,
    val showOnce: Boolean = true,
    val skippable: Boolean = true,
    val contentId: String = "",
    val screens: List<OnboardingScreen> = emptyList(),
)

data class UiSettings(
    val primaryColor: String = "#F97316",
    val secondaryColor: String = "#111827",
    val accentColor: String = "#FB923C",
    val logoUrl: String = "",
    val darkMode: Boolean = true,
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val bannerEnabled: Boolean = false,
    val bannerImageUrl: String = "",
    val bannerLinkUrl: String = "",
)

data class LegalSettings(
    val privacyPolicyUrl: String = "",
    val termsUrl: String = "",
    val refundPolicyUrl: String = "",
)
