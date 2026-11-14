package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.dto.AppInfoDto
import com.saferenttechnologies.android.data.remote.dto.AuthDto
import com.saferenttechnologies.android.data.remote.dto.ConfigDataDto
import com.saferenttechnologies.android.data.remote.dto.ConvenienceFeeDto
import com.saferenttechnologies.android.data.remote.dto.DocumentsDto
import com.saferenttechnologies.android.data.remote.dto.FeaturesDto
import com.saferenttechnologies.android.data.remote.dto.LegalDto
import com.saferenttechnologies.android.data.remote.dto.MaintenanceDto
import com.saferenttechnologies.android.data.remote.dto.NotificationsDto
import com.saferenttechnologies.android.data.remote.dto.OnboardingDto
import com.saferenttechnologies.android.data.remote.dto.OnboardingScreenDto
import com.saferenttechnologies.android.data.remote.dto.PaymentsDto
import com.saferenttechnologies.android.data.remote.dto.PropertiesDto
import com.saferenttechnologies.android.data.remote.dto.PropertyTypeDto
import com.saferenttechnologies.android.data.remote.dto.ReferralProgramDto
import com.saferenttechnologies.android.data.remote.dto.ReferralsDto
import com.saferenttechnologies.android.data.remote.dto.SplashDto
import com.saferenttechnologies.android.data.remote.dto.SupportDto
import com.saferenttechnologies.android.data.remote.dto.UiDto
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppInfo
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.ConvenienceFee
import com.saferenttechnologies.android.domain.model.DocumentSettings
import com.saferenttechnologies.android.domain.model.Features
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.Maintenance
import com.saferenttechnologies.android.domain.model.NotificationSettings
import com.saferenttechnologies.android.domain.model.OnboardingScreen
import com.saferenttechnologies.android.domain.model.OnboardingSettings
import com.saferenttechnologies.android.domain.model.PaymentSettings
import com.saferenttechnologies.android.domain.model.PropertySettings
import com.saferenttechnologies.android.domain.model.PropertyType
import com.saferenttechnologies.android.domain.model.ReferralProgram
import com.saferenttechnologies.android.domain.model.ReferralSettings
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.model.SupportSettings
import com.saferenttechnologies.android.domain.model.UiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Raw "data" object → domain. Also keeps unknown boolean feature flags so
 * isFeatureEnabled() works for flags added on the server later.
 *
 * @throws IllegalArgumentException (SerializationException) if the JSON shape is unusable.
 */
fun JsonObject.toAppConfig(json: Json): AppConfig {
    val dto = json.decodeFromJsonElement(ConfigDataDto.serializer(), this)
    val extraFlags = (this["features"] as? JsonObject)
        ?.mapNotNull { (key, value) ->
            val flag = (value as? JsonPrimitive)?.booleanOrNull
            if (flag != null && key !in Features.KNOWN_KEYS) key to flag else null
        }
        ?.toMap()
        .orEmpty()
    return dto.toDomain(extraFlags)
}

fun ConfigDataDto.toDomain(extraFeatureFlags: Map<String, Boolean> = emptyMap()): AppConfig = AppConfig(
    configVersion = configVersion,
    updatedAt = updatedAt,
    app = app.toDomain(),
    maintenance = maintenance.toDomain(),
    features = features.toDomain(extraFeatureFlags),
    auth = auth.toDomain(),
    properties = properties.toDomain(),
    documents = documents.toDomain(),
    notifications = notifications.toDomain(),
    payments = payments.toDomain(),
    referrals = referrals.toDomain(),
    support = support.toDomain(),
    splash = splash.toDomain(),
    onboarding = onboarding.toDomain(),
    ui = ui.toDomain(),
    legal = legal.toDomain(),
)

fun AppInfoDto.toDomain() = AppInfo(
    name = name,
    packageName = packageName,
    latestVersion = latestVersion,
    minimumSupportedVersion = minimumSupportedVersion,
    forceUpdate = forceUpdate,
    updateMessage = updateMessage,
    playStoreUrl = playStoreUrl,
)

fun MaintenanceDto.toDomain() = Maintenance(
    enabled = enabled,
    title = title,
    message = message,
    startsAt = startsAt,
    expectedEndAt = expectedEndAt,
)

fun FeaturesDto.toDomain(extra: Map<String, Boolean> = emptyMap()) = Features(
    properties = properties,
    tenants = tenants,
    landlords = landlords,
    rentManagement = rentManagement,
    rentPayments = rentPayments,
    rentReminders = rentReminders,
    documents = documents,
    rentalAgreements = rentalAgreements,
    electricityBills = electricityBills,
    propertyTaxDocuments = propertyTaxDocuments,
    otherDocuments = otherDocuments,
    notifications = notifications,
    referrals = referrals,
    support = support,
    chat = chat,
    extra = extra,
)

