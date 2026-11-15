package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.RevisionResult
import com.saferenttechnologies.android.domain.model.ActionResult
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplicationDecision
import com.saferenttechnologies.android.domain.model.CreateInvitation
import com.saferenttechnologies.android.domain.model.Invitation
import com.saferenttechnologies.android.domain.model.InvitationCreated
import com.saferenttechnologies.android.domain.model.InvitationLink
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.Page
import com.saferenttechnologies.android.domain.model.PropertyDetail
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertyListItem
import com.saferenttechnologies.android.domain.model.PropertySummary
import com.saferenttechnologies.android.domain.model.RentalApplication
import com.saferenttechnologies.android.domain.model.StatusChange

interface ManagePropertyRepository {
    suspend fun summary(): AppResult<PropertySummary>
    suspend fun list(filter: PropertyFilter, query: String, page: Int): AppResult<Page<PropertyListItem>>
    suspend fun detail(id: String): AppResult<PropertyDetail>

    suspend fun publish(id: String): AppResult<ActionResult>
    suspend fun unpublish(id: String): AppResult<ActionResult>
    suspend fun setActiveStatus(id: String, active: Boolean): AppResult<ActionResult>
    suspend fun submit(id: String): AppResult<ActionResult>

    /** Change request of a live listing: send for verification / throw away. Message + new revision (null after discard). */
    suspend fun submitChanges(id: String): AppResult<RevisionResult>
    suspend fun discardChanges(id: String): AppResult<RevisionResult>

    /** Draft → deleted (or archived when it had activity); otherwise archived. */
    suspend fun archiveOrDelete(id: String): AppResult<ActionResult>

    suspend fun setUnitAvailability(id: String, unitId: String, version: Int, status: String): AppResult<ActionResult>
    suspend fun setBedAvailability(id: String, roomId: String, bedId: String, version: Int, status: String): AppResult<ActionResult>

    suspend fun leases(propertyId: String, active: Boolean): AppResult<List<Lease>>
    /** Landlord: tenants of every property (GET leases without propertyId). */
    suspend fun allLeases(active: Boolean): AppResult<List<Lease>>
    suspend fun invitations(propertyId: String, status: String): AppResult<List<Invitation>>
    suspend fun createInvitation(propertyId: String, input: CreateInvitation): AppResult<InvitationCreated>
    suspend fun renewInvitationLink(invitationId: String, expiresInDays: Int): AppResult<InvitationLink>
    suspend fun revokeInvitation(invitationId: String): AppResult<ActionResult>
    suspend fun applications(propertyId: String, status: String): AppResult<List<RentalApplication>>
    suspend fun decideApplication(applicationId: String, decision: ApplicationDecision): AppResult<ActionResult>
    suspend fun statusHistory(propertyId: String, page: Int): AppResult<Page<StatusChange>>
}
