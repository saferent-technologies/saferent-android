package com.saferenttechnologies.android.domain.model

/** Landlord dashboard numbers (GET properties/summary). Money = rupee strings. */
data class PropertySummary(
    val total: Int = 0,
    val byStatus: Map<String, Int> = emptyMap(),
    val unitsAvailable: Int = 0,
    val unitsOccupied: Int = 0,
    val rentPeriod: String = "",
    val rentExpected: String = "0",
    val rentCollected: String = "0",
    val rentOverdue: String = "0",
    val overdueCount: Int = 0,
    val unreadNotifications: Int = 0,
    /** Live listings whose changes are being verified / not sent or rejected. */
    val changesUnderVerification: Int = 0,
    val changesDraftOrRejected: Int = 0,
) {
    fun count(status: String): Int = byStatus[status] ?: 0

    /** How many properties a My-properties tab would show (null = unknown → keep the tab). */
    fun countFor(filter: PropertyFilter): Int? = when (filter) {
        PropertyFilter.ALL -> total
        PropertyFilter.AVAILABLE_UNITS -> unitsAvailable
        // Not in the summary = 0 (Inactive / Archived too).
        else -> (filter.publicationStatus ?: filter.activeStatus)?.let { byStatus[it] ?: 0 }
    }

    /** Tabs worth showing: All, the selected one, and every tab with at least one property. */
    fun visibleFilters(selected: PropertyFilter): List<PropertyFilter> =
        PropertyFilter.entries.filter { it == PropertyFilter.ALL || it == selected || (countFor(it) ?: 1) > 0 }
}

// ---------------- change requests (edits of a live listing) ----------------

enum class RevisionStatus {
    DRAFT, PENDING_VERIFICATION, REJECTED, APPROVED, DISCARDED, UNKNOWN;

    companion object {
        fun of(value: String?): RevisionStatus? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { v -> entries.firstOrNull { it.name.equals(v, true) } ?: UNKNOWN }
    }
}

/** A change request: edits of an approved / live property, saved apart until SafeRent verifies them. */
data class PropertyRevision(
    val id: String,
    val status: RevisionStatus,
    val changedSections: List<String> = emptyList(),
    val createdAt: String = "",
    val submittedAt: String? = null,
    val decidedAt: String? = null,
    val moderationNote: String? = null,
) {
    /** Landlord can still edit / submit / discard it. */
    val open: Boolean get() = status == RevisionStatus.DRAFT || status == RevisionStatus.REJECTED
    val inReview: Boolean get() = status == RevisionStatus.PENDING_VERIFICATION
}

data class RevisionResult(val message: String, val revision: PropertyRevision?)

/** Live listing → edits go to a change request instead of the listing. */
fun isLiveListing(publicationStatus: String): Boolean =
    publicationStatus.equals("APPROVED", true) || publicationStatus.equals("PUBLISHED", true)

/**
 * Tenants can be invited only to an approved (live) and active property, and only to a flat / bed the
 * server doesn't mark as still waiting for approval (a flat added in a change request).
 */
object InviteRules {
    private val NOT_APPROVED = setOf("DRAFT", "PENDING", "PENDING_VERIFICATION", "PENDING_APPROVAL", "REJECTED", "IN_REVIEW")

    fun propertyAllows(p: Property): Boolean =
        isLiveListing(p.publicationStatus) && (p.activeStatus.isBlank() || p.activeStatus.equals("ACTIVE", true))

    /** Flat / bed values (approvalStatus / verificationStatus / publicationStatus when the server sends one). */
    fun placeAllows(values: Map<String, String>): Boolean =
        listOf("approvalStatus", "verificationStatus", "publicationStatus", "moderationStatus")
            .mapNotNull { values[it]?.trim()?.uppercase()?.ifBlank { null } }
            .none { it in NOT_APPROVED }
}

/** "My properties" tabs → query filters. */
enum class PropertyFilter(val publicationStatus: String? = null, val activeStatus: String? = null) {
    ALL,
    LIVE(publicationStatus = "PUBLISHED"),
    UNDER_VERIFICATION(publicationStatus = "PENDING_VERIFICATION"),
    NEEDS_CHANGES(publicationStatus = "REJECTED"),
    APPROVED(publicationStatus = "APPROVED"),
    DRAFTS(publicationStatus = "DRAFT"),
    INACTIVE(activeStatus = "INACTIVE"),
    ARCHIVED(activeStatus = "ARCHIVED"),
    /** Properties with at least one available flat / bed (dashboard "Available units"). */
    AVAILABLE_UNITS,
}

data class PropertyListItem(
    val id: String,
    val code: String = "",
    val title: String = "",
    val name: String = "",
    val propertyTypeLabel: String = "",
    val listingType: String = "",
    val locality: String = "",
    val city: String = "",
    val activeStatus: String = "ACTIVE",
    val publicationStatus: String = "DRAFT",
    val completenessScore: Int = 0,
    val rentFrom: String = "",
    val rentTo: String = "",
    val availableCount: Int = 0,
    val occupiedCount: Int = 0,
    val coverImageId: String? = null,
    val wizardStep: Int = 1,
    val hasDraftChanges: Boolean = false,
    val changesStatus: RevisionStatus? = null,
)

