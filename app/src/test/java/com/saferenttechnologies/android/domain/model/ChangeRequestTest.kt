package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeRequestTest {
    private val live = Property(id = "p1", publicationStatus = "PUBLISHED")
    private val allCan = PropertyPermissions(manage = true, editRevision = true, submitRevision = true, discardRevision = true)

    private fun detail(status: RevisionStatus?, can: PropertyPermissions = allCan) =
        PropertyDetail(live, can, status?.let { PropertyRevision("rv1", it, listOf("basic", "pricing")) })

    @Test fun `status parsing - unknown values kept as UNKNOWN, blank is no change`() {
        assertEquals(RevisionStatus.PENDING_VERIFICATION, RevisionStatus.of("pending_verification"))
        assertEquals(RevisionStatus.UNKNOWN, RevisionStatus.of("SOMETHING_NEW"))
        assertNull(RevisionStatus.of(""))
        assertNull(RevisionStatus.of(null))
    }

    @Test fun `live listing check`() {
        assertTrue(isLiveListing("PUBLISHED"))
        assertTrue(isLiveListing("APPROVED"))
        assertFalse(isLiveListing("DRAFT"))
        assertFalse(isLiveListing("REJECTED"))
    }

    @Test fun `unsent or rejected change - submit, discard, edit (from the can flags)`() {
        for (s in listOf(RevisionStatus.DRAFT, RevisionStatus.REJECTED)) {
            val d = detail(s)
            assertTrue(PropertyActions.canSubmitChanges(d))
            assertTrue(PropertyActions.canDiscardChanges(d))
            assertTrue(PropertyActions.canEditChanges(d))
        }
        val noFlags = detail(RevisionStatus.DRAFT, PropertyPermissions(manage = true))
        assertFalse(PropertyActions.canSubmitChanges(noFlags))
        assertFalse(PropertyActions.canDiscardChanges(noFlags))
    }

    @Test fun `change being verified - locked`() {
        val d = detail(RevisionStatus.PENDING_VERIFICATION)
        assertFalse(PropertyActions.canSubmitChanges(d))
        assertFalse(PropertyActions.canDiscardChanges(d))
        assertFalse(PropertyActions.canEditChanges(d))
    }

    @Test fun `no change yet - edit allowed, nothing to submit`() {
        val d = detail(null)
        assertTrue(PropertyActions.canEditChanges(d))
        assertFalse(PropertyActions.canSubmitChanges(d))
    }

    @Test fun `list badges per changesStatus`() {
        fun item(status: RevisionStatus?, draft: Boolean = true) = PropertyListItem(id = "p", hasDraftChanges = draft, changesStatus = status)
        assertEquals(ChangeBadge.UNSENT, ChangeBadges.of(item(RevisionStatus.DRAFT)))
        assertNull(ChangeBadges.of(item(RevisionStatus.DRAFT, draft = false)))
        assertEquals(ChangeBadge.IN_REVIEW, ChangeBadges.of(item(RevisionStatus.PENDING_VERIFICATION)))
        assertEquals(ChangeBadge.REJECTED, ChangeBadges.of(item(RevisionStatus.REJECTED)))
        assertNull(ChangeBadges.of(item(RevisionStatus.APPROVED)))
        assertNull(ChangeBadges.of(item(RevisionStatus.DISCARDED)))
        assertNull(ChangeBadges.of(item(null)))
    }
}
