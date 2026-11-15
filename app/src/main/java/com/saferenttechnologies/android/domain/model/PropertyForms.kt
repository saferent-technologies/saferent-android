package com.saferenttechnologies.android.domain.model

/**
 * App-side forms (the server doesn't send field lists for these steps). [FieldDef.label] holds the
 * KEY — the UI turns it into a string resource, so no text is hard-coded here.
 */
object PropertyForms {
    private val DISCLOSURE = listOf("YES", "NO", "NOT_APPLICABLE", "NOT_DISCLOSED")

    private fun f(
        key: String,
        type: FieldType,
        required: Boolean = false,
        maxLength: Int? = null,
        min: Double? = null,
        max: Double? = null,
        options: List<String> = emptyList(),
        requiredWhen: RequiredWhen? = null,
    ) = FieldDef(key, key, type, required, maxLength, min, max, options, requiredWhen)

    fun disclosure(types: PropertyTypes?) = types?.enum("disclosure", DISCLOSURE) ?: DISCLOSURE

    fun basic(): List<FieldDef> = listOf(
        f("title", FieldType.TEXT, required = true, maxLength = 150),
        f("description", FieldType.TEXTAREA, maxLength = 5000),
    )

    /** Filled from the location fix only (not editable). */
    val LOCATION_ADDRESS_KEYS = setOf("locality", "city", "state", "pincode")

    fun address(): List<FieldDef> = listOf(
        f("addressLine", FieldType.TEXT, required = true, maxLength = 250),
        f("locality", FieldType.TEXT, maxLength = 120),
        f("city", FieldType.TEXT, required = true, maxLength = 100),
        f("state", FieldType.TEXT, required = true, maxLength = 100),
        f("pincode", FieldType.TEXT, required = true, maxLength = 6),
    )

    /** Fallback when a type sends no unitFields. */
    fun defaultUnit(): List<FieldDef> = listOf(f("unitNumber", FieldType.TEXT, required = true, maxLength = 30).copy(digitsOnly = true))

    fun room(types: PropertyTypes?): List<FieldDef> = listOf(
        f("roomNumber", FieldType.TEXT, required = true, maxLength = 30),
        f("floorNumber", FieldType.INTEGER, min = -5.0, max = 200.0),
        f(
            "sharingType", FieldType.SELECT, required = true,
            options = types?.enum("sharingTypes", listOf("SINGLE", "DOUBLE", "TRIPLE", "FOUR_SHARING", "CUSTOM"))
                ?: listOf("SINGLE", "DOUBLE", "TRIPLE", "FOUR_SHARING", "CUSTOM"),
        ),
        f("customSharing", FieldType.INTEGER, min = 1.0, max = 20.0, requiredWhen = RequiredWhen("sharingType", "CUSTOM")),
        f("attachedBathroom", FieldType.DISCLOSURE, options = disclosure(types)),
        f("acAvailable", FieldType.DISCLOSURE, options = disclosure(types)),
        f("rentPerBed", FieldType.MONEY, required = true, min = 0.0),
        f("securityDeposit", FieldType.MONEY, min = 0.0),
    )

    /** SALE → salePrice instead of monthly rent. */
    fun pricing(listingType: String, types: PropertyTypes?): List<FieldDef> =
        if (listingType == "SALE") {
            listOf(f("salePrice", FieldType.MONEY, required = true, min = 0.0))
        } else {
            // Rent is due every month on the tenant's joining date → no "rent due day" any more.
            listOf(
                f("monthlyRent", FieldType.MONEY, required = true, min = 0.0),
                f("securityDeposit", FieldType.MONEY, min = 0.0),
                f("advanceRentMonths", FieldType.INTEGER, min = 0.0, max = 24.0),
                f("lockInPeriodMonths", FieldType.INTEGER, min = 0.0, max = 120.0),
                f("noticePeriodDays", FieldType.INTEGER, min = 0.0, max = 365.0),
                f("rentNegotiable", FieldType.DISCLOSURE, options = disclosure(types)),
                f("lateFeePolicy", FieldType.TEXTAREA, maxLength = 500),
                f("rentIncreasePolicy", FieldType.TEXTAREA, maxLength = 500),
                f("agreementCharges", FieldType.MONEY, min = 0.0),
                f(
                    "agreementChargesPaidBy", FieldType.SELECT,
                    options = listOf("LANDLORD", "TENANT", "SHARED", "NOT_DISCLOSED"),
                    requiredWhen = RequiredWhen("agreementCharges", "*"),
                ).copy(shownWhen = ShownWhen("agreementCharges")),
                f("refundPolicy", FieldType.TEXTAREA, maxLength = 5000),
                // Ticked → tenants may pay a month from the deposit (landlord approves); off → no such option.
                f("depositUsableAsRent", FieldType.BOOLEAN),
                f("otherTerms", FieldType.TEXTAREA, maxLength = 5000),
            )
        }

    /** Rent / deposit / sale price keys — on each flat for an apartment, never on the building. */
    val FLAT_PRICE_KEYS = setOf("monthlyRent", "securityDeposit", "salePrice")

