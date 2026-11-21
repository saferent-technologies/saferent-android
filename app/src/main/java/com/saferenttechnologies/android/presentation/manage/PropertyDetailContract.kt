package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.domain.model.UnitPricing
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.PropertyRevision
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.Invitation
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.PropertyAction
import com.saferenttechnologies.android.domain.model.PropertyActions
import com.saferenttechnologies.android.domain.model.PropertyDetail
import com.saferenttechnologies.android.domain.model.PropertyDocument
import com.saferenttechnologies.android.domain.model.RentalApplication
import com.saferenttechnologies.android.domain.model.StatusChange
import com.saferenttechnologies.android.presentation.property.CameraMode

enum class DetailTab {
    OVERVIEW, UNITS, TENANTS, INVITATIONS, APPLICATIONS, PHOTOS, DOCUMENTS, HISTORY;

    companion object {
        /**
         * Tabs on the property screen, in this order: flats / units first, overview last. No tenants tab
         * (each flat's sheet shows its tenant + lease), photos live in the edit wizard, no history.
         */
        val VISIBLE: List<DetailTab> get() = listOf(UNITS, INVITATIONS, APPLICATIONS, DOCUMENTS, OVERVIEW)

        /** "?tab=documents" → DOCUMENTS ("rooms" / "beds" / "tenants" → UNITS); no tab (status news) → OVERVIEW; unknown → UNITS. */
        fun fromLink(tab: String?): DetailTab {
            val key = tab?.trim()?.lowercase().orEmpty()
            return when (key) {
                "" -> OVERVIEW
                "rooms", "beds", "flats", "tenants" -> UNITS
                else -> VISIBLE.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: UNITS
            }
        }
    }
}

/** Invite form (prefilled from a unit / bed). Values are strings while typing. */
data class InviteForm(
    val unitId: String? = null,
    val bedId: String? = null,
    val target: String = "",
    val recipientName: String = "",
    val recipientPhone: String = "",
    val recipientEmail: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val maintenanceCharges: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rentDueDay: String = "5",
    val noticePeriodDays: String = "30",
    val lockInMonths: String = "",
    val additionalTerms: String = "",
    val error: String? = null,
    /** "Tenant's phone or SafeRent ID" → look them up on SafeRent first. */
    val lookup: String = "",
    val lookupBusy: Boolean = false,
    val lookupInvalid: Boolean = false,
    /** Found on SafeRent (their details shown, invitation goes to their dashboard). */
    val found: com.saferenttechnologies.android.domain.model.PersonPreview? = null,
    /** Searched, not on SafeRent → fill the details by hand (invite link by phone). */
    val notOnSafeRent: Boolean = false,
    /** "Ask the tenant": answered when they accept. */
    val requirements: com.saferenttechnologies.android.domain.model.InviteRequirements = com.saferenttechnologies.android.domain.model.InviteRequirements(),
)

data class DecisionForm(
    val application: RentalApplication,
    val approve: Boolean,
    val startDate: String = application.proposedMoveIn,
    val months: String = "11",
    val rentDueDay: String = "5",
    val note: String = "",
)

