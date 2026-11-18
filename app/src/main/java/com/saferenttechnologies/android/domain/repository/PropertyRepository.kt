package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AmenityGroup
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Charge
import com.saferenttechnologies.android.domain.model.Completeness
import com.saferenttechnologies.android.domain.model.FieldDef
import com.saferenttechnologies.android.domain.model.GeoAddress
import com.saferenttechnologies.android.domain.model.GeoFix
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyDocument
import com.saferenttechnologies.android.domain.model.PropertyTypes
import kotlinx.coroutines.flow.Flow

/** Form values with their field definitions (so numbers / booleans go out with the right JSON type). */
data class TypedValues(val fields: List<FieldDef>, val values: Map<String, String>)

/** PATCH properties/{id}. Null = not sent. */
data class PropertyPatch(
    val version: Int,
    val wizardStep: Int,
    val title: String? = null,
    val description: String? = null,
    val listingType: String? = null,
    val addressLine: String? = null,
    val locality: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val building: TypedValues? = null,
)

/** PUT pricing (property or one unit): rent, deposit and terms (rentals only). */
data class PricingInput(val values: TypedValues, val charges: List<Charge>)

/** A duplex floor (PUT units/{id}/floors). */
data class FloorInput(val floorNumber: Int, val bedrooms: Int, val bathrooms: Int, val kitchens: Int, val livingRooms: Int)

interface PropertyRepository {
    suspend fun propertyTypes(): AppResult<PropertyTypes>
    suspend fun drafts(): AppResult<List<Property>>
    suspend fun property(id: String): AppResult<Property>
    suspend fun create(typeKey: String, listingType: String, title: String?): AppResult<Property>
    suspend fun patch(id: String, patch: PropertyPatch): AppResult<Property>

    /** [pricing] = the flat's own rent / deposit (apartment) → sent as "pricing" in the same request. */
    suspend fun addUnit(id: String, unit: TypedValues, pricing: Map<String, String> = emptyMap()): AppResult<Unit>
    suspend fun updateUnit(id: String, unitId: String, version: Int, unit: TypedValues, pricing: Map<String, String> = emptyMap()): AppResult<Unit>
    suspend fun deleteUnit(id: String, unitId: String): AppResult<Unit>
    suspend fun saveFloors(id: String, unitId: String, floors: List<FloorInput>): AppResult<Unit>

    suspend fun addRoom(id: String, room: TypedValues): AppResult<Unit>
    suspend fun updateRoom(id: String, roomId: String, version: Int, room: TypedValues): AppResult<Unit>
    suspend fun addBed(id: String, roomId: String, bedNumber: String, rent: String?): AppResult<Unit>

    suspend fun savePricing(id: String, pricing: PricingInput, unitId: String? = null): AppResult<Unit>
    suspend fun saveUtilities(id: String, values: TypedValues): AppResult<Unit>
    /** Saved utilities (GET …/utilities), as form strings; empty when none yet. */
    suspend fun utilities(id: String): AppResult<Map<String, String>>
    /** Saved guest policies + extra rules (GET …/policies). */
    suspend fun policies(id: String): AppResult<Pair<Map<String, String>, List<Pair<String, String>>>>
    suspend fun savePolicies(id: String, values: TypedValues, rules: List<Pair<String, String>>): AppResult<Unit>
    suspend fun amenities(): AppResult<List<AmenityGroup>>
    /** [unitId] / [roomId] = amenities inside that unit / PG room; both null = the building's (common) amenities. */
    suspend fun saveAmenities(id: String, items: Map<String, String>, custom: List<String>, unitId: String? = null, roomId: String? = null): AppResult<Unit>

    suspend fun deleteImage(id: String, imageId: String): AppResult<Unit>
    suspend fun setImageOrder(id: String, imageId: String, displayOrder: Int): AppResult<Unit>

    suspend fun documents(id: String): AppResult<List<PropertyDocument>>
    suspend fun uploadDocument(id: String, filePath: String, docType: String): AppResult<PropertyDocument>
    suspend fun replaceDocument(docId: String, filePath: String): AppResult<PropertyDocument>

    suspend fun completeness(id: String): AppResult<Completeness>
    suspend fun submit(id: String): AppResult<Property>
}

/** GPS on the phone: FusedLocationProviderClient + Android Geocoder (no backend call). */
interface LocationProvider {
    /** One fresh fix (PRIORITY_HIGH_ACCURACY, 15 s timeout). Null = no fix / no permission. */
    suspend fun currentFix(): GeoFix?

    /** Address for the pin via the device Geocoder. Null = not available → type it by hand. */
    suspend fun addressOf(latitude: Double, longitude: Double): GeoAddress?

    /** Typed place ("Madhapur, Hyderabad") → its spot, via the device Geocoder. Null = not found. */
    suspend fun placeOf(query: String): GeoFix? = null
}

/** Draft id kept so "Save & exit" / process death resumes the same draft. */
interface PropertyDraftStore {
    suspend fun draftId(): String?
    suspend fun saveDraftId(id: String?)
}

/** One captured photo waiting for / failing upload (the file is kept until it's uploaded). */
data class PhotoUpload(
    val workId: String,
    val filePath: String,
    val category: String,
    val slot: Int?,
    val unitId: String?,
    val roomId: String?,
    val message: String? = null,
)

/** Live state of a property's photo uploads. */
data class PhotoUploadSummary(
    val running: Int = 0,
    val failed: List<PhotoUpload> = emptyList(),
    /** Grows on every finished upload → reload the property's photos. */
    val succeeded: Int = 0,
)

/** Queues photo uploads in the background (WorkManager, multipart, network required, retry with backoff). */
interface PhotoUploadQueue {
    fun enqueue(propertyId: String, filePath: String, category: String, slot: Int?, unitId: String?, roomId: String?)
    fun observe(propertyId: String): Flow<PhotoUploadSummary>

    /** Re-queue a failed upload (its old entry is dropped). */
    fun retry(propertyId: String, upload: PhotoUpload)
}

/**
 * Keeps the user's AREA on the server (for search / "homes near you"), only when location is
 * already allowed — never asks for permission itself. Sends at most once a day or after a move.
 */
interface UserLocationSync {
    /** true = sent now. */
    suspend fun sync(role: String?): Boolean
    /** Signed out → forget what was sent (next user starts fresh). */
    fun reset()
}
