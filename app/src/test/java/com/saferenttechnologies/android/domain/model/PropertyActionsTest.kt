package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.domain.model.PropertyAction.ACTIVATE
import com.saferenttechnologies.android.domain.model.PropertyAction.ARCHIVE
import com.saferenttechnologies.android.domain.model.PropertyAction.CONTACT_SUPPORT
import com.saferenttechnologies.android.domain.model.PropertyAction.DEACTIVATE
import com.saferenttechnologies.android.domain.model.PropertyAction.DELETE_DRAFT
import com.saferenttechnologies.android.domain.model.PropertyAction.EDIT
import com.saferenttechnologies.android.domain.model.PropertyAction.PUBLISH
import com.saferenttechnologies.android.domain.model.PropertyAction.RESUBMIT
import com.saferenttechnologies.android.domain.model.PropertyAction.SUBMIT
import com.saferenttechnologies.android.domain.model.PropertyAction.UNPUBLISH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyActionsTest {
    private fun allowed(pub: String, active: String = "ACTIVE", manage: Boolean = true) = PropertyActions.allowed(pub, active, manage)

    @Test fun `draft - edit, submit, delete draft`() =
        assertEquals(listOf(EDIT, SUBMIT, DELETE_DRAFT), allowed("DRAFT"))

    @Test fun `under verification - no edit`() {
        val a = allowed("PENDING_VERIFICATION")
        assertFalse(EDIT in a)
        assertEquals(listOf(DEACTIVATE, ARCHIVE), a)
    }

    @Test fun `approved - publish, edit (goes to a change request, no warning)`() {
        val a = allowed("APPROVED")
        assertTrue(PUBLISH in a && EDIT in a)
        assertFalse(UNPUBLISH in a)
    }

    @Test fun `published - unpublish, edit`() {
        val a = allowed("PUBLISHED")
        assertTrue(UNPUBLISH in a && EDIT in a)
        assertFalse(PUBLISH in a)
    }

    @Test fun `rejected - edit and resubmit`() {
        val a = allowed("REJECTED")
        assertTrue(EDIT in a && RESUBMIT in a)
    }

    @Test fun `inactive - activate (no publish)`() {
        val a = allowed("APPROVED", active = "INACTIVE")
        assertTrue(ACTIVATE in a)
        assertFalse(PUBLISH in a || DEACTIVATE in a)
    }

    @Test fun `suspended - nothing, archived - contact support only, no manage - read only`() {
        assertTrue(allowed("PUBLISHED", active = "SUSPENDED").isEmpty())
        assertEquals(listOf(CONTACT_SUPPORT), allowed("PUBLISHED", active = "ARCHIVED"))
        assertTrue(allowed("DRAFT", manage = false).isEmpty())
    }

    @Test fun `occupied is never set by hand`() = assertFalse("OCCUPIED" in PropertyActions.MANUAL_AVAILABILITY)

    @Test fun `change being verified - no edit, unsent or rejected change - edit`() {
        assertFalse(EDIT in PropertyActions.allowed("PUBLISHED", "ACTIVE", true, RevisionStatus.PENDING_VERIFICATION))
        assertTrue(EDIT in PropertyActions.allowed("PUBLISHED", "ACTIVE", true, RevisionStatus.DRAFT))
        assertTrue(EDIT in PropertyActions.allowed("PUBLISHED", "ACTIVE", true, RevisionStatus.REJECTED))
    }
}
