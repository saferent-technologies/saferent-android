package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal

/** A landlord's rental invitation, as the invited tenant sees it (GET invitations/token/{token}). */
data class TenantInvitation(
    val token: String,
    val id: String = "",
    val code: String = "",
    /** PENDING / ACCEPTED / REJECTED / REVOKED / EXPIRED (a PENDING one past [expiresAt] reads as EXPIRED). */
    val status: String = "PENDING",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val propertyType: String = "",
    val place: String = "",
    val coverImageId: String? = null,
    /** "Flat 302", "Room 2 · Bed B" … */
    val target: String = "",
    val landlordName: String = "",
    val landlordVerified: Boolean = false,
    val recipientName: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val maintenanceCharges: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rentDueDay: Int? = null,
    val noticePeriodDays: Int? = null,
    val lockInMonths: Int? = null,
    val additionalTerms: String = "",
    val expiresAt: String = "",
) {
    val isPending: Boolean get() = status.equals("PENDING", true)

    /** Rent + deposit (+ maintenance) due at move-in; blank when the rent is unknown. */
    val moveInTotal: String
        get() {
            val rent = monthlyRent.toBigDecimalOrNull() ?: return ""
            val sum = listOf(securityDeposit, maintenanceCharges).mapNotNull { it.toBigDecimalOrNull() }.fold(rent, BigDecimal::add)
            return sum.toPlainString()
        }

    companion object {
        val CLOSED = listOf("ACCEPTED", "REJECTED", "REVOKED", "EXPIRED")
    }
}

/** What happened after Accept / Decline: server message + the lease it created (if any). */
data class InvitationDecision(val message: String, val leaseId: String? = null)