data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val total: Int) {
    val hasMore: Boolean get() = page < totalPages
}

/** `can` from GET properties/{id}. */
data class PropertyPermissions(
    val manage: Boolean = false,
    /** SafeRent moderator: approve / reject a property waiting for verification. */
    val review: Boolean = false,
    val inspect: Boolean = false,
    val editRevision: Boolean = false,
    val submitRevision: Boolean = false,
    val discardRevision: Boolean = false,
)

data class PropertyDetail(
    val property: Property,
    val can: PropertyPermissions,
    /** Open change (DRAFT / PENDING_VERIFICATION / REJECTED); null = none. */
    val pendingRevision: PropertyRevision? = null,
    /** Latest finished change (only when none is open). */
    val lastRevision: PropertyRevision? = null,
    /** What still blocks approval (owner KYC, ownership proof) + what the viewer may do. */
    val approval: PropertyApproval? = null,
)

/** GET properties/{id} → approval. Messages come from the server (shown as they are). */
data class PropertyApproval(
    val ownerVerified: Boolean = false,
    /** Owner sent DigiLocker + selfie, staff hasn't reviewed it yet. */
    val ownerKycSubmitted: Boolean = false,
    val ownershipProofVerified: Boolean = false,
    val blockers: List<ApprovalBlocker> = emptyList(),
    val canApprove: Boolean = false,
    val canApproveWithoutProof: Boolean = false,
    val confirmTitle: String = "",
    val confirmMessage: String = "",
) {
    val ownerBlocked: Boolean get() = blockers.any { it.code == ApprovalBlocker.OWNER_NOT_VERIFIED }
    val proofBlocked: Boolean get() = blockers.any { it.code == ApprovalBlocker.OWNERSHIP_PROOF_REQUIRED }
}

data class ApprovalBlocker(val code: String, val message: String, val ownerKycSubmitted: Boolean = false) {
    companion object {
        const val OWNER_NOT_VERIFIED = "OWNER_NOT_VERIFIED"
        const val OWNERSHIP_PROOF_REQUIRED = "OWNERSHIP_PROOF_REQUIRED"
        val CODES = setOf(OWNER_NOT_VERIFIED, OWNERSHIP_PROOF_REQUIRED)
    }
}

/** Server message to show in a snackbar + the updated property (when it returns one). */
data class ActionResult(val message: String, val property: Property? = null, val deleted: Boolean = false, val archived: Boolean = false)

data class Lease(
    val id: String,
    val status: String,
    val place: String = "",
    val placeCode: String = "",
    val unitId: String? = null,
    val bedId: String? = null,
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val rentDueDay: Int? = null,
    val startDate: String = "",
    val endDate: String = "",
    val tenantName: String = "",
    /** Null for ended leases (no contact details). */
    val tenantPhone: String? = null,
    val exitDate: String? = null,
    /** Set on the all-properties list (GET leases without propertyId). */
    val propertyId: String = "",
    val propertyTitle: String = "",
    /** APARTMENT / PG / INDEPENDENT_HOUSE … (+ the server's label) → the type tabs on the Tenants screen. */
    val propertyType: String = "",
    val propertyTypeLabel: String = "",
)

data class Invitation(
    val id: String,
    val code: String = "",
    val target: String = "",
    val status: String = "",
    val recipientName: String = "",
    val monthlyRent: String = "",
    val expiresAt: String = "",
    /** Which flat / bed it is for (null = whole property / not given). */
    val unitId: String? = null,
    val bedId: String? = null,
) {
    val isPending: Boolean get() = status.equals("PENDING", true)
}

/**
 * One open invite per flat / bed: while an invitation is PENDING the "Invite tenant" button for that
 * flat / bed is hidden. It comes back once the invite is revoked, rejected or expires.
 */
object InviteLocks {
    fun units(invitations: List<Invitation>): Set<String> = invitations.filter { it.isPending }.mapNotNull { it.unitId?.ifBlank { null } }.toSet()
    fun beds(invitations: List<Invitation>): Set<String> = invitations.filter { it.isPending }.mapNotNull { it.bedId?.ifBlank { null } }.toSet()
    fun isLocked(invitations: List<Invitation>, unitId: String?, bedId: String?): Boolean =
        (bedId != null && bedId in beds(invitations)) || (bedId == null && unitId != null && unitId in units(invitations))
}

/** The share link — only returned when it's created / renewed. */
data class InvitationLink(val url: String, val expiresAt: String = "", val shareText: String = "") {
    /** What goes to the share sheet: the server's link.shareText as is (the bare url only if it is missing). */
    val shareMessage: String get() = shareText.ifBlank { url }
}

