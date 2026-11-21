package com.saferenttechnologies.android.presentation.inspection

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Condition
import com.saferenttechnologies.android.domain.model.Inspection
import com.saferenttechnologies.android.domain.model.InspectionForm
import com.saferenttechnologies.android.domain.model.InspectionRules
import com.saferenttechnologies.android.domain.model.InspectionType
import com.saferenttechnologies.android.domain.model.InventoryItem
import com.saferenttechnologies.android.domain.model.RepairItem
import com.saferenttechnologies.android.domain.model.ScheduleForm
import com.saferenttechnologies.android.domain.repository.InspectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A flat / room / lease the inspection can be about (from the property screen). */
data class InspectionPlace(val id: String, val label: String)
data class InspectionLease(val id: String, val label: String, val unitId: String?, val bedId: String?)

data class InspectionsState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val propertyTitle: String = "",
    /** inspections.manage / properties.manage → schedule, record, photos, acknowledge. */
    val canManage: Boolean = false,
    val units: List<InspectionPlace> = emptyList(),
    val rooms: List<InspectionPlace> = emptyList(),
    val leases: List<InspectionLease> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** null = not loaded yet (shimmer). */
    val items: List<Inspection>? = null,
    /** Report open (read only). */
    val selectedId: String? = null,
    /** "Record report" open for this inspection. */
    val editing: Boolean = false,
    val form: InspectionForm = InspectionForm(),
    /** Save tapped with problems → local checks under the fields. */
    val showErrors: Boolean = false,
    /** 400 details.issues: path → message. */
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** Schedule sheet. */
    val schedule: ScheduleForm? = null,
    val scheduleError: String? = null,
    val confirmAcknowledge: Boolean = false,
    /** "Which area?" sheet before the camera. */
    val pickArea: Boolean = false,
    /** Area chosen → camera open. */
    val cameraArea: String? = null,
    /** Photos still being sent (one at a time). */
    val uploading: Int = 0,
    /** "Load from furniture register" running. */
    val loadingRegister: Boolean = false,
) : UiState {
    val selected: Inspection? get() = items?.firstOrNull { it.id == selectedId }
    val canEditSelected: Boolean get() = selected?.let { InspectionRules.canEdit(it, canManage) } == true
    val canAcknowledgeSelected: Boolean get() = selected?.let { InspectionRules.canAcknowledge(it, canManage) } == true
    val problems: Map<String, InspectionRules.Problem> get() = InspectionRules.validate(form)
    val busy: Boolean get() = saving || loading
}

sealed interface InspectionsIntent : UiIntent {
    data class Open(
        val propertyId: String,
        val title: String,
        val canManage: Boolean,
        val units: List<InspectionPlace>,
        val rooms: List<InspectionPlace>,
        val leases: List<InspectionLease>,
    ) : InspectionsIntent
    data object Close : InspectionsIntent
    data object Refresh : InspectionsIntent
    data class Select(val id: String) : InspectionsIntent
    data object CloseReport : InspectionsIntent

    // schedule
    data object StartSchedule : InspectionsIntent
    data class EditSchedule(val form: ScheduleForm) : InspectionsIntent
    data object SubmitSchedule : InspectionsIntent
    data object CancelSchedule : InspectionsIntent

    // record
    data object StartRecord : InspectionsIntent
    data object CloseRecord : InspectionsIntent
    data class EditForm(val form: InspectionForm) : InspectionsIntent
    data class SetCondition(val key: String, val value: Condition) : InspectionsIntent
    data class AddInventory(val appliance: Boolean, val item: String = "") : InspectionsIntent
    data class EditInventory(val appliance: Boolean, val index: Int, val row: InventoryItem) : InspectionsIntent
    data class RemoveInventory(val appliance: Boolean, val index: Int) : InspectionsIntent
    data object AddRepair : InspectionsIntent
    data class EditRepair(val index: Int, val repair: RepairItem) : InspectionsIntent
    data class RemoveRepair(val index: Int) : InspectionsIntent
    data object Save : InspectionsIntent
    /** Fill furniture / appliances from the property's register (linked rows). */
    data object LoadFromRegister : InspectionsIntent

    // acknowledge
    data class AskAcknowledge(val show: Boolean) : InspectionsIntent
    data object Acknowledge : InspectionsIntent

    // photos
    data class PickArea(val show: Boolean) : InspectionsIntent
    data class AreaChosen(val area: String) : InspectionsIntent
    data object CloseCamera : InspectionsIntent
    data class PhotoTaken(val filePath: String) : InspectionsIntent
}

sealed interface InspectionsEffect : UiEffect {
    data class Message(val text: String) : InspectionsEffect
}