fun AuthDto.toDomain() = AuthSettings(
    emailLogin = emailLogin,
    phoneLogin = phoneLogin,
    emailOtp = emailOtp,
    phoneOtp = phoneOtp,
    googleLogin = googleLogin,
    otpExpiryMinutes = otpExpiryMinutes,
    otpResendSeconds = otpResendSeconds,
    otpMaxAttempts = otpMaxAttempts,
    showDemographics = showDemographics,
    demographicsSkippable = demographicsSkippable,
)

fun PropertyTypeDto.toDomain() = PropertyType(
    key = key,
    label = label,
    category = category,
    structure = structure,
)

fun PropertiesDto.toDomain() = PropertySettings(
    enabledTypes = enabledTypes.map { it.toDomain() },
    maxImages = maxImages,
    maxDocuments = maxDocuments,
    maxImageSizeMB = maxImageSizeMB,
    allowedImageFormats = allowedImageFormats,
)

fun DocumentsDto.toDomain() = DocumentSettings(
    agreement = agreement,
    electricityBill = electricityBill,
    propertyTax = propertyTax,
    otherDocuments = otherDocuments,
    customNameRequired = customNameRequired,
    maxFileSizeMB = maxFileSizeMB,
    allowedFormats = allowedFormats,
    notifyTenantOnUpload = notifyTenantOnUpload,
    defaultVisibility = defaultVisibility,
)

fun NotificationsDto.toDomain() = NotificationSettings(
    pushEnabled = pushEnabled,
    rentDue = rentDue,
    rentOverdue = rentOverdue,
    paymentSuccess = paymentSuccess,
    documentUploaded = documentUploaded,
    agreementUploaded = agreementUploaded,
    propertyAssigned = propertyAssigned,
    maintenance = maintenance,
)

fun ConvenienceFeeDto.toDomain() = ConvenienceFee(
    type = type,
    value = value,
    maxAmount = maxAmount,
)

fun PaymentsDto.toDomain() = PaymentSettings(
    enabled = enabled,
    razorpay = razorpay,
    upi = upi,
    card = card,
    netbanking = netbanking,
    currency = currency,
    convenienceFee = convenienceFee.toDomain(),
    minAmount = minAmount,
    maxAmount = maxAmount,
)

fun ReferralProgramDto.toDomain() = ReferralProgram(
    type = type,
    rewardAmount = rewardAmount,
    durationMonths = durationMonths,
)

fun ReferralsDto.toDomain() = ReferralSettings(
    enabled = enabled,
    minSuccessfulReferrals = minSuccessfulReferrals,
    eligibilityRules = eligibilityRules,
    programs = programs.map { it.toDomain() },
)

fun SupportDto.toDomain() = SupportSettings(
    email = email,
    phone = phone,
    whatsapp = whatsapp,
    helpCenterUrl = helpCenterUrl,
    faqEnabled = faqEnabled,
)

fun SplashDto.toDomain() = SplashSettings(
    version = version,
    enabled = enabled,
    title = title,
    description = description,
    lightLogoUrl = lightLogoUrl,
    darkLogoUrl = darkLogoUrl,
    lottieUrl = lottieUrl,
    minDurationMs = minDurationMs,
)

fun OnboardingScreenDto.toDomain() = OnboardingScreen(
    id = id,
    title = title,
    description = description,
    buttonText = buttonText,
    mediaType = mediaType,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    lottieUrl = lottieUrl,
)

fun OnboardingDto.toDomain() = OnboardingSettings(
    version = version,
    enabled = enabled,
    showOnce = showOnce,
    skippable = skippable,
    skipButtonText = skipButtonText,
    screens = if (enabled) screens.map { it.toDomain() } else emptyList(),
)

fun UiDto.toDomain() = UiSettings(
    primaryColor = primaryColor,
    secondaryColor = secondaryColor,
    accentColor = accentColor,
    logoUrl = logoUrl,
    darkMode = darkMode,
    announcementEnabled = announcementEnabled,
    announcementTitle = announcementTitle,
    announcementMessage = announcementMessage,
    bannerEnabled = bannerEnabled,
    bannerImageUrl = bannerImageUrl,
    bannerLinkUrl = bannerLinkUrl,
)

fun LegalDto.toDomain() = LegalSettings(
    privacyPolicyUrl = privacyPolicyUrl,
    termsUrl = termsUrl,
    refundPolicyUrl = refundPolicyUrl,
    termsVersion = termsVersion,
    privacyVersion = privacyVersion,
)
