package com.saferenttechnologies.android.presentation.society

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.CommonArea
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.ParkingSlot
import com.saferenttechnologies.android.domain.model.Society
import com.saferenttechnologies.android.domain.model.SocietyData
import com.saferenttechnologies.android.domain.model.SocietyRule
import com.saferenttechnologies.android.domain.model.SocietyRules
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import com.saferenttechnologies.android.domain.repository.SocietyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A flat the owner can allocate parking to. */
data class SocietyUnit(val id: String, val label: String)

data class SocietyState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val title: String = "",
    val units: List<SocietyUnit> = emptyList(),
    val parkingTab: Boolean = false,
    val data: SocietyData? = null,
    val form: Society = Society(),
    val error: String? = null,
    /** 412 "Society details are for buildings and gated communities." */
    val notApplicable: String? = null,
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** Parking add / edit sheet (id blank = new). */
    val slot: ParkingSlot? = null,
    val slotErrors: Boolean = false,
    val slotServerError: String? = null,
    val slotBusy: Boolean = false,
    val deleteSlot: ParkingSlot? = null,
    val uploadingRules: Boolean = false,
    val sharing: Boolean = false,
) : UiState {
    val problems: Map<String, SocietyRules.Problem> get() = SocietyRules.validate(form)
    private val saved: Society get() = data?.society ?: Society()
    val changed: Boolean get() = data != null && SocietyRules.clean(saved) != SocietyRules.clean(form)
    val slotProblems: Map<String, SocietyRules.Problem> get() = slot?.let { SocietyRules.validateSlot(it) }.orEmpty()
    /** ≈ per month: the server's number until the amount / frequency is edited. */
    val monthly: Double?
        get() = if (form.maintenanceAmount == saved.maintenanceAmount && form.maintenanceFrequency == saved.maintenanceFrequency && saved.maintenanceMonthly != null) saved.maintenanceMonthly
        else SocietyRules.monthly(ComplianceRules.amount(form.maintenanceAmount), form.maintenanceFrequency)
}

sealed interface SocietyIntent : UiIntent {
    data class Open(val propertyId: String, val title: String = "", val units: List<SocietyUnit> = emptyList()) : SocietyIntent
    data object Close : SocietyIntent
    data object Retry : SocietyIntent
    data class Tab(val parking: Boolean) : SocietyIntent
    data class Edit(val form: Society) : SocietyIntent
    data object AddRule : SocietyIntent
    data class EditRule(val index: Int, val rule: SocietyRule) : SocietyIntent
    data class RemoveRule(val index: Int) : SocietyIntent
    data class AddArea(val name: String = "") : SocietyIntent
    data class EditArea(val index: Int, val area: CommonArea) : SocietyIntent
    data class RemoveArea(val index: Int) : SocietyIntent
    data class ToggleGate(val method: String) : SocietyIntent
    data object Save : SocietyIntent
    /** null closes the sheet; a slot with a blank id = new. */
    data class OpenSlot(val slot: ParkingSlot?) : SocietyIntent
    data class EditSlot(val slot: ParkingSlot) : SocietyIntent
    data object SaveSlot : SocietyIntent
    data class AskDelete(val slot: ParkingSlot?) : SocietyIntent
    data object ConfirmDelete : SocietyIntent
    data class UploadRules(val uri: String) : SocietyIntent
    data class ShareRules(val shared: Boolean) : SocietyIntent
}

sealed interface SocietyEffect : UiEffect {
    data class Message(val text: String) : SocietyEffect
}

