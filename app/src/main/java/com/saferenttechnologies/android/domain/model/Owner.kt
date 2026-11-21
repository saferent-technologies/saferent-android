package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal

/**
 * Landlord "Owner details" (GET / PUT landlord/owner-details). Private: never shown to tenants,
 * never logged. Empty string = not set (sent as null when cleared).
 */
data class OwnerDetails(
    val fullName: String = "",
    val nameLocked: Boolean = false,
    val phoneNumber: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val alternatePhone: String = "",
    /** en | te | hi | ta | kn | ml | mr | bn | gu | ur | or | pa */
    val preferredLanguage: String = "",
    /** CALL | SMS | WHATSAPP | EMAIL | IN_APP */
    val preferredContactMethod: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val emergencyContactRelation: String = "",
) {
    /** Landlord home shows "Complete owner details" while any of these is missing. */
    val incomplete: Boolean
        get() = preferredLanguage.isBlank() || preferredContactMethod.isBlank() || emergencyContactPhone.isBlank()

    /** The editable fields by their API name. */
    fun editable(): Map<String, String> = mapOf(
        "alternatePhone" to alternatePhone,
        "address" to address,
        "city" to city,
        "state" to state,
        "pincode" to pincode,
        "preferredLanguage" to preferredLanguage,
        "preferredContactMethod" to preferredContactMethod,
        "emergencyContactName" to emergencyContactName,
        "emergencyContactPhone" to emergencyContactPhone,
        "emergencyContactRelation" to emergencyContactRelation,
    )
}

/** Local checks for Owner details (the server checks again). Keys = API field names. */
object OwnerRules {
    /** Geocoder names that differ from ours. */
    private val STATE_ALIASES = mapOf("orissa" to "Odisha", "pondicherry" to "Puducherry", "nct of delhi" to "Delhi", "national capital territory of delhi" to "Delhi")

    /** Geocoder's adminArea → one of [STATES] (null when it isn't one). */
    fun stateOf(raw: String): String? {
        val r = raw.trim()
        if (r.length < 3) return null
        STATE_ALIASES[r.lowercase()]?.let { return it }
        return STATES.firstOrNull { it.equals(r, ignoreCase = true) }
            ?: STATES.firstOrNull { r.contains(it, ignoreCase = true) }
    }

    /**
     * Address from the phone's location: street + area, city, state, pincode.
     * [onlyEmpty] (automatic fill on open) keeps anything already typed; the button replaces.
     */
    fun withLocation(d: OwnerDetails, geo: GeoAddress, onlyEmpty: Boolean): OwnerDetails {
        val street = listOf(geo.addressLine, geo.locality).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
            .ifBlank { geo.formattedAddress.trim() }
        val pin = geo.pincode.filter(Char::isDigit).takeIf { it.length == 6 }.orEmpty()
        val state = stateOf(geo.state).orEmpty()
        fun pick(old: String, new: String) = if (new.isBlank() || (onlyEmpty && old.isNotBlank())) old else new
        return d.copy(
            address = pick(d.address, street.take(255)),
            city = pick(d.city, geo.city.trim().take(80)),
            state = pick(d.state, state),
            pincode = pick(d.pincode, pin),
        )
    }

    /** Nothing in the address section yet → fill it from the location automatically. */
    fun addressEmpty(d: OwnerDetails) = d.address.isBlank() && d.city.isBlank() && d.state.isBlank() && d.pincode.isBlank()

