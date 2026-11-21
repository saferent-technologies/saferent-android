package com.saferenttechnologies.android.domain.model

/** A nearby hospital / police station (owner fills it in; tenants with a lease see it). */
data class SafetyPlace(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val distanceKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val isEmpty: Boolean get() = name.isBlank() && phone.isBlank() && address.isBlank() && distanceKm == null
}

data class EmergencyContact(val label: String = "", val phone: String = "")

/** GET / PUT properties/{id}/safety "safety" (null = not answered). */
data class Safety(
    val fireExtinguisher: Boolean? = null,
    val fireExtinguisherCount: Int? = null,
    val fireExtinguisherExpiry: String? = null,
    val smokeDetectors: Boolean? = null,
    val smokeDetectorLocations: String = "",
    val emergencyExits: Int? = null,
    val emergencyStaircase: Boolean? = null,
    val firstAidKit: Boolean? = null,
    val firstAidLocation: String = "",
    val securityName: String = "",
    val securityPhone: String = "",
    val security24x7: Boolean? = null,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val nearestHospital: SafetyPlace = SafetyPlace(),
    val nearestPoliceStation: SafetyPlace = SafetyPlace(),
    val instructions: String = "",
)

/** STRUCTURAL_SAFETY_CERTIFICATE | FIRE_NOC | OCCUPANCY_CERTIFICATE; status APPROVED | PENDING | REJECTED | null. */
data class SafetyCertificate(val docType: String, val uploaded: Boolean = false, val status: String? = null)

data class SafetyData(
    val safety: Safety = Safety(),
    /** VALID | EXPIRING (within 30 days) | EXPIRED | null. */
    val extinguisherState: String? = null,
    val score: Int = 0,
    val certificates: List<SafetyCertificate> = SafetyRules.CERTIFICATES.map { SafetyCertificate(it) },
    val updatedAt: String? = null,
)

/** Browse: features only (no phone numbers, no instructions). security = 24X7 | AVAILABLE | NONE. */
data class ListingSafety(
    val fireExtinguisher: Boolean? = null,
    val fireExtinguisherState: String? = null,
    val smokeDetectors: Boolean? = null,
    val emergencyExits: Int? = null,
    val emergencyStaircase: Boolean? = null,
    val firstAidKit: Boolean? = null,
    val security: String? = null,
    val verifiedCertificates: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = fireExtinguisher == null && smokeDetectors == null && emergencyExits == null && emergencyStaircase == null &&
            firstAidKit == null && security == null && verifiedCertificates.isEmpty()
}

data class Helpline(val label: String, val phone: String)

/** GET leases/{id}/emergency (tenant with an active lease; cached encrypted for offline use). */
data class EmergencyInfo(
    val propertyTitle: String = "",
    val address: String = "",
    val landlordName: String = "",
    val landlordPhone: String = "",
    val securityName: String = "",
    val securityPhone: String = "",
    val security24x7: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList(),
    val helplines: List<Helpline> = SafetyRules.DEFAULT_HELPLINES,
    val hospital: SafetyPlace? = null,
    val police: SafetyPlace? = null,
    val firstAidLocation: String? = null,
    val extinguisherCount: Int? = null,
    val extinguisherExpiry: String? = null,
    val extinguisherState: String? = null,
    val smokeDetectorLocations: String? = null,
    val emergencyExits: Int? = null,
    val emergencyStaircase: Boolean? = null,
    val instructions: String = "",
)

/** Client checks (the server checks again). Error keys = the API's issue paths. */
object SafetyRules {
    val CERTIFICATES = listOf("STRUCTURAL_SAFETY_CERTIFICATE", "FIRE_NOC", "OCCUPANCY_CERTIFICATE")
    const val MAX_CONTACTS = 6
    const val MAX_EXTINGUISHERS = 500
    const val MAX_EXITS = 50
    const val SMOKE_MAX = 200
    const val FIRST_AID_MAX = 120
    const val INSTRUCTIONS_MAX = 2000
    const val NAME_MAX = 120

    /** Always on the tenant's Emergency screen (the server sends its own list too). */
    val DEFAULT_HELPLINES = listOf(
        Helpline("Emergency (all services)", "112"),
        Helpline("Police", "100"),
        Helpline("Fire", "101"),
        Helpline("Ambulance", "108"),
        Helpline("Women helpline", "1091"),
    )

    enum class Problem { EXPIRY_REQUIRED, COUNT, PHONE, SECURITY_NAME, TOO_LONG, CONTACT_LABEL, DISTANCE }

    /** Indian phone (mobile or landline, 6 to 12 digits after +91 / 0), blank = fine. */
    fun phoneOk(text: String): Boolean {
        val d = text.filter { it.isDigit() }
        if (d.isEmpty()) return true
        val n = when {
            text.trim().startsWith("+91") || (d.length == 12 && d.startsWith("91")) -> d.drop(2)
            d.startsWith("0") -> d.drop(1)
            else -> d
        }
        return n.length in 6..11
    }