data class PropertyDetailState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val detail: PropertyDetail? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val tab: DetailTab = DetailTab.UNITS,
    /** Action waiting for the confirm dialog. */
    val confirm: PropertyAction? = null,
    /** Submit 412 → checklist (problem, wizard step). */
    val problems: List<Pair<String, Int>> = emptyList(),
    /** Moderator: "Approve and go live" confirm (true = without ownership document). */
    val approveConfirm: Boolean? = null,
    /** Moderator: reject with a reason. */
    val rejectReason: String? = null,
    /** 412 owner not verified / ownership proof missing → bottom sheet with what to do. */
    val approvalBlock: com.saferenttechnologies.android.domain.model.ApprovalBlocker? = null,
    /** Owner: "Verify your identity" bottom sheet (Verify identity / Contact support). */
    val verifySheet: Boolean = false,

    val leases: List<Lease> = emptyList(),
    val pastTenants: Boolean = false,
    val invitations: List<Invitation> = emptyList(),
    val invitationStatus: String = "PENDING",
    /** Always the PENDING invitations (any tab filter) → hides "Invite tenant" on flats / beds already invited. */
    val pendingInvites: List<Invitation> = emptyList(),
    /** Active leases whose tenant asked to close → "Close requested" chip (GET leases/{id}/exit). */
    val closeRequested: Set<String> = emptySet(),
    /** Flat sheet open for this unit id. */
    val openUnitId: String? = null,
    /** Current tenants (any tab) → who lives in which flat. */
    val activeLeases: List<Lease> = emptyList(),
    val activeLeasesLoading: Boolean = false,
    /** Past tenants of this property still to review → no new invites until done. */
    val reviewsDue: List<com.saferenttechnologies.android.domain.model.PendingReview> = emptyList(),
    /** "Review the last tenant of this flat first" popup (Invite tapped on that flat). */
    val reviewPrompt: com.saferenttechnologies.android.domain.model.PendingReview? = null,
    val inviteForm: InviteForm? = null,
    /** "Tenant details" sheet of an accepted invitation (null = closed; loading while [tenantDetails] is null). */
    val tenantDetailsFor: String? = null,
    val tenantDetails: com.saferenttechnologies.android.domain.model.TenantDetailsView? = null,
    val applications: List<RentalApplication> = emptyList(),
    val decision: DecisionForm? = null,
    val history: List<StatusChange> = emptyList(),
    val historyPage: Int = 0,
    val historyTotalPages: Int = 1,
    val documents: List<PropertyDocument> = emptyList(),
    val camera: CameraMode? = null,
    val replacingDocId: String? = null,
    val photoCategory: String = "FRONT_VIEW",
    val docType: String = "OWNERSHIP_PROOF",
    /** "Discard changes?" popup. */
    val confirmDiscard: Boolean = false,
    /** Change request submit came back with problems → checklist sheet (problem → wizard step). */
    val changeProblems: List<Pair<String, Int>>? = null,
) : UiState {
    val revision: PropertyRevision? get() = detail?.pendingRevision
    val actions: List<PropertyAction>
        get() = detail?.let { PropertyActions.allowed(it.property, it.can, it.pendingRevision) }.orEmpty()
    val canManage: Boolean get() = detail?.can?.manage == true
    /** Approved + active property → "Invite tenant" works (otherwise shown grey with a note). */
    val canInvite: Boolean get() = canManage && detail?.property?.let(com.saferenttechnologies.android.domain.model.InviteRules::propertyAllows) == true
    /** SafeRent moderator looking at a property waiting for verification. */
    val isModerating: Boolean
        get() = detail?.can?.review == true && detail.property.publicationStatus.equals("PENDING_VERIFICATION", true)
    /** The owner's own property waiting for verification (approval banners). */
    val ownerWaiting: Boolean
        get() = canManage && detail?.property?.publicationStatus.equals("PENDING_VERIFICATION", true) && detail?.approval != null
    /** Apartment / independent building / commercial complex / villa community: flats with their own rent + photos. */
    val isUnitPriced: Boolean
        get() = detail?.property?.let { UnitPricing.isUnitPriced(it.propertyType, it.building["villaListing"]) } == true
    /** "Flat" / "Portion" / "Shop" / "Villa" / "Unit". */
    val unitWord: String get() = UnitPricing.unitWord(detail?.property?.propertyType, null, detail?.property?.structure)
    /** Editing is off while SafeRent reviews (listing or its changes), or when suspended / archived. */
    val canEdit: Boolean get() = PropertyAction.EDIT in actions && (detail?.let(PropertyActions::canEditChanges) ?: false)
    /** Edit button reads "Continue changes" while a change is open. */
    val continuesChanges: Boolean get() = revision?.open == true
}

sealed interface PropertyDetailIntent : UiIntent {
    /** Accepted invitation → what the tenant answered. */
    data class OpenTenantDetails(val invitationId: String) : PropertyDetailIntent
    data object CloseTenantDetails : PropertyDetailIntent
    /** "Continue changes" on the change request card. */
    data object ContinueChanges : PropertyDetailIntent
    // ---- approval (moderator) ----
    data class AskApprove(val withoutProof: Boolean) : PropertyDetailIntent
    data object ConfirmApprove : PropertyDetailIntent
    data object AskReject : PropertyDetailIntent
    data class RejectReason(val text: String) : PropertyDetailIntent
    data object ConfirmReject : PropertyDetailIntent
    data object DismissApproval : PropertyDetailIntent
    /** Blocker action: OWNERSHIP_PROOF_REQUIRED → Documents tab. */
    data object CheckDocuments : PropertyDetailIntent
    // ---- owner ----
    data object OpenVerifySheet : PropertyDetailIntent
    data object VerifyIdentity : PropertyDetailIntent
    data object ContactSupportVerify : PropertyDetailIntent
    /** [tab] from a notification link (?tab=documents). */
    data class Open(val propertyId: String, val tab: DetailTab = DetailTab.UNITS) : PropertyDetailIntent
    data object Close : PropertyDetailIntent
    data object Refresh : PropertyDetailIntent
    data class SelectTab(val tab: DetailTab) : PropertyDetailIntent

