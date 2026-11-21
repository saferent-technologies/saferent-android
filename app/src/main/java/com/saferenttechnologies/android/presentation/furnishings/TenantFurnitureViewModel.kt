package com.saferenttechnologies.android.presentation.furnishings

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RegisterItem
import com.saferenttechnologies.android.domain.repository.RegisterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantFurnitureState(val propertyId: String? = null, val items: List<RegisterItem> = emptyList()) : UiState

sealed interface TenantFurnitureIntent : UiIntent {
    data class Load(val propertyId: String) : TenantFurnitureIntent
}

sealed interface TenantFurnitureEffect : UiEffect

/** Tenant home (active lease): "Furniture in your home" (reduced items from the server). Quiet: no errors shown. */
@HiltViewModel
class TenantFurnitureViewModel @Inject constructor(
    private val repository: RegisterRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantFurnitureState, TenantFurnitureIntent, TenantFurnitureEffect>(TenantFurnitureState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantFurnitureState() } } }
    }

    override suspend fun handleIntent(intent: TenantFurnitureIntent) {
        when (intent) {
            is TenantFurnitureIntent.Load -> {
                if (currentState.propertyId != intent.propertyId) setState { TenantFurnitureState(propertyId = intent.propertyId) }
                (repository.register(intent.propertyId) as? AppResult.Success)?.let { r ->
                    if (currentState.propertyId == intent.propertyId) setState { copy(items = r.data.items) }
                }
            }
        }
    }
}