    /** Apartment "Common charges": the pricing step without the flat's own price fields. */
    fun commonPricing(listingType: String, types: PropertyTypes?): List<FieldDef> =
        if (listingType == "SALE") emptyList() else pricing(listingType, types).filterNot { it.key in FLAT_PRICE_KEYS }

    /** Pricing step for a property type (apartment → common charges only). */
    fun pricingFor(apartment: Boolean, listingType: String, types: PropertyTypes?): List<FieldDef> =
        if (apartment) commonPricing(listingType, types) else pricing(listingType, types)

    /** A flat's own price (apartment): rent + deposit both required; SALE → sale price. */
    fun flatPricing(listingType: String): List<FieldDef> =
        if (listingType == "SALE") {
            listOf(f("salePrice", FieldType.MONEY, required = true, min = 0.0))
        } else {
            listOf(
                f("monthlyRent", FieldType.MONEY, required = true, min = 0.0),
                f("securityDeposit", FieldType.MONEY, required = true, min = 0.0),
            )
        }

    // ---------------- Utilities (PUT properties/{id}/utilities) ----------------

    private fun showIf(field: String, vararg values: String) = ShownWhen(field, anyOf = values.toSet())

    fun utilElectricity(types: PropertyTypes?): List<FieldDef> = listOf(
        f("separateElectricityMeter", FieldType.DISCLOSURE, options = disclosure(types)),
        f("meterNumber", FieldType.TEXT, maxLength = 40).copy(shownWhen = showIf("separateElectricityMeter", "YES")),
        f("meterPaymentType", FieldType.SELECT, options = listOf("PREPAID", "POSTPAID", "NOT_DISCLOSED"))
            .copy(shownWhen = showIf("separateElectricityMeter", "YES")),
        f("electricityBillingType", FieldType.SELECT, options = listOf("ACTUAL_BILL", "FIXED", "INCLUDED_IN_RENT", "NOT_APPLICABLE", "NOT_DISCLOSED")),
        f("electricityRatePerUnit", FieldType.DECIMAL, min = 0.0, max = 100.0).copy(shownWhen = showIf("electricityBillingType", "ACTUAL_BILL", "FIXED")),
    )

    fun utilWater(types: PropertyTypes?): List<FieldDef> = listOf(
        f("separateWaterMeter", FieldType.DISCLOSURE, options = disclosure(types)),
        f("waterSupplyType", FieldType.SELECT, options = listOf("MUNICIPAL", "BOREWELL", "TANKER", "MIXED", "NOT_APPLICABLE", "NOT_DISCLOSED")),
        f("waterChargesType", FieldType.SELECT, options = chargeBilling(types)),
        f(
            "waterChargesAmount", FieldType.MONEY, min = 0.0,
            requiredWhen = RequiredWhen("waterChargesType", "FIXED_MONTHLY", setOf("ONE_TIME")),
        ).copy(shownWhen = showIf("waterChargesType", "FIXED_MONTHLY", "ONE_TIME", "USAGE_BASED")),
        f("waterAvailability", FieldType.TEXT, maxLength = 120),
    )

    fun utilGasInternet(types: PropertyTypes?): List<FieldDef> = listOf(
        f("gasConnectionType", FieldType.SELECT, options = listOf("PIPED", "CYLINDER", "NONE", "NOT_DISCLOSED")),
        f("internetAvailable", FieldType.DISCLOSURE, options = disclosure(types)),
        f("internetCharges", FieldType.MONEY, min = 0.0).copy(shownWhen = showIf("internetAvailable", "YES")),
        f("powerBackup", FieldType.DISCLOSURE, options = disclosure(types)),
        f("generatorAvailable", FieldType.DISCLOSURE, options = disclosure(types)),
    )

    fun utilBuilding(types: PropertyTypes?): List<FieldDef> = listOf(
        f("sewageFacilities", FieldType.DISCLOSURE, options = disclosure(types)),
        f("wasteCollection", FieldType.DISCLOSURE, options = disclosure(types)),
        f("notes", FieldType.TEXTAREA, maxLength = 1000),
    )

    fun utilities(types: PropertyTypes?): List<FieldDef> =
        utilElectricity(types) + utilWater(types) + utilGasInternet(types) + utilBuilding(types)

    /** Body extras the server wants with "included in rent" (never an amount then). */
    fun utilityExtras(values: Map<String, String>): Map<String, String> = buildMap {
        if (values["electricityBillingType"] == "INCLUDED_IN_RENT") put("electricityIncludedInRent", "YES")
        if (values["waterChargesType"] == "INCLUDED_IN_RENT") put("waterIncludedInRent", "YES")
    }

    // ---------------- Guests & house rules (PUT properties/{id}/policies) ----------------

