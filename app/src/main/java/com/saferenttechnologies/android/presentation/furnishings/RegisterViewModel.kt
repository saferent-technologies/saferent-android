package com.saferenttechnologies.android.presentation.furnishings

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Register
import com.saferenttechnologies.android.domain.model.RegisterItem
import com.saferenttechnologies.android.domain.model.RegisterItemForm
import com.saferenttechnologies.android.domain.model.RegisterRules
import com.saferenttechnologies.android.domain.model.RepairForm
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.repository.RegisterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A place items belong to: a flat (unitId), a PG room (roomId) or the building (both null). */
data class RegisterPlace(
    val unitId: String? = null,
    val roomId: String? = null,
    val label: String,
    /** UNFURNISHED / SEMI_FURNISHED / FULLY_FURNISHED / "" (building). */
    val furnishing: String = "",
) {
    val key: String get() = unitId?.let { "u:$it" } ?: roomId?.let { "r:$it" } ?: "b"
    val furnished: Boolean get() = RegisterRules.furnished(furnishing)
}

data class RegisterState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val title: String = "",
    /** Opened from the wizard of a live listing → the change request copy (view=draft). */
    val draft: Boolean = false,
    val canManage: Boolean = false,
    /** Building first, then flats / rooms. */
    val places: List<RegisterPlace> = emptyList(),
    /** Opened for one flat / room (wizard flat panel) → only that place + building. */
    val focus: RegisterPlace? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** null = not loaded (shimmer). */
    val register: Register? = null,
    val selectedId: String? = null,
    /** Add / edit form open; [editingId] null = new item. */
    val form: RegisterItemForm? = null,
    val editingId: String? = null,
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val repairForm: RepairForm? = null,
    val repairErrors: Boolean = false,
    val confirmDelete: Boolean = false,
    val camera: Boolean = false,
    val uploading: Int = 0,
) : UiState {
    val selected: RegisterItem? get() = register?.items?.firstOrNull { it.id == selectedId }
    val editing: RegisterItem? get() = register?.items?.firstOrNull { it.id == editingId }
    val problems: Map<String, RegisterRules.Problem> get() = form?.let { RegisterRules.validate(it) }.orEmpty()
    val repairProblems: Map<String, RegisterRules.Problem> get() = repairForm?.let { RegisterRules.validateRepair(it) }.orEmpty()
    /** Places shown: the focused one + building, or all. */
    val shownPlaces: List<RegisterPlace> get() = focus?.let { f -> places.filter { it.key == "b" || it.key == f.key } } ?: places
    val busy: Boolean get() = saving || loading
}

sealed interface RegisterIntent : UiIntent {
    data class Open(
        val propertyId: String,
        val title: String,
        val canManage: Boolean,
        val places: List<RegisterPlace>,
        val draft: Boolean = false,
        val focus: RegisterPlace? = null,
    ) : RegisterIntent
    data object Close : RegisterIntent
    data object Refresh : RegisterIntent
    data class Select(val id: String?) : RegisterIntent

    data class StartAdd(val place: RegisterPlace? = null, val category: String? = null) : RegisterIntent
    data object StartEdit : RegisterIntent
    data class EditForm(val form: RegisterItemForm) : RegisterIntent
    data object SaveItem : RegisterIntent
    data object CancelForm : RegisterIntent

    data class AskDelete(val show: Boolean) : RegisterIntent
    data object DeleteItem : RegisterIntent

    data class Camera(val open: Boolean) : RegisterIntent
    data class PhotoTaken(val filePath: String) : RegisterIntent
    data class DeletePhoto(val photoId: String) : RegisterIntent

    data object StartRepair : RegisterIntent
    data class EditRepair(val form: RepairForm) : RegisterIntent
    data object SaveRepair : RegisterIntent
    data object CancelRepair : RegisterIntent
    data class MarkRepairDone(val repairId: String) : RegisterIntent
    data class DeleteRepair(val repairId: String) : RegisterIntent
}