    /** "98765 43210" → "+919876543210"; landlines / short numbers stay as typed digits ("040…"); blank → null. */
    fun phoneForApi(text: String): String? {
        val d = text.filter { it.isDigit() }
        if (d.isEmpty()) return null
        return when {
            text.trim().startsWith("+") -> "+$d"
            d.length == 10 && d.first() in "6789" -> "+91$d"
            d.length == 12 && d.startsWith("91") -> "+$d"
            else -> d
        }
    }

    fun validate(s: Safety): Map<String, Problem> = buildMap {
        if (s.fireExtinguisher == true) {
            if (s.fireExtinguisherExpiry.isNullOrBlank()) put("fireExtinguisherExpiry", Problem.EXPIRY_REQUIRED)
            s.fireExtinguisherCount?.let { if (it !in 1..MAX_EXTINGUISHERS) put("fireExtinguisherCount", Problem.COUNT) }
        }
        if (s.smokeDetectors == true && s.smokeDetectorLocations.trim().length > SMOKE_MAX) put("smokeDetectorLocations", Problem.TOO_LONG)
        if (s.firstAidKit == true && s.firstAidLocation.trim().length > FIRST_AID_MAX) put("firstAidLocation", Problem.TOO_LONG)
        if (!phoneOk(s.securityPhone)) put("securityPhone", Problem.PHONE)
        if (s.securityPhone.isNotBlank() && s.securityName.isBlank()) put("securityName", Problem.SECURITY_NAME)
        if (s.securityName.trim().length > NAME_MAX) put("securityName", Problem.TOO_LONG)
        s.emergencyContacts.forEachIndexed { i, c ->
            if (c.phone.isBlank() && c.label.isBlank()) return@forEachIndexed
            if (c.phone.isBlank() || !phoneOk(c.phone)) put("emergencyContacts.$i.phone", Problem.PHONE)
            if (c.label.isBlank()) put("emergencyContacts.$i.label", Problem.CONTACT_LABEL)
        }
        listOf("nearestHospital" to s.nearestHospital, "nearestPoliceStation" to s.nearestPoliceStation).forEach { (k, p) ->
            if (!phoneOk(p.phone)) put("$k.phone", Problem.PHONE)
            p.distanceKm?.let { if (it < 0 || it > 500) put("$k.distanceKm", Problem.DISTANCE) }
        }
        if (s.instructions.trim().length > INSTRUCTIONS_MAX) put("instructions", Problem.TOO_LONG)
    }

    /** What is sent: dependent answers only with their "Yes"; empty rows / places dropped. */
    fun clean(s: Safety): Safety = s.copy(
        fireExtinguisherCount = s.fireExtinguisherCount.takeIf { s.fireExtinguisher == true },
        fireExtinguisherExpiry = s.fireExtinguisherExpiry.takeIf { s.fireExtinguisher == true },
        smokeDetectorLocations = if (s.smokeDetectors == true) s.smokeDetectorLocations.trim() else "",
        firstAidLocation = if (s.firstAidKit == true) s.firstAidLocation.trim() else "",
        securityName = s.securityName.trim(),
        emergencyContacts = s.emergencyContacts.map { it.copy(label = it.label.trim()) }.filter { it.label.isNotBlank() || it.phone.isNotBlank() }.take(MAX_CONTACTS),
        instructions = s.instructions.trim(),
    )

    /** Extinguisher chip from the date (the server sends its own state too). EXPIRING = within 30 days. */
    fun extinguisherState(expiry: String?, today: SimpleDate = SimpleDate.today()): String? {
        val d = expiry?.let { SimpleDate.parse(it.take(10)) } ?: return null
        val days = SimpleDateDays.between(today, d)
        return when {
            days < 0 -> "EXPIRED"
            days <= 30 -> "EXPIRING"
            else -> "VALID"
        }
    }

    /** Emergency: 412 LEASE_ENDED / 404 lease not found → no details (and no saved copy). */
    fun leaseGone(e: AppError): Boolean = when (e) {
        is AppError.InvalidState -> true
        is AppError.Server -> e.httpCode == 404 || e.httpCode == 412 || e.errorCode == "LEASE_ENDED"
        else -> false
    }

    /** Google Maps: directions by coordinates, else by name + address. */
    fun directionsUrl(p: SafetyPlace): String? = when {
        p.latitude != null && p.longitude != null -> "https://www.google.com/maps/dir/?api=1&destination=${p.latitude},${p.longitude}"
        p.address.isNotBlank() || p.name.isNotBlank() ->
            "https://www.google.com/maps/dir/?api=1&destination=" + java.net.URLEncoder.encode(listOf(p.name, p.address).filter { it.isNotBlank() }.joinToString(", "), "UTF-8")
        else -> null
    }

    /** Owner "Find nearby": a Maps search (free, no API) near the property. */
    fun nearbySearchUrl(what: String, near: String): String =
        "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(if (near.isBlank()) what else "$what near $near", "UTF-8")
}
