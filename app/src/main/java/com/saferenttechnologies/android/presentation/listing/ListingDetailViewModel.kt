package com.saferenttechnologies.android.presentation.listing

import com.saferenttechnologies.android.domain.model.FlatSelection
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplyError
import com.saferenttechnologies.android.domain.model.ApplyFailure
import com.saferenttechnologies.android.domain.model.ApplyInput
import com.saferenttechnologies.android.domain.model.ApplyRules
import com.saferenttechnologies.android.domain.model.BedOption
import com.saferenttechnologies.android.domain.model.CostBreakdown
import com.saferenttechnologies.android.domain.model.ListingDetail
import com.saferenttechnologies.android.domain.model.ReviewsPreview
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UnitOption
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.usecase.ApplyToListingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplyForm(
    val moveIn: SimpleDate? = null,
    val occupants: Int = 1,
    val message: String = "",
    val error: ApplyError? = null,
)

data class ReportForm(val reason: String = "FAKE_LISTING", val details: String = "", val tooShort: Boolean = false)

data class ListingDetailState(
    val isOpen: Boolean = false,
    val listingId: String? = null,
    val detail: ListingDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val selectedUnitId: String? = null,
    val selectedBedId: String? = null,
    val reviews: ReviewsPreview? = null,
    val apply: ApplyForm? = null,
    val applied: Boolean = false,
    val enquiry: String? = null,
    val report: ReportForm? = null,
    val busy: Boolean = false,
    /** Full-screen photo viewer (index) or null. */
    val viewer: Int? = null,
) : UiState {
    val selectedUnit: UnitOption? get() = detail?.units?.firstOrNull { it.id == selectedUnitId }
    val selectedBed: BedOption? get() = detail?.beds?.firstOrNull { it.id == selectedBedId }
    val breakdown: CostBreakdown? get() = selectedUnit?.breakdown
    val applicationStatus: String? get() = detail?.myApplication?.status?.uppercase()

    /** Apartment: my application is for the selected flat. */
    val appliedToSelectedFlat: Boolean
        get() = detail?.myApplication?.let { it.unitId == null || it.unitId == selectedUnitId } == true

    /** Pager photos: apartment → the selected flat's, then the building's. */
    val photos: List<com.saferenttechnologies.android.domain.model.ListingPhoto>
        get() = detail?.let { FlatSelection.photos(it, selectedUnitId) }.orEmpty()
}

sealed interface ListingDetailIntent : UiIntent {
    /** [unitId] from a deep link ("?unitId=") pre-selects that flat. */
    data class Open(val listingId: String, val unitId: String? = null) : ListingDetailIntent
    data object Close : ListingDetailIntent
    data object Retry : ListingDetailIntent
    data class SelectUnit(val unitId: String) : ListingDetailIntent
    data class SelectBed(val bedId: String) : ListingDetailIntent
    data class OpenViewer(val index: Int) : ListingDetailIntent
    data object CloseViewer : ListingDetailIntent
    data object Share : ListingDetailIntent
    data object OpenMaps : ListingDetailIntent

    data object StartApply : ListingDetailIntent
    data class ApplyChanged(val form: ApplyForm) : ListingDetailIntent
    data object SendApply : ListingDetailIntent
    data object CancelApply : ListingDetailIntent
    data object DismissApplied : ListingDetailIntent
    data object Withdraw : ListingDetailIntent

    data object StartEnquiry : ListingDetailIntent
    data class EnquiryChanged(val text: String) : ListingDetailIntent
    data object SendEnquiry : ListingDetailIntent
    data object CancelEnquiry : ListingDetailIntent

    data object StartReport : ListingDetailIntent
    data class ReportChanged(val form: ReportForm) : ListingDetailIntent
    data object SendReport : ListingDetailIntent
    data object CancelReport : ListingDetailIntent
}

