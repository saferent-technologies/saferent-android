package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleLookupTest {
    @Test fun `phones in any common format`() {
        assertEquals(LookupQuery.Phone("9876543210"), PeopleLookup.parse("+91 98765 43210"))
        assertEquals(LookupQuery.Phone("9876543210"), PeopleLookup.parse("09876543210"))
        assertNull(PeopleLookup.parse("12345"))
        assertNull(PeopleLookup.parse("5876543210"))
    }

    @Test fun `safe rent ids`() {
        assertEquals(LookupQuery.SafeRentId("SR-T-0012"), PeopleLookup.parse(" sr-t-0012 "))
        assertNull(PeopleLookup.parse("ab"))
    }
}

class PeopleLookupInputTest {
    @Test fun `phone box keeps 10 digits only`() {
        org.junit.Assert.assertEquals("9876543210", PeopleLookup.cleanInput("98765432101"))
        org.junit.Assert.assertEquals("98765", PeopleLookup.cleanInput("98 765"))
        org.junit.Assert.assertEquals("SR-T-0012", PeopleLookup.cleanInput("SR-T-0012"))
    }
}
