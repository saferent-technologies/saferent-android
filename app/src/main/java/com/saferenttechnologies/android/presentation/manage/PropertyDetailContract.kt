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
    val inviteForm: InviteForm? = null,
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
}
