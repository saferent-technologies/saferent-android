package com.saferenttechnologies.android.presentation.kyc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.KycStatus
import com.saferenttechnologies.android.domain.repository.KycRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KycState(
    val isOpen: Boolean = false,
    val status: KycStatus? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** A POST is running (DigiLocker start / finish, selfie, submit) → buttons disabled. */
    val busy: Boolean = false,
    val selfieCamera: Boolean = false,
    /** DigiLocker opened in the browser, not finished yet. */
    val waitingDigiLocker: Boolean = false,
    /** Opened right after the landlord's profile (sign up) → "Skip for now" / "Go to dashboard". */
    val afterProfile: Boolean = false,
    /** "Choose your ID" list open. */
    val idPicker: Boolean = false,
    /** The ID being filled in (number, share code / password, file, photos). */
    val idForm: com.saferenttechnologies.android.domain.model.IdForm? = null,
    /** Camera open for the ID photo: "front" / "back". */
    val idCamera: String? = null,
    /** After sign in: checking whether to show this screen before the dashboard (loading screen meanwhile). */
    val checking: Boolean = false,
    /** "How to verify" screen open (list of guides from the server). */
    val tutorialsOpen: Boolean = false,
    /** null = not loaded yet (shimmer). */
    val tutorials: List<com.saferenttechnologies.android.domain.model.KycTutorial>? = null,
    val tutorialsError: String? = null,
    /** The guide being shown (video / PDF / picture). */
    val tutorial: com.saferenttechnologies.android.domain.model.KycTutorial? = null,
    /** PDF guide: downloaded file to show. */
    val tutorialPdf: String? = null,
    val tutorialLoading: Boolean = false,
) : UiState

sealed interface KycIntent : UiIntent {
    /** Landlord dashboard: just the status (for the badge / "Verify now" card). */
    data object LoadStatus : KycIntent
    data object Open : KycIntent
    data object Close : KycIntent
    data object Refresh : KycIntent
    /** App resumed (back from the browser): a saved DigiLocker session → finish it (result unknown). */
    data object Resumed : KycIntent
    data object StartDigiLocker : KycIntent
    data class DigiLockerReturned(val sessionId: String?, val success: Boolean?, val errMessage: String?) : KycIntent
    data object OpenSelfie : KycIntent
    data object CloseSelfie : KycIntent
    data class SelfieTaken(val filePath: String) : KycIntent
    data object Submit : KycIntent
    /** Landlord signed in (new sign up or any phone) → open before the dashboard unless verified / under review / already shown. */
    data object OpenAfterProfile : KycIntent
    data object OpenIdPicker : KycIntent
    data object CloseIdPicker : KycIntent
    data class ChooseId(val type: com.saferenttechnologies.android.domain.model.IdDocType) : KycIntent
    data class IdNumber(val text: String) : KycIntent
    data class IdSecret(val text: String) : KycIntent
    data class IdFilePicked(val uri: String, val name: String, val mime: String?) : KycIntent
    data class IdCamera(val side: String) : KycIntent
    data class IdPhotoTaken(val path: String) : KycIntent
    data object CloseIdCamera : KycIntent
    data object SubmitId : KycIntent
    data object CloseIdForm : KycIntent
    /** "How to verify" card in the Choose your ID sheet → the guides screen. */
    data object OpenTutorials : KycIntent
    /** Back to the Choose your ID sheet. */
    data object CloseTutorials : KycIntent
    data object RetryTutorials : KycIntent
    data class OpenTutorial(val tutorial: com.saferenttechnologies.android.domain.model.KycTutorial) : KycIntent
    /** Back to the guides list. */
    data object CloseTutorial : KycIntent
}

sealed interface KycEffect : UiEffect {
    data class Message(val text: String) : KycEffect
    /** Open DigiLocker in the browser. */
    data class OpenUrl(val url: String) : KycEffect
}

