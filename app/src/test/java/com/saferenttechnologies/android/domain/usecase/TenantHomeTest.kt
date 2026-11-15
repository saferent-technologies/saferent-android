package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.ApplyInput
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplyFailure
import com.saferenttechnologies.android.domain.model.BrowseFilters
import com.saferenttechnologies.android.domain.model.ListingDetail
import com.saferenttechnologies.android.domain.model.ListingPage
import com.saferenttechnologies.android.domain.model.LocationSuggestion
import com.saferenttechnologies.android.domain.model.ReviewsPreview
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.model.TenantLease
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantHomeTest {
    private class Repo : BrowseRepository {
        var lease: TenantLease? = null
        var applyResult: AppResult<String> = AppResult.Success("Application sent")
        var applyCalls = 0
        override suspend fun browse(filters: BrowseFilters, page: Int, pageSize: Int): AppResult<ListingPage> = AppResult.Failure(AppError.Unknown())
        override suspend fun locations(query: String): AppResult<List<LocationSuggestion>> = AppResult.Success(emptyList())
        override suspend fun listing(id: String): AppResult<ListingDetail> = AppResult.Failure(AppError.Unknown())
        override suspend fun reviews(propertyId: String): AppResult<ReviewsPreview> = AppResult.Failure(AppError.Unknown())
        override suspend fun apply(propertyId: String, input: ApplyInput): AppResult<String> {
            applyCalls++
            return applyResult
        }
        override suspend fun enquire(propertyId: String, message: String) = AppResult.Success("")
        override suspend fun report(propertyId: String, reason: String, details: String) = AppResult.Success("")
        override suspend fun activeLease(): AppResult<TenantLease?> = AppResult.Success(lease)
        override suspend fun pendingReviews() = AppResult.Success(0)
        override suspend fun myApplications(status: String): AppResult<List<TenantApplication>> = AppResult.Success(emptyList())
        override suspend fun withdraw(applicationId: String) = AppResult.Success("")
    }

    @Test fun `active lease - dashboard, none - explore`() = runTest {
        val repo = Repo()
        assertEquals(AppResult.Success(TenantHomeState.Explore), GetTenantHomeStateUseCase(repo)())
        repo.lease = TenantLease("l1", propertyTitle = "Royals Residency", place = "Flat 302")
        val r = GetTenantHomeStateUseCase(repo)() as AppResult.Success
        assertTrue(r.data is TenantHomeState.Linked)
    }

    @Test fun `apply - invalid never calls the server, 409 maps to already applied`() = runTest {
        val repo = Repo()
        val apply = ApplyToListingUseCase(repo)
        val today = SimpleDate(2026, 11, 14)
        val bad = apply("p1", ApplyInput(unitId = "u1", proposedMoveIn = SimpleDate(2026, 11, 1), occupants = 2), today)
        assertTrue(bad is ApplyToListingUseCase.Result.Invalid)
        assertEquals(0, repo.applyCalls)

        repo.applyResult = AppResult.Failure(AppError.Server(409, message = "You already applied"))
        val dup = apply("p1", ApplyInput(unitId = "u1", proposedMoveIn = SimpleDate(2026, 12, 1), occupants = 2), today)
        assertTrue((dup as ApplyToListingUseCase.Result.Failed).failure is ApplyFailure.AlreadyApplied)
    }
}