data class CreateInvitation(
    val unitId: String? = null,
    val bedId: String? = null,
    val recipientName: String,
    val recipientPhone: String,
    val recipientEmail: String = "",
    /** Tenant already on SafeRent (found by SafeRent ID / phone) → their invitation shows on their dashboard. */
    val recipientUserId: String? = null,
    val monthlyRent: String,
    val securityDeposit: String = "",
    val maintenanceCharges: String = "",
    val startDate: String,
    val endDate: String = "",
    val rentDueDay: Int? = null,
    val noticePeriodDays: Int? = null,
    val lockInMonths: Int? = null,
    val additionalTerms: String = "",
    val expiresInDays: Int = 7,
    /** "Ask the tenant": null / everything off = ask nothing (left out). */
    val requirements: InviteRequirements? = null,
)

data class InvitationCreated(val message: String, val invitation: Invitation, val link: InvitationLink?)

data class RentalApplication(
    val id: String,
    val status: String,
    val proposedMoveIn: String = "",
    val occupants: Int? = null,
    val message: String = "",
    val unitNumber: String = "",
    val bedNumber: String = "",
    val tenantName: String = "",
    val occupation: String = "",
    val familySize: Int? = null,
    val currentCity: String = "",
    val createdAt: String = "",
)

data class ApplicationDecision(
    val approve: Boolean,
    val startDate: String = "",
    val months: Int? = null,
    val rentDueDay: Int? = null,
    val note: String = "",
)

data class StatusChange(
    val id: String,
    val entityType: String = "",
    val field: String = "",
    val from: String = "",
    val to: String = "",
    val reason: String = "",
    val createdAt: String = "",
    val actorName: String = "",
)

/** Everything the landlord can do from the property screen. */
enum class PropertyAction {
    EDIT,
    /** APPROVED / PUBLISHED: editing sends the listing back for verification → confirm first. */
    EDIT_WITH_WARNING,
    SUBMIT,
    RESUBMIT,
    PUBLISH,
    UNPUBLISH,
    DEACTIVATE,
    ACTIVATE,
    ARCHIVE,
    DELETE_DRAFT,
    CONTACT_SUPPORT,
}

/** Mirrors the server's lifecycle rules (§4). The server enforces them too. */
object PropertyActions {
    /**
     * [revision] = the open change request of a live listing. Editing a live listing no longer touches
     * it (edits go to the change), so there's no warning; while the change is being verified, no edit.
     */
    fun allowed(publicationStatus: String, activeStatus: String, canManage: Boolean, revision: RevisionStatus? = null): List<PropertyAction> {
        if (!canManage) return emptyList() // read-only
        val pub = publicationStatus.uppercase()
        val active = activeStatus.uppercase()
        if (active == "ARCHIVED") return listOf(PropertyAction.CONTACT_SUPPORT) // restore needs a SafeRent admin
        if (active == "SUSPENDED") return emptyList() // set / lifted only by SafeRent
        return buildList {
            if (pub != "PENDING_VERIFICATION" && revision != RevisionStatus.PENDING_VERIFICATION) add(PropertyAction.EDIT)
            if (active == "ACTIVE" && pub == "DRAFT") add(PropertyAction.SUBMIT)
            if (active == "ACTIVE" && pub == "REJECTED") add(PropertyAction.RESUBMIT)
            if (active == "ACTIVE" && pub == "APPROVED") add(PropertyAction.PUBLISH)
            if (pub == "PUBLISHED") add(PropertyAction.UNPUBLISH)
            if (active == "INACTIVE") add(PropertyAction.ACTIVATE)
            if (active == "ACTIVE" && pub != "DRAFT") add(PropertyAction.DEACTIVATE)
            if (pub == "DRAFT") add(PropertyAction.DELETE_DRAFT) else add(PropertyAction.ARCHIVE)
        }
    }

    /** A property with tenants living in it can't be archived (deleted) — end the leases first. */
    fun allowed(property: Property, can: PropertyPermissions, revision: PropertyRevision? = null) =
        allowed(property.publicationStatus, property.activeStatus, can.manage, revision?.status)
            .let { if (property.hasTenants) it - PropertyAction.ARCHIVE else it }

    /** Change-request buttons come from the server's can flags. */
    fun canSubmitChanges(detail: PropertyDetail) = detail.pendingRevision?.open == true && detail.can.submitRevision
    fun canDiscardChanges(detail: PropertyDetail) = detail.pendingRevision?.open == true && detail.can.discardRevision
    fun canEditChanges(detail: PropertyDetail) =
        detail.pendingRevision?.inReview != true && (detail.pendingRevision == null || detail.can.editRevision)

    /** Units can be set to these by hand; OCCUPIED only comes from a lease. */
    val MANUAL_AVAILABILITY = listOf("AVAILABLE", "RESERVED", "UNDER_MAINTENANCE", "NOT_AVAILABLE")
}

enum class ChangeBadge { UNSENT, IN_REVIEW, REJECTED }

/** List badge for a live listing's change request (pure, unit-tested). */
object ChangeBadges {
    fun of(item: PropertyListItem): ChangeBadge? = when (item.changesStatus) {
        RevisionStatus.DRAFT -> if (item.hasDraftChanges) ChangeBadge.UNSENT else null
        RevisionStatus.PENDING_VERIFICATION -> ChangeBadge.IN_REVIEW
        RevisionStatus.REJECTED -> ChangeBadge.REJECTED
        else -> null
    }
}