    val LANGUAGES = listOf(
        "en" to "English", "te" to "తెలుగు", "hi" to "हिन्दी", "ta" to "தமிழ்", "kn" to "ಕನ್ನಡ", "ml" to "മലയാളം",
        "mr" to "मराठी", "bn" to "বাংলা", "gu" to "ગુજરાતી", "ur" to "اردو", "or" to "ଓଡ଼ିଆ", "pa" to "ਪੰਜਾਬੀ",
    )
    val CONTACT_METHODS = listOf("CALL", "SMS", "WHATSAPP", "EMAIL", "IN_APP")
    val RELATIONS = listOf("Spouse", "Parent", "Sibling", "Child", "Friend")
    val STATES = listOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
        "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
        "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
        "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh",
        "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
    )

    private val PHONE = Regex("^\\+?[1-9]\\d{9,14}$")

    /** Spaces, dashes and brackets are allowed; the digits (with an optional +) must look like a phone number. */
    fun phoneOk(raw: String): Boolean = PHONE.matches(raw.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' })

    /** Only digits for comparing two numbers (+91 98765 43210 == 9876543210). */
    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit).takeLast(10)
        val y = b.filter(Char::isDigit).takeLast(10)
        return x.isNotEmpty() && x == y
    }

    enum class Problem { PHONE, SAME_AS_MOBILE, TOO_LONG, PINCODE, NAME_LENGTH, RELATION_LENGTH }

    fun validate(d: OwnerDetails): Map<String, Problem> = buildMap {
        val alt = d.alternatePhone.trim()
        if (alt.isNotEmpty()) {
            if (!phoneOk(alt)) put("alternatePhone", Problem.PHONE)
            else if (sameNumber(alt, d.phoneNumber)) put("alternatePhone", Problem.SAME_AS_MOBILE)
        }
        if (d.address.trim().length > 255) put("address", Problem.TOO_LONG)
        if (d.city.trim().length > 80) put("city", Problem.TOO_LONG)
        if (d.state.trim().length > 80) put("state", Problem.TOO_LONG)
        val pin = d.pincode.trim()
        if (pin.isNotEmpty() && !(pin.length == 6 && pin.all(Char::isDigit))) put("pincode", Problem.PINCODE)
        val ecName = d.emergencyContactName.trim()
        if (ecName.isNotEmpty() && ecName.length !in 2..120) put("emergencyContactName", Problem.NAME_LENGTH)
        val ecPhone = d.emergencyContactPhone.trim()
        if (ecPhone.isNotEmpty() && !phoneOk(ecPhone)) put("emergencyContactPhone", Problem.PHONE)
        val rel = d.emergencyContactRelation.trim()
        if (rel.isNotEmpty() && rel.length !in 2..40) put("emergencyContactRelation", Problem.RELATION_LENGTH)
    }

    /** PUT body: only the fields that changed (trimmed; empty = null clears it). */
    fun changes(original: OwnerDetails, edited: OwnerDetails): Map<String, String?> {
        val before = original.editable()
        return edited.editable().mapValues { it.value.trim() }
            .filter { (k, v) -> v != before[k].orEmpty().trim() }
            .mapValues { (_, v) -> v.ifEmpty { null } }
    }
}

// ---------------- property ownership ----------------

enum class OwnerType { INDIVIDUAL, JOINT, COMPANY }

enum class AuthorityType { POWER_OF_ATTORNEY, BOARD_RESOLUTION, AUTHORIZATION_LETTER, OTHER }

/** Numbers are kept as typed (strings) so the form can show exactly what was entered. */
data class CoOwner(
    val name: String = "",
    val relation: String = "",
    val phone: String = "",
    val email: String = "",
    val sharePercent: String = "",
)

data class Representative(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val relation: String = "",
    val authorityType: AuthorityType = AuthorityType.POWER_OF_ATTORNEY,
)

/** PUT properties/{id}/ownership always sends this whole object. */
data class Ownership(
    val ownerType: OwnerType = OwnerType.INDIVIDUAL,
    /** SOLE | JOINT. Individual → SOLE, Joint owners → JOINT, Company → chosen. */
    val ownershipType: String = "SOLE",
    val companyName: String = "",
    val ownerSharePercent: String = "",
    val coOwners: List<CoOwner> = emptyList(),
    /** null = nobody manages it for the owner (not allowed for a company). */
    val representative: Representative? = null,
    val updatedAt: String = "",
) {
    val isJoint: Boolean get() = ownershipType == "JOINT"
}

/** GET properties/{id}/ownership. */
data class OwnershipInfo(
    val ownership: Ownership?,
    val powerOfAttorneyUploaded: Boolean = false,
    val canEdit: Boolean = true,
)

/** Local checks for the Ownership form (pure, unit-tested). Keys = the server's issue paths. */
object OwnershipRules {
    const val MAX_CO_OWNERS = 10
    private val HUNDRED = BigDecimal(100)

    /** "50", "33.33" → number; more than 2 decimals or not a number → null. */
    fun percent(raw: String): BigDecimal? {
        val v = raw.trim().toBigDecimalOrNull() ?: return null
        return v.takeIf { it.scale() <= 2 }
    }

    /** Owner's share + every other owner's share; null while one of them isn't a number yet. */
    fun total(o: Ownership): BigDecimal? {
        val mine = percent(o.ownerSharePercent) ?: return null
        return o.coOwners.fold(mine) { sum, c -> sum + (percent(c.sharePercent) ?: return null) }
    }