@HiltViewModel
class SocietyViewModel @Inject constructor(
    private val repository: SocietyRepository,
    private val documents: OwnerRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<SocietyState, SocietyIntent, SocietyEffect>(SocietyState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { SocietyState() } } }
    }

    override suspend fun handleIntent(intent: SocietyIntent) {
        when (intent) {
            is SocietyIntent.Open -> {
                setState { SocietyState(isOpen = true, propertyId = intent.propertyId, title = intent.title, units = intent.units) }
                load()
            }
            SocietyIntent.Close -> if (!currentState.saving && !currentState.slotBusy) setState { SocietyState() }
            SocietyIntent.Retry -> load()
            is SocietyIntent.Tab -> setState { copy(parkingTab = intent.parking) }
            is SocietyIntent.Edit -> edit { trimmed(intent.form) }
            SocietyIntent.AddRule -> if (currentState.form.rules.size < SocietyRules.MAX_RULES) edit { copy(rules = rules + SocietyRule()) }
            is SocietyIntent.EditRule -> edit {
                copy(rules = rules.mapIndexed { i, r -> if (i == intent.index) SocietyRule(intent.rule.title.take(SocietyRules.RULE_TITLE_MAX), intent.rule.description.take(SocietyRules.RULE_DESC_MAX)) else r })
            }
            is SocietyIntent.RemoveRule -> edit { copy(rules = rules.filterIndexed { i, _ -> i != intent.index }) }
            is SocietyIntent.AddArea -> {
                val f = currentState.form
                if (f.commonAreas.size < SocietyRules.MAX_AREAS && (intent.name.isBlank() || f.commonAreas.none { it.name.equals(intent.name, true) })) {
                    edit { copy(commonAreas = commonAreas + CommonArea(intent.name)) }
                }
            }
            is SocietyIntent.EditArea -> edit {
                copy(commonAreas = commonAreas.mapIndexed { i, a -> if (i == intent.index) CommonArea(intent.area.name.take(60), intent.area.timings.take(80), intent.area.notes.take(200)) else a })
            }
            is SocietyIntent.RemoveArea -> edit { copy(commonAreas = commonAreas.filterIndexed { i, _ -> i != intent.index }) }
            is SocietyIntent.ToggleGate -> edit {
                copy(gateAccessMethods = if (intent.method in gateAccessMethods) gateAccessMethods - intent.method else gateAccessMethods + intent.method)
            }
            SocietyIntent.Save -> save()
            is SocietyIntent.OpenSlot -> if (!currentState.slotBusy) setState { copy(slot = intent.slot, slotErrors = false, slotServerError = null) }
            is SocietyIntent.EditSlot -> setState {
                copy(slot = intent.slot.copy(slotNumber = intent.slot.slotNumber.take(SocietyRules.SLOT_MAX), notes = intent.slot.notes.take(200)), slotServerError = null)
            }
            SocietyIntent.SaveSlot -> saveSlot()
            is SocietyIntent.AskDelete -> setState { copy(deleteSlot = intent.slot) }
            SocietyIntent.ConfirmDelete -> deleteSlot()
            is SocietyIntent.UploadRules -> uploadRules(intent.uri)
            is SocietyIntent.ShareRules -> shareRules(intent.shared)
        }
    }

    private fun edit(change: Society.() -> Society) = setState { copy(form = form.change(), serverErrors = emptyMap()) }

    private fun trimmed(s: Society) = s.copy(
        associationName = s.associationName.take(SocietyRules.NAME_MAX),
        registrationNumber = s.registrationNumber.take(SocietyRules.REG_MAX),
        contactName = s.contactName.take(80),
        contactPhone = s.contactPhone.take(16),
        contactEmail = s.contactEmail.take(120),
        officeHours = s.officeHours.take(80),
        maintenanceAmount = ComplianceRules.amountInput(s.maintenanceAmount),
        visitorPolicy = s.visitorPolicy.take(SocietyRules.TEXT_MAX),
        gateContactName = s.gateContactName.take(80),
        gateContactPhone = s.gateContactPhone.take(16),
        clubhouseRules = s.clubhouseRules.take(SocietyRules.TEXT_MAX),
        moveAllowedTimings = s.moveAllowedTimings.take(120),
        moveChargeAmount = ComplianceRules.amountInput(s.moveChargeAmount),
        moveDepositAmount = ComplianceRules.amountInput(s.moveDepositAmount),
        moveNotes = s.moveNotes.take(SocietyRules.TEXT_MAX),
    )

    private fun message(e: AppError) = e.takeUnless { it is AppError.AccountBlocked }?.message

    private suspend fun load() {
        val id = currentState.propertyId ?: return
        setState { copy(error = null) }
        when (val r = repository.society(id)) {
            is AppResult.Success -> setState { copy(data = r.data, form = r.data.society ?: Society()) }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.InvalidState) setState { copy(notApplicable = e.message) } else setState { copy(error = message(e).orEmpty()) }
            }
        }
    }

    private suspend fun reloadQuietly() {
        val id = currentState.propertyId ?: return
        (repository.society(id) as? AppResult.Success)?.let { ok -> setState { copy(data = ok.data.copy(society = data?.society ?: ok.data.society)) } }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.propertyId ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        val r = repository.saveSociety(id, s.form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                val (msg, data) = r.data
                setState { copy(data = data, form = data.society ?: Society(), showErrors = false, serverErrors = emptyMap()) }
                sendEffect(SocietyEffect.Message(msg.ifBlank { SAVED }))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) setState { copy(serverErrors = e.issues, showErrors = true) }
                if (e is AppError.InvalidState) setState { copy(notApplicable = e.message) }
                message(e)?.let { sendEffect(SocietyEffect.Message(it)) }
            }
        }
    }

    private suspend fun saveSlot() {
        val s = currentState
        val id = s.propertyId ?: return
        val slot = s.slot ?: return
        if (s.slotBusy) return
        if (s.slotProblems.isNotEmpty()) return setState { copy(slotErrors = true) }
        val old = s.data?.parkingSlots?.firstOrNull { it.id == slot.id }
        val r = if (slot.id.isBlank() || old == null) {
            setState { copy(slotBusy = true) }
            repository.addSlot(id, slot)
        } else {
            val changes = SocietyRules.slotChanges(old, slot)
            if (changes.isEmpty()) return setState { copy(slot = null) }
            setState { copy(slotBusy = true) }
            repository.updateSlot(id, slot.id, changes)
        }
        setState { copy(slotBusy = false) }
        when (r) {
            is AppResult.Success -> {
                val saved = r.data.let { if (it.unitLabel == null && it.unitId != null) it.copy(unitLabel = s.units.firstOrNull { u -> u.id == it.unitId }?.label) else it }
                setState {
                    val list = data?.parkingSlots.orEmpty()
                    val next = if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
                    copy(slot = null, data = data?.copy(parkingSlots = next))
                }
            }
            is AppResult.Failure -> {
                val e = r.error
                // 409 duplicate number / 400 wrong flat / 412 flat not approved yet → shown in the sheet.
                val issue = (e as? AppError.FieldIssues)?.issues?.values?.firstOrNull()
                setState { copy(slotServerError = issue ?: message(e)) }
            }
        }
    }

    private suspend fun deleteSlot() {
        val s = currentState
        val id = s.propertyId ?: return
        val slot = s.deleteSlot ?: return
        setState { copy(deleteSlot = null, slotBusy = true) }
        val r = repository.deleteSlot(id, slot.id)
        setState { copy(slotBusy = false) }
        when (r) {
            is AppResult.Success -> setState { copy(data = data?.let { d -> d.copy(parkingSlots = d.parkingSlots.filterNot { it.id == slot.id }) }) }
            is AppResult.Failure -> message(r.error)?.let { sendEffect(SocietyEffect.Message(it)) }
        }
    }

    private suspend fun uploadRules(uri: String) {
        val id = currentState.propertyId ?: return
        if (currentState.uploadingRules) return
        setState { copy(uploadingRules = true) }
        val r = documents.uploadPropertyDocument(id, uri, "SOCIETY_RULES")
        setState { copy(uploadingRules = false) }
        when (r) {
            is AppResult.Success -> {
                sendEffect(SocietyEffect.Message(r.data))
                reloadQuietly()
            }
            is AppResult.Failure -> message(r.error)?.let { sendEffect(SocietyEffect.Message(it)) }
        }
    }

    private suspend fun shareRules(shared: Boolean) {
        val doc = currentState.data?.rulesDocument ?: return
        if (currentState.sharing) return
        setState { copy(sharing = true, data = data?.copy(rulesDocument = doc.copy(sharedWithTenant = shared))) }
        val r = repository.shareDocument(doc.documentId, shared)
        setState { copy(sharing = false) }
        if (r is AppResult.Failure) {
            setState { copy(data = data?.copy(rulesDocument = doc)) }
            message(r.error)?.let { sendEffect(SocietyEffect.Message(it)) }
        }
    }

    companion object {
        const val SAVED = "@society_saved"
    }
}
