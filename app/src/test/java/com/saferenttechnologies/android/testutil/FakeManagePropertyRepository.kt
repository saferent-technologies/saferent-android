package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.ActionResult
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplicationDecision
import com.saferenttechnologies.android.domain.model.CreateInvitation
import com.saferenttechnologies.android.domain.model.Invitation
import com.saferenttechnologies.android.domain.model.InvitationCreated
import com.saferenttechnologies.android.domain.model.InvitationLink
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.Page
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyDetail
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertyListItem
import com.saferenttechnologies.android.domain.model.PropertyPermissions
import com.saferenttechnologies.android.domain.model.PropertySummary
import com.saferenttechnologies.android.domain.model.RentalApplication
import com.saferenttechnologies.android.domain.model.RevisionResult
import com.saferenttechnologies.android.domain.model.StatusChange
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository

class FakeManagePropertyRepository : ManagePropertyRepository {
    var property = Property(id = "p1", code = "SR-PROP-1", publicationStatus = "APPROVED")
    var actionResult: AppResult<ActionResult> = AppResult.Success(ActionResult("Done"))
    var invitationResult: AppResult<InvitationCreated> = AppResult.Failure(AppError.Unknown())
    var detailCalls = 0
        private set

    override suspend fun summary() = AppResult.Success(PropertySummary())
    override suspend fun list(filter: PropertyFilter, query: String, page: Int) = AppResult.Success(Page(emptyList<PropertyListItem>(), 1, 1, 0))
    override suspend fun detail(id: String): AppResult<PropertyDetail> {
        detailCalls++
        return AppResult.Success(PropertyDetail(property, PropertyPermissions(manage = true)))
    }
    var revisionResult: AppResult<RevisionResult> = AppResult.Success(RevisionResult("Changes sent for verification.", null))
    override suspend fun submitChanges(id: String) = revisionResult
    override suspend fun discardChanges(id: String) = revisionResult
    override suspend fun publish(id: String) = actionResult
    override suspend fun unpublish(id: String) = actionResult
    override suspend fun setActiveStatus(id: String, active: Boolean) = actionResult
    override suspend fun submit(id: String) = actionResult
    override suspend fun archiveOrDelete(id: String) = actionResult
    override suspend fun setUnitAvailability(id: String, unitId: String, version: Int, status: String) = actionResult
    override suspend fun setBedAvailability(id: String, roomId: String, bedId: String, version: Int, status: String) = actionResult
    override suspend fun leases(propertyId: String, active: Boolean) = AppResult.Success(emptyList<Lease>())
    override suspend fun allLeases(active: Boolean) = AppResult.Success(emptyList<Lease>())
    override suspend fun review(id: String, approve: Boolean, withoutProof: Boolean, reason: String): AppResult<com.saferenttechnologies.android.domain.model.ActionResult> =
        AppResult.Success(com.saferenttechnologies.android.domain.model.ActionResult(""))
    override suspend fun invitations(propertyId: String, status: String) = AppResult.Success(emptyList<Invitation>())
    override suspend fun createInvitation(propertyId: String, input: CreateInvitation) = invitationResult
    override suspend fun renewInvitationLink(invitationId: String, expiresInDays: Int): AppResult<InvitationLink> =
        AppResult.Success(InvitationLink("https://x/inv", shareText = "Hi again https://x/inv"))
    override suspend fun revokeInvitation(invitationId: String) = actionResult
    override suspend fun applications(propertyId: String, status: String) = AppResult.Success(emptyList<RentalApplication>())
    override suspend fun decideApplication(applicationId: String, decision: ApplicationDecision) = actionResult
    override suspend fun statusHistory(propertyId: String, page: Int) = AppResult.Success(Page(emptyList<StatusChange>(), 1, 1, 0))
}
