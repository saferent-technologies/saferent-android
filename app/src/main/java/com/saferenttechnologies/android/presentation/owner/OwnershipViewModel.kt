package com.saferenttechnologies.android.presentation.owner

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.CoOwner
import com.saferenttechnologies.android.domain.model.OwnerType
import com.saferenttechnologies.android.domain.model.Ownership
import com.saferenttechnologies.android.domain.model.OwnershipRules
import com.saferenttechnologies.android.domain.model.Representative
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnershipState(
    val propertyId: String? = null,
    val loading: Boolean = false,
    /** Couldn't load (retry). */
    val loadError: String? = null,
    /** What the server has (normalized); null = never saved. */
    val saved: Ownership? = null,
    val form: Ownership = Ownership(),
    val powerOfAttorneyUploaded: Boolean = false,
    val canEdit: Boolean = true,
    /** Save tapped with problems → show the local checks under the fields. */
    val showErrors: Boolean = false,
    /** 400 details.issues: path → message. */
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val uploadingPoa: Boolean = false,
) : UiState {
    val loaded: Boolean get() = propertyId != null && !loading && loadError == null
    /** Something not saved yet (Submit waits for it). Never saved counts as unsaved. */
    val dirty: Boolean get() = canEdit && (saved == null || OwnershipRules.normalized(form) != OwnershipRules.normalized(saved))
    val problems: Map<String, OwnershipRules.Problem> get() = OwnershipRules.validate(form)
    val canSave: Boolean get() = canEdit && dirty && !saving && OwnershipRules.sharesOk(OwnershipRules.normalized(form))
    val needsPoa: Boolean get() = OwnershipRules.needsPoa(saved ?: form, powerOfAttorneyUploaded)
}

sealed interface OwnershipIntent : UiIntent {
    /** Wizard Ownership step / property detail card. Same id again → keeps what's typed. */
    data class Load(val propertyId: String, val force: Boolean = false) : OwnershipIntent
    data object Retry : OwnershipIntent
    data class SetOwnerType(val type: OwnerType) : OwnershipIntent
    /** Company only: SOLE / JOINT. */
    data class SetOwnershipType(val type: String) : OwnershipIntent
    data class CompanyName(val text: String) : OwnershipIntent
    data class MyShare(val text: String) : OwnershipIntent
    data object AddCoOwner : OwnershipIntent
    data class RemoveCoOwner(val index: Int) : OwnershipIntent
    data class EditCoOwner(val index: Int, val coOwner: CoOwner) : OwnershipIntent
    data class RepresentativeOn(val on: Boolean) : OwnershipIntent
    data class EditRepresentative(val representative: Representative) : OwnershipIntent
    data object Save : OwnershipIntent
    /** Power of Attorney picked (content://, JPG / PNG / PDF). */
    data class PoaPicked(val uri: String) : OwnershipIntent
}

sealed interface OwnershipEffect : UiEffect {
    data class Message(val text: String) : OwnershipEffect
}

