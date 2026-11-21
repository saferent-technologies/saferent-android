package com.saferenttechnologies.android.presentation.visits

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.BookVisit
import com.saferenttechnologies.android.domain.model.SlotDay
import com.saferenttechnologies.android.domain.model.Visit
import com.saferenttechnologies.android.domain.model.VisitRules
import com.saferenttechnologies.android.domain.model.VisitSlots
import com.saferenttechnologies.android.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A flat / bed the visit can be for (listing with several). */
data class VisitPlace(val label: String, val unitId: String? = null, val bedId: String? = null)

data class BookingState(
    val propertyId: String,
    val title: String,
    val places: List<VisitPlace> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val days: List<SlotDay> = emptyList(),
    val approvalMode: String = "MANUAL",
    val maxDaysAhead: Int = 14,
    val date: String? = null,
    val start: String? = null,
    val visitors: Int = 1,
    val note: String = "",
    val place: VisitPlace? = null,
    val error: String? = null,
    val saving: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = days.isNotEmpty() && days.size < maxDaysAhead &&
            (SimpleDateDiff.days(VisitRules.todayIst(), VisitRules.nextDay(days.last().date)) ?: 0) < maxDaysAhead
}

/** Tiny date difference helper (no java.time). */
object SimpleDateDiff {
    fun days(from: String, to: String): Int? {
        val a = com.saferenttechnologies.android.domain.model.SimpleDate.parse(from) ?: return null
        val b = com.saferenttechnologies.android.domain.model.SimpleDate.parse(to) ?: return null
        val utc = java.util.TimeZone.getTimeZone("UTC")
        fun ms(d: com.saferenttechnologies.android.domain.model.SimpleDate) = java.util.Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
        return ((ms(b) - ms(a)) / 86_400_000L).toInt()
    }
}

data class FeedbackForm(val visitId: String, val rating: Int = 0, val interested: Boolean? = null, val comment: String = "")

data class TenantVisitsState(
    /** Listing detail: "Book a visit" / "Your visit …" for this property. */
    val listingId: String? = null,
    val listingSlots: VisitSlots? = null,
    val booking: BookingState? = null,
    // My visits
    val isOpen: Boolean = false,
    val pastTab: Boolean = false,
    val upcoming: List<Visit>? = null,
    val past: List<Visit>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val focusId: String? = null,
    val cancelFor: String? = null,
    val cancelReason: String = "",
    val feedback: FeedbackForm? = null,
    val busy: Boolean = false,
) : UiState {
    val shown: List<Visit>? get() = if (pastTab) past else upcoming
}

sealed interface TenantVisitsIntent : UiIntent {
    data class LoadForListing(val propertyId: String) : TenantVisitsIntent
    data class StartBooking(val propertyId: String, val title: String, val places: List<VisitPlace>) : TenantVisitsIntent
    data object CloseBooking : TenantVisitsIntent
    data class PickDate(val date: String) : TenantVisitsIntent
    data class PickSlot(val start: String) : TenantVisitsIntent
    data class Visitors(val n: Int) : TenantVisitsIntent
    data class Note(val text: String) : TenantVisitsIntent
    data class PickPlace(val place: VisitPlace?) : TenantVisitsIntent
    data object LoadMoreDays : TenantVisitsIntent
    data object Book : TenantVisitsIntent

    data class OpenMine(val focusId: String? = null, val feedback: Boolean = false) : TenantVisitsIntent
    data object Close : TenantVisitsIntent
    data class Tab(val past: Boolean) : TenantVisitsIntent
    data object Refresh : TenantVisitsIntent
    data class AskCancel(val visitId: String?) : TenantVisitsIntent
    data class CancelReason(val text: String) : TenantVisitsIntent
    data object ConfirmCancel : TenantVisitsIntent
    data class OpenFeedback(val visitId: String?) : TenantVisitsIntent
    data class EditFeedback(val form: FeedbackForm) : TenantVisitsIntent
    data object SendFeedback : TenantVisitsIntent
}

sealed interface TenantVisitsEffect : UiEffect {
    data class Message(val text: String) : TenantVisitsEffect
}

