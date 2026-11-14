package com.saferenttechnologies.android.data.remote.dto

import kotlinx.serialization.Serializable

/** One consent in POST userProfile → "consents". */
@Serializable
data class ConsentItemDto(
    /** TERMS | PRIVACY | WHATSAPP */
    val type: String = "",
    /** "" for WHATSAPP. */
    val version: String = "",
    val granted: Boolean = true,
)

@Serializable
data class AcceptedDto(
    val accepted: Boolean = false,
    val acceptedVersion: String? = null,
    val acceptedAt: String? = null,
)

@Serializable
data class WhatsappDto(
    val granted: Boolean = false,
    val updatedAt: String? = null,
)

/** userProfile response → "consents". */
@Serializable
data class ConsentStatusDto(
    val terms: AcceptedDto = AcceptedDto(),
    val privacy: AcceptedDto = AcceptedDto(),
    val whatsapp: WhatsappDto = WhatsappDto(),
)
