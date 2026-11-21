package com.saferenttechnologies.android.presentation.manage

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.ActionResult
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplicationDecision
import com.saferenttechnologies.android.domain.model.CreateInvitation
import com.saferenttechnologies.android.domain.model.InviteLocks
import com.saferenttechnologies.android.domain.model.PropertyAction
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.SubmitProblems
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import com.saferenttechnologies.android.domain.repository.PhotoUploadQueue
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import com.saferenttechnologies.android.domain.usecase.GetSupportConfigUseCase
import com.saferenttechnologies.android.domain.usecase.UploadPhotoUseCase
import com.saferenttechnologies.android.presentation.property.CameraMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Property detail: lifecycle actions (mirroring PropertyActions), units / beds availability,
 * tenants, invitations, applications, photos, documents, history. Shows the server `message`.
 * Never logs phone numbers or addresses.
 */
private const val MAX_EXIT_CHECKS = 20
private val INVITATION_STATUSES = listOf("PENDING", "ACCEPTED", "REJECTED", "REVOKED", "EXPIRED")

@HiltViewModel
class PropertyDetailViewModel @Inject constructor(
    private val manage: ManagePropertyRepository,
    private val properties: PropertyRepository,
    private val uploadPhoto: UploadPhotoUseCase,
    private val uploads: PhotoUploadQueue,
    private val logger: AppLogger,
    private val getSupportConfig: GetSupportConfigUseCase,
    private val leases: LeaseRepository,
    private val people: com.saferenttechnologies.android.domain.repository.PeopleRepository,
    private val draftStore: com.saferenttechnologies.android.domain.repository.PropertyDraftStore,
    sessionEvents: SessionEvents,
) : MviViewModel<PropertyDetailState, PropertyDetailIntent, PropertyDetailEffect>(PropertyDetailState()) {

    private var uploadsJob: Job? = null
    private var lastSucceeded = 0

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) close() }
        }
    }

    override suspend fun handleIntent(intent: PropertyDetailIntent) {
        when (intent) {
            is PropertyDetailIntent.Open -> {
                close()
                setState { PropertyDetailState(isOpen = true, propertyId = intent.propertyId, loading = true, tab = intent.tab) }
                refresh()
                if (currentState.detail != null) loadTab(intent.tab)
                watchUploads(intent.propertyId)
            }
            PropertyDetailIntent.Close -> close()
            PropertyDetailIntent.Refresh -> {
                com.saferenttechnologies.android.data.remote.ApiCache.clear()
                refresh()
                loadTab(currentState.tab)
            }
            is PropertyDetailIntent.SelectTab -> {
                setState { copy(tab = intent.tab) }
                loadTab(intent.tab)
            }

            is PropertyDetailIntent.Action -> onAction(intent.action)
            PropertyDetailIntent.Confirm -> currentState.confirm?.let { action ->
                setState { copy(confirm = null) }
                runAction(action)
            }
            PropertyDetailIntent.DismissConfirm -> setState { copy(confirm = null) }
            is PropertyDetailIntent.Edit -> currentState.propertyId?.let { sendEffect(PropertyDetailEffect.OpenEditor(it, intent.step)) }
            // "Continue changes" → the step they were on when they closed the wizard (else the server's step, else details).
            is PropertyDetailIntent.OpenTenantDetails -> {
                setState { copy(tenantDetailsFor = intent.invitationId, tenantDetails = null) }
                when (val r = manage.tenantDetails(intent.invitationId)) {
                    is AppResult.Success -> if (currentState.tenantDetailsFor == intent.invitationId) setState { copy(tenantDetails = r.data) }
                    is AppResult.Failure -> {
                        setState { copy(tenantDetailsFor = null) }
                        showError(r.error)
                    }
                }
            }
            PropertyDetailIntent.CloseTenantDetails -> setState { copy(tenantDetailsFor = null, tenantDetails = null) }
            PropertyDetailIntent.ContinueChanges -> currentState.propertyId?.let { id ->
                val saved = draftStore.lastStep(id)
                val server = currentState.detail?.property?.wizardStep ?: 0
                val step = (saved ?: server.takeIf { it > WizardStep.BASIC } ?: WizardStep.BASIC)
                    .coerceIn(WizardStep.BASIC, WizardStep.LAST) // type can't change on a live listing
                sendEffect(PropertyDetailEffect.OpenEditor(id, step))
            }

            PropertyDetailIntent.SubmitChanges -> submitChanges()
            PropertyDetailIntent.DiscardChanges -> setState { copy(confirmDiscard = true) }
            PropertyDetailIntent.DismissDiscard -> setState { copy(confirmDiscard = false) }
            PropertyDetailIntent.ConfirmDiscard -> discardChanges()
            PropertyDetailIntent.DismissChangeProblems -> setState { copy(changeProblems = null) }

            is PropertyDetailIntent.SetUnitAvailability -> {
                val id = currentState.propertyId ?: return
                val unit = currentState.detail?.property?.units?.firstOrNull { it.id == intent.unitId } ?: return
                act { manage.setUnitAvailability(id, unit.id, unit.version, intent.status) }
            }
            is PropertyDetailIntent.SetBedAvailability -> {
                val id = currentState.propertyId ?: return
                val bed = currentState.detail?.property?.rooms?.firstOrNull { it.id == intent.roomId }?.beds?.firstOrNull { it.id == intent.bedId } ?: return
                act { manage.setBedAvailability(id, intent.roomId, bed.id, bed.version, intent.status) }
            }

            is PropertyDetailIntent.ShowPastTenants -> {
                setState { copy(pastTenants = intent.past) }
                loadTab(DetailTab.TENANTS)
            }
            is PropertyDetailIntent.CallTenant -> sendEffect(PropertyDetailEffect.Dial(intent.phone))
            is PropertyDetailIntent.OpenUnit -> {
                setState { copy(openUnitId = intent.unitId) }
                loadActiveLeases()
            }
            PropertyDetailIntent.CloseUnit -> setState { copy(openUnitId = null) }
            is PropertyDetailIntent.ReviewTenant -> {
                setState { copy(reviewPrompt = null) }
                sendEffect(PropertyDetailEffect.ReviewLease(intent.leaseId))
            }
            PropertyDetailIntent.AddUnit -> currentState.propertyId?.takeIf { currentState.canEdit }?.let {
                sendEffect(PropertyDetailEffect.OpenEditor(it, WizardStep.UNITS, newUnit = true))
            }
            is PropertyDetailIntent.OpenLease -> intent.lease.let { l ->
                sendEffect(
                    PropertyDetailEffect.OpenLease(
                        l.id,
                        LeaseSummary(
                            id = l.id,
                            status = l.status,
                            propertyTitle = currentState.detail?.property?.title.orEmpty(),
                            place = listOf(l.place, l.placeCode).filter { it.isNotBlank() }.joinToString(" · "),
                            monthlyRent = l.monthlyRent,
                            securityDeposit = l.securityDeposit,
                            rentDueDay = l.rentDueDay,
                            startDate = l.startDate,
                            endDate = l.endDate,
                            otherName = l.tenantName,
                        ),
                    ),
                )
            }
            is PropertyDetailIntent.WhatsAppTenant -> sendEffect(PropertyDetailEffect.WhatsApp(intent.phone))

            // The last tenant of THIS flat / bed not reviewed yet → popup; other flats invite normally.
            is PropertyDetailIntent.StartInvite -> if (!currentState.canInvite) {
                // Not approved yet → nothing to invite to.
                sendEffect(PropertyDetailEffect.Message(INVITE_NOT_APPROVED))
            } else {
                val due = com.saferenttechnologies.android.domain.model.ReviewLocks.dueFor(currentState.reviewsDue, intent.unitId, intent.bedId)
                if (due != null) setState { copy(reviewPrompt = due) }
                else if (!InviteLocks.isLocked(currentState.pendingInvites, intent.unitId, intent.bedId)) setState {
                copy(inviteForm = InviteForm(unitId = intent.unitId, bedId = intent.bedId, target = intent.target, monthlyRent = intent.rent.substringBefore('.')))
                }
            }
            PropertyDetailIntent.DismissReviewPrompt -> setState { copy(reviewPrompt = null) }
            PropertyDetailIntent.InviteLookup -> inviteLookup()
            is PropertyDetailIntent.AskApprove -> currentState.detail?.approval?.let { a ->
                if (if (intent.withoutProof) a.canApproveWithoutProof else a.canApprove) setState { copy(approveConfirm = intent.withoutProof) }
            }
            PropertyDetailIntent.ConfirmApprove -> currentState.approveConfirm?.let { without ->
                setState { copy(approveConfirm = null) }
                decide(approve = true, withoutProof = without, reason = "")
            }
            PropertyDetailIntent.AskReject -> setState { copy(rejectReason = "") }
            is PropertyDetailIntent.RejectReason -> setState { copy(rejectReason = intent.text.take(1000)) }
            PropertyDetailIntent.ConfirmReject -> currentState.rejectReason?.takeIf { it.trim().length >= 5 }?.let { reason ->
                setState { copy(rejectReason = null) }
                decide(approve = false, withoutProof = false, reason = reason)
            }
            PropertyDetailIntent.DismissApproval -> setState { copy(approveConfirm = null, rejectReason = null, approvalBlock = null, verifySheet = false) }
            PropertyDetailIntent.CheckDocuments -> {
                setState { copy(approvalBlock = null, tab = DetailTab.DOCUMENTS) }
                loadTab(DetailTab.DOCUMENTS)
            }
            PropertyDetailIntent.OpenVerifySheet -> setState { copy(verifySheet = true, approvalBlock = null) }
            PropertyDetailIntent.VerifyIdentity -> {
                setState { copy(verifySheet = false, approvalBlock = null) }
                sendEffect(PropertyDetailEffect.OpenOwnerVerification)
            }
            PropertyDetailIntent.ContactSupportVerify -> {
                setState { copy(verifySheet = false, approvalBlock = null) }
                currentState.propertyId?.let { sendEffect(PropertyDetailEffect.OpenVerificationSupport(it)) }
            }
            PropertyDetailIntent.InviteClearFound -> setState { copy(inviteForm = inviteForm?.copy(found = null, notOnSafeRent = false)) }
            is PropertyDetailIntent.InviteChanged -> setState { copy(inviteForm = intent.form.copy(error = null)) }
            PropertyDetailIntent.CancelInvite -> setState { copy(inviteForm = null) }
            PropertyDetailIntent.SendInvite -> sendInvite()
            is PropertyDetailIntent.InvitationFilter -> {
                setState { copy(invitationStatus = intent.status) }
                loadTab(DetailTab.INVITATIONS)
            }
            is PropertyDetailIntent.RenewLink -> {
                setState { copy(busy = true) }
                val r = manage.renewInvitationLink(intent.invitationId, INVITE_DAYS)
                setState { copy(busy = false) }
                when (r) {
                    is AppResult.Success -> sendEffect(PropertyDetailEffect.Share(r.data.shareMessage, r.data.url))
                    is AppResult.Failure -> showError(r.error)
                }
            }
            is PropertyDetailIntent.Revoke -> if (act { manage.revokeInvitation(intent.invitationId) }) {
                loadTab(DetailTab.INVITATIONS)
                loadPendingInvites()
            }

            is PropertyDetailIntent.StartDecision -> setState { copy(decision = DecisionForm(intent.application, intent.approve)) }
            is PropertyDetailIntent.DecisionChanged -> setState { copy(decision = intent.form) }
            PropertyDetailIntent.CancelDecision -> setState { copy(decision = null) }
            PropertyDetailIntent.SubmitDecision -> submitDecision()

            PropertyDetailIntent.LoadMoreHistory -> if (currentState.historyPage < currentState.historyTotalPages) loadHistory(currentState.historyPage + 1)

            is PropertyDetailIntent.SelectPhotoCategory -> setState { copy(photoCategory = intent.category) }
            is PropertyDetailIntent.SelectDocType -> setState { copy(docType = intent.type) }
            is PropertyDetailIntent.OpenCamera -> setState { copy(camera = intent.mode, replacingDocId = intent.replaceDocId) }
            PropertyDetailIntent.CloseCamera -> setState { copy(camera = null, replacingDocId = null) }
            is PropertyDetailIntent.Captured -> captured(intent.filePath)
            is PropertyDetailIntent.DeleteImage -> currentState.propertyId?.let { id -> unitAct { properties.deleteImage(id, intent.imageId) } }
            is PropertyDetailIntent.SetCover -> currentState.propertyId?.let { id -> unitAct { properties.setImageOrder(id, intent.imageId, 0) } }
        }
    }

    // ---------------- lifecycle actions ----------------

    private fun onAction(action: PropertyAction) {
        when (action) {
            // Draft → where it was left; otherwise from the details step.
            PropertyAction.EDIT -> currentState.propertyId?.let { id ->
                val p = currentState.detail?.property
                val step = if (p?.publicationStatus == "DRAFT") p.wizardStep else WizardStep.BASIC
                sendEffect(PropertyDetailEffect.OpenEditor(id, step))
            }
            PropertyAction.ACTIVATE, PropertyAction.SUBMIT, PropertyAction.RESUBMIT -> viewModelScope.launch { runAction(action) }
            PropertyAction.CONTACT_SUPPORT -> contactSupport()
            // Publish, unpublish, deactivate, archive, delete, edit-with-warning → confirm first.
            else -> setState { copy(confirm = action) }
        }
    }

    // ---------------- change request (live listing) ----------------

    /** POST revision/submit → PENDING_VERIFICATION; 412 problems → checklist sheet. */
    private suspend fun submitChanges() {
        val id = currentState.propertyId ?: return
        if (currentState.busy) return
        logger.d(CHANGES_TAG, "submit changes")
        setState { copy(busy = true, changeProblems = null) }
        val r = manage.submitChanges(id)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> changesDone(r.data.message)
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.InvalidState && e.problems.isNotEmpty()) {
                    setState { copy(changeProblems = e.problems.map { it to SubmitProblems.stepFor(it) }) }
                }
                showError(e)
            }
        }
    }

    /** DELETE revision → the live listing stays exactly as it is. */
    private suspend fun discardChanges() {
        val id = currentState.propertyId ?: return
        logger.d(CHANGES_TAG, "discard changes")
        setState { copy(confirmDiscard = false, busy = true) }
        val r = manage.discardChanges(id)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> changesDone(r.data.message)
            is AppResult.Failure -> showError(r.error)
        }
    }

    private suspend fun changesDone(message: String) {
        currentState.propertyId?.let { draftStore.saveLastStep(it, null) } // submitted / discarded → start fresh next time
        if (message.isNotBlank()) sendEffect(PropertyDetailEffect.Message(message))
        sendEffect(PropertyDetailEffect.Changed) // list + dashboard numbers
        refresh()
    }

    private suspend fun runAction(action: PropertyAction) {
        val id = currentState.propertyId ?: return
        logger.d(TAG, "Action $action")
        when (action) {
            PropertyAction.EDIT, PropertyAction.EDIT_WITH_WARNING -> sendEffect(PropertyDetailEffect.OpenEditor(id, WizardStep.BASIC))
            PropertyAction.SUBMIT, PropertyAction.RESUBMIT -> {
                setState { copy(busy = true, problems = emptyList()) }
                val r = manage.submit(id)
                setState { copy(busy = false) }
                when (r) {
                    is AppResult.Success -> done(r.data)
                    is AppResult.Failure -> {
                        val e = r.error
                        if (e is AppError.InvalidState && e.problems.isNotEmpty()) {
                            setState { copy(problems = e.problems.map { it to SubmitProblems.stepFor(it) }) }
                        }
                        showError(e)
                    }
                }
            }
            PropertyAction.PUBLISH -> act { manage.publish(id) }
            PropertyAction.UNPUBLISH -> act { manage.unpublish(id) }
            PropertyAction.DEACTIVATE -> act { manage.setActiveStatus(id, active = false) }
            PropertyAction.ACTIVATE -> act { manage.setActiveStatus(id, active = true) }
            PropertyAction.ARCHIVE, PropertyAction.DELETE_DRAFT -> {
                setState { copy(busy = true) }
                val r = manage.archiveOrDelete(id)
                setState { copy(busy = false) }
                when (r) {
                    is AppResult.Success -> {
                        sendEffect(PropertyDetailEffect.Message(r.data.message))
                        sendEffect(PropertyDetailEffect.Changed)
                        if (r.data.deleted) close() else refresh()
                    }
                    is AppResult.Failure -> showError(r.error)
                }
            }
            PropertyAction.CONTACT_SUPPORT -> contactSupport()
        }
    }

    /** Runs a write; message + refresh + "changed" on success. */
    private suspend fun act(call: suspend () -> AppResult<ActionResult>): Boolean {
        setState { copy(busy = true) }
        val r = call()
        setState { copy(busy = false) }
        return when (r) {
            is AppResult.Success -> {
                done(r.data)
                true
            }
            is AppResult.Failure -> {
                showError(r.error)
                false
            }
        }
    }

    private suspend fun unitAct(call: suspend () -> AppResult<Unit>) {
        setState { copy(busy = true) }
        val r = call()
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> refresh()
            is AppResult.Failure -> showError(r.error)
        }
    }

    private suspend fun done(result: ActionResult) {
        if (result.message.isNotBlank()) sendEffect(PropertyDetailEffect.Message(result.message))
        sendEffect(PropertyDetailEffect.Changed)
        refresh()
    }

    // ---------------- loading ----------------

    private suspend fun refresh() {
        val id = currentState.propertyId ?: return
        when (val r = manage.detail(id)) {
            is AppResult.Success -> {
                setState { copy(detail = r.data, loading = false, error = null) }
                loadPendingInvites()
            }
            is AppResult.Failure -> {
                val gone = (r.error as? AppError.Server)?.httpCode == 404 && currentState.detail == null
                setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
                // The screen closes itself (onNotFound) and the caller shows "no longer available".
                if (gone) sendEffect(PropertyDetailEffect.NotFound)
            }
        }
    }

    /** Every status in one list (pending first, then accepted, rejected, revoked, expired). */
    private suspend fun loadAllInvitations(id: String) {
        val all = coroutineScope {
            INVITATION_STATUSES.map { s -> async { (manage.invitations(id, s) as? AppResult.Success)?.data.orEmpty() } }.awaitAll()
        }.flatten().distinctBy { it.id }
        setState { copy(invitations = all) }
    }

    private suspend fun loadActiveLeases() {
        val id = currentState.propertyId ?: return
        setState { copy(activeLeasesLoading = true) }
        val r = manage.leases(id, active = true)
        setState { copy(activeLeasesLoading = false, activeLeases = (r as? AppResult.Success)?.data ?: activeLeases) }
        // "Close requested" on the flat's tenant (the tenants tab that used to load this is gone).
        loadCloseRequests(currentState.activeLeases)
    }

    /** Which current tenants asked to close their lease (one GET /exit per lease, first 20). */
    private fun loadCloseRequests(list: List<Lease>) {
        viewModelScope.launch {
            val asked = list.take(MAX_EXIT_CHECKS).mapNotNull { l ->
                (leases.exitState(l.id) as? AppResult.Success)?.data?.takeIf { it.needsMyResponse }?.let { l.id }
            }.toSet()
            setState { copy(closeRequested = asked) }
        }
    }

    private suspend fun loadPendingInvites() {
        val id = currentState.propertyId ?: return
        if (!currentState.canManage) return
        viewModelScope.launch {
            (leases.pendingReviews() as? AppResult.Success)?.let { r -> setState { copy(reviewsDue = r.data.filter { it.propertyId == id }) } }
        }
        (manage.invitations(id, "PENDING") as? AppResult.Success)?.let { r -> setState { copy(pendingInvites = r.data) } }
    }

    private suspend fun loadTab(tab: DetailTab) {
        val id = currentState.propertyId ?: return
        when (tab) {
            DetailTab.TENANTS -> (manage.leases(id, active = !currentState.pastTenants) as? AppResult.Success)?.let { r ->
                setState { copy(leases = r.data) }
                if (!currentState.pastTenants) loadCloseRequests(r.data)
            }
            DetailTab.INVITATIONS -> loadAllInvitations(id)
            DetailTab.APPLICATIONS -> (manage.applications(id, "PENDING") as? AppResult.Success)?.let { r -> setState { copy(applications = r.data) } }
            DetailTab.HISTORY -> loadHistory(1)
            DetailTab.DOCUMENTS -> (properties.documents(id) as? AppResult.Success)?.let { r -> setState { copy(documents = r.data) } }
            else -> Unit
        }
    }

    private suspend fun loadHistory(page: Int) {
        val id = currentState.propertyId ?: return
        (manage.statusHistory(id, page) as? AppResult.Success)?.let { r ->
            setState {
                copy(
                    history = if (page == 1) r.data.items else history + r.data.items,
                    historyPage = r.data.page,
                    historyTotalPages = r.data.totalPages,
                )
            }
        }
    }

    private fun watchUploads(propertyId: String) {
        uploadsJob?.cancel()
        lastSucceeded = 0
        uploadsJob = viewModelScope.launch {
            var first = true
            uploads.observe(propertyId).collect { summary ->
                if (first) {
                    lastSucceeded = summary.succeeded
                    first = false
                } else if (summary.succeeded > lastSucceeded) {
                    lastSucceeded = summary.succeeded
                    refresh()
                }
            }
        }
    }

    // ---------------- invitations / applications ----------------

    /** SafeRent ID / phone → their name (and phone) filled in; not found → enter details by hand. */
    private suspend fun inviteLookup() {
        val f = currentState.inviteForm ?: return
        if (f.lookupBusy) return
        val q = com.saferenttechnologies.android.domain.model.PeopleLookup.parse(f.lookup)
            ?: return setState { copy(inviteForm = f.copy(lookupInvalid = true)) }
        setState { copy(inviteForm = f.copy(lookupBusy = true, lookupInvalid = false, found = null, notOnSafeRent = false)) }
        val r = people.lookup(q)
        val now = currentState.inviteForm ?: return
        setState {
            copy(
                inviteForm = when (r) {
                    is AppResult.Success -> r.data?.let { p ->
                        now.copy(
                            lookupBusy = false,
                            found = p,
                            recipientName = now.recipientName.ifBlank { p.fullName },
                            recipientPhone = if (q is com.saferenttechnologies.android.domain.model.LookupQuery.Phone) q.value else now.recipientPhone,
                        )
                    } ?: now.copy(
                        lookupBusy = false,
                        notOnSafeRent = true,
                        recipientPhone = if (q is com.saferenttechnologies.android.domain.model.LookupQuery.Phone) q.value else now.recipientPhone,
                    )
                    is AppResult.Failure -> now.copy(lookupBusy = false)
                },
            )
        }
        (r as? AppResult.Failure)?.error?.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(PropertyDetailEffect.Message(it)) }
    }

    private suspend fun sendInvite() {
        val id = currentState.propertyId ?: return
        val f = currentState.inviteForm ?: return
        val digits = f.recipientPhone.filter(Char::isDigit).takeLast(10)
        val error = when {
            f.recipientName.isBlank() -> InviteError.NAME
            // Found on SafeRent by ID → the server knows their phone.
            digits.length != 10 && f.found == null -> InviteError.PHONE
            f.monthlyRent.toDoubleOrNull() == null -> InviteError.RENT
            SimpleDate.parse(f.startDate) == null -> InviteError.START_DATE
            else -> null
        }
        if (error != null) return setState { copy(inviteForm = f.copy(error = error.name)) }
        setState { copy(busy = true) }
        val r = manage.createInvitation(
            id,
            CreateInvitation(
                unitId = f.unitId,
                bedId = f.bedId,
                recipientName = f.recipientName,
                recipientPhone = if (digits.length == 10) "+91$digits" else "",
                recipientUserId = f.found?.id,
                recipientEmail = f.recipientEmail,
                monthlyRent = f.monthlyRent,
                securityDeposit = f.securityDeposit,
                maintenanceCharges = f.maintenanceCharges,
                startDate = f.startDate,
                endDate = f.endDate,
                rentDueDay = f.rentDueDay.toIntOrNull(),
                noticePeriodDays = f.noticePeriodDays.toIntOrNull(),
                lockInMonths = f.lockInMonths.toIntOrNull(),
                additionalTerms = f.additionalTerms,
                expiresInDays = INVITE_DAYS,
                requirements = f.requirements.takeIf { it.asksAnything },
            ),
        )
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(inviteForm = null, tab = DetailTab.INVITATIONS) }
                sendEffect(PropertyDetailEffect.Message(r.data.message))
                // The link comes ONLY in this response → share it now (share sheet + copy).
                r.data.link?.let { sendEffect(PropertyDetailEffect.Share(it.shareMessage, it.url)) }
                loadTab(DetailTab.INVITATIONS)
                loadPendingInvites()
            }
            is AppResult.Failure -> showError(r.error)
        }
    }

    private suspend fun submitDecision() {
        val d = currentState.decision ?: return
        if (d.approve && (SimpleDate.parse(d.startDate) == null || (d.months.toIntOrNull() ?: 0) <= 0)) return
        val ok = act {
            manage.decideApplication(
                d.application.id,
                ApplicationDecision(d.approve, d.startDate, d.months.toIntOrNull(), d.rentDueDay.toIntOrNull(), d.note),
            )
        }
        if (ok) {
            setState { copy(decision = null) }
            loadTab(DetailTab.APPLICATIONS)
            if (d.approve) {
                // Approve creates the lease → the unit is OCCUPIED and the tenant shows under Tenants.
                (manage.leases(currentState.propertyId ?: return, active = true) as? AppResult.Success)?.let { r -> setState { copy(leases = r.data) } }
            }
        }
    }

    // ---------------- photos / documents ----------------

    private suspend fun captured(filePath: String) {
        val state = currentState
        val id = state.propertyId
        val mode = state.camera
        setState { copy(camera = null) }
        if (id == null || mode == null) {
            File(filePath).delete()
            return
        }
        when (mode) {
            CameraMode.PHOTO -> {
                val count = state.detail?.property?.images?.size ?: 0
                if (uploadPhoto(id, count, filePath, state.photoCategory, null, null, null)) {
                    sendEffect(PropertyDetailEffect.Message(UPLOADING_MESSAGE))
                } else {
                    File(filePath).delete()
                }
            }
            CameraMode.DOCUMENT -> {
                setState { copy(busy = true) }
                val replace = state.replacingDocId
                val r = if (replace != null) properties.replaceDocument(replace, filePath) else properties.uploadDocument(id, filePath, state.docType)
                setState { copy(busy = false, replacingDocId = null) }
                when (r) {
                    is AppResult.Success -> loadTab(DetailTab.DOCUMENTS)
                    is AppResult.Failure -> showError(r.error)
                }
            }
        }
    }

    private fun contactSupport() {
        viewModelScope.launch { sendEffect(PropertyDetailEffect.ContactSupport(getSupportConfig().email)) }
    }

    // ---------------- errors ----------------

    /** 412 / 409 / 400 messages are shown as the server wrote them. VERSION_CONFLICT → reload. */
    /** Moderator approve / reject → message, reload the property. */
    private suspend fun decide(approve: Boolean, withoutProof: Boolean, reason: String) {
        val id = currentState.propertyId ?: return
        if (currentState.busy) return
        setState { copy(busy = true) }
        val r = manage.review(id, approve, withoutProof, reason)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                sendEffect(PropertyDetailEffect.Message(r.data.message.ifBlank { if (approve) APPROVED else REJECTED }))
                sendEffect(PropertyDetailEffect.Changed)
                refresh()
            }
            is AppResult.Failure -> showError(r.error)
        }
    }

    private suspend fun showError(error: AppError) {
        // 412 owner not verified / ownership proof missing (approve, publish) → bottom sheet with the next step.
        if (error is AppError.InvalidState && error.errorCode?.uppercase() in com.saferenttechnologies.android.domain.model.ApprovalBlocker.CODES) {
            setState { copy(approvalBlock = com.saferenttechnologies.android.domain.model.ApprovalBlocker(error.errorCode!!.uppercase(), error.message)) }
            refresh()
            return
        }
        when (error) {
            is AppError.AccountBlocked -> return // global blocked screen
            is AppError.Conflict -> if (error.versionConflict) refresh()
            else -> Unit
        }
        sendEffect(PropertyDetailEffect.Message(error.message))
    }

    private fun close() {
        uploadsJob?.cancel()
        uploadsJob = null
        setState { PropertyDetailState() }
    }

    private val CHANGES_TAG = "SR-Changes"

    enum class InviteError { NAME, PHONE, RENT, START_DATE }

    companion object {
        private const val TAG = "SR-ManageProperty"
        const val INVITE_DAYS = 7
        const val INVITE_NOT_APPROVED = "You can invite tenants once SafeRent approves this property."
        /** Placeholder replaced by a string resource in the screen. */
        const val UPLOADING_MESSAGE = "@uploading"
        private const val APPROVED = "Approved, the listing is live."
        private const val REJECTED = "Rejected. The owner can fix it and send it again."
    }
}