/** Owner verification: DigiLocker (Aadhaar) → live selfie → submit → staff review. Landlords only. */
@HiltViewModel
class KycViewModel @Inject constructor(
    private val repository: KycRepository,
    private val savedState: SavedStateHandle,
    sessionEvents: SessionEvents,
) : MviViewModel<KycState, KycIntent, KycEffect>(KycState()) {

    init {
        viewModelScope.launch {
            sessionEvents.events.collect {
                if (it == SessionEvent.SignedOut) {
                    setState { KycState() }
                    repository.setPromptDone(false) // local cache only; the server's promptDone decides after sign in
                }
            }
        }
    }

    override suspend fun handleIntent(intent: KycIntent) {
        when (intent) {
            KycIntent.LoadStatus -> if (!currentState.loading) load()
            KycIntent.Open -> {
                setState { copy(isOpen = true) }
                load()
                finishPendingIfAny()
            }
            KycIntent.Close -> {
                // "Skip for now" after sign in → dashboard, and don't ask again until the next sign in.
                if (currentState.afterProfile) markPromptDone()
                setState { copy(isOpen = false, selfieCamera = false, afterProfile = false, idPicker = false, idForm = null, idCamera = null) }
            }
            KycIntent.OpenAfterProfile -> openAfterSignIn()
            KycIntent.OpenIdPicker -> if (currentState.status?.canEdit != false && !currentState.busy) setState { copy(idPicker = true) }
            KycIntent.CloseIdPicker -> setState { copy(idPicker = false) }
            is KycIntent.ChooseId -> if (currentState.status?.allows(intent.type) != false) setState { copy(idPicker = false, idForm = com.saferenttechnologies.android.domain.model.IdForm(intent.type)) }
            is KycIntent.IdNumber -> setState { copy(idForm = idForm?.let { it.copy(number = com.saferenttechnologies.android.domain.model.IdRules.cleanNumber(it.type, intent.text)) }) }
            is KycIntent.IdSecret -> setState { copy(idForm = idForm?.let { f -> f.copy(secret = if (f.type == com.saferenttechnologies.android.domain.model.IdDocType.AADHAAR_XML) intent.text.filter(Char::isDigit).take(4) else intent.text.take(64)) }) }
            is KycIntent.IdFilePicked -> setState {
                copy(idForm = idForm?.let { f ->
                    val name = com.saferenttechnologies.android.domain.model.IdRules.cleanFileName(intent.name)
                    if (com.saferenttechnologies.android.domain.model.IdRules.fileOk(f.type, name, intent.mime)) f.copy(file = intent.uri, fileName = name, wrongFile = false)
                    else f.copy(file = null, fileName = "", wrongFile = true)
                })
            }
            is KycIntent.IdCamera -> setState { copy(idCamera = intent.side) }
            is KycIntent.IdPhotoTaken -> setState {
                val side = idCamera
                copy(idCamera = null, idForm = idForm?.let { if (side == "back") it.copy(back = intent.path) else it.copy(front = intent.path) })
            }
            KycIntent.CloseIdCamera -> setState { copy(idCamera = null) }
            KycIntent.CloseIdForm -> setState { copy(idForm = null) }
            KycIntent.OpenTutorials -> {
                setState { copy(idPicker = false, tutorialsOpen = true) }
                loadTutorials()
            }
            KycIntent.RetryTutorials -> loadTutorials()
            KycIntent.CloseTutorials -> setState { copy(tutorialsOpen = false, tutorial = null, tutorialPdf = null, tutorialLoading = false, idPicker = true) }
            is KycIntent.OpenTutorial -> openTutorial(intent.tutorial)
            KycIntent.CloseTutorial -> setState { copy(tutorial = null, tutorialPdf = null, tutorialLoading = false) }
            KycIntent.SubmitId -> submitId()
            KycIntent.Refresh -> load()
            KycIntent.Resumed -> if (currentState.isOpen) finishPendingIfAny()
            KycIntent.StartDigiLocker -> startDigiLocker()
            is KycIntent.DigiLockerReturned -> {
                setState { copy(isOpen = true) }
                // Only finish a session we started and haven't finished yet (onResume may have done it already).
                val saved = savedState.get<String>(KEY) ?: repository.pendingSession()
                if (saved != null && (intent.sessionId == null || intent.sessionId == saved)) complete(saved, intent.success, intent.errMessage) else load()
            }
            KycIntent.OpenSelfie -> if (currentState.status?.canEdit == true && !currentState.busy) setState { copy(selfieCamera = true) }
            KycIntent.CloseSelfie -> setState { copy(selfieCamera = false) }
            is KycIntent.SelfieTaken -> {
                setState { copy(selfieCamera = false) }
                act { repository.uploadSelfie(intent.filePath) }
            }
            KycIntent.Submit -> if (currentState.status?.canSubmit == true) {
                act { repository.submit() }
                // Sent for review right after sign in → straight to the dashboard.
                if (currentState.afterProfile && currentState.status?.isSubmitted == true) {
                    markPromptDone()
                    setState { copy(isOpen = false, afterProfile = false, selfieCamera = false, idPicker = false, idForm = null, idCamera = null) }
                }
            }
        }
    }

    /** Fresh every time the screen opens (staff may add a guide for a new ID); the old list stays while it loads. */
    private suspend fun loadTutorials() {
        setState { copy(tutorialsError = null) }
        when (val r = repository.tutorials()) {
            is AppResult.Success -> setState { copy(tutorials = r.data) }
            is AppResult.Failure -> setState { copy(tutorialsError = r.error.takeUnless { it is AppError.AccountBlocked }?.message ?: tutorialsError) }
        }
    }

    private suspend fun openTutorial(t: com.saferenttechnologies.android.domain.model.KycTutorial) {
        setState { copy(tutorial = t, tutorialPdf = null, tutorialLoading = t.type == com.saferenttechnologies.android.domain.model.TutorialType.PDF) }
        if (t.type != com.saferenttechnologies.android.domain.model.TutorialType.PDF) return
        when (val r = repository.downloadTutorialPdf(t.url)) {
            is AppResult.Success -> if (currentState.tutorial?.id == t.id) setState { copy(tutorialPdf = r.data, tutorialLoading = false) }
            is AppResult.Failure -> {
                if (currentState.tutorial?.id == t.id) setState { copy(tutorial = null, tutorialLoading = false) }
                message(r.error)
            }
        }
    }

    private suspend fun openAfterSignIn() {
        if (currentState.isOpen || currentState.checking) return
        if (repository.promptDone()) return
        setState { copy(checking = true) }
        load()
        val s = currentState.status
        when {
            // Couldn't load → dashboard now, try again on the next app start.
            s == null -> setState { copy(checking = false) }
            // Finished / skipped before (server: any phone, reinstall) or already verified / under review → dashboard.
            s.promptDone || s.isApproved || s.isSubmitted -> {
                repository.setPromptDone(true)
                setState { copy(checking = false) }
            }
            else -> setState { copy(checking = false, isOpen = true, afterProfile = true) }
        }
    }

    /** Skipped or submitted → remember here and on the server (not shown again on any phone). */
    private fun markPromptDone() {
        setState { copy(status = status?.copy(promptDone = true)) }
        viewModelScope.launch {
            repository.setPromptDone(true)
            repository.markPromptDoneOnServer()
        }
    }

    private suspend fun submitId() {
        val form = currentState.idForm ?: return
        if (!com.saferenttechnologies.android.domain.model.IdRules.isComplete(form)) return setState { copy(idForm = form.copy(showErrors = true)) }
        if (currentState.busy) return
        setState { copy(busy = true) }
        val r = repository.uploadIdentity(form)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(idForm = null) }
                if (r.data.isNotBlank()) sendEffect(KycEffect.Message(r.data))
                load()
            }
            is AppResult.Failure -> message(r.error) // keep the form so they can fix it
        }
    }

    private suspend fun load() {
        setState { copy(loading = status == null, error = null) }
        when (val r = repository.status()) {
            is AppResult.Success -> setState { copy(status = r.data, loading = false) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun startDigiLocker() {
        if (currentState.busy || currentState.status?.canEdit != true) return
        setState { copy(busy = true) }
        when (val r = repository.startDigiLocker()) {
            is AppResult.Success -> {
                savedState[KEY] = r.data.sessionId
                repository.savePendingSession(r.data.sessionId)
                setState { copy(busy = false, waitingDigiLocker = true) }
                sendEffect(KycEffect.OpenUrl(r.data.url))
            }
            is AppResult.Failure -> {
                setState { copy(busy = false) }
                message(r.error)
            }
        }
    }

    /** Came back without the App Link (browser showed the page) → finish with success unknown. */
    private suspend fun finishPendingIfAny() {
        val id = savedState.get<String>(KEY) ?: repository.pendingSession() ?: return
        complete(id, null, null)
    }

    private suspend fun complete(sessionId: String, success: Boolean?, errMessage: String?) {
        if (currentState.busy) return
        setState { copy(busy = true) }
        val r = repository.completeDigiLocker(sessionId, success, errMessage)
        // Finished either way (412 = expired / failed: start again) → forget the session.
        savedState[KEY] = null
        repository.savePendingSession(null)
        setState { copy(busy = false, waitingDigiLocker = false) }
        if (r is AppResult.Failure) message(r.error)
        load()
    }

    private suspend fun act(call: suspend () -> AppResult<String>) {
        if (currentState.busy) return
        setState { copy(busy = true) }
        val r = call()
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> if (r.data.isNotBlank()) sendEffect(KycEffect.Message(r.data))
            is AppResult.Failure -> message(r.error)
        }
        load()
    }

    private fun message(e: AppError) {
        if (e is AppError.AccountBlocked) return
        sendEffect(KycEffect.Message(e.message))
    }

    private companion object {
        const val KEY = "kyc_digilocker_session"
    }
}
