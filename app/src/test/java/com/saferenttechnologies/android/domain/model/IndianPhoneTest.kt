package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianPhoneTest {

    @Test
    fun `sanitize keeps 10 digits and strips +91, 0, spaces`() {
        assertEquals("9849012345", IndianPhone.sanitize("98490 12345"))
        assertEquals("9849012345", IndianPhone.sanitize("+91 98490-12345"))
        assertEquals("9849012345", IndianPhone.sanitize("09849012345"))
        assertEquals("9849012345", IndianPhone.sanitize("98490123456789"))
    }

    @Test
    fun `valid only for 10 digits starting 6 to 9`() {
        assertTrue(IndianPhone.isValid("9849012345"))
        assertTrue(IndianPhone.isValid("6000000000"))
        assertFalse(IndianPhone.isValid("5849012345"))
        assertFalse(IndianPhone.isValid("984901234"))
    }

    @Test
    fun `format and E164`() {
        assertEquals("98490 12345", IndianPhone.format("9849012345"))
        assertEquals("984", IndianPhone.format("984"))
        assertEquals("+919849012345", IndianPhone.toE164("9849012345"))
    }
}