    fun policyGuests(types: PropertyTypes?): List<FieldDef> = listOf(
        f("guestsAllowed", FieldType.DISCLOSURE, options = disclosure(types)),
        // No guests → no overnight guests (and no max stay).
        f("overnightGuestsAllowed", FieldType.DISCLOSURE, options = disclosure(types)).copy(shownWhen = ShownWhen("guestsAllowed", noneOf = setOf("NO"))),
        f("maximumGuestStayDays", FieldType.INTEGER, min = 1.0, max = 365.0).copy(shownWhen = showIf("overnightGuestsAllowed", "YES")),
        f("guestRegistrationRequired", FieldType.DISCLOSURE, options = disclosure(types)),
        // Visitors register → ask the visiting hours.
        f("visitorTimingRestrictions", FieldType.TEXT, maxLength = 255).copy(shownWhen = showIf("guestRegistrationRequired", "YES")),
        f("landlordApprovalRequired", FieldType.DISCLOSURE, options = disclosure(types)),
        f("guestParkingAvailable", FieldType.DISCLOSURE, options = disclosure(types)),
        f("guestChargesApplicable", FieldType.DISCLOSURE, options = disclosure(types)),
        f("guestChargeAmount", FieldType.MONEY, min = 0.0, requiredWhen = RequiredWhen("guestChargesApplicable", "YES"))
            .copy(shownWhen = showIf("guestChargesApplicable", "YES")),
    )

    /** "Cooking allowed?" only matters for a PG / hostel room. */
    fun policyHouse(types: PropertyTypes?, pg: Boolean = false): List<FieldDef> = listOfNotNull(
        f("petsAllowed", FieldType.DISCLOSURE, options = disclosure(types)),
        f("smokingAllowed", FieldType.DISCLOSURE, options = disclosure(types)),
        f("partiesAllowed", FieldType.DISCLOSURE, options = disclosure(types)),
        f("cookingAllowed", FieldType.DISCLOSURE, options = disclosure(types)).takeIf { pg },
        f("sublettingAllowed", FieldType.DISCLOSURE, options = disclosure(types)),
        f("loudMusicPolicy", FieldType.TEXT, maxLength = 255),
    )

    fun policies(types: PropertyTypes?, pg: Boolean = false): List<FieldDef> = policyGuests(types) + policyHouse(types, pg)

    /** Extra house rule: title 2–80, description ≤ 500, at most 30. */
    const val RULE_TITLE_MAX = 80
    const val RULE_DESCRIPTION_MAX = 500
    const val RULES_MAX = 30

    fun chargeKinds(types: PropertyTypes?) =
        types?.enum("chargeKinds", listOf("MAINTENANCE", "ELECTRICITY", "WATER", "GAS", "PARKING", "INTERNET", "OTHER"))
            ?: listOf("MAINTENANCE", "ELECTRICITY", "WATER", "GAS", "PARKING", "INTERNET", "OTHER")

    fun chargeBilling(types: PropertyTypes?) =
        types?.enum("chargeBilling", listOf("INCLUDED_IN_RENT", "FIXED_MONTHLY", "USAGE_BASED", "ONE_TIME", "NOT_APPLICABLE", "NOT_DISCLOSED"))
            ?: listOf("INCLUDED_IN_RENT", "FIXED_MONTHLY", "USAGE_BASED", "ONE_TIME", "NOT_APPLICABLE", "NOT_DISCLOSED")

    fun chargeFrequency(types: PropertyTypes?) =
        types?.enum("chargeFrequency", listOf("MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY", "ONE_TIME"))
            ?: listOf("MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY", "ONE_TIME")

    fun imageCategories(types: PropertyTypes?) =
        types?.enum("imageCategories", DEFAULT_IMAGE_CATEGORIES) ?: DEFAULT_IMAGE_CATEGORIES

    /** Ownership proof first: one of the first three proves ownership. */
    fun documentTypes(types: PropertyTypes?): List<String> {
        val all = types?.enum("documentTypes", DEFAULT_DOC_TYPES) ?: DEFAULT_DOC_TYPES
        return OWNERSHIP_DOC_TYPES.filter { it in all } + all.filter { it !in OWNERSHIP_DOC_TYPES }
    }

    val OWNERSHIP_DOC_TYPES = listOf("OWNERSHIP_PROOF", "SALE_DEED", "PROPERTY_TAX_RECEIPT")

    /** Inspection / meter shots are taken by SafeRent staff, not offered here. */
    private val DEFAULT_IMAGE_CATEGORIES = listOf(
        "FRONT_VIEW", "ENTRANCE", "LIVING_ROOM", "BEDROOM", "KITCHEN", "BATHROOM", "BALCONY",
        "PARKING", "BUILDING_EXTERIOR", "COMMON_AREA", "OTHER",
    )
    private val DEFAULT_DOC_TYPES = listOf(
        "OWNERSHIP_PROOF", "SALE_DEED", "PROPERTY_TAX_RECEIPT", "ELECTRICITY_BILL",
        "BUILDING_PERMISSION", "RENTAL_AGREEMENT", "LANDLORD_IDENTITY", "OTHER",
    )

    /** 6-digit Indian PIN code. */
    fun isValidPincode(value: String) = Regex("^[1-9][0-9]{5}$").matches(value.trim())

    /** Above this, ask the landlord to move near a window / outside and retry. */
    const val MAX_ACCURACY_METERS = 50f
}
