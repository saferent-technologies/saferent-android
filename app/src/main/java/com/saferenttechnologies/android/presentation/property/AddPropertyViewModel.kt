package com.saferenttechnologies.android.presentation.property

import com.saferenttechnologies.android.domain.model.ListingTypes
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UnitAvailability
import com.saferenttechnologies.android.domain.model.FormVisibility
import com.saferenttechnologies.android.domain.model.isFullyOccupied
import com.saferenttechnologies.android.domain.model.isOccupied
import com.saferenttechnologies.android.domain.model.FlatAmenityCycle
import com.saferenttechnologies.android.domain.model.ApartmentRules
import com.saferenttechnologies.android.domain.model.SubmitProblems
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import com.saferenttechnologies.android.domain.model.BuildingLimits
import com.saferenttechnologies.android.domain.model.PhotoRequirements
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Charge
import com.saferenttechnologies.android.domain.model.ChargeRules
import com.saferenttechnologies.android.domain.model.FieldDef
import com.saferenttechnologies.android.domain.model.FieldError
import com.saferenttechnologies.android.domain.model.FieldValidator
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyForms
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.domain.repository.FloorInput
import com.saferenttechnologies.android.domain.repository.PhotoUploadQueue
import com.saferenttechnologies.android.domain.repository.PricingInput
import com.saferenttechnologies.android.domain.repository.PropertyDraftStore
import com.saferenttechnologies.android.domain.repository.PropertyPatch
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import com.saferenttechnologies.android.domain.repository.TypedValues
import com.saferenttechnologies.android.domain.usecase.CreateDraftUseCase
import com.saferenttechnologies.android.domain.usecase.LoadDraftsUseCase
import com.saferenttechnologies.android.domain.usecase.LoadPropertyTypesUseCase
import com.saferenttechnologies.android.domain.usecase.LocationLookup
import com.saferenttechnologies.android.domain.usecase.ReverseGeocodeUseCase
import com.saferenttechnologies.android.domain.usecase.SaveStepUseCase
import com.saferenttechnologies.android.domain.usecase.SubmitPropertyUseCase
import com.saferenttechnologies.android.domain.usecase.UploadPhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Add-property wizard (10 steps). Every step is saved on the server before moving on, with the
 * latest `version` and the next `wizardStep`. Never logs addresses or coordinates.
 */