/** "Who owns this property?": GET / PUT properties/{id}/ownership + Power of Attorney upload. Private data, never logged. */
@HiltViewModel
class OwnershipViewModel @Inject constructor(
    private val repository: OwnerRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<OwnershipState, OwnershipIntent, OwnershipEffect>(OwnershipState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { OwnershipState() } } }
    }

    override suspend fun handleIntent(intent: OwnershipIntent) {
        when (intent) {
            is OwnershipIntent.Load -> {
                val s = currentState
                if (!intent.force && s.propertyId == intent.propertyId && (s.loaded || s.loading)) return
                setState { OwnershipState(propertyId = intent.propertyId, loading = true) }
                load(intent.propertyId)
            }
            OwnershipIntent.Retry -> currentState.propertyId?.let {
                setState { copy(loading = true, loadError = null) }
                load(it)
            }
            is OwnershipIntent.SetOwnerType -> edit { OwnershipRules.withOwnerType(it, intent.type) }
            is OwnershipIntent.SetOwnershipType -> edit {
                val joint = intent.type == "JOINT"
                it.copy(ownershipType = intent.type, coOwners = if (joint) it.coOwners.ifEmpty { listOf(CoOwner()) } else emptyList())
            }
            is OwnershipIntent.CompanyName -> edit { it.copy(companyName = intent.text.take(150)) }
            is OwnershipIntent.MyShare -> edit { it.copy(ownerSharePercent = cleanPercent(intent.text)) }
            OwnershipIntent.AddCoOwner -> edit { if (it.coOwners.size >= OwnershipRules.MAX_CO_OWNERS) it else it.copy(coOwners = it.coOwners + CoOwner()) }
            is OwnershipIntent.RemoveCoOwner -> edit { o -> o.copy(coOwners = o.coOwners.filterIndexed { i, _ -> i != intent.index }) }
            is OwnershipIntent.EditCoOwner -> edit { o ->
                val c = intent.coOwner.copy(sharePercent = cleanPercent(intent.coOwner.sharePercent), name = intent.coOwner.name.take(120), relation = intent.coOwner.relation.take(40))
                o.copy(coOwners = o.coOwners.mapIndexed { i, old -> if (i == intent.index) c else old })
            }
            is OwnershipIntent.RepresentativeOn -> edit {
                // A company always needs one: the switch stays on.
                if (!intent.on && it.ownerType == OwnerType.COMPANY) it
                else it.copy(representative = if (intent.on) it.representative ?: Representative() else null)
            }
            is OwnershipIntent.EditRepresentative -> edit {
                it.copy(representative = intent.representative.copy(relation = intent.representative.relation.take(40), name = intent.representative.name.take(120)))
            }
            OwnershipIntent.Save -> save()
            is OwnershipIntent.PoaPicked -> uploadPoa(intent.uri)
        }
    }

    private suspend fun load(id: String) {
        when (val r = repository.ownership(id)) {
            is AppResult.Success -> {
                if (currentState.propertyId != id) return
                val saved = r.data.ownership?.let(OwnershipRules::normalized)
                setState {
                    copy(
                        loading = false,
                        loadError = null,
                        saved = saved,
                        // Edits typed before (same property) win over a refresh.
                        form = if (dirty && form != Ownership()) form else saved ?: Ownership(),
                        powerOfAttorneyUploaded = r.data.powerOfAttorneyUploaded,
                        canEdit = r.data.canEdit,
                    )
                }
            }
            is AppResult.Failure -> setState { copy(loading = false, loadError = r.error.message) }
        }
    }

    private fun edit(change: (Ownership) -> Ownership) {
        if (!currentState.canEdit || currentState.saving) return
        setState { copy(form = change(form), serverErrors = emptyMap()) }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.propertyId ?: return
        if (!s.canEdit || s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true, serverErrors = emptyMap()) }
        when (val r = repository.saveOwnership(id, OwnershipRules.normalized(s.form))) {
            is AppResult.Success -> {
                val saved = OwnershipRules.normalized(r.data.second ?: s.form)
                setState { copy(saving = false, saved = saved, form = saved, showErrors = false) }
                sendEffect(OwnershipEffect.Message(r.data.first.ifBlank { SAVED }))
                // POA status may have changed with the authority type.
                (repository.ownership(id) as? AppResult.Success)?.let { i -> setState { copy(powerOfAttorneyUploaded = i.data.powerOfAttorneyUploaded, canEdit = i.data.canEdit) } }
            }
            is AppResult.Failure -> {
                setState { copy(saving = false) }
                when (val e = r.error) {
                    is AppError.FieldIssues -> {
                        setState { copy(serverErrors = e.issues, showErrors = true) }
                        if (e.issues.isEmpty()) sendEffect(OwnershipEffect.Message(e.message))
                    }
                    // Sent for review / approved → read-only from now on.
                    is AppError.InvalidState -> {
                        setState { copy(canEdit = false) }
                        sendEffect(OwnershipEffect.Message(e.message))
                    }
                    is AppError.AccountBlocked -> Unit
                    else -> sendEffect(OwnershipEffect.Message(e.message))
                }
            }
        }
    }

    private suspend fun uploadPoa(uri: String) {
        val id = currentState.propertyId ?: return
        if (currentState.uploadingPoa) return
        setState { copy(uploadingPoa = true) }
        val r = repository.uploadPowerOfAttorney(id, uri)
        if (r is AppResult.Success) {
            // GET again so powerOfAttorneyUploaded turns true and the warning goes away.
            (repository.ownership(id) as? AppResult.Success)?.let { i -> setState { copy(powerOfAttorneyUploaded = i.data.powerOfAttorneyUploaded) } }
            sendEffect(OwnershipEffect.Message(r.data.ifBlank { POA_UPLOADED }))
        } else if (r is AppResult.Failure && r.error !is AppError.AccountBlocked) {
            sendEffect(OwnershipEffect.Message(r.error.message))
        }
        setState { copy(uploadingPoa = false) }
    }

    private fun cleanPercent(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '.' }
        val dot = digits.indexOf('.')
        return if (dot < 0) digits.take(3) else digits.substring(0, dot).take(3) + "." + digits.substring(dot + 1).filter(Char::isDigit).take(2)
    }

    companion object {
        const val SAVED = "Ownership details saved"
        const val POA_UPLOADED = "Power of Attorney uploaded"
    }
}
