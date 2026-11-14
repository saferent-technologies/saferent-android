package com.saferenttechnologies.android.data.remote.dto

import kotlinx.serialization.Serializable

// Field names and types match GET api/v1/app/config → "data" exactly.
// Every field has a default so a missing field never crashes (and Json.coerceInputValues
// turns an unexpected null for a non-null field into its default).

@Serializable
data class ConfigDataDto(
    val configVersion: Int = 0,
    val updatedAt: String? = null,
    val app: AppInfoDto = AppInfoDto(),
    val maintenance: MaintenanceDto = MaintenanceDto(),
    val features: FeaturesDto = FeaturesDto(),
    val auth: AuthDto = AuthDto(),
    val properties: PropertiesDto = PropertiesDto(),
    val documents: DocumentsDto = DocumentsDto(),
    val notifications: NotificationsDto = NotificationsDto(),
    val payments: PaymentsDto = PaymentsDto(),
    val referrals: ReferralsDto = ReferralsDto(),
    val support: SupportDto = SupportDto(),
    val splash: SplashDto = SplashDto(),
    val onboarding: OnboardingDto = OnboardingDto(),
    val ui: UiDto = UiDto(),
    val legal: LegalDto = LegalDto(),
)

@Serializable
data class AppInfoDto(
    val name: String = "SafeRent",
    val packageName: String = "com.saferenttechnologies.android",
    val latestVersion: String = "",
    val minimumSupportedVersion: String = "",
    val forceUpdate: Boolean = false,
    val updateMessage: String = "",
    val playStoreUrl: String = "",
)

@Serializable
data class MaintenanceDto(
    val enabled: Boolean = false,
    val title: String = "",
    val message: String = "",
    val startsAt: String? = null,
    val expectedEndAt: String? = null,
)

@Serializable
data class FeaturesDto(
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
)

@Serializable
data class AuthDto(
    val emailLogin: Boolean = true,
    val phoneLogin: Boolean = true,
    val emailOtp: Boolean = true,
    val phoneOtp: Boolean = true,
    val googleLogin: Boolean = false,
    val otpExpiryMinutes: Int = 5,
    val otpResendSeconds: Int = 60,
    val otpMaxAttempts: Int = 5,
    /** Ask for demographics (name, etc.) after sign-in. */
    val showDemographics: Boolean = true,
    /** User may skip the demographics step. */
    val demographicsSkippable: Boolean = true,
)

@Serializable
data class PropertyTypeDto(
    val key: String = "",
    val label: String = "",
    val category: String = "",
    val structure: String = "",
)

@Serializable
data class PropertiesDto(
    val enabledTypes: List<PropertyTypeDto> = emptyList(),
    val maxImages: Int = 40,
    val maxDocuments: Int = 50,
    val maxImageSizeMB: Int = 5,
    val allowedImageFormats: List<String> = listOf("jpg", "jpeg", "png", "webp"),
)

@Serializable
data class DocumentsDto(
    val agreement: Boolean = true,
    val electricityBill: Boolean = true,
    val propertyTax: Boolean = true,
    val otherDocuments: Boolean = true,
    val customNameRequired: Boolean = true,
    val maxFileSizeMB: Int = 10,
    val allowedFormats: List<String> = listOf("pdf", "jpg", "jpeg", "png"),
    val notifyTenantOnUpload: Boolean = true,
    /** PRIVATE | SHARED_WITH_TENANT */
    val defaultVisibility: String = "PRIVATE",
)

@Serializable
data class NotificationsDto(
    val pushEnabled: Boolean = true,
    val rentDue: Boolean = true,
    val rentOverdue: Boolean = true,
    val paymentSuccess: Boolean = true,
    val documentUploaded: Boolean = true,
    val agreementUploaded: Boolean = true,
    val propertyAssigned: Boolean = true,
    val maintenance: Boolean = true,
)

@Serializable
data class ConvenienceFeeDto(
    /** NONE | PERCENT | FLAT */
    val type: String = "NONE",
    val value: Double = 0.0,
    val maxAmount: Double? = null,
)

@Serializable
data class PaymentsDto(
    val enabled: Boolean = true,
    val razorpay: Boolean = true,
    val upi: Boolean = true,
    val card: Boolean = true,
    val netbanking: Boolean = true,
    val currency: String = "INR",
    val convenienceFee: ConvenienceFeeDto = ConvenienceFeeDto(),
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
)

@Serializable
data class ReferralProgramDto(
    val type: String = "",
    val rewardAmount: Double? = null,
    val durationMonths: Int? = null,
)

@Serializable
data class ReferralsDto(
    val enabled: Boolean = true,
    val minSuccessfulReferrals: Int = 1,
    val eligibilityRules: String = "",
    val programs: List<ReferralProgramDto> = emptyList(),
)

@Serializable
data class SupportDto(
    val email: String = "support@saferenttechnologies.com",
    val phone: String = "",
    val whatsapp: String = "",
    val helpCenterUrl: String = "",
    val faqEnabled: Boolean = false,
)

@Serializable
data class SplashDto(
    /** Content version of the splash (e.g. "1.4"); changes when admins edit it. */
    val version: String = "1.0",
    val enabled: Boolean = true,
    val title: String = "SafeRent",
    val description: String = "Verified homes. Honest renting.",
    /** Logo for the light theme. "" → app's bundled logo. PNG / JPG / WEBP (no SVG). */
    val lightLogoUrl: String = "",
    /** Logo for the dark theme. "" → falls back to [lightLogoUrl], then the bundled logo. */
    val darkLogoUrl: String = "",
    val lottieUrl: String = "",
    val minDurationMs: Long = 1500L,
)

@Serializable
data class OnboardingScreenDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    /** Primary button label ("Next"; the server sends "Get started" on the last screen). */
    val buttonText: String = "Next",
    /** NONE | IMAGE | VIDEO | LOTTIE — only the URL matching this type is filled. */
    val mediaType: String = "NONE",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val lottieUrl: String = "",
)

@Serializable
data class OnboardingDto(
    /** Content version of onboarding ("1.0" … "2.0"); compared with == only. */
    val version: String = "1.0",
    /** Missing onboarding → true, so the app's default onboarding is shown. */
    val enabled: Boolean = true,
    val showOnce: Boolean = true,
    val skippable: Boolean = true,
    val skipButtonText: String = "Skip",
    val screens: List<OnboardingScreenDto> = emptyList(),
)

@Serializable
data class UiDto(
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

@Serializable
data class LegalDto(
    val privacyPolicyUrl: String = "",
    val termsUrl: String = "",
    val refundPolicyUrl: String = "",
    /** Current Terms of Service version — users must have accepted this one. */
    val termsVersion: String = "2026-11-01",
    /** Current Privacy Policy version. */
    val privacyVersion: String = "2026-11-01",
)
