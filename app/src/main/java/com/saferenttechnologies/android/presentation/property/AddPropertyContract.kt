package com.saferenttechnologies.android.presentation.property

import com.saferenttechnologies.android.domain.model.UnitPricing
import com.saferenttechnologies.android.domain.model.isLiveListing
import com.saferenttechnologies.android.domain.model.PropertyRevision
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AmenityGroup
import com.saferenttechnologies.android.domain.model.Charge
import com.saferenttechnologies.android.domain.model.ChargeError
import com.saferenttechnologies.android.domain.model.Completeness
import com.saferenttechnologies.android.domain.model.FieldError
import com.saferenttechnologies.android.domain.model.PhotoRequirements
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.RequiredPhoto
import com.saferenttechnologies.android.domain.model.PropertyDocument
import com.saferenttechnologies.android.domain.model.PropertyStructure
import com.saferenttechnologies.android.domain.model.PropertyTypeDef
import com.saferenttechnologies.android.domain.model.PropertyTypes
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.domain.repository.PhotoUpload
import com.saferenttechnologies.android.domain.repository.PhotoUploadSummary

/** Which form a field change belongs to. */
enum class FormScope { BASIC, ADDRESS, BUILDING, UNIT, ROOM, PRICING, UTILITIES, POLICIES }

enum class CameraMode { PHOTO, DOCUMENT }

/** Whose 360 photo is being taken: a flat / portion / shop (unitId), a PG room (roomId), or the building. */
data class PanoramaTarget(val unitId: String? = null, val roomId: String? = null)

/** Why the GPS step needs the user's attention. */
enum class LocationNote { LOW_ACCURACY, NO_FIX, LOOKUP_UNAVAILABLE }

/** Banner texts that come from strings.xml (server messages are shown as they are). */
enum class AddPropertyMessage {
    NETWORK_OR_SERVER, VERSION_CONFLICT, MAX_PHOTOS, UNITS_LIMIT, PHOTO_REQUIRED, UNIT_REQUIRED, PIN_INVALID,
    REQUIRED_PHOTOS_MISSING, WAIT_FOR_UPLOADS, CHANGES_LOCKED, UNIT_OCCUPIED,
    NOT_PANORAMA, PANORAMA_UNREADABLE, VIDEO_REQUIRED,
}

/** Add / edit one flat (units) or room (PG). */
/** Apartment: one flat's own amenities + photos (opened from its card on the Flats step). */
data class FlatPanel(
    /** The unit (flat / portion / shop / villa) — or [roomId] for a PG / hostel room. */
    val unitId: String? = null,
    val roomId: String? = null,
    /** key → YES / NO / NOT_DISCLOSED (missing = not set). */
    val amenities: Map<String, String> = emptyMap(),
    val custom: List<String> = emptyList(),
    val saved: Boolean = false,
)

data class FormEditor(
    val id: String? = null,
    val version: Int = 1,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
)

/** Duplex floors (strings while typing). */
data class FloorRow(
    val floorNumber: String = "0",
    val bedrooms: String = "",
    val bathrooms: String = "",
    val kitchens: String = "",
    val livingRooms: String = "",
)

