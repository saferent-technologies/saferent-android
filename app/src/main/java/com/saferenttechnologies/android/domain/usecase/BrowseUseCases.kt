package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.ApplyError
import com.saferenttechnologies.android.domain.model.ApplyFailure
import com.saferenttechnologies.android.domain.model.ApplyInput
import com.saferenttechnologies.android.domain.model.ApplyRules
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.TenantLease
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import javax.inject.Inject

/** Where the tenant starts: an active lease → dashboard, otherwise Explore. */
sealed interface TenantHomeState {
    data class Linked(val lease: TenantLease) : TenantHomeState
    data object Explore : TenantHomeState
}

class GetTenantHomeStateUseCase @Inject constructor(private val repository: BrowseRepository) {
    suspend operator fun invoke(): AppResult<TenantHomeState> = when (val r = repository.activeLease()) {
        is AppResult.Success -> AppResult.Success(r.data?.let { TenantHomeState.Linked(it) } ?: TenantHomeState.Explore)
        is AppResult.Failure -> r
    }
}

class ApplyToListingUseCase @Inject constructor(private val repository: BrowseRepository) {
    sealed interface Result {
        data class Sent(val message: String) : Result
        data class Invalid(val error: ApplyError) : Result
        data class Failed(val failure: ApplyFailure) : Result
    }

    suspend operator fun invoke(propertyId: String, input: ApplyInput, today: SimpleDate = SimpleDate.today()): Result {
        ApplyRules.validate(input, today)?.let { return Result.Invalid(it) }
        return when (val r = repository.apply(propertyId, input)) {
            is AppResult.Success -> Result.Sent(r.data)
            is AppResult.Failure -> Result.Failed(ApplyFailure.from(r.error))
        }
    }
}
