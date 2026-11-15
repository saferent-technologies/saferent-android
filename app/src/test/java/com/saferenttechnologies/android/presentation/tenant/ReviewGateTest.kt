package com.saferenttechnologies.android.presentation.tenant

import com.saferenttechnologies.android.domain.model.DepositInfo
import com.saferenttechnologies.android.domain.model.DepositRefund
import com.saferenttechnologies.android.domain.model.ReviewDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewGateTest {
    private val rated = ReviewDraft(mapOf("overall" to 4))

    @Test fun `review + deposit answer needed`() {
        assertFalse(ReviewGate().ready(depositAsked = false))
        assertTrue(ReviewGate(draft = rated).ready(depositAsked = false))
        assertFalse(ReviewGate(draft = rated).ready(depositAsked = true))
        assertTrue(ReviewGate(draft = rated, refund = DepositRefund.YES).ready(depositAsked = true))
        assertFalse(ReviewGate(draft = rated, refund = DepositRefund.PARTIAL).ready(depositAsked = true))
        assertTrue(ReviewGate(draft = rated, refund = DepositRefund.PARTIAL, refundAmount = "10000").ready(depositAsked = true))
    }

    @Test fun `deposit usable as rent only when paid and something left`() {
        assertFalse(DepositInfo(amount = "36000", paid = false).canUseForRent)
        assertEquals("36000", DepositInfo(amount = "36000", paid = true).usable)
        assertEquals("18000", DepositInfo(amount = "36000", paid = true, available = "18000").usable)
        assertFalse(DepositInfo(amount = "36000", paid = true, available = "0").canUseForRent)
        assertTrue(DepositInfo(amount = "36000", paid = true, usableAsRent = true).canUseForRent)
        // Landlord didn't allow it → never offered.
        assertFalse(DepositInfo(amount = "36000", paid = true, usableAsRent = false).canUseForRent)
    }

    @Test fun `no deposit question when the deposit was 0`() {
        val pr = com.saferenttechnologies.android.domain.model.PendingReview("le1", depositAmount = "0.00")
        assertFalse(pr.asksDeposit)
        assertFalse(pr.copy(depositAmount = "").asksDeposit)
        assertTrue(pr.copy(depositAmount = "36000").asksDeposit)
    }
}