sealed interface RegisterEffect : UiEffect {
    data class Message(val text: String) : RegisterEffect
}

/** Owner / manager: furniture & appliances register of one property. Never logs item contents. */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: RegisterRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { RegisterState() } } }
    }

    override suspend fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.Open -> {
                val same = currentState.propertyId == intent.propertyId && currentState.draft == intent.draft
                setState {
                    RegisterState(
                        isOpen = true, propertyId = intent.propertyId, title = intent.title, draft = intent.draft,
                        canManage = intent.canManage, places = intent.places, focus = intent.focus, register = if (same) register else null,
                    )
                }
                load()
            }
            RegisterIntent.Close -> if (!currentState.saving) setState { copy(isOpen = false, selectedId = null, form = null, repairForm = null, camera = false) }
            RegisterIntent.Refresh -> load()
            is RegisterIntent.Select -> setState { copy(selectedId = intent.id, confirmDelete = false) }

            is RegisterIntent.StartAdd -> if (currentState.canManage) {
                val place = intent.place ?: currentState.focus
                setState {
                    copy(
                        form = RegisterItemForm(unitId = place?.unitId, roomId = place?.roomId, category = intent.category ?: "OTHER"),
                        editingId = null, showErrors = false, serverErrors = emptyMap(),
                    )
                }
            }
            RegisterIntent.StartEdit -> currentState.selected?.let { i ->
                if (currentState.canManage) setState { copy(form = RegisterItemForm.from(i), editingId = i.id, showErrors = false, serverErrors = emptyMap()) }
            }
            is RegisterIntent.EditForm -> if (!currentState.saving) setState { copy(form = clean(intent.form), serverErrors = emptyMap()) }
            RegisterIntent.CancelForm -> if (!currentState.saving) setState { copy(form = null, editingId = null) }
            RegisterIntent.SaveItem -> saveItem()

            is RegisterIntent.AskDelete -> setState { copy(confirmDelete = intent.show && canManage) }
            RegisterIntent.DeleteItem -> deleteItem()

            is RegisterIntent.Camera -> setState { copy(camera = intent.open && canManage) }
            is RegisterIntent.PhotoTaken -> uploadPhoto(intent.filePath)
            is RegisterIntent.DeletePhoto -> act { pid, item -> repository.deletePhoto(pid, item, intent.photoId).map { null } }

            RegisterIntent.StartRepair -> if (currentState.canManage) setState { copy(repairForm = RepairForm(repairDate = SimpleDate.today().iso()), repairErrors = false) }
            is RegisterIntent.EditRepair -> setState { copy(repairForm = intent.form.copy(cost = cleanCost(intent.form.cost), description = intent.form.description.take(1000), doneBy = intent.form.doneBy.take(120))) }
            RegisterIntent.CancelRepair -> if (!currentState.saving) setState { copy(repairForm = null) }
            RegisterIntent.SaveRepair -> saveRepair()
            is RegisterIntent.MarkRepairDone -> act { pid, item -> repository.markRepairDone(pid, item, intent.repairId) }
            is RegisterIntent.DeleteRepair -> act { pid, item -> repository.deleteRepair(pid, item, intent.repairId).map { null } }
        }
    }

    private fun <T, R> AppResult<T>.map(f: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(f(data))
        is AppResult.Failure -> this
    }

    private fun clean(f: RegisterItemForm) = f.copy(
        name = f.name.take(80), quantity = f.quantity.filter(Char::isDigit).take(3), brand = f.brand.take(80), model = f.model.take(80),
        serialNumber = f.serialNumber.take(80), notes = f.notes.take(500),
    )

    private fun cleanCost(raw: String): String {
        val d = raw.filter { it.isDigit() || it == '.' }
        val dot = d.indexOf('.')
        return (if (dot < 0) d else d.substring(0, dot) + "." + d.substring(dot + 1).filter(Char::isDigit).take(2)).take(11)
    }

    private suspend fun load() {
        val id = currentState.propertyId ?: return
        setState { copy(loading = true, error = null) }
        when (val r = repository.register(id, currentState.draft)) {
            is AppResult.Success -> if (currentState.propertyId == id) setState { copy(loading = false, register = r.data) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    /** Replace one item with the server's copy (repairs change the working status). */
    private fun replace(item: RegisterItem) = setState {
        val r = register ?: Register()
        val items = if (r.items.any { it.id == item.id }) r.items.map { if (it.id == item.id) item else it } else r.items + item
        copy(register = r.copy(items = items))
    }

    private suspend fun saveItem() {
        val s = currentState
        val pid = s.propertyId ?: return
        val form = s.form ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        val original = s.editing
        val r = if (original == null) repository.create(pid, form, s.draft) else repository.update(pid, original, form, s.draft)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                val (message, item) = r.data
                setState { copy(form = null, editingId = null, showErrors = false, selectedId = item?.id ?: selectedId) }
                if (message.isNotBlank()) sendEffect(RegisterEffect.Message(message))
                item?.let(::replace)
                load() // summary counts
            }
            is AppResult.Failure -> fail(r.error)
        }
    }

    private suspend fun deleteItem() {
        val s = currentState
        val pid = s.propertyId ?: return
        val id = s.selectedId ?: return
        setState { copy(confirmDelete = false, saving = true) }
        val r = repository.delete(pid, id)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(selectedId = null) }
                if (r.data.isNotBlank()) sendEffect(RegisterEffect.Message(r.data))
                load()
            }
            is AppResult.Failure -> fail(r.error)
        }
    }

    private suspend fun uploadPhoto(path: String) {
        val s = currentState
        val pid = s.propertyId ?: return
        val item = s.selected ?: return
        setState { copy(camera = false) }
        if (item.photos.size + s.uploading >= RegisterRules.MAX_PHOTOS) {
            java.io.File(path).delete()
            return sendEffect(RegisterEffect.Message(MAX_PHOTOS))
        }
        setState { copy(uploading = uploading + 1) }
        val r = repository.uploadPhoto(pid, item.id, path)
        setState { copy(uploading = (uploading - 1).coerceAtLeast(0)) }
        when (r) {
            is AppResult.Success -> load()
            is AppResult.Failure -> fail(r.error)
        }
    }

    private suspend fun saveRepair() {
        val s = currentState
        val pid = s.propertyId ?: return
        val item = s.selected ?: return
        val form = s.repairForm ?: return
        if (s.saving) return
        if (s.repairProblems.isNotEmpty()) return setState { copy(repairErrors = true) }
        setState { copy(saving = true) }
        val r = repository.addRepair(pid, item.id, form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(repairForm = null, repairErrors = false) }
                r.data?.let(::replace) ?: load()
                load()
            }
            is AppResult.Failure -> fail(r.error)
        }
    }

    /** Small item changes (photo / repair): always redraw from the server's copy. */
    private suspend fun act(call: suspend (propertyId: String, itemId: String) -> AppResult<RegisterItem?>) {
        val s = currentState
        val pid = s.propertyId ?: return
        val id = s.selectedId ?: return
        if (s.saving || !s.canManage) return
        setState { copy(saving = true) }
        val r = call(pid, id)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                r.data?.let(::replace)
                load()
            }
            is AppResult.Failure -> fail(r.error)
        }
    }

    private fun fail(e: AppError) {
        when (e) {
            is AppError.FieldIssues -> {
                setState { copy(serverErrors = e.issues, showErrors = true, repairErrors = true) }
                sendEffect(RegisterEffect.Message(e.issues.values.firstOrNull() ?: e.message))
            }
            is AppError.AccountBlocked -> Unit
            else -> sendEffect(RegisterEffect.Message(e.message))
        }
    }

    companion object {
        const val MAX_PHOTOS = "An item can have at most 6 photos."
    }
}
