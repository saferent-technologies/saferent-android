package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerLocationTest {
    private val geo = GeoAddress(addressLine = "12, MG Road", locality = "Madhapur", city = "Hyderabad", state = "Telangana", pincode = "500081")

    @Test fun fillsEverything() {
        val d = OwnerRules.withLocation(OwnerDetails(), geo, onlyEmpty = true)
        assertEquals("12, MG Road, Madhapur", d.address)
        assertEquals("Hyderabad", d.city)
        assertEquals("Telangana", d.state)
        assertEquals("500081", d.pincode)
    }

    @Test fun autoKeepsTyped() {
        val d = OwnerRules.withLocation(OwnerDetails(city = "Secunderabad"), geo, onlyEmpty = true)
        assertEquals("Secunderabad", d.city)
        assertEquals("500081", d.pincode)
        assertEquals("Hyderabad", OwnerRules.withLocation(OwnerDetails(city = "Secunderabad"), geo, onlyEmpty = false).city)
    }

    @Test fun states() {
        assertEquals("Odisha", OwnerRules.stateOf("Orissa"))
        assertEquals("Delhi", OwnerRules.stateOf("NCT of Delhi"))
        assertEquals("Tamil Nadu", OwnerRules.stateOf("tamil nadu"))
        assertNull(OwnerRules.stateOf("California"))
    }

    @Test fun badPincodeSkipped() = assertEquals("", OwnerRules.withLocation(OwnerDetails(), geo.copy(pincode = "5000"), onlyEmpty = true).pincode)

    @Test fun empty() = assertTrue(OwnerRules.addressEmpty(OwnerDetails()))
}