data class AddPropertyState(
    val isOpen: Boolean = false,
    /**
     * Opened from "Edit" on a section (e.g. step 5) → Back on that step (or before it) closes the
     * wizard instead of walking 4 → 3 → 2 → 1. Null for add / continue draft.
     */
    val editFromStep: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Non-null → "Continue draft / Start new" sheet. */
    val drafts: List<Property>? = null,
    val step: Int = WizardStep.CATEGORY,
    val types: PropertyTypes? = null,
    val selectedTypeKey: String? = null,
    val listingType: String = "RENT",
    val property: Property? = null,

    val basic: Map<String, String> = emptyMap(),
    val address: Map<String, String> = emptyMap(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locating: Boolean = false,
    val locationNote: LocationNote? = null,
    val building: Map<String, String> = emptyMap(),

    val unitEditor: FormEditor? = null,
    val floors: List<FloorRow> = emptyList(),
    val floorsUnitId: String? = null,
    val roomEditor: FormEditor? = null,
    /** room id → (bed number, rent) being added. */
    val bedDraft: Triple<String, String, String>? = null,

    val pricing: Map<String, String> = emptyMap(),
    val charges: List<Charge> = emptyList(),
    val chargeErrors: Map<Int, ChargeError> = emptyMap(),

    val utilities: Map<String, String> = emptyMap(),
    val policies: Map<String, String> = emptyMap(),
    val rules: List<Pair<String, String>> = emptyList(),
    /** Saved utilities / policies were loaded for this property id (pre-fill once). */
    val optionalLoadedFor: String? = null,
    val amenityGroups: List<AmenityGroup> = emptyList(),
    /** amenity key → "YES" */
    val amenities: Map<String, String> = emptyMap(),
    val customAmenities: List<String> = emptyList(),

    val imageCategory: String = "FRONT_VIEW",
    val imageSlot: Int = 1,
    /** Required photos already shot in this session (shown Done right away, before the server has them). */
    val requiredShot: Set<RequiredPhotoKey> = emptySet(),
    val imageUnitId: String? = null,
    val imageRoomId: String? = null,
    val uploads: PhotoUploadSummary = PhotoUploadSummary(),
    val maxImages: Int = 40,

    val docType: String = "OWNERSHIP_PROOF",
    val documents: List<PropertyDocument> = emptyList(),
    /** Retake of an existing document. */
    val replacingDocId: String? = null,
    val uploadingDocument: Boolean = false,

    val camera: CameraMode? = null,
    /** SafeRent 360 camera open for this flat / room (both null = the whole building). */
    val panoramaFor: PanoramaTarget? = null,
    /** Video recorder open for this flat / room / building. */
    val videoFor: PanoramaTarget? = null,
    /** Submit for review tapped but the owner isn't verified → "Verify identity / Contact support" sheet. */
    val verifyOwnerSheet: Boolean = false,
    /** Submit tapped while the owner's verification is under review → "In review" sheet (can still submit). */
    val ownerInReviewSheet: Boolean = false,
    /** Recorded videos on their way to the server (uploading / uploaded / failed). */
    val videoUploads: List<com.saferenttechnologies.android.domain.repository.VideoUpload> = emptyList(),
    /** Flats still without a video tour (submit blocked): numbers for the banner. */
    val missingVideoUnits: List<String> = emptyList(),
    /** Same flats by id (from the app's check or the server's 412 VIDEO_REQUIRED) → their video slots turn red. */
    val missingVideoUnitIds: Set<String> = emptySet(),
    /** "Delete this video tour?" for this video id. */
    val deleteVideoId: String? = null,
    /** Flat panel "Furniture & appliances" tapped → the host opens the register for this place. */
    val openFurniture: PanoramaTarget? = null,
    val completeness: Completeness? = null,
    /** Submit 412: problem → step. */
    val problems: List<Pair<String, Int>> = emptyList(),
    val submitted: Boolean = false,
    /** "Are you sure you want to submit?" popup. */
    val confirmSubmit: Boolean = false,
    /** Ownership declaration not made (or the text changed) → shown before submitting. */
    val declaration: com.saferenttechnologies.android.domain.model.OwnershipDeclaration? = null,
    val declareAgree: Boolean = false,
    val declaring: Boolean = false,
    /** 403: a manager can't declare → "Waiting for the owner". */
    val declarationOwnerOnly: Boolean = false,
    /** Change request of a live listing (from GET property detail). */
    val revision: PropertyRevision? = null,
    /** Change is being verified → wizard is read-only. */
    val locked: Boolean = false,
    /** Apartment: the flat whose amenities / photos are being edited. */
    val flatPanel: FlatPanel? = null,

    val fieldErrors: Map<String, FieldError> = emptyMap(),
    /** Server text (wins) or a local message. */
    val bannerText: String? = null,
    val bannerMessage: AddPropertyMessage? = null,
) : UiState {
    val type: PropertyTypeDef? get() = types?.type(selectedTypeKey ?: property?.propertyType)
    val structure: PropertyStructure get() = type?.structure ?: property?.structure ?: PropertyStructure.MULTI_UNIT
    val busy: Boolean get() = loading || saving
    val imageCount: Int get() = (property?.images?.size ?: 0) + uploads.running

    /** BHK → one bedroom photo per bedroom (mandatory). */
    /** Editing a property that already went past draft (approved / live / rejected …). */
    val isEditingSubmitted: Boolean get() = property != null && property.publicationStatus != "DRAFT"

    /**
     * Only a draft or a rejected property can be (re)submitted. Approved / live / under-verification
     * edits are saved step by step — the server decides if they need verification again.
     */
    val needsSubmit: Boolean get() = property == null || property.publicationStatus in setOf("DRAFT", "REJECTED") || isLiveEdit

    /** Apartment = building + flats (rent / amenities / photos on each flat). Other types unchanged. */
    val isUnitPriced: Boolean
        get() = UnitPricing.isUnitPriced(type?.key ?: property?.propertyType, building["villaListing"] ?: property?.building?.get("villaListing"))

    /** PG / hostel: rent per bed, deposit, amenities, photos on each room. */
    val isRoomPriced: Boolean get() = UnitPricing.isRoomPriced(structure)

    /** "Flat" / "Portion" / "Shop" / "Villa" / "Room". */
    val unitWord: String get() = UnitPricing.unitWord(type?.key ?: property?.propertyType, type?.unitLabel, structure)

    /** APPROVED / PUBLISHED: edits go to a change request; the listing stays live until it's approved. */
    val isLiveEdit: Boolean get() = property != null && isLiveListing(property.publicationStatus)

    /** Apartments: Front view is mandatory too. */
    /** Buildings (apartment, independent building, complex, villa community, PG): front view is mandatory. */
    private val needsFrontView: Boolean get() = type?.isFlat == true || isUnitPriced || isRoomPriced
    /** A shop with no room counts needs one inside photo ("Other"), a home its living room. */
    private val insideCategory: String
        get() = if ((type?.category ?: "").equals("COMMERCIAL", true)) "OTHER" else PhotoRequirements.LIVING_ROOM
    val missingRequiredPhotos: List<RequiredPhoto>
        get() = property?.let { PhotoRequirements.missing(it, needsFrontView, insideCategory) }.orEmpty()
            .filterNot { RequiredPhotoKey(it.category, it.slot, it.unitId, it.roomId) in requiredShot }
    val requiredPhotos: List<RequiredPhoto> get() = property?.let { PhotoRequirements.required(it, needsFrontView, insideCategory) }.orEmpty()
    val failedUploads: List<PhotoUpload> get() = uploads.failed
    /** Type can't change once the draft exists. */
    val typeLocked: Boolean get() = property != null
}

sealed interface AddPropertyIntent : UiIntent {
    data object Open : AddPropertyIntent
    /** Edit an existing property (from its detail screen) at [step]. */
    /** [newUnit] = open on the Units step with a blank "add flat" editor (property screen → Add flat). */
    data class Edit(val propertyId: String, val step: Int, val newUnit: Boolean = false) : AddPropertyIntent
    /** Draft card "Edit" → open the wizard where the landlord left it (property.wizardStep). */
    data class ResumeDraft(val propertyId: String) : AddPropertyIntent
    data class ContinueDraft(val id: String) : AddPropertyIntent
    data object StartNew : AddPropertyIntent
    data object DismissDrafts : AddPropertyIntent
    data object SaveAndExit : AddPropertyIntent
    data object Close : AddPropertyIntent
    data object Back : AddPropertyIntent
    data object Next : AddPropertyIntent
    data object Skip : AddPropertyIntent
    data class GoToStep(val step: Int) : AddPropertyIntent
    data object Retry : AddPropertyIntent
    data object DismissBanner : AddPropertyIntent

    data class SelectType(val key: String) : AddPropertyIntent
    data class SelectListingType(val value: String) : AddPropertyIntent
    /** A 360 / panorama photo picked from the gallery (taken with the camera's Panorama mode). */
    data class AddPanorama(val uri: String) : AddPropertyIntent
    /** Open SafeRent's 360 camera (straight into panorama capture) for this flat / room / building. */
    data class OpenPanoramaCamera(val target: PanoramaTarget) : AddPropertyIntent
    data class PanoramaCaptured(val filePath: String) : AddPropertyIntent
    data object ClosePanoramaCamera : AddPropertyIntent
    /** Record a video tour of this flat / room / building (in-app, watermarked). */
    data class OpenVideoCamera(val target: PanoramaTarget) : AddPropertyIntent
    data class VideoRecorded(val filePath: String, val durationSec: Int) : AddPropertyIntent
    data object CloseVideoCamera : AddPropertyIntent
    data object DismissVerifyOwner : AddPropertyIntent
    /** "In review" sheet → submit anyway. */
    data object SubmitWhileInReview : AddPropertyIntent
    data class RetryVideo(val upload: com.saferenttechnologies.android.domain.repository.VideoUpload) : AddPropertyIntent
    data class AskDeleteVideo(val videoId: String) : AddPropertyIntent
    data object ConfirmDeleteVideo : AddPropertyIntent
    data object DismissDeleteVideo : AddPropertyIntent
    data class OpenFurniture(val target: PanoramaTarget) : AddPropertyIntent
    data object FurnitureOpened : AddPropertyIntent
    data class FieldChanged(val scope: FormScope, val key: String, val value: String) : AddPropertyIntent

    data object UseMyLocation : AddPropertyIntent

    data object NewUnit : AddPropertyIntent
    data class EditUnit(val id: String) : AddPropertyIntent
    data object SaveUnit : AddPropertyIntent

    // apartment: a flat's amenities + photos
    data class OpenFlatPanel(val unitId: String) : AddPropertyIntent
    data class OpenRoomPanel(val roomId: String) : AddPropertyIntent
    data object CloseFlatPanel : AddPropertyIntent
    /** Not set → YES → NO → NOT_DISCLOSED → not set. */
    data class CycleFlatAmenity(val key: String) : AddPropertyIntent
    data class AddFlatCustom(val label: String) : AddPropertyIntent
    data class RemoveFlatCustom(val index: Int) : AddPropertyIntent
    data object SaveFlatAmenities : AddPropertyIntent
    data object CancelEditor : AddPropertyIntent
    data class DeleteUnit(val id: String) : AddPropertyIntent
    data class EditFloors(val unitId: String) : AddPropertyIntent
    data object AddFloor : AddPropertyIntent
    data class FloorChanged(val index: Int, val row: FloorRow) : AddPropertyIntent
    data class RemoveFloor(val index: Int) : AddPropertyIntent
    data object SaveFloors : AddPropertyIntent

    data object NewRoom : AddPropertyIntent
    data class EditRoom(val id: String) : AddPropertyIntent
    data object SaveRoom : AddPropertyIntent
    data class StartBed(val roomId: String) : AddPropertyIntent
    data class BedChanged(val number: String, val rent: String) : AddPropertyIntent
    data object SaveBed : AddPropertyIntent

    data class AddCharge(val kind: String) : AddPropertyIntent
    data class ChargeChanged(val index: Int, val charge: Charge) : AddPropertyIntent
    data class RemoveCharge(val index: Int) : AddPropertyIntent

    data object AddRule : AddPropertyIntent
    data class RuleChanged(val index: Int, val title: String, val description: String) : AddPropertyIntent
    data class RemoveRule(val index: Int) : AddPropertyIntent
    data class ToggleAmenity(val key: String) : AddPropertyIntent
    data class AddCustomAmenity(val label: String) : AddPropertyIntent
    data class RemoveCustomAmenity(val index: Int) : AddPropertyIntent

    data class SelectImageCategory(val category: String) : AddPropertyIntent
    data class SelectImageSlot(val slot: Int) : AddPropertyIntent
    data class SelectImageTarget(val unitId: String?, val roomId: String?) : AddPropertyIntent
    /** Tap a missing "Bedroom N" → camera set up for exactly that photo. */
    data class SelectRequiredPhoto(val unitId: String?, val slot: Int, val category: String, val roomId: String? = null) : AddPropertyIntent
    data class OpenCamera(val mode: CameraMode, val replaceDocId: String? = null) : AddPropertyIntent
    data object CloseCamera : AddPropertyIntent
    data class Captured(val filePath: String) : AddPropertyIntent
    data class DeleteImage(val id: String) : AddPropertyIntent
    data class SetCover(val id: String) : AddPropertyIntent
    data class RetryUpload(val upload: PhotoUpload) : AddPropertyIntent
    data class SelectDocType(val type: String) : AddPropertyIntent

    /** Last step button → checks, then the confirm popup. */
    data object Submit : AddPropertyIntent
    data object ConfirmSubmit : AddPropertyIntent
    data class DeclarationAgree(val agree: Boolean) : AddPropertyIntent
    /** POST ownership-declaration, then submit. */
    data object Declare : AddPropertyIntent
    data object DismissDeclaration : AddPropertyIntent
    data object DismissSubmit : AddPropertyIntent
    data object Done : AddPropertyIntent
}

sealed interface AddPropertyEffect : UiEffect

/** Which checklist row a photo was taken for. */
data class RequiredPhotoKey(val category: String, val slot: Int, val unitId: String?, val roomId: String? = null)