@HiltViewModel
class AddPropertyViewModel @Inject constructor(
    private val loadTypes: LoadPropertyTypesUseCase,
    private val loadDrafts: LoadDraftsUseCase,
    private val createDraft: CreateDraftUseCase,
    private val saveStep: SaveStepUseCase,
    private val reverseGeocode: ReverseGeocodeUseCase,
    private val uploadPhoto: UploadPhotoUseCase,
    private val submitProperty: SubmitPropertyUseCase,
    private val repository: PropertyRepository,
    private val uploads: PhotoUploadQueue,
    private val draftStore: PropertyDraftStore,
    private val manage: ManagePropertyRepository,
    private val logger: AppLogger,
    private val panoramas: com.saferenttechnologies.android.domain.repository.PanoramaFiles,
    private val videoUploads: com.saferenttechnologies.android.domain.repository.VideoUploadQueue,
    private val kyc: com.saferenttechnologies.android.domain.repository.KycRepository,
    private val compliance: com.saferenttechnologies.android.domain.repository.ComplianceRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<AddPropertyState, AddPropertyIntent, AddPropertyEffect>(AddPropertyState()) {

    private var uploadsJob: Job? = null
    private var lastSucceeded = 0

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) reset() }
        }
    }

    override suspend fun handleIntent(intent: AddPropertyIntent) {
        when (intent) {
            AddPropertyIntent.Open -> open()
            is AddPropertyIntent.Edit -> {
                setState { AddPropertyState(isOpen = true, loading = true, types = types) }
                if (currentState.types == null && !loadTypesIntoState()) return
                val max = uploadPhoto.maxImages()
                setState { copy(maxImages = max) }
                continueDraft(intent.propertyId)
                // Load failed → the error is already shown; stay on whatever the wizard shows.
                if (currentState.property?.id == intent.propertyId) {
                    val start = intent.step.coerceIn(WizardStep.CATEGORY, WizardStep.LAST)
                    setState { copy(step = start, editFromStep = start) }
                    onEnterStep()
                    if (intent.newUnit) handleIntent(AddPropertyIntent.NewUnit)
                }
            }
            is AddPropertyIntent.ResumeDraft -> {
                setState { AddPropertyState(isOpen = true, loading = true, types = types) }
                if (currentState.types == null && !loadTypesIntoState()) return
                val max = uploadPhoto.maxImages()
                setState { copy(maxImages = max) }
                continueDraft(intent.propertyId) // step = the saved wizardStep
                if (currentState.property?.id == intent.propertyId) onEnterStep()
            }
            is AddPropertyIntent.ContinueDraft -> continueDraft(intent.id)
            AddPropertyIntent.StartNew -> setState { copy(drafts = null, step = WizardStep.CATEGORY) }
            AddPropertyIntent.DismissDrafts -> reset()
            AddPropertyIntent.SaveAndExit -> {
                if (currentState.property != null && currentState.step in WizardStep.BASIC..WizardStep.BUILDING) {
                    save(advance = false)
                }
                // Live listing: the change simply stays as DRAFT (submit later from the property screen).
                if (currentState.fieldErrors.isEmpty()) reset()
            }
            AddPropertyIntent.Close -> reset()
            AddPropertyIntent.Back -> back()
            AddPropertyIntent.Next -> save(advance = true)
            AddPropertyIntent.Skip -> if (currentState.step == WizardStep.OPTIONAL) moveTo(WizardStep.PHOTOS, patchStep = true)
            is AddPropertyIntent.GoToStep -> goTo(intent.step)
            AddPropertyIntent.Retry -> if (currentState.types == null) loadTypesIntoState() else reloadProperty()
            AddPropertyIntent.DismissBanner -> setState { copy(bannerText = null, bannerMessage = null) }

            is AddPropertyIntent.SelectType -> if (!currentState.typeLocked) {
                val type = currentState.types?.type(intent.key)
                setState {
                    copy(
                        selectedTypeKey = intent.key,
                        listingType = ListingTypes.rentalOnly(type?.listingTypes).let { ok -> ok.firstOrNull { it == listingType } ?: ok.first() },
                        fieldErrors = emptyMap(),
                    )
                }
            }
            is AddPropertyIntent.AddPanorama -> addPanorama(intent.uri)
            is AddPropertyIntent.OpenPanoramaCamera -> if (currentState.imageCount < currentState.maxImages) {
                setState { copy(panoramaFor = intent.target) }
            } else banner(AddPropertyMessage.MAX_PHOTOS)
            AddPropertyIntent.ClosePanoramaCamera -> setState { copy(panoramaFor = null) }
            is AddPropertyIntent.PanoramaCaptured -> panoramaCaptured(intent.filePath)
            is AddPropertyIntent.OpenVideoCamera -> setState { copy(videoFor = intent.target) }
            AddPropertyIntent.CloseVideoCamera -> setState { copy(videoFor = null) }
            AddPropertyIntent.DismissVerifyOwner -> setState { copy(verifyOwnerSheet = false, ownerInReviewSheet = false) }
            is AddPropertyIntent.RetryVideo -> currentState.property?.id?.let { videoUploads.retry(it, intent.upload) }
            is AddPropertyIntent.AskDeleteVideo -> if (!currentState.busy) setState { copy(deleteVideoId = intent.videoId) }
            AddPropertyIntent.DismissDeleteVideo -> setState { copy(deleteVideoId = null) }
            is AddPropertyIntent.OpenFurniture -> setState { copy(openFurniture = intent.target) }
            AddPropertyIntent.FurnitureOpened -> setState { copy(openFurniture = null) }
            AddPropertyIntent.ConfirmDeleteVideo -> currentState.deleteVideoId?.let { videoId ->
                setState { copy(deleteVideoId = null) }
                mutate { repository.deleteVideo(it, videoId) }
            }
            AddPropertyIntent.SubmitWhileInReview -> {
                setState { copy(ownerInReviewSheet = false) }
                confirmOrDeclare()
            }
            is AddPropertyIntent.VideoRecorded -> {
                val target = currentState.videoFor ?: PanoramaTarget()
                setState { copy(videoFor = null) }
                val id = currentState.property?.id
                if (id == null) {
                    File(intent.filePath).delete()
                } else {
                    // One video per place: "Record again" replaces it → remove the old one first (else 409).
                    val old = currentState.property?.videos?.lastOrNull { it.unitId == target.unitId && it.roomId == target.roomId }
                    val removed = old == null || mutate { repository.deleteVideo(it, old.id) }
                    if (removed) {
                        videoUploads.enqueue(id, intent.filePath, intent.durationSec, target.unitId, target.roomId)
                        setState { copy(missingVideoUnitIds = missingVideoUnitIds - setOfNotNull(target.unitId)) }
                    } else {
                        File(intent.filePath).delete() // error banner shown; record again to retry
                    }
                }
            }
            is AddPropertyIntent.SelectListingType -> setState { copy(listingType = ListingTypes.safe(intent.value)) }
            is AddPropertyIntent.FieldChanged -> fieldChanged(intent)

            AddPropertyIntent.UseMyLocation -> useMyLocation()

            AddPropertyIntent.NewUnit -> {
                // Building says 4 flats → a 5th can't be added.
                if (!BuildingLimits.canAddUnit(currentState.building, currentState.property?.units?.size ?: 0)) {
                    return banner(AddPropertyMessage.UNITS_LIMIT)
                }
                setState { copy(unitEditor = FormEditor(values = UnitAvailability.prefill(emptyMap(), SimpleDate.today())), roomEditor = null) }
            }
            is AddPropertyIntent.EditUnit -> currentState.property?.units?.firstOrNull { it.id == intent.id }?.let { u ->
                if (u.isOccupied) return banner(AddPropertyMessage.UNIT_OCCUPIED)
                setState { copy(unitEditor = FormEditor(u.id, u.version, UnitAvailability.prefill(u.values + ("unitNumber" to u.unitNumber), SimpleDate.today()))) }
            }
            AddPropertyIntent.SaveUnit -> saveUnit()
            is AddPropertyIntent.OpenFlatPanel ->
                if (occupiedUnit(intent.unitId)) banner(AddPropertyMessage.UNIT_OCCUPIED) else openFlatPanel(intent.unitId)
            is AddPropertyIntent.OpenRoomPanel ->
                if (occupiedRoom(intent.roomId)) banner(AddPropertyMessage.UNIT_OCCUPIED) else openRoomPanel(intent.roomId)
            AddPropertyIntent.CloseFlatPanel -> setState { copy(flatPanel = null) }
            is AddPropertyIntent.CycleFlatAmenity -> setState {
                val p = flatPanel ?: return@setState this
                val next = FlatAmenityCycle.next(p.amenities[intent.key])
                copy(flatPanel = p.copy(amenities = if (next == null) p.amenities - intent.key else p.amenities + (intent.key to next), saved = false))
            }
            is AddPropertyIntent.AddFlatCustom -> setState {
                val p = flatPanel ?: return@setState this
                val label = intent.label.trim().take(60)
                if (label.isEmpty() || p.custom.any { it.equals(label, true) }) this else copy(flatPanel = p.copy(custom = p.custom + label, saved = false))
            }
            is AddPropertyIntent.RemoveFlatCustom -> setState {
                val p = flatPanel ?: return@setState this
                copy(flatPanel = p.copy(custom = p.custom.filterIndexed { i, _ -> i != intent.index }, saved = false))
            }
            AddPropertyIntent.SaveFlatAmenities -> saveFlatAmenities()
            AddPropertyIntent.CancelEditor -> setState { copy(unitEditor = null, roomEditor = null, bedDraft = null, floorsUnitId = null) }
            is AddPropertyIntent.DeleteUnit ->
                if (occupiedUnit(intent.id)) banner(AddPropertyMessage.UNIT_OCCUPIED) else mutate { repository.deleteUnit(it, intent.id) }
            is AddPropertyIntent.EditFloors -> if (occupiedUnit(intent.unitId)) banner(AddPropertyMessage.UNIT_OCCUPIED) else setState {
                copy(floorsUnitId = intent.unitId, floors = floors.ifEmpty { listOf(FloorRow("0"), FloorRow("1")) })
            }
            AddPropertyIntent.AddFloor -> setState { copy(floors = floors + FloorRow(floors.size.toString())) }
            is AddPropertyIntent.FloorChanged -> setState {
                copy(floors = floors.toMutableList().also { if (intent.index in it.indices) it[intent.index] = intent.row })
            }
            is AddPropertyIntent.RemoveFloor -> setState { copy(floors = floors.filterIndexed { i, _ -> i != intent.index }) }
            AddPropertyIntent.SaveFloors -> saveFloors()

            AddPropertyIntent.NewRoom -> setState { copy(roomEditor = FormEditor(), unitEditor = null) }
            is AddPropertyIntent.EditRoom -> currentState.property?.rooms?.firstOrNull { it.id == intent.id }?.let { r ->
                if (r.isFullyOccupied) return banner(AddPropertyMessage.UNIT_OCCUPIED)
                setState { copy(roomEditor = FormEditor(r.id, r.version, r.values + ("roomNumber" to r.roomNumber))) }
            }
            AddPropertyIntent.SaveRoom -> saveRoom()
            is AddPropertyIntent.StartBed -> setState { copy(bedDraft = Triple(intent.roomId, "", "")) }
            is AddPropertyIntent.BedChanged -> setState { copy(bedDraft = bedDraft?.copy(second = intent.number, third = intent.rent)) }
            AddPropertyIntent.SaveBed -> currentState.bedDraft?.let { (roomId, number, rent) ->
                if (number.isBlank()) return
                if (mutate { repository.addBed(it, roomId, number, rent) }) setState { copy(bedDraft = null) }
            }

            is AddPropertyIntent.AddCharge -> setState { copy(charges = charges + Charge(intent.kind, "FIXED_MONTHLY", frequency = "MONTHLY")) }
            is AddPropertyIntent.ChargeChanged -> setState {
                val list = charges.toMutableList().also { if (intent.index in it.indices) it[intent.index] = intent.charge }
                copy(charges = list, chargeErrors = chargeErrors - intent.index)
            }
            is AddPropertyIntent.RemoveCharge -> setState {
                copy(charges = charges.filterIndexed { i, _ -> i != intent.index }, chargeErrors = emptyMap())
            }

            AddPropertyIntent.AddRule -> setState { copy(rules = rules + ("" to "")) }
            is AddPropertyIntent.RuleChanged -> setState {
                copy(rules = rules.toMutableList().also { if (intent.index in it.indices) it[intent.index] = intent.title to intent.description })
            }
            is AddPropertyIntent.RemoveRule -> setState { copy(rules = rules.filterIndexed { i, _ -> i != intent.index }) }
            is AddPropertyIntent.ToggleAmenity -> setState {
                copy(amenities = if (intent.key in amenities) amenities - intent.key else amenities + (intent.key to "YES"))
            }
            is AddPropertyIntent.AddCustomAmenity -> if (intent.label.isNotBlank()) {
                setState { copy(customAmenities = customAmenities + intent.label.trim()) }
            }
            is AddPropertyIntent.RemoveCustomAmenity -> setState {
                copy(customAmenities = customAmenities.filterIndexed { i, _ -> i != intent.index })
            }

            is AddPropertyIntent.SelectImageCategory -> setState { copy(imageCategory = intent.category, imageSlot = 1) }
            is AddPropertyIntent.SelectImageSlot -> setState { copy(imageSlot = intent.slot.coerceIn(1, 20)) }
            is AddPropertyIntent.SelectImageTarget -> setState { copy(imageUnitId = intent.unitId, imageRoomId = intent.roomId) }
            is AddPropertyIntent.SelectRequiredPhoto -> setState {
                copy(imageCategory = intent.category, imageSlot = intent.slot, imageUnitId = intent.unitId, imageRoomId = intent.roomId)
            }
            is AddPropertyIntent.OpenCamera -> {
                if (currentState.locked && intent.mode == CameraMode.PHOTO) return banner(AddPropertyMessage.CHANGES_LOCKED)
                if (intent.mode == CameraMode.PHOTO && currentState.imageCount >= currentState.maxImages) {
                    return banner(AddPropertyMessage.MAX_PHOTOS)
                }
                setState { copy(camera = intent.mode, replacingDocId = intent.replaceDocId) }
            }
            AddPropertyIntent.CloseCamera -> setState { copy(camera = null, replacingDocId = null) }
            is AddPropertyIntent.Captured -> captured(intent.filePath)
            is AddPropertyIntent.DeleteImage -> {
                // A photo was removed → let the server copy decide what is still needed.
                setState { copy(requiredShot = emptySet()) }
                mutate { repository.deleteImage(it, intent.id) }
            }
            is AddPropertyIntent.SetCover -> mutate { repository.setImageOrder(it, intent.id, 0) }
            is AddPropertyIntent.RetryUpload -> currentState.property?.id?.let { uploads.retry(it, intent.upload) }
            is AddPropertyIntent.SelectDocType -> setState { copy(docType = intent.type) }

            AddPropertyIntent.Submit -> askSubmit()
            AddPropertyIntent.ConfirmSubmit -> {
                setState { copy(confirmSubmit = false) }
                submit()
            }
            AddPropertyIntent.DismissSubmit -> setState { copy(confirmSubmit = false) }
            is AddPropertyIntent.DeclarationAgree -> setState { copy(declareAgree = intent.agree) }
            AddPropertyIntent.Declare -> declare()
            AddPropertyIntent.DismissDeclaration -> if (!currentState.declaring) setState { copy(declaration = null, declareAgree = false) }
            AddPropertyIntent.Done -> reset()
        }
    }

    // ---------------- open / resume ----------------

    private suspend fun open() {
        if (currentState.isOpen) return
        setState { AddPropertyState(isOpen = true, loading = true) }
        if (!loadTypesIntoState()) return
        val maxImages = uploadPhoto.maxImages()
        val drafts = (loadDrafts() as? AppResult.Success)?.data.orEmpty()
        setState { copy(loading = false, maxImages = maxImages, drafts = drafts.takeIf { it.isNotEmpty() }) }
    }

    private suspend fun loadTypesIntoState(): Boolean = when (val r = loadTypes()) {
        is AppResult.Success -> {
            setState { copy(types = r.data, loading = false) }
            true
        }
        is AppResult.Failure -> {
            setState { copy(loading = false) }
            showError(r.error)
            false
        }
    }

    private suspend fun continueDraft(id: String) {
        setState { copy(drafts = null, loading = true) }
        when (val r = repository.property(id)) {
            is AppResult.Success -> {
                // Only real drafts are remembered for "continue draft".
                if (r.data.publicationStatus == "DRAFT") draftStore.saveDraftId(id)
                applyProperty(r.data, resume = true)
                setState { copy(loading = false) }
                loadRevision(id)
            }
            is AppResult.Failure -> {
                setState { copy(loading = false) }
                showError(r.error)
            }
        }
    }

    /** Fills every form from the server copy (resume / reload). */
    private fun applyProperty(p: Property, resume: Boolean) {
        setState {
            copy(
                property = p,
                selectedTypeKey = p.propertyType,
                listingType = ListingTypes.safe(p.listingType),
                step = if (resume) p.wizardStep.coerceIn(WizardStep.CATEGORY, WizardStep.LAST) else step,
                basic = mapOf("title" to p.title, "description" to p.description),
                address = mapOf(
                    "addressLine" to p.addressLine, "locality" to p.locality, "city" to p.city,
                    "state" to p.state, "pincode" to p.pincode,
                ),
                latitude = p.latitude ?: latitude,
                longitude = p.longitude ?: longitude,
                building = p.building,
                pricing = p.pricing.ifEmpty { pricing },
                charges = p.charges.ifEmpty { charges },
            )
        }
        watchUploads(p.id)
    }

    private var videosJob: Job? = null

    private fun watchUploads(propertyId: String) {
        if (videosJob == null) videosJob = viewModelScope.launch {
            var uploadedBefore = -1
            videoUploads.observe(propertyId).collect { list ->
                setState { copy(videoUploads = list) }
                val uploaded = list.count { it.state == com.saferenttechnologies.android.domain.repository.VideoUploadState.UPLOADED }
                if (uploadedBefore >= 0 && uploaded > uploadedBefore) reloadProperty() // one reached the server → show it
                uploadedBefore = uploaded
            }
        }
        if (uploadsJob != null) return
        uploadsJob = viewModelScope.launch {
            uploads.observe(propertyId).collect { summary ->
                setState { copy(uploads = summary) }
                if (summary.succeeded > lastSucceeded) {
                    lastSucceeded = summary.succeeded
                    reloadProperty()
                }
            }
        }
    }

    private suspend fun reloadProperty() {
        val id = currentState.property?.id ?: return
        when (val r = repository.property(id)) {
            is AppResult.Success -> setState { copy(property = r.data) }
            is AppResult.Failure -> showError(r.error)
        }
    }

    // ---------------- navigation ----------------

    private fun back() {
        val state = currentState
        when {
            state.camera != null -> setState { copy(camera = null) }
            state.unitEditor != null || state.roomEditor != null || state.floorsUnitId != null ->
                setState { copy(unitEditor = null, roomEditor = null, floorsUnitId = null, bedDraft = null) }
            // Opened on this section from "Edit" → close, don't walk back to step 1.
            state.editFromStep != null && state.step <= state.editFromStep -> reset()
            state.step > WizardStep.CATEGORY -> setState { copy(step = step - 1, fieldErrors = emptyMap()) }
            else -> reset()
        }
    }

    private suspend fun goTo(step: Int) {
        if (currentState.property == null) return
        setState { copy(step = step.coerceIn(WizardStep.CATEGORY, WizardStep.LAST), fieldErrors = emptyMap(), problems = emptyList()) }
        onEnterStep()
    }

    private suspend fun moveTo(step: Int, patchStep: Boolean) {
        if (patchStep) {
            val p = currentState.property ?: return
            if (!applyResult(saveStep(p.id, PropertyPatch(p.version, step)))) return
        }
        setState { copy(step = step, fieldErrors = emptyMap()) }
        onEnterStep()
    }

    private suspend fun onEnterStep() {
        val state = currentState
        val id = state.property?.id ?: return
        when (state.step) {
            WizardStep.OPTIONAL -> {
                if (state.amenityGroups.isEmpty()) {
                    (repository.amenities() as? AppResult.Success)?.let { r -> setState { copy(amenityGroups = r.data) } }
                }
                // Edit / resumed draft: pre-fill what was saved before (the user's unsaved answers win).
                if (state.optionalLoadedFor != id) {
                    (repository.utilities(id) as? AppResult.Success)?.let { r -> setState { copy(utilities = r.data + utilities) } }
                    (repository.policies(id) as? AppResult.Success)?.let { r ->
                        setState { copy(policies = r.data.first + policies, rules = rules.ifEmpty { r.data.second }) }
                    }
                    setState { copy(optionalLoadedFor = id) }
                }
            }
            WizardStep.DOCUMENTS -> {
                (repository.documents(id) as? AppResult.Success)?.let { r -> setState { copy(documents = r.data) } }
                // Live listing: last step = "Review your changes" with the change's completeness.
                if (state.isLiveEdit) (repository.completeness(id) as? AppResult.Success)?.let { r -> setState { copy(completeness = r.data) } }
            }
            // Ownership: loaded by its own screen model (OwnershipViewModel) from the host.
        }
    }

    // ---------------- saving each step ----------------

    private suspend fun save(advance: Boolean) {
        val state = currentState
        if (state.busy) return
        if (state.locked) {
            // Read-only while SafeRent verifies the change: just move between steps.
            if (advance) goTo(state.step + 1) else banner(AddPropertyMessage.CHANGES_LOCKED)
            return
        }
        val next = (state.step + 1).coerceAtMost(WizardStep.LAST)
        setState { copy(fieldErrors = emptyMap(), bannerText = null, bannerMessage = null) }

        val ok = when (state.step) {
            WizardStep.CATEGORY -> saveCategory(next)
            WizardStep.BASIC -> validate(PropertyForms.basic(), state.basic) && patch(next) {
                it.copy(title = state.basic["title"].orEmpty(), description = state.basic["description"].orEmpty(), listingType = state.listingType)
            }
            WizardStep.LOCATION -> saveLocation(next)
            WizardStep.BUILDING -> {
                val fields = state.type?.buildingFields.orEmpty()
                val tooFew = BuildingLimits.unitsError(state.building, state.property?.units?.size ?: 0)
                if (tooFew != null) {
                    setState { copy(fieldErrors = mapOf(tooFew)) }
                    false
                } else {
                    validate(fields, state.building) && patch(next) { it.copy(building = TypedValues(fields, state.building)) }
                }
            }
            WizardStep.UNITS -> {
                val count = if (state.structure.usesRooms) state.property?.rooms?.size else state.property?.units?.size
                if ((count ?: 0) == 0) {
                    banner(AddPropertyMessage.UNIT_REQUIRED)
                    false
                } else {
                    patch(next) { it }
                }
            }
            WizardStep.PRICING -> savePricing(next)
            WizardStep.OPTIONAL -> saveOptional(next)
            WizardStep.PHOTOS -> when {
                state.uploads.running > 0 -> {
                    banner(AddPropertyMessage.WAIT_FOR_UPLOADS)
                    false
                }
                state.imageCount == 0 -> {
                    banner(AddPropertyMessage.PHOTO_REQUIRED)
                    false
                }
                state.missingRequiredPhotos.isNotEmpty() -> {
                    banner(AddPropertyMessage.REQUIRED_PHOTOS_MISSING)
                    false
                }
                else -> patch(next) { it }
            }
            WizardStep.DOCUMENTS -> patch(next) { it }
            else -> false
        }
        if (ok && advance) {
            logger.d(TAG, "Step ${state.step} saved → step $next")
            setState { copy(step = next) }
            onEnterStep()
        }
    }

    private suspend fun saveCategory(next: Int): Boolean {
        val state = currentState
        val typeKey = state.selectedTypeKey ?: return false
        if (state.property != null) return patch(next) { it.copy(listingType = state.listingType) }
        setState { copy(saving = true) }
        val r = createDraft(typeKey, state.listingType, state.basic["title"])
        setState { copy(saving = false) }
        return when (r) {
            is AppResult.Success -> {
                applyProperty(r.data, resume = false)
                patch(next) { it }
            }
            is AppResult.Failure -> {
                showError(r.error)
                false
            }
        }
    }

    private suspend fun saveLocation(next: Int): Boolean {
        val state = currentState
        if (!validate(PropertyForms.address(), state.address)) return false
        if (!PropertyForms.isValidPincode(state.address["pincode"].orEmpty())) {
            setState { copy(fieldErrors = mapOf("pincode" to FieldError.Required)) }
            banner(AddPropertyMessage.PIN_INVALID)
            return false
        }
        return patch(next) {
            it.copy(
                addressLine = state.address["addressLine"].orEmpty(),
                locality = state.address["locality"].orEmpty(),
                city = state.address["city"].orEmpty(),
                state = state.address["state"].orEmpty(),
                pincode = state.address["pincode"].orEmpty(),
                latitude = state.latitude,
                longitude = state.longitude,
            )
        }
    }

    private suspend fun savePricing(next: Int): Boolean {
        val state = currentState
        val id = state.property?.id ?: return false
        // Apartment: "Common charges" — never the flat's rent / deposit / sale price (server refuses them).
        val fields = PropertyForms.pricingFor(state.isUnitPriced || state.isRoomPriced, state.listingType, state.types)
        val chargeErrors = ChargeRules.validate(state.charges)
        if (chargeErrors.isNotEmpty()) setState { copy(chargeErrors = chargeErrors) }
        if (!validate(fields, state.pricing) || chargeErrors.isNotEmpty()) return false
        // Only shown fields (e.g. no "paid by" without agreement charges; no old rent due day).
        val values = FormVisibility.clean(fields, state.pricing)
        return mutate { repository.savePricing(id, PricingInput(TypedValues(fields, values), state.charges)) } && patch(next) { it }
    }

    /** Optional step: saves only the sections the landlord filled in. */
    private suspend fun saveOptional(next: Int): Boolean {
        val state = currentState
        val id = state.property?.id ?: return false
        // Hidden follow-ups are dropped (meter number without a separate meter, water amount when included …).
        val utilFields = PropertyForms.utilities(state.types)
        val policyFields = PropertyForms.policies(state.types, pg = state.structure.usesRooms)
        val utilities = FormVisibility.clean(utilFields, state.utilities).filterValues { it.isNotBlank() }
        val policies = FormVisibility.clean(policyFields, state.policies).filterValues { it.isNotBlank() }
        if (!validate(utilFields + policyFields, utilities + policies)) return false
        if (utilities.isNotEmpty() &&
            !mutate { repository.saveUtilities(id, TypedValues(utilFields, utilities + PropertyForms.utilityExtras(utilities))) }
        ) return false
        val rules = state.rules.map { (t, d) -> t.trim() to d.trim() }.filter { it.first.length >= 2 }.take(PropertyForms.RULES_MAX)
        if ((policies.isNotEmpty() || rules.isNotEmpty()) &&
            !mutate { repository.savePolicies(id, TypedValues(policyFields, policies), rules) }
        ) return false
        if ((state.amenities.isNotEmpty() || state.customAmenities.isNotEmpty()) &&
            !mutate { repository.saveAmenities(id, state.amenities, state.customAmenities) }
        ) return false
        return patch(next) { it }
    }

    /** PATCH properties/{id} with the latest version + [wizardStep]. */
    private suspend fun patch(wizardStep: Int, build: (PropertyPatch) -> PropertyPatch): Boolean {
        val p = currentState.property ?: return false
        setState { copy(saving = true) }
        val r = saveStep(p.id, build(PropertyPatch(version = p.version, wizardStep = wizardStep)))
        setState { copy(saving = false) }
        return applyResult(r)
    }

    private suspend fun applyResult(r: AppResult<Property>): Boolean = when (r) {
        is AppResult.Success -> {
            setState { copy(property = r.data) }
            // Live listing: the first save creates the change and its ids replace the live ones →
            // always continue from a fresh ?view=draft (ids + version of the change).
            if (currentState.isLiveEdit) reloadProperty()
            true
        }
        is AppResult.Failure -> {
            showError(r.error)
            false
        }
    }

    // ---------------- location ----------------

    private suspend fun useMyLocation() {
        if (currentState.locating) return
        setState { copy(locating = true, locationNote = null) }
        when (val r = reverseGeocode()) {
            LocationLookup.NoFix -> setState { copy(locating = false, locationNote = LocationNote.NO_FIX) }
            is LocationLookup.Found -> setState {
                val a = r.address
                copy(
                    locating = false,
                    latitude = r.fix.latitude,
                    longitude = r.fix.longitude,
                    accuracyMeters = r.fix.accuracyMeters,
                    locationNote = when {
                        r.lowAccuracy -> LocationNote.LOW_ACCURACY
                        a == null -> LocationNote.LOOKUP_UNAVAILABLE
                        else -> null
                    },
                    // Locality / city / state / PIN always follow the fix (not editable).
                    // House / street is prefilled only if the landlord hasn't typed one.
                    address = if (a == null) address else address + mapOf(
                        "locality" to a.locality, "city" to a.city, "state" to a.state, "pincode" to a.pincode,
                    ) + listOf("addressLine" to a.addressLine).filter { (k, v) -> v.isNotBlank() && address[k].isNullOrBlank() },
                )
            }
        }
    }

    // ---------------- units / rooms ----------------

    private suspend fun saveUnit() {
        val state = currentState
        val editor = state.unitEditor ?: return
        val id = state.property?.id ?: return
        // "Availability" isn't a form field any more: availableFrom + (optional) status are added below.
        val fields = UnitAvailability.formFields(state.type?.unitFields?.ifEmpty { null } ?: PropertyForms.defaultUnit())
        // Apartment: each flat has its own rent + deposit (SALE: price), saved with the flat.
        val priceFields = if (state.isUnitPriced) PropertyForms.flatPricing(state.listingType) else emptyList()
        val today = SimpleDate.today()
        val errors = FieldValidator.validate(fields + priceFields, editor.values) +
            UnitAvailability.validate(editor.values, today).mapValues { FieldError.Server(it.value) }
        if (errors.isNotEmpty()) return setState { copy(unitEditor = editor.copy(errors = errors)) }
        // Never OCCUPIED; status only when "not ready" (or AVAILABLE on edit).
        val availability = UnitAvailability.body(editor.values, isNew = editor.id == null, today = today)
        val values = TypedValues(
            fields + UnitAvailability.sentFields,
            editor.values.filterKeys { k -> fields.any { it.key == k } } + availability,
        )
        val pricing = if (state.isUnitPriced) ApartmentRules.flatPricingBody(editor.values, state.listingType) else emptyMap()
        val ok = mutate(onIssues = { issues -> setState { copy(unitEditor = unitEditor?.copy(errors = issues)) } }) {
            if (editor.id == null) repository.addUnit(id, values, pricing) else repository.updateUnit(id, editor.id, editor.version, values, pricing)
        }
        if (ok) {
            setState { copy(unitEditor = null) }
            // New / edited flat → straight to its amenities + photos.
            if (state.isUnitPriced) {
                val number = editor.values["unitNumber"]?.trim()
                val unit = currentState.property?.units?.firstOrNull { it.id == editor.id } ?: currentState.property?.units?.firstOrNull { it.unitNumber == number }
                unit?.let { openFlatPanel(it.id) }
            }
        }
    }

    // ---------------- apartment: a flat's amenities ----------------

    private suspend fun openFlatPanel(unitId: String) {
        val unit = currentState.property?.units?.firstOrNull { it.id == unitId } ?: return
        if (currentState.amenityGroups.isEmpty()) {
            (repository.amenities() as? AppResult.Success)?.let { r -> setState { copy(amenityGroups = r.data) } }
        }
        val catalogue = currentState.amenityGroups.flatMap { g -> g.items.map { it.key } }.toSet()
        // Keys outside the catalogue were added as custom amenities.
        val (known, custom) = unit.amenities.entries.partition { it.key in catalogue }
        logger.d(APARTMENT_TAG, "flat panel ${unit.unitNumber}")
        setState {
            copy(
                flatPanel = FlatPanel(
                    unitId = unitId,
                    amenities = known.associate { it.key to it.value },
                    custom = custom.filter { it.value.equals("YES", true) }.map { it.key },
                ),
            )
        }
    }

    /** PG / hostel room: its amenities + photos. */
    private suspend fun openRoomPanel(roomId: String) {
        val room = currentState.property?.rooms?.firstOrNull { it.id == roomId } ?: return
        if (currentState.amenityGroups.isEmpty()) {
            (repository.amenities() as? AppResult.Success)?.let { r -> setState { copy(amenityGroups = r.data) } }
        }
        val catalogue = currentState.amenityGroups.flatMap { g -> g.items.map { it.key } }.toSet()
        val (known, custom) = room.amenities.entries.partition { it.key in catalogue }
        logger.d(APARTMENT_TAG, "room panel ${room.roomNumber}")
        setState {
            copy(
                flatPanel = FlatPanel(
                    roomId = roomId,
                    amenities = known.associate { it.key to it.value },
                    custom = custom.filter { it.value.equals("YES", true) }.map { it.key },
                ),
            )
        }
    }

    /** PUT amenities { unitId | roomId, items, custom } — inside this unit / room only. */
    private suspend fun saveFlatAmenities() {
        val panel = currentState.flatPanel ?: return
        val id = currentState.property?.id ?: return
        if (mutate { repository.saveAmenities(id, panel.amenities, panel.custom, unitId = panel.unitId, roomId = panel.roomId) }) {
            setState { copy(flatPanel = flatPanel?.copy(saved = true)) }
        }
    }

    private suspend fun saveFloors() {
        val state = currentState
        val id = state.property?.id ?: return
        val unitId = state.floorsUnitId ?: return
        val floors = state.floors.mapNotNull { r ->
            val n = r.floorNumber.toIntOrNull() ?: return@mapNotNull null
            FloorInput(n, r.bedrooms.toIntOrNull() ?: 0, r.bathrooms.toIntOrNull() ?: 0, r.kitchens.toIntOrNull() ?: 0, r.livingRooms.toIntOrNull() ?: 0)
        }
        if (mutate { repository.saveFloors(id, unitId, floors) }) setState { copy(floorsUnitId = null) }
    }

    private suspend fun saveRoom() {
        val state = currentState
        val editor = state.roomEditor ?: return
        val id = state.property?.id ?: return
        val fields = PropertyForms.room(state.types)
        val errors = FieldValidator.validate(fields, editor.values)
        if (errors.isNotEmpty()) return setState { copy(roomEditor = editor.copy(errors = errors)) }
        val values = TypedValues(fields, editor.values.filterKeys { k -> fields.any { it.key == k } })
        val ok = mutate(onIssues = { issues -> setState { copy(roomEditor = roomEditor?.copy(errors = issues)) } }) {
            if (editor.id == null) repository.addRoom(id, values) else repository.updateRoom(id, editor.id, editor.version, values)
        }
        if (ok) {
            setState { copy(roomEditor = null) }
            // New / edited room → its amenities + photos.
            val number = editor.values["roomNumber"]?.trim()
            val room = currentState.property?.rooms?.firstOrNull { it.id == editor.id } ?: currentState.property?.rooms?.firstOrNull { it.roomNumber == number }
            room?.let { openRoomPanel(it.id) }
        }
    }

    /**
     * Runs a write on the property, then reloads it (new version, units, images…).
     * Field issues go to [onIssues] (e.g. "Flat number already exists" under unitNumber).
     */
    private suspend fun mutate(
        onIssues: ((Map<String, FieldError>) -> Unit)? = null,
        call: suspend (propertyId: String) -> AppResult<Unit>,
    ): Boolean {
        val id = currentState.property?.id ?: return false
        setState { copy(saving = true) }
        val r = call(id)
        setState { copy(saving = false) }
        return when (r) {
            is AppResult.Success -> {
                reloadProperty()
                true
            }
            is AppResult.Failure -> {
                val e = r.error
                when {
                    onIssues != null && e is AppError.FieldIssues && e.issues.isNotEmpty() ->
                        onIssues(e.issues.mapValues { FieldError.Server(it.value) })
                    onIssues != null && e is AppError.Conflict && !e.versionConflict ->
                        onIssues(mapOf("unitNumber" to FieldError.Server(e.message), "roomNumber" to FieldError.Server(e.message)))
                    else -> showError(e)
                }
                false
            }
        }
    }

    // ---------------- photos / documents ----------------

    /** 360 / panorama from the gallery → scaled JPEG (no EXIF) → same upload queue as photos. */
    private suspend fun addPanorama(uri: String) {
        val state = currentState
        val id = state.property?.id ?: return
        val prep = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { panoramas.prepare(uri) }
        when (prep) {
            com.saferenttechnologies.android.domain.repository.PanoramaPrep.NotPanorama -> banner(AddPropertyMessage.NOT_PANORAMA)
            com.saferenttechnologies.android.domain.repository.PanoramaPrep.Unreadable -> banner(AddPropertyMessage.PANORAMA_UNREADABLE)
            is com.saferenttechnologies.android.domain.repository.PanoramaPrep.Ok -> {
                val queued = uploadPhoto(id, state.imageCount, prep.filePath, com.saferenttechnologies.android.domain.model.PanoramaRules.CATEGORY, null, null, null)
                if (!queued) {
                    File(prep.filePath).delete()
                    banner(AddPropertyMessage.MAX_PHOTOS)
                } else {
                    // Shows in the upload progress / photo list like other photos.
                    logger.d(TAG, "panorama queued (${prep.width}x${prep.height})")
                }
            }
        }
    }

    /** Our 360 camera's photo → upload as PANORAMA_360 with that flat's unitId / room's roomId. */
    private suspend fun panoramaCaptured(filePath: String) {
        val state = currentState
        val target = state.panoramaFor ?: PanoramaTarget()
        setState { copy(panoramaFor = null) }
        val id = state.property?.id ?: run { File(filePath).delete(); return }
        val queued = uploadPhoto(id, state.imageCount, filePath, com.saferenttechnologies.android.domain.model.PanoramaRules.CATEGORY, null, target.unitId, target.roomId)
        if (!queued) {
            File(filePath).delete()
            banner(AddPropertyMessage.MAX_PHOTOS)
        }
    }

    private suspend fun captured(filePath: String) {
        val state = currentState
        val id = state.property?.id
        val mode = state.camera
        setState { copy(camera = null) }
        if (id == null) {
            File(filePath).delete()
            return
        }
        when (mode) {
            CameraMode.PHOTO -> {
                val slot = state.imageSlot.takeIf { state.imageCategory in PhotoRequirements.COUNTED }
                val queued = uploadPhoto(id, state.imageCount, filePath, state.imageCategory, slot, state.imageUnitId, state.imageRoomId)
                if (!queued) {
                    File(filePath).delete()
                    banner(AddPropertyMessage.MAX_PHOTOS)
                } else {
                    // Checklist row → Done right away (the upload finishes in the background).
                    val key = RequiredPhotoKey(state.imageCategory, state.imageSlot, state.imageUnitId, state.imageRoomId)
                    setState { copy(requiredShot = requiredShot + key) }
                }
            }
            CameraMode.DOCUMENT -> {
                setState { copy(uploadingDocument = true) }
                val replace = state.replacingDocId
                val r = if (replace != null) repository.replaceDocument(replace, filePath) else repository.uploadDocument(id, filePath, state.docType)
                setState { copy(uploadingDocument = false, replacingDocId = null) }
                when (r) {
                    is AppResult.Success -> (repository.documents(id) as? AppResult.Success)?.let { d -> setState { copy(documents = d.data) } }
                    is AppResult.Failure -> showError(r.error) // the file stays; the landlord can retake
                }
            }
            null -> File(filePath).delete()
        }
    }

    // ---------------- submit ----------------

    // ---------------- change request (live listing) ----------------

    /** Live listing: which change is open (DRAFT / PENDING_VERIFICATION / REJECTED) → banners + lock. */
    private suspend fun loadRevision(id: String) {
        if (!currentState.isLiveEdit) {
            setState { copy(revision = null, locked = false) }
            return
        }
        val revision = (manage.detail(id) as? AppResult.Success)?.data?.pendingRevision
        logger.d(CHANGES_TAG, "live edit, change ${revision?.status ?: "none yet"}")
        setState { copy(revision = revision, locked = revision?.inReview == true) }
    }

    /** POST revision/submit → "Changes sent. Your listing stays live meanwhile." */
    private suspend fun submitChanges() {
        val id = currentState.property?.id ?: return
        if (currentState.busy) return
        setState { copy(saving = true, problems = emptyList(), bannerText = null, bannerMessage = null) }
        val r = manage.submitChanges(id)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                logger.d(CHANGES_TAG, "changes sent for verification")
                setState { copy(submitted = true, revision = r.data.revision, locked = true) }
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.InvalidState && e.problems.isNotEmpty()) {
                    setState { copy(problems = e.problems.map { it to ApartmentRules.stepFor(it, isUnitPriced || isRoomPriced) }, bannerText = e.message) }
                } else {
                    showError(e) // e.g. 412 "You haven't changed anything yet."
                }
            }
        }
    }

    /** Documents step → "Submit for verification": local checks, then "Are you sure?". */
    private suspend fun askSubmit() {
        if (currentState.property == null || currentState.busy) return
        if (currentState.locked) return banner(AddPropertyMessage.CHANGES_LOCKED)
        // Under verification (not live yet): nothing to submit — the edits are already saved.
        if (!currentState.needsSubmit) {
            if (currentState.uploads.running > 0) return banner(AddPropertyMessage.WAIT_FOR_UPLOADS)
            logger.d(TAG, "Changes saved (status ${currentState.property?.publicationStatus}) → close")
            reset()
            return
        }
        if (currentState.uploads.running > 0) return banner(AddPropertyMessage.WAIT_FOR_UPLOADS)
        if (currentState.missingRequiredPhotos.isNotEmpty()) return banner(AddPropertyMessage.REQUIRED_PHOTOS_MISSING)
        // Every flat needs its video tour (the building's own video stays optional). Fresh copy first:
        // a video that finished uploading a moment ago must count.
        reloadProperty()
        val p = currentState.property
        val serverVideos = p?.videos.orEmpty()
        val localUnits = currentState.videoUploads.filter { it.state != com.saferenttechnologies.android.domain.repository.VideoUploadState.FAILED }.mapNotNull { it.unitId }.toSet()
        logger.d(TAG, "Video check: server videos=${serverVideos.size} (for flats ${serverVideos.count { it.unitId != null }}), sent from this phone=${localUnits.size}, server lists videos=${p?.videosFromServer}")
        // Server doesn't send "videos" yet → the app can't see uploaded videos, so it doesn't block (the server checks).
        val missing = if (p?.videosFromServer != true) emptyList() else com.saferenttechnologies.android.domain.model.VideoRules.unitsWithoutVideo(
            units = p.units.map { it.id to it.unitNumber },
            serverVideos = serverVideos,
            uploadingUnitIds = localUnits,
        )
        if (missing.isNotEmpty()) {
            val ids = p?.units.orEmpty().filter { u -> u.unitNumber.ifBlank { u.id } in missing }.map { it.id }.toSet()
            setState { copy(missingVideoUnits = missing, missingVideoUnitIds = ids) }
            return banner(AddPropertyMessage.VIDEO_REQUIRED)
        }
        // Owner not verified (and not sent for review yet) → verify first: SafeRent approves
        // properties only from verified owners. Status unknown (offline) → don't block here.
        val status = (kyc.status() as? AppResult.Success)?.data
        if (status != null && !status.isApproved && !status.isSubmitted) return setState { copy(verifyOwnerSheet = true) }
        // Verification sent, not reviewed yet → tell them, they can still send the property.
        if (status != null && status.isSubmitted) return setState { copy(ownerInReviewSheet = true) }
        confirmOrDeclare()
    }

    /**
     * Last step before submitting: the ownership declaration. Not made (or the text changed) → show it;
     * made / can't check (offline, older server) → "Are you sure?" (the server checks again on submit).
     */
    private suspend fun confirmOrDeclare() {
        val id = currentState.property?.id
        if (id == null || currentState.isLiveEdit) return setState { copy(confirmSubmit = true) }
        val d = (compliance.compliance(id) as? AppResult.Success)?.data?.declaration
        if (d != null && !d.current && d.text.isNotBlank()) {
            setState { copy(declaration = d, declareAgree = false, declarationOwnerOnly = false) }
        } else {
            setState { copy(confirmSubmit = true) }
        }
    }

    /** Submit said "Confirm the ownership declaration." → open it. */
    private suspend fun openDeclaration() {
        val id = currentState.property?.id ?: return
        val d = (compliance.compliance(id) as? AppResult.Success)?.data?.declaration ?: return
        setState { copy(declaration = d, declareAgree = false, declarationOwnerOnly = false) }
    }

    private suspend fun declare() {
        val s = currentState
        val id = s.property?.id ?: return
        val d = s.declaration ?: return
        if (!s.declareAgree || s.declaring) return
        setState { copy(declaring = true) }
        val r = compliance.declare(id, d.version)
        setState { copy(declaring = false) }
        when (r) {
            is AppResult.Success -> {
                logger.d(TAG, "Ownership declaration made → submit")
                setState { copy(declaration = null, declareAgree = false) }
                submit()
            }
            is AppResult.Failure -> {
                val e = r.error
                when {
                    e is AppError.Server && e.httpCode == 403 -> setState { copy(declarationOwnerOnly = true, declareAgree = false) }
                    // "The declaration text changed. Reload and read it again."
                    e is AppError.FieldIssues -> openDeclaration()
                }
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { setState { copy(bannerText = it) } }
            }
        }
    }

    private suspend fun submit() {
        if (currentState.isLiveEdit) return submitChanges()
        val id = currentState.property?.id ?: return
        if (currentState.busy) return
        setState { copy(saving = true, problems = emptyList(), bannerText = null, bannerMessage = null) }
        when (val r = submitProperty(id)) {
            is SubmitPropertyUseCase.Result.Submitted -> {
                logger.d(TAG, "Submitted for verification")
                setState { copy(saving = false, submitted = true, property = r.property) }
            }
            is SubmitPropertyUseCase.Result.Problems -> {
                // "Confirm the ownership declaration." → the declaration, not a wizard step.
                val needsDeclaration = r.problems.any { (p, _) -> com.saferenttechnologies.android.domain.model.ComplianceRules.isDeclarationProblem(p) }
                val rest = r.problems.filterNot { (p, _) -> com.saferenttechnologies.android.domain.model.ComplianceRules.isDeclarationProblem(p) }
                setState {
                    // Apartment: "Add the rent." etc. are fixed on the flats.
                    val problems = if (isUnitPriced || isRoomPriced) rest.map { (p, _) -> p to ApartmentRules.stepFor(p, apartment = true) } else rest
                    copy(saving = false, problems = problems, bannerText = if (rest.isEmpty()) null else r.message)
                }
                if (needsDeclaration) openDeclaration()
            }
            is SubmitPropertyUseCase.Result.Failed -> {
                setState { copy(saving = false) }
                showError(r.error)
            }
        }
    }

    // ---------------- forms ----------------

    private fun fieldChanged(i: AddPropertyIntent.FieldChanged) = setState {
        val errors = fieldErrors - i.key
        when (i.scope) {
            FormScope.BASIC -> copy(basic = basic + (i.key to i.value), fieldErrors = errors)
            FormScope.ADDRESS -> copy(address = address + (i.key to i.value), fieldErrors = errors)
            FormScope.BUILDING -> copy(building = building + (i.key to i.value), fieldErrors = errors)
            FormScope.PRICING -> copy(pricing = pricing + (i.key to i.value), fieldErrors = errors)
            FormScope.UTILITIES -> copy(utilities = utilities + (i.key to i.value))
            FormScope.POLICIES -> copy(policies = policies + (i.key to i.value))
            FormScope.UNIT -> copy(unitEditor = unitEditor?.let { it.copy(values = it.values + (i.key to i.value), errors = it.errors - i.key) })
            FormScope.ROOM -> copy(roomEditor = roomEditor?.let { it.copy(values = it.values + (i.key to i.value), errors = it.errors - i.key) })
        }
    }

    private fun validate(fields: List<FieldDef>, values: Map<String, String>): Boolean {
        val errors = FieldValidator.validate(fields, values)
        if (errors.isNotEmpty()) setState { copy(fieldErrors = errors) }
        return errors.isEmpty()
    }

    // ---------------- errors ----------------

    private suspend fun showError(error: AppError) {
        when (error) {
            is AppError.FieldIssues -> setState {
                copy(fieldErrors = error.issues.mapValues { FieldError.Server(it.value) }, bannerText = error.message)
            }
            is AppError.Conflict -> if (error.versionConflict) {
                reloadProperty() // fresh version; the landlord just taps again
                banner(AddPropertyMessage.VERSION_CONFLICT)
            } else {
                setState { copy(bannerText = error.message) }
            }
            is AppError.InvalidState -> if (error.errorCode == "VIDEO_REQUIRED") {
                // Server: these flats have no video → banner with their numbers + red video slots.
                val units = currentState.property?.units.orEmpty()
                val numbers = error.unitIds.map { id -> units.firstOrNull { it.id == id }?.unitNumber?.ifBlank { null } ?: id }
                setState { copy(missingVideoUnits = numbers, missingVideoUnitIds = error.unitIds.toSet()) }
                banner(AddPropertyMessage.VIDEO_REQUIRED)
            } else {
                // 412 "Your changes are being verified…" → the change can't be edited now.
                if (error.message.contains("being verified", ignoreCase = true)) setState { copy(locked = true) }
                setState { copy(bannerText = error.message) }
            }
            is AppError.AccountBlocked -> Unit // global handling shows the blocked screen
            is AppError.Server -> setState { copy(bannerText = error.message) }
            else -> banner(AddPropertyMessage.NETWORK_OR_SERVER, error.message)
        }
    }

    private val CHANGES_TAG = "SR-Changes"
    private val APARTMENT_TAG = "SR-Apartment"

    private fun occupiedUnit(id: String) = currentState.property?.units?.firstOrNull { it.id == id }?.isOccupied == true
    private fun occupiedRoom(id: String) = currentState.property?.rooms?.firstOrNull { it.id == id }?.isFullyOccupied == true

    private fun banner(message: AddPropertyMessage, text: String? = null) =
        setState { copy(bannerMessage = message, bannerText = text) }

    private fun reset() {
        // Remember where they left (closed / back / save & exit) → "Continue changes" opens there.
        // Sent for review → forget it (the next edit starts fresh).
        val s = currentState
        s.property?.id?.let { id ->
            if (s.isOpen) {
                val step = if (s.submitted) null else s.step
                viewModelScope.launch { draftStore.saveLastStep(id, step) }
            }
        }
        uploadsJob?.cancel()
        uploadsJob = null
        lastSucceeded = 0
        setState { AddPropertyState(types = types) }
    }

    private companion object {
        const val TAG = "SR-AddProperty"
    }
}
