package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.GeoAddress
import com.saferenttechnologies.android.domain.model.GeoFix
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyForms
import com.saferenttechnologies.android.domain.model.PropertyTypes
import com.saferenttechnologies.android.domain.model.SubmitProblems
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.PhotoUploadQueue
import com.saferenttechnologies.android.domain.repository.PropertyDraftStore
import com.saferenttechnologies.android.domain.repository.PropertyPatch
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import javax.inject.Inject

class LoadPropertyTypesUseCase @Inject constructor(private val repository: PropertyRepository) {
    suspend operator fun invoke(): AppResult<PropertyTypes> = repository.propertyTypes()
}

/** Drafts for "Continue draft / Start new" — the one saved on this phone first. */
class LoadDraftsUseCase @Inject constructor(
    private val repository: PropertyRepository,
    private val draftStore: PropertyDraftStore,
) {
    suspend operator fun invoke(): AppResult<List<Property>> = when (val r = repository.drafts()) {
        is AppResult.Success -> {
            val saved = draftStore.draftId()
            AppResult.Success(r.data.sortedByDescending { it.id == saved })
        }
        is AppResult.Failure -> r
    }
}

/** POST properties when the type is chosen; the id is kept for "Save & exit" / process death. */
class CreateDraftUseCase @Inject constructor(
    private val repository: PropertyRepository,
    private val draftStore: PropertyDraftStore,
) {
    suspend operator fun invoke(typeKey: String, listingType: String, title: String?): AppResult<Property> =
        repository.create(typeKey, listingType, title).also { if (it is AppResult.Success) draftStore.saveDraftId(it.data.id) }
}

/** PATCH properties/{id} with the latest version + wizardStep (web and app resume at the same step). */
class SaveStepUseCase @Inject constructor(private val repository: PropertyRepository) {
    suspend operator fun invoke(propertyId: String, patch: PropertyPatch): AppResult<Property> = repository.patch(propertyId, patch)
}

sealed interface LocationLookup {
    /** [address] null → the phone couldn't find an address: keep the pin, type the address by hand. */
    data class Found(val fix: GeoFix, val address: GeoAddress?, val lowAccuracy: Boolean) : LocationLookup
    data object NoFix : LocationLookup
}

/** Location step: one fresh GPS fix (FusedLocationProviderClient) → address from the device Geocoder. */
class ReverseGeocodeUseCase @Inject constructor(
    private val location: LocationProvider,
) {
    suspend operator fun invoke(): LocationLookup {
        val fix = location.currentFix() ?: return LocationLookup.NoFix
        val address = location.addressOf(fix.latitude, fix.longitude)
        return LocationLookup.Found(fix, address, lowAccuracy = fix.accuracyMeters > PropertyForms.MAX_ACCURACY_METERS)
    }
}

/** Queues a captured photo, respecting config properties.maxImages. */
class UploadPhotoUseCase @Inject constructor(
    private val queue: PhotoUploadQueue,
    private val config: ConfigRepository,
) {
    /** False = the property already has the maximum number of photos. */
    suspend operator fun invoke(
        propertyId: String,
        currentCount: Int,
        filePath: String,
        category: String,
        slot: Int?,
        unitId: String?,
        roomId: String?,
    ): Boolean {
        if (currentCount >= maxImages()) return false
        queue.enqueue(propertyId, filePath, category, slot, unitId, roomId)
        return true
    }

    suspend fun maxImages(): Int = config.getConfig().properties.maxImages.coerceAtLeast(1)
}

/** Submit for verification. 412 → problems (each mapped to its step by [SubmitProblems]). */
class SubmitPropertyUseCase @Inject constructor(
    private val repository: PropertyRepository,
    private val draftStore: PropertyDraftStore,
) {
    sealed interface Result {
        data class Submitted(val property: Property) : Result
        data class Problems(val message: String, val problems: List<Pair<String, Int>>) : Result
        data class Failed(val error: AppError) : Result
    }

    suspend operator fun invoke(propertyId: String): Result = when (val r = repository.submit(propertyId)) {
        is AppResult.Success -> {
            draftStore.saveDraftId(null)
            Result.Submitted(r.data)
        }
        is AppResult.Failure -> when (val e = r.error) {
            is AppError.InvalidState -> if (e.errorCode == "VIDEO_REQUIRED") Result.Failed(e)
                else Result.Problems(e.message, e.problems.map { it to SubmitProblems.stepFor(it) })
            else -> Result.Failed(e)
        }
    }
}