sealed interface ListingDetailEffect : UiEffect {
    data class Message(val text: String) : ListingDetailEffect
    data class Share(val text: String) : ListingDetailEffect
    data class OpenMaps(val latitude: Double, val longitude: Double, val label: String) : ListingDetailEffect
    /** 412 REVIEW_REQUIRED — the review screen isn't in the app yet → message. */
    data class ReviewRequired(val message: String) : ListingDetailEffect
    /** An application was sent / withdrawn → refresh tenant lists. */
    data object ApplicationsChanged : ListingDetailEffect
}

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: BrowseRepository,
    private val applyToListing: ApplyToListingUseCase,
    private val logger: AppLogger,
    private val savedState: SavedStateHandle,
    sessionEvents: SessionEvents,
) : MviViewModel<ListingDetailState, ListingDetailIntent, ListingDetailEffect>(ListingDetailState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { ListingDetailState() } } }
    }

    override suspend fun handleIntent(intent: ListingDetailIntent) {
        when (intent) {
            is ListingDetailIntent.Open -> {
                // Same listing again (e.g. after rotation / process death) keeps the chosen flat.
                val keep = intent.unitId ?: savedState.get<String>(KEY_UNIT).takeIf { savedState.get<String>(KEY_LISTING) == intent.listingId }
                savedState[KEY_LISTING] = intent.listingId
                setState { ListingDetailState(isOpen = true, listingId = intent.listingId, loading = true, selectedUnitId = keep) }
                load()
            }
            ListingDetailIntent.Close -> setState { ListingDetailState() }
            ListingDetailIntent.Retry -> load()
            is ListingDetailIntent.SelectUnit -> {
                savedState[KEY_UNIT] = intent.unitId
                setState { copy(selectedUnitId = intent.unitId, selectedBedId = null) }
            }
            is ListingDetailIntent.SelectBed -> setState { copy(selectedBedId = intent.bedId, selectedUnitId = null) }
            is ListingDetailIntent.OpenViewer -> setState { copy(viewer = intent.index) }
            ListingDetailIntent.CloseViewer -> setState { copy(viewer = null) }
            ListingDetailIntent.Share -> currentState.detail?.let { sendEffect(ListingDetailEffect.Share(shareText(it.id, it.title))) }
            ListingDetailIntent.OpenMaps -> currentState.detail?.let { d ->
                if (d.latitude != null && d.longitude != null) sendEffect(ListingDetailEffect.OpenMaps(d.latitude, d.longitude, d.title))
            }

            ListingDetailIntent.StartApply -> setState { copy(apply = ApplyForm(moveIn = SimpleDate.today().plusDays(7))) }
            is ListingDetailIntent.ApplyChanged -> setState { copy(apply = intent.form.copy(error = null)) }
            ListingDetailIntent.CancelApply -> setState { copy(apply = null) }
            ListingDetailIntent.SendApply -> sendApply()
            ListingDetailIntent.DismissApplied -> setState { copy(applied = false) }
            ListingDetailIntent.Withdraw -> withdraw()

            ListingDetailIntent.StartEnquiry -> setState { copy(enquiry = "") }
            is ListingDetailIntent.EnquiryChanged -> setState { copy(enquiry = intent.text.take(MAX_MESSAGE)) }
            ListingDetailIntent.CancelEnquiry -> setState { copy(enquiry = null) }
            ListingDetailIntent.SendEnquiry -> sendEnquiry()

            ListingDetailIntent.StartReport -> setState { copy(report = ReportForm()) }
            is ListingDetailIntent.ReportChanged -> setState { copy(report = intent.form.copy(tooShort = false)) }
            ListingDetailIntent.CancelReport -> setState { copy(report = null) }
            ListingDetailIntent.SendReport -> sendReport()
        }
    }

    private suspend fun load() {
        val id = currentState.listingId ?: return
        setState { copy(loading = true, error = null) }
        when (val r = repository.listing(id)) {
            is AppResult.Success -> {
                val d = r.data
                setState {
                    copy(
                        detail = d,
                        loading = false,
                        // First available home pre-selected so the price card shows real numbers
                        // (apartment: the flat kept / passed in, else the first AVAILABLE flat).
                        selectedUnitId = if (d.isUnitPriced) FlatSelection.default(d, selectedUnitId) else selectedUnitId ?: d.units.firstOrNull { it.available }?.id,
                        selectedBedId = selectedBedId ?: d.beds.firstOrNull { it.available }?.id,
                    )
                }
                viewModelScope.launch {
                    (repository.reviews(id) as? AppResult.Success)?.let { rv -> setState { copy(reviews = rv.data) } }
                }
            }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun sendApply() {
        val s = currentState
        val id = s.listingId ?: return
        val form = s.apply ?: return
        if (s.busy) return
        val input = ApplyInput(
            unitId = s.selectedUnitId.takeIf { s.detail?.usesBeds != true },
            bedId = s.selectedBedId.takeIf { s.detail?.usesBeds == true },
            proposedMoveIn = form.moveIn,
            occupants = form.occupants,
            message = form.message,
        )
        setState { copy(busy = true) }
        when (val r = applyToListing(id, input)) {
            is ApplyToListingUseCase.Result.Sent -> {
                logger.d(TAG, "application sent")
                setState { copy(busy = false, apply = null, applied = true) }
                sendEffect(ListingDetailEffect.ApplicationsChanged)
                load() // myApplication → "Application sent · Withdraw"
            }
            is ApplyToListingUseCase.Result.Invalid -> setState { copy(busy = false, apply = form.copy(error = r.error)) }
            is ApplyToListingUseCase.Result.Failed -> {
                setState { copy(busy = false) }
                when (val f = r.failure) {
                    is ApplyFailure.ReviewRequired -> {
                        setState { copy(apply = null) }
                        sendEffect(ListingDetailEffect.ReviewRequired(f.message))
                    }
                    is ApplyFailure.AlreadyApplied -> {
                        setState { copy(apply = null) }
                        sendEffect(ListingDetailEffect.Message(f.message))
                        load()
                    }
                    is ApplyFailure.NotAvailable -> {
                        setState { copy(apply = null) }
                        sendEffect(ListingDetailEffect.Message(f.message))
                        load()
                    }
                    is ApplyFailure.Other -> if (f.error !is AppError.AccountBlocked) sendEffect(ListingDetailEffect.Message(f.message))
                }
            }
        }
    }

    private suspend fun withdraw() {
        val appId = currentState.detail?.myApplication?.id ?: return
        setState { copy(busy = true) }
        val r = repository.withdraw(appId)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                if (r.data.isNotBlank()) sendEffect(ListingDetailEffect.Message(r.data))
                sendEffect(ListingDetailEffect.ApplicationsChanged)
                load()
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(ListingDetailEffect.Message(r.error.message))
        }
    }

    private suspend fun sendEnquiry() {
        val id = currentState.listingId ?: return
        val text = currentState.enquiry?.trim().orEmpty()
        if (text.length < MIN_ENQUIRY) return
        setState { copy(busy = true) }
        val r = repository.enquire(id, text)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(enquiry = null) }
                sendEffect(ListingDetailEffect.Message(ENQUIRY_SENT))
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(ListingDetailEffect.Message(r.error.message))
        }
    }

    private suspend fun sendReport() {
        val id = currentState.listingId ?: return
        val form = currentState.report ?: return
        if (form.details.trim().length < MIN_REPORT) {
            setState { copy(report = form.copy(tooShort = true)) }
            return
        }
        setState { copy(busy = true) }
        val r = repository.report(id, form.reason, form.details)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(report = null) }
                sendEffect(ListingDetailEffect.Message(REPORT_SENT))
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(ListingDetailEffect.Message(r.error.message))
        }
    }

    companion object {
        const val MIN_ENQUIRY = 5
        const val MAX_MESSAGE = 1000
        const val MIN_REPORT = 10
        const val ENQUIRY_SENT = "@enquiry_sent"
        const val REPORT_SENT = "@report_sent"
        private const val TAG = "SR-Explore"

        private const val KEY_LISTING = "listing.id"
        private const val KEY_UNIT = "listing.unitId"

        fun shareText(id: String, title: String) = "$title on SafeRent – https://dev.saferenttechnologies.com/homes/$id"

        /** Occupants stepper bounds (shared with the sheet). */
        val OCCUPANTS = ApplyRules.MIN_OCCUPANTS..ApplyRules.MAX_OCCUPANTS
    }
}
