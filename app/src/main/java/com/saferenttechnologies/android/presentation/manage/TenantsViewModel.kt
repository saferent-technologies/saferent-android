package com.saferenttechnologies.android.presentation.manage

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Landlord: every tenant across all properties — Current / Past, tap → tenant sheet. */
data class TenantsState(
    val isOpen: Boolean = false,
    val past: Boolean = false,
    val items: List<Lease> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Tenant sheet open for this lease id. */
    val openLeaseId: String? = null,
    /** Current tenants who asked to close their lease. */
    val closeRequested: Set<String> = emptySet(),
    /** Search box: name, home, flat / room, code, phone. */
    val query: String = "",
    /** Property type tab (null = all). */
    val type: String? = null,
) : UiState {
    val open: Lease? get() = items.firstOrNull { it.id == openLeaseId }
    /** One tab per property type that has tenants (a type with 0 gets no tab). */
    val types: List<TenantTypeTab> get() = TenantSearch.typeTabs(items)
    val visible: List<Lease> get() = TenantSearch.filter(items.filter { type == null || it.propertyType == type }, query)
}

data class TenantTypeTab(val type: String, val label: String, val count: Int)

/** Client-side search over the loaded tenants (pure, unit-tested). Every word must match somewhere. */
object TenantSearch {
    /** Apartment (3), PG (1) … — only types that have tenants, biggest first. Unknown type → no tab. */
    fun typeTabs(items: List<Lease>): List<TenantTypeTab> =
        items.filter { it.propertyType.isNotBlank() }
            .groupBy { it.propertyType }
            .map { (type, list) -> TenantTypeTab(type, list.firstNotNullOfOrNull { it.propertyTypeLabel.ifBlank { null } }.orEmpty(), list.size) }
            .sortedByDescending { it.count }

    fun filter(items: List<Lease>, query: String): List<Lease> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return items
        return items.filter { l ->
            val hay = listOf(l.tenantName, l.propertyTitle, l.place, l.placeCode, l.tenantPhone.orEmpty().filter(Char::isDigit))
                .joinToString(" ").lowercase()
            words.all { it in hay }
        }
    }
}

sealed interface TenantsIntent : UiIntent {
    data object Open : TenantsIntent
    /** From a "close request" notification without a lease id: open the lease that waits for me. */
    data object OpenCloseRequest : TenantsIntent
    data object Close : TenantsIntent
    data class ShowPast(val past: Boolean) : TenantsIntent
    data object Refresh : TenantsIntent
    data class OpenTenant(val leaseId: String) : TenantsIntent
    data object CloseTenant : TenantsIntent
    data class Search(val query: String) : TenantsIntent
    data class SelectType(val type: String?) : TenantsIntent
}

sealed interface TenantsEffect : UiEffect {
    data class OpenLease(val lease: Lease) : TenantsEffect
}

@HiltViewModel
class TenantsViewModel @Inject constructor(
    private val manage: ManagePropertyRepository,
    private val leases: LeaseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantsState, TenantsIntent, TenantsEffect>(TenantsState()) {

    private var exitJob: Job? = null

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantsState() } } }
    }

    override suspend fun handleIntent(intent: TenantsIntent) {
        when (intent) {
            TenantsIntent.Open -> {
                setState { TenantsState(isOpen = true) }
                load(refreshing = false)
            }
            TenantsIntent.OpenCloseRequest -> {
                setState { TenantsState(isOpen = true) }
                load(refreshing = false)
                exitJob?.join()
                // The (first) tenant who asked → that lease; none → the list stays (Back → home).
                currentState.items.firstOrNull { it.id in currentState.closeRequested }?.let { sendEffect(TenantsEffect.OpenLease(it)) }
            }
            TenantsIntent.Close -> setState { TenantsState() }
            is TenantsIntent.ShowPast -> if (intent.past != currentState.past) {
                setState { copy(past = intent.past, items = emptyList(), openLeaseId = null, type = null) } // query stays
                load(refreshing = false)
            }
            TenantsIntent.Refresh -> if (currentState.isOpen) load(refreshing = true)
            is TenantsIntent.OpenTenant -> setState { copy(openLeaseId = intent.leaseId) }
            TenantsIntent.CloseTenant -> setState { copy(openLeaseId = null) }
            is TenantsIntent.Search -> setState { copy(query = intent.query.take(60)) }
            is TenantsIntent.SelectType -> setState { copy(type = intent.type) }
        }
    }

    private suspend fun load(refreshing: Boolean) {
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        val past = currentState.past
        when (val r = manage.allLeases(active = !past)) {
            is AppResult.Success -> {
                setState { copy(items = r.data, loading = false, refreshing = false) }
                if (!past) loadCloseRequests(r.data)
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    /** "Close requested" chips (one GET /exit per current lease, first 20). */
    private fun loadCloseRequests(list: List<Lease>) {
        exitJob?.cancel()
        exitJob = viewModelScope.launch {
            val asked = list.take(20).mapNotNull { l ->
                (leases.exitState(l.id) as? AppResult.Success)?.data?.takeIf { it.needsMyResponse }?.let { l.id }
            }.toSet()
            setState { copy(closeRequested = asked) }
        }
    }
}