    fun sharesOk(o: Ownership): Boolean = !o.isJoint || total(o)?.compareTo(HUNDRED) == 0

    /** Picking "who owns it" sets the ownership kind (Individual → sole, Joint owners → joint). */
    fun withOwnerType(o: Ownership, type: OwnerType): Ownership = when (type) {
        OwnerType.INDIVIDUAL -> o.copy(ownerType = type, ownershipType = "SOLE", coOwners = emptyList(), ownerSharePercent = "")
        OwnerType.JOINT -> o.copy(ownerType = type, ownershipType = "JOINT", coOwners = o.coOwners.ifEmpty { listOf(CoOwner()) })
        // A company always has someone acting for it.
        OwnerType.COMPANY -> o.copy(ownerType = type, representative = o.representative ?: Representative(authorityType = AuthorityType.BOARD_RESOLUTION))
    }

    /** What goes to the server: sole ownership never carries co-owners or a share. */
    fun normalized(o: Ownership): Ownership {
        val type = when (o.ownerType) {
            OwnerType.INDIVIDUAL -> "SOLE"
            OwnerType.JOINT -> "JOINT"
            OwnerType.COMPANY -> o.ownershipType
        }
        val joint = type == "JOINT"
        return o.copy(
            ownershipType = type,
            companyName = if (o.ownerType == OwnerType.COMPANY) o.companyName.trim() else "",
            ownerSharePercent = if (joint) o.ownerSharePercent.trim() else "",
            coOwners = if (joint) o.coOwners.map { it.copy(name = it.name.trim(), relation = it.relation.trim(), phone = it.phone.trim(), email = it.email.trim(), sharePercent = it.sharePercent.trim()) } else emptyList(),
            representative = o.representative?.let { r -> r.copy(name = r.name.trim(), phone = r.phone.trim(), email = r.email.trim(), relation = r.relation.trim()) },
        )
    }

    enum class Problem { REQUIRED, COMPANY_NAME, SHARE, SHARES_TOTAL, PHONE, RELATION_LENGTH, NO_CO_OWNERS, TOO_MANY }

    fun validate(raw: Ownership): Map<String, Problem> = buildMap {
        val o = normalized(raw)
        if (o.ownerType == OwnerType.COMPANY && o.companyName.length !in 2..150) put("companyName", Problem.COMPANY_NAME)
        if (o.isJoint) {
            val mine = percent(o.ownerSharePercent)
            if (mine == null || mine <= BigDecimal.ZERO || mine > HUNDRED) put("ownerSharePercent", Problem.SHARE)
            when {
                o.coOwners.isEmpty() -> put("coOwners", Problem.NO_CO_OWNERS)
                o.coOwners.size > MAX_CO_OWNERS -> put("coOwners", Problem.TOO_MANY)
            }
            o.coOwners.forEachIndexed { i, c ->
                if (c.name.isBlank()) put("coOwners.$i.name", Problem.REQUIRED)
                val s = percent(c.sharePercent)
                if (s == null || s <= BigDecimal.ZERO || s > HUNDRED) put("coOwners.$i.sharePercent", Problem.SHARE)
                if (c.phone.isNotBlank() && !OwnerRules.phoneOk(c.phone)) put("coOwners.$i.phone", Problem.PHONE)
            }
            if (!containsKey("coOwners") && !sharesOk(o)) put("coOwners", Problem.SHARES_TOTAL)
        }
        val r = o.representative
        if (o.ownerType == OwnerType.COMPANY && r == null) put("representative", Problem.REQUIRED)
        if (r != null) {
            if (r.name.isBlank()) put("representative.name", Problem.REQUIRED)
            if (r.phone.isBlank()) put("representative.phone", Problem.REQUIRED)
            else if (!OwnerRules.phoneOk(r.phone)) put("representative.phone", Problem.PHONE)
            if (r.relation.isBlank()) put("representative.relation", Problem.REQUIRED)
            else if (r.relation.length !in 2..40) put("representative.relation", Problem.RELATION_LENGTH)
        }
    }

    /** Power of Attorney chosen but its document isn't uploaded yet → warning card. */
    fun needsPoa(o: Ownership?, uploaded: Boolean): Boolean =
        !uploaded && o?.representative?.authorityType == AuthorityType.POWER_OF_ATTORNEY
}
