package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.SafeRentJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyRulesTest {

    // ---- DynamicFieldForm validation ----

    private val fields = listOf(
        FieldDef("name", "Apartment name", FieldType.TEXT, required = true, maxLength = 10),
        FieldDef("totalFloors", "Total floors", FieldType.INTEGER, required = true, min = 1.0, max = 200.0),
        FieldDef("parking", "Parking", FieldType.SELECT, options = listOf("YES", "NO")),
        FieldDef("parkingSlots", "Parking slots", FieldType.INTEGER, requiredWhen = RequiredWhen("parking", "YES")),
        FieldDef("rent", "Rent", FieldType.MONEY, min = 0.0),
    )

    @Test
    fun `required fields`() {
        val errors = FieldValidator.validate(fields, emptyMap())
        assertEquals(setOf("name", "totalFloors"), errors.keys)
        assertEquals(FieldError.Required, errors["name"])
    }

    @Test
    fun `requiredWhen only when the other field has that value`() {
        val base = mapOf("name" to "Sai", "totalFloors" to "5")
        assertTrue(FieldValidator.validate(fields, base + ("parking" to "NO")).isEmpty())
        assertEquals(FieldError.Required, FieldValidator.validate(fields, base + ("parking" to "YES"))["parkingSlots"])
    }

    @Test
    fun `min max maxLength and numbers`() {
        val errors = FieldValidator.validate(
            fields,
            mapOf("name" to "Sai Residency Towers", "totalFloors" to "201", "rent" to "-5"),
        )
        assertEquals(FieldError.TooLong(10), errors["name"])
        assertEquals(FieldError.AboveMax(200.0), errors["totalFloors"])
        assertEquals(FieldError.BelowMin(0.0), errors["rent"])
        assertEquals(FieldError.NotANumber, FieldValidator.validate(fields, mapOf("name" to "a", "totalFloors" to "2.5"))["totalFloors"])
        assertEquals(FieldError.BelowMin(1.0), FieldValidator.validate(fields, mapOf("name" to "a", "totalFloors" to "0"))["totalFloors"])
    }

    // ---- charge rules ----

    @Test
    fun `charges - amount required or not allowed by billing`() {
        val errors = ChargeRules.validate(
            listOf(
                Charge("MAINTENANCE", "FIXED_MONTHLY"), // needs amount
                Charge("WATER", "INCLUDED_IN_RENT", amount = "200"), // must not have one
                Charge("ELECTRICITY", "USAGE_BASED"), // fine without
                Charge("PARKING", "ONE_TIME", amount = "500"), // fine
                Charge("GAS", "NOT_DISCLOSED"), // fine
            ),
        )
        assertEquals(mapOf(0 to ChargeError.AMOUNT_REQUIRED, 1 to ChargeError.AMOUNT_NOT_ALLOWED), errors)
    }

    // ---- submit 412 → checklist mapped to steps ----

    @Test
    fun `submit 412 problems map to their steps`() {
        val error = AppError.Server(
            httpCode = 412,
            errorCode = "INVALID_STATE",
            message = "Complete the required details before submitting.",
            details = """{"problems":["Add the PIN code.","Add at least one photo.","Add the rent.","Flat 302: Bedrooms required."]}""",
        ).toPropertyError(SafeRentJson.instance) as AppError.InvalidState

        assertEquals(
            listOf(WizardStep.LOCATION, WizardStep.PHOTOS, WizardStep.PRICING, WizardStep.UNITS),
            error.problems.map(SubmitProblems::stepFor),
        )
    }

    @Test
    fun `400 issues and 409 version conflict`() {
        val json = SafeRentJson.instance
        val issues = AppError.Server(400, "VALIDATION_ERROR", message = "Check the details",
            details = """{"issues":[{"path":"pincode","message":"Enter a 6-digit PIN code."},{"path":"building.totalFloors","message":"Too many"}]}""")
            .toPropertyError(json) as AppError.FieldIssues
        assertEquals("Enter a 6-digit PIN code.", issues.issues["pincode"])
        assertEquals("Too many", issues.issues["totalFloors"])

        val conflict = AppError.Server(409, "CONFLICT", reason = "VERSION_CONFLICT", message = "Updated elsewhere").toPropertyError(json) as AppError.Conflict
        assertTrue(conflict.versionConflict)
    }

    @Test
    fun `pincode`() {
        assertTrue(PropertyForms.isValidPincode("500081"))
        assertTrue(!PropertyForms.isValidPincode("050081") && !PropertyForms.isValidPincode("50008"))
    }
}

class BuildingParkingHiddenTest {
    @org.junit.Test fun `parking fields are not asked on the building step`() {
        org.junit.Assert.assertTrue(FieldOverrides.hiddenInBuilding(FieldDef(key = "parkingAvailable", label = "parkingAvailable", type = FieldType.BOOLEAN)))
        org.junit.Assert.assertFalse(FieldOverrides.hiddenInBuilding(FieldDef(key = "liftAvailable", label = "liftAvailable", type = FieldType.BOOLEAN)))
    }
}

class BuildingLimitsTest {
    @org.junit.Test fun `apartment total floors must be at least 1`() {
        val f = BuildingLimits.forFlats(FieldDef("totalFloors", "Total floors", FieldType.INTEGER))
        org.junit.Assert.assertTrue(f.required)
        org.junit.Assert.assertEquals(FieldError.BelowMin(1.0), FieldValidator.validate(listOf(f), mapOf("totalFloors" to "0"))["totalFloors"])
        org.junit.Assert.assertEquals(FieldError.Required, FieldValidator.validate(listOf(f), emptyMap())["totalFloors"])
    }

    @org.junit.Test fun `4 flats in the building - a 5th can't be added`() {
        val building = mapOf("totalFlats" to "4")
        org.junit.Assert.assertTrue(BuildingLimits.canAddUnit(building, 3))
        org.junit.Assert.assertFalse(BuildingLimits.canAddUnit(building, 4))
        org.junit.Assert.assertTrue(BuildingLimits.canAddUnit(emptyMap(), 10)) // no total → no cap
        org.junit.Assert.assertEquals("totalFlats" to FieldError.BelowMin(5.0), BuildingLimits.unitsError(building, 5))
    }

    @Test fun `water availability is hours a day, digits 0 to 24`() {
        val water = PropertyForms.utilWater(null).first { it.key == "waterAvailability" }
        org.junit.Assert.assertTrue(water.digitsOnly)
        org.junit.Assert.assertTrue(FieldValidator.validate(listOf(water), mapOf("waterAvailability" to "24")).isEmpty())
        org.junit.Assert.assertEquals(FieldError.AboveMax(24.0), FieldValidator.validate(listOf(water), mapOf("waterAvailability" to "30"))["waterAvailability"])
    }
}