/** Tenant: book a visit from a listing + "My visits" (cancel, feedback). Never logs notes or feedback. */
@HiltViewModel
class TenantVisitsViewModel @Inject constructor(
    private val repository: VisitRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantVisitsState, TenantVisitsIntent, TenantVisitsEffect>(TenantVisitsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantVisitsState() } } }
    }

    override suspend fun handleIntent(intent: TenantVisitsIntent) {
        when (intent) {
            is TenantVisitsIntent.LoadForListing -> {
                if (currentState.listingId != intent.propertyId) setState { copy(listingId = intent.propertyId, listingSlots = null) }
                // Not live / off → nothing shown (no error).
                (repository.slots(intent.propertyId, null, 1) as? AppResult.Success)?.let { r ->
                    if (currentState.listingId == intent.propertyId) setState { copy(listingSlots = r.data) }
                }
            }
            is TenantVisitsIntent.StartBooking -> {
                setState { copy(booking = BookingState(intent.propertyId, intent.title, intent.places, place = intent.places.singleOrNull())) }
                loadDays(null)
            }
            TenantVisitsIntent.CloseBooking -> if (currentState.booking?.saving != true) setState { copy(booking = null) }
            is TenantVisitsIntent.PickDate -> booking { copy(date = intent.date, start = null, error = null) }
            is TenantVisitsIntent.PickSlot -> booking { copy(start = intent.start, error = null) }
            is TenantVisitsIntent.Visitors -> booking { copy(visitors = intent.n.coerceIn(1, 10)) }
            is TenantVisitsIntent.Note -> booking { copy(note = intent.text.take(300)) }
            is TenantVisitsIntent.PickPlace -> booking { copy(place = intent.place) }
            TenantVisitsIntent.LoadMoreDays -> currentState.booking?.let { b ->
                if (!b.loadingMore && b.days.isNotEmpty()) {
                    booking { copy(loadingMore = true) }
                    loadDays(VisitRules.nextDay(b.days.last().date))
                }
            }
            TenantVisitsIntent.Book -> book()

            is TenantVisitsIntent.OpenMine -> {
                setState { copy(isOpen = true, focusId = intent.focusId, pastTab = false) }
                load()
                if (intent.feedback && intent.focusId != null) setState { copy(feedback = FeedbackForm(intent.focusId), pastTab = true) }
            }
            TenantVisitsIntent.Close -> if (!currentState.busy) setState { copy(isOpen = false, cancelFor = null, feedback = null) }
            is TenantVisitsIntent.Tab -> setState { copy(pastTab = intent.past) }
            TenantVisitsIntent.Refresh -> load()
            is TenantVisitsIntent.AskCancel -> setState { copy(cancelFor = intent.visitId, cancelReason = "") }
            is TenantVisitsIntent.CancelReason -> setState { copy(cancelReason = intent.text.take(300)) }
            TenantVisitsIntent.ConfirmCancel -> cancel()
            is TenantVisitsIntent.OpenFeedback -> setState { copy(feedback = intent.visitId?.let { FeedbackForm(it) }) }
            is TenantVisitsIntent.EditFeedback -> setState { copy(feedback = intent.form.copy(comment = intent.form.comment.take(500))) }
            TenantVisitsIntent.SendFeedback -> sendFeedback()
        }
    }

    private fun booking(change: BookingState.() -> BookingState) = setState { copy(booking = booking?.change()) }

    /** Slots for 7 days from [from] (null = today); more appended on "Load more". */
    private suspend fun loadDays(from: String?) {
        val b = currentState.booking ?: return
        when (val r = repository.slots(b.propertyId, from, 7)) {
            is AppResult.Success -> booking {
                val all = (if (from == null) emptyList() else days) + r.data.days.filter { d -> d.slots.isNotEmpty() && days.none { it.date == d.date } }
                copy(
                    loading = false, loadingMore = false, days = all, approvalMode = r.data.approvalMode, maxDaysAhead = r.data.maxDaysAhead,
                    date = date?.takeIf { d -> all.any { it.date == d } } ?: all.firstOrNull()?.date,
                    start = start?.takeIf { s -> all.any { d -> d.slots.any { it.start == s } } },
                )
            }
            is AppResult.Failure -> booking { copy(loading = false, loadingMore = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun book() {
        val b = currentState.booking ?: return
        val start = b.start ?: return booking { copy(error = PICK_SLOT) }
        if (b.saving) return
        booking { copy(saving = true, error = null) }
        val r = repository.book(b.propertyId, BookVisit(start, b.place?.unitId, b.place?.bedId, b.visitors, b.note))
        booking { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(booking = null) }
                sendEffect(TenantVisitsEffect.Message(r.data.first))
                handleIntent(TenantVisitsIntent.LoadForListing(b.propertyId))
            }
            is AppResult.Failure -> {
                val e = r.error
                val msg = if (e is AppError.FieldIssues) e.issues.values.firstOrNull() ?: e.message else e.message
                booking { copy(error = msg.takeUnless { e is AppError.AccountBlocked }) }
                // Slot taken / not available any more → fresh slots.
                if (e is AppError.Conflict || e is AppError.InvalidState || (e is AppError.Server && (e.httpCode == 409 || e.httpCode == 412))) {
                    booking { copy(start = null) }
                    loadDays(null)
                }
            }
        }
    }

    private suspend fun load() {
        setState { copy(loading = true, error = null) }
        val up = repository.myVisits(upcoming = true)
        val all = repository.myVisits(upcoming = false)
        when {
            up is AppResult.Success && all is AppResult.Success -> {
                val upIds = up.data.map { it.id }.toSet()
                setState { copy(loading = false, upcoming = up.data.sortedBy { it.scheduledAt }, past = all.data.filter { it.id !in upIds }) }
            }
            else -> {
                val e = (up as? AppResult.Failure)?.error ?: (all as? AppResult.Failure)?.error
                setState { copy(loading = false, error = e?.takeUnless { it is AppError.AccountBlocked }?.message) }
            }
        }
    }

    private fun replace(v: Visit) = setState {
        copy(upcoming = upcoming?.map { if (it.id == v.id) v else it }, past = past?.map { if (it.id == v.id) v else it })
    }

    private suspend fun cancel() {
        val s = currentState
        val id = s.cancelFor ?: return
        if (!VisitRules.reasonOk(s.cancelReason) || s.busy) return
        setState { copy(busy = true) }
        val r = repository.cancelMine(id, s.cancelReason)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(cancelFor = null, cancelReason = "") }
                sendEffect(TenantVisitsEffect.Message(r.data.first))
                load()
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(TenantVisitsEffect.Message(r.error.message))
        }
    }

    private suspend fun sendFeedback() {
        val f = currentState.feedback ?: return
        if (f.rating !in 1..5 || f.interested == null || currentState.busy) return
        setState { copy(busy = true) }
        val r = repository.feedback(f.visitId, f.rating, f.interested, f.comment)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(feedback = null) }
                r.data.second?.let(::replace) ?: load()
                sendEffect(TenantVisitsEffect.Message(r.data.first))
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(TenantVisitsEffect.Message(r.error.message))
        }
    }

    companion object {
        const val PICK_SLOT = "@pick_slot"
    }
}