/** Inspection reports of one property: list → report → record / schedule / acknowledge / photos. Never logs report contents. */
@HiltViewModel
class InspectionsViewModel @Inject constructor(
    private val repository: InspectionRepository,
    private val register: com.saferenttechnologies.android.domain.repository.RegisterRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<InspectionsState, InspectionsIntent, InspectionsEffect>(InspectionsState()) {

    private var draftJob: Job? = null

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { InspectionsState() } } }
    }

    override suspend fun handleIntent(intent: InspectionsIntent) {
        when (intent) {
            is InspectionsIntent.Open -> {
                val same = currentState.propertyId == intent.propertyId
                setState {
                    InspectionsState(
                        isOpen = true, propertyId = intent.propertyId, propertyTitle = intent.title, canManage = intent.canManage,
                        units = intent.units, rooms = intent.rooms, leases = intent.leases, items = if (same) items else null,
                    )
                }
                load()
            }
            InspectionsIntent.Close -> if (!currentState.saving) setState { copy(isOpen = false, selectedId = null, editing = false, schedule = null, pickArea = false, cameraArea = null) }
            InspectionsIntent.Refresh -> load()
            is InspectionsIntent.Select -> setState { copy(selectedId = intent.id) }
            InspectionsIntent.CloseReport -> setState { copy(selectedId = null, confirmAcknowledge = false) }

            InspectionsIntent.StartSchedule -> if (currentState.canManage) setState { copy(schedule = ScheduleForm(), scheduleError = null) }
            is InspectionsIntent.EditSchedule -> setState { copy(schedule = intent.form, scheduleError = null) }
            InspectionsIntent.CancelSchedule -> if (!currentState.saving) setState { copy(schedule = null, scheduleError = null) }
            InspectionsIntent.SubmitSchedule -> submitSchedule()

            InspectionsIntent.StartRecord -> startRecord()
            InspectionsIntent.CloseRecord -> if (!currentState.saving) setState { copy(editing = false, showErrors = false, serverErrors = emptyMap()) }
            is InspectionsIntent.EditForm -> edit { intent.form }
            is InspectionsIntent.SetCondition -> edit { it.copy(conditions = it.conditions + (intent.key to intent.value)) }
            is InspectionsIntent.AddInventory -> edit { f ->
                val row = InventoryItem(item = intent.item)
                if (intent.appliance) f.copy(appliances = (f.appliances + row).take(InspectionRules.MAX_ROWS))
                else f.copy(furniture = (f.furniture + row).take(InspectionRules.MAX_ROWS))
            }
            is InspectionsIntent.EditInventory -> edit { f ->
                val row = intent.row.copy(item = intent.row.item.take(80), quantity = intent.row.quantity.filter(Char::isDigit).take(4))
                if (intent.appliance) f.copy(appliances = f.appliances.mapIndexed { i, r -> if (i == intent.index) row else r })
                else f.copy(furniture = f.furniture.mapIndexed { i, r -> if (i == intent.index) row else r })
            }
            is InspectionsIntent.RemoveInventory -> edit { f ->
                if (intent.appliance) f.copy(appliances = f.appliances.filterIndexed { i, _ -> i != intent.index })
                else f.copy(furniture = f.furniture.filterIndexed { i, _ -> i != intent.index })
            }
            InspectionsIntent.AddRepair -> edit { f -> if (f.repairs.size >= InspectionRules.MAX_REPAIRS) f else f.copy(repairs = f.repairs + RepairItem()) }
            is InspectionsIntent.EditRepair -> edit { f ->
                val r = intent.repair.copy(
                    item = intent.repair.item.take(120),
                    description = intent.repair.description.take(1000),
                    estimatedCost = InspectionRules.cleanDecimal(intent.repair.estimatedCost).take(12),
                )
                // An open repair and no result yet → "Needs repairs".
                InspectionRules.withSuggestedResult(f.copy(repairs = f.repairs.mapIndexed { i, old -> if (i == intent.index) r else old }))
            }
            is InspectionsIntent.RemoveRepair -> edit { f -> f.copy(repairs = f.repairs.filterIndexed { i, _ -> i != intent.index }) }
            InspectionsIntent.Save -> save()
            InspectionsIntent.LoadFromRegister -> loadRegister()

            is InspectionsIntent.AskAcknowledge -> setState { copy(confirmAcknowledge = intent.show && canAcknowledgeSelected) }
            InspectionsIntent.Acknowledge -> acknowledge()

            is InspectionsIntent.PickArea -> setState { copy(pickArea = intent.show && canEditSelected) }
            is InspectionsIntent.AreaChosen -> setState { copy(pickArea = false, cameraArea = intent.area) }
            InspectionsIntent.CloseCamera -> setState { copy(cameraArea = null) }
            is InspectionsIntent.PhotoTaken -> uploadPhoto(intent.filePath)
        }
    }

    private suspend fun load() {
        val id = currentState.propertyId ?: return
        setState { copy(loading = true, error = null) }
        when (val r = repository.list(id)) {
            is AppResult.Success -> if (currentState.propertyId == id) setState { copy(loading = false, items = r.data.sortedByDescending { it.shownDate }) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun submitSchedule() {
        val s = currentState
        val form = s.schedule ?: return
        val id = s.propertyId ?: return
        if (s.saving) return
        if (form.scheduledAt.isBlank()) return setState { copy(scheduleError = SCHEDULE_DATE) }
        if (form.type.needsLease && form.leaseId == null) return setState { copy(scheduleError = LEASE_NEEDED) }
        setState { copy(saving = true) }
        val r = repository.schedule(id, form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(schedule = null) }
                load()
                setState { copy(selectedId = r.data.id) }
            }
            is AppResult.Failure -> {
                val e = r.error
                val text = if (e is AppError.FieldIssues) e.issues.values.firstOrNull() ?: e.message else e.message
                if (e !is AppError.AccountBlocked) setState { copy(scheduleError = text) }
            }
        }
    }

    private suspend fun startRecord() {
        val i = currentState.selected ?: return
        if (!currentState.canEditSelected) return
        // Unsaved typing from before (app was closed) wins over the server copy.
        val draft = repository.draft(i.id)
        val form = InspectionRules.withSuggestedResult(draft ?: InspectionForm.from(i))
        setState { copy(editing = true, form = form, showErrors = false, serverErrors = emptyMap()) }
    }

    private fun edit(change: (InspectionForm) -> InspectionForm) {
        val s = currentState
        if (!s.editing || s.saving) return
        setState { copy(form = change(form), serverErrors = emptyMap()) }
        // Keep a local copy (debounced) so nothing is lost if the app is killed.
        val id = s.selectedId ?: return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(600)
            repository.saveDraft(id, currentState.form)
        }
    }

    private suspend fun save() {
        val s = currentState
        val pid = s.propertyId ?: return
        val id = s.selectedId ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true, serverErrors = emptyMap()) }
        val r = repository.record(pid, id, s.form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                draftJob?.cancel()
                repository.saveDraft(id, null)
                setState { copy(editing = false, showErrors = false) }
                sendEffect(InspectionsEffect.Message(r.data.ifBlank { SAVED }))
                load() // photos and the saved values come from GET
            }
            is AppResult.Failure -> when (val e = r.error) {
                is AppError.FieldIssues -> {
                    setState { copy(serverErrors = e.issues, showErrors = true) }
                    sendEffect(InspectionsEffect.Message(e.issues.values.firstOrNull() ?: e.message))
                }
                // Acknowledged meanwhile → read only.
                is AppError.InvalidState -> {
                    repository.saveDraft(id, null)
                    setState { copy(editing = false) }
                    sendEffect(InspectionsEffect.Message(e.message))
                    load()
                }
                is AppError.AccountBlocked -> Unit
                else -> sendEffect(InspectionsEffect.Message(e.message))
            }
        }
    }

    private suspend fun loadRegister() {
        val s = currentState
        val pid = s.propertyId ?: return
        val i = s.selected ?: return
        if (!s.editing || s.saving || s.loadingRegister) return
        setState { copy(loadingRegister = true) }
        val r = register.register(pid)
        setState { copy(loadingRegister = false) }
        when (r) {
            is AppResult.Success -> {
                // The inspection's flat / room + building items (all when it has no place).
                val items = com.saferenttechnologies.android.domain.model.RegisterRules.forInspection(r.data.items, i.unitId, i.roomId)
                if (items.isEmpty()) return sendEffect(InspectionsEffect.Message(REGISTER_EMPTY))
                val (furniture, appliances) = com.saferenttechnologies.android.domain.model.RegisterRules.inspectionRows(items)
                edit { f ->
                    f.copy(
                        furniture = com.saferenttechnologies.android.domain.model.RegisterRules.merge(f.furniture, furniture).take(InspectionRules.MAX_ROWS),
                        appliances = com.saferenttechnologies.android.domain.model.RegisterRules.merge(f.appliances, appliances).take(InspectionRules.MAX_ROWS),
                    )
                }
                sendEffect(InspectionsEffect.Message("@loaded:${items.size}"))
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(InspectionsEffect.Message(r.error.message))
        }
    }

    private suspend fun acknowledge() {
        val s = currentState
        val pid = s.propertyId ?: return
        val id = s.selectedId ?: return
        setState { copy(confirmAcknowledge = false, saving = true) }
        val r = repository.acknowledge(pid, id)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                sendEffect(InspectionsEffect.Message(r.data.ifBlank { ACKNOWLEDGED }))
                load()
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(InspectionsEffect.Message(r.error.message))
        }
    }

    private suspend fun uploadPhoto(path: String) {
        val s = currentState
        val pid = s.propertyId ?: return
        val id = s.selectedId ?: return
        val area = s.cameraArea ?: "OTHER"
        setState { copy(cameraArea = null, uploading = uploading + 1) }
        val r = repository.uploadPhoto(pid, id, area, path)
        setState { copy(uploading = (uploading - 1).coerceAtLeast(0)) }
        when (r) {
            is AppResult.Success -> load()
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(InspectionsEffect.Message(r.error.message))
        }
    }

    companion object {
        const val SAVED = "Inspection report saved"
        const val ACKNOWLEDGED = "Report acknowledged"
        /** Replaced by strings in the screen. */
        const val SCHEDULE_DATE = "@date"
        const val LEASE_NEEDED = "@lease"
        const val REGISTER_EMPTY = "@register_empty"
    }
}
