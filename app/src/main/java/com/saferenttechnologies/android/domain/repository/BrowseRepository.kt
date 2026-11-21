package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.ApplyInput
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.BrowseFilters
import com.saferenttechnologies.android.domain.model.ListingDetail
import com.saferenttechnologies.android.domain.model.ListingPage
import com.saferenttechnologies.android.domain.model.LocationSuggestion
import com.saferenttechnologies.android.domain.model.ReviewsPreview
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.model.TenantLease
import kotlinx.coroutines.flow.Flow

/** Tenant side: browse listings, apply / enquire / report, leases, applications, reviews. */
interface BrowseRepository {
    suspend fun browse(filters: BrowseFilters, page: Int, pageSize: Int): AppResult<ListingPage>
    suspend fun locations(query: String): AppResult<List<LocationSuggestion>>
    suspend fun listing(id: String): AppResult<ListingDetail>
    suspend fun reviews(propertyId: String, unitId: String? = null, bedId: String? = null): AppResult<ReviewsPreview>

    /** Server message ("Application sent"). */
    suspend fun apply(propertyId: String, input: ApplyInput): AppResult<String>
    suspend fun enquire(propertyId: String, message: String): AppResult<String>
    suspend fun report(propertyId: String, reason: String, details: String): AppResult<String>

    suspend fun activeLease(): AppResult<TenantLease?>
    /** Count of rentals waiting for a review (GET reviews/pending). */
    suspend fun pendingReviews(): AppResult<Int>
    suspend fun myApplications(status: String): AppResult<List<TenantApplication>>
    suspend fun withdraw(applicationId: String): AppResult<String>
}

/** Last 8 searches (DataStore). */
interface RecentSearchStore {
    val recent: Flow<List<String>>
    suspend fun add(query: String)
    suspend fun clear()
}
