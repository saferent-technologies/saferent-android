package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewLocksTest {
    private val flat101 = PendingReview("l1", unitId = "u101")
    private val bedB1 = PendingReview("l2", unitId = "r1", bedId = "b1")
    private val whole = PendingReview("l3")

    @Test fun `only the same flat is blocked`() {
        assertEquals(flat101, ReviewLocks.dueFor(listOf(flat101), "u101", null))
        assertNull(ReviewLocks.dueFor(listOf(flat101), "u102", null))
    }

    @Test fun `only the same bed is blocked`() {
        assertEquals(bedB1, ReviewLocks.dueFor(listOf(bedB1), "r1", "b1"))
        assertNull(ReviewLocks.dueFor(listOf(bedB1), "r1", "b2"))
    }

    @Test fun `whole property lease blocks whole property invite only`() {
        assertEquals(whole, ReviewLocks.dueFor(listOf(whole), null, null))
        assertNull(ReviewLocks.dueFor(listOf(whole), "u101", null))
    }
}