    data class Action(val action: PropertyAction) : PropertyDetailIntent
    data object Confirm : PropertyDetailIntent
    data object DismissConfirm : PropertyDetailIntent
    data class Edit(val step: Int) : PropertyDetailIntent

    // change request of a live listing
    data object SubmitChanges : PropertyDetailIntent
    data object DiscardChanges : PropertyDetailIntent
    data object ConfirmDiscard : PropertyDetailIntent
    data object DismissDiscard : PropertyDetailIntent
    data object DismissChangeProblems : PropertyDetailIntent

    data class SetUnitAvailability(val unitId: String, val status: String) : PropertyDetailIntent
    data class SetBedAvailability(val roomId: String, val bedId: String, val status: String) : PropertyDetailIntent

    data class ShowPastTenants(val past: Boolean) : PropertyDetailIntent
    data class CallTenant(val phone: String) : PropertyDetailIntent
    data class WhatsAppTenant(val phone: String) : PropertyDetailIntent

    data class StartInvite(val unitId: String?, val bedId: String?, val target: String, val rent: String) : PropertyDetailIntent
    data object DismissReviewPrompt : PropertyDetailIntent
    /** Invite form: look the tenant up by phone / SafeRent ID. */
    data object InviteLookup : PropertyDetailIntent
    data object InviteClearFound : PropertyDetailIntent
    data class InviteChanged(val form: InviteForm) : PropertyDetailIntent
    data object SendInvite : PropertyDetailIntent
    data object CancelInvite : PropertyDetailIntent
    data class InvitationFilter(val status: String) : PropertyDetailIntent
    data class RenewLink(val invitationId: String) : PropertyDetailIntent
    data class Revoke(val invitationId: String) : PropertyDetailIntent
    data class OpenLease(val lease: Lease) : PropertyDetailIntent
    /** Flat card → flat sheet (photos, tenant, lease). */
    data class OpenUnit(val unitId: String) : PropertyDetailIntent
    data object CloseUnit : PropertyDetailIntent
    /** "Add flat" → wizard on the Units step with a new flat. */
    data object AddUnit : PropertyDetailIntent
    /** Banner "Review Ravi" → lease screen with the review sheet open. */
    data class ReviewTenant(val leaseId: String) : PropertyDetailIntent

    data class StartDecision(val application: RentalApplication, val approve: Boolean) : PropertyDetailIntent
    data class DecisionChanged(val form: DecisionForm) : PropertyDetailIntent
    data object SubmitDecision : PropertyDetailIntent
    data object CancelDecision : PropertyDetailIntent

    data object LoadMoreHistory : PropertyDetailIntent

    data class SelectPhotoCategory(val category: String) : PropertyDetailIntent
    data class SelectDocType(val type: String) : PropertyDetailIntent
    data class OpenCamera(val mode: CameraMode, val replaceDocId: String? = null) : PropertyDetailIntent
    data object CloseCamera : PropertyDetailIntent
    data class Captured(val filePath: String) : PropertyDetailIntent
    data class DeleteImage(val imageId: String) : PropertyDetailIntent
    data class SetCover(val imageId: String) : PropertyDetailIntent
}

sealed interface PropertyDetailEffect : UiEffect {
    /** Owner → Owner verification screen. */
    data object OpenOwnerVerification : PropertyDetailEffect
    /** Owner → a support ticket about verification. */
    data class OpenVerificationSupport(val propertyId: String) : PropertyDetailEffect
    /** Server `message` (or our text) in a snackbar. */
    data class Message(val text: String) : PropertyDetailEffect
    data class Share(val text: String, val url: String) : PropertyDetailEffect
    /** Open the add wizard on this property at [step]. */
    data class OpenEditor(val propertyId: String, val step: Int, val newUnit: Boolean = false) : PropertyDetailEffect
    data class Dial(val phone: String) : PropertyDetailEffect
    data class WhatsApp(val phone: String) : PropertyDetailEffect
    data class ContactSupport(val email: String) : PropertyDetailEffect
    /** Property list + dashboard summary must reload. */
    data object Changed : PropertyDetailEffect

    /** 404 — the property is gone (opened from an old notification). */
    data object NotFound : PropertyDetailEffect

    /** Tenant card tapped → lease screen (close requests). */
    data class OpenLease(val leaseId: String, val preview: LeaseSummary) : PropertyDetailEffect
    /** Open the lease with "Write your review" straight away. */
    data class ReviewLease(val leaseId: String) : PropertyDetailEffect
    /** Invite tapped while a past tenant still needs a review. */
    data class ReviewFirst(val name: String) : PropertyDetailEffect
}
