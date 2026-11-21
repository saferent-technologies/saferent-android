package com.saferenttechnologies.android.domain.model

/** Landlord owner verification (GET kyc/me). Never holds the full Aadhaar number. */
data class KycStatus(
    /** NOT_STARTED | IN_PROGRESS | SUBMITTED | APPROVED | REJECTED */
    val stage: String = "NOT_STARTED",
    val note: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null,
    /** setu | mock | off ("off" → DigiLocker not available yet). */
    val provider: String = "off",
    val phoneVerified: Boolean = false,
    val identity: KycIdentity? = null,
    val selfie: Boolean = false,
    val properties: List<KycProperty> = emptyList(),
    val canEdit: Boolean = false,
    val canSubmit: Boolean = false,
    /** Server: the verification screen after sign in was already finished or skipped (same on every phone / reinstall). */
    val promptDone: Boolean = false,
    /** ID types the server can check right now (kyc/me "idTypes"). Empty = server didn't say → show all. */
    val idTypes: Set<IdDocType> = emptySet(),
) {
    fun allows(type: IdDocType): Boolean = idTypes.isEmpty() || type in idTypes
    val isApproved: Boolean get() = stage == "APPROVED"
    val isSubmitted: Boolean get() = stage == "SUBMITTED"
    val isRejected: Boolean get() = stage == "REJECTED"
    val digiLockerOff: Boolean get() = provider.equals("off", true)
    /** Steps ① ② ③ are shown (hidden once approved). */
    val showSteps: Boolean get() = !isApproved
    val identityDone: Boolean get() = identity != null
}

data class KycIdentity(
    val name: String = "",
    val dob: String = "",
    val gender: String = "",
    val aadhaarLast4: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val test: Boolean = false,
    /** EXACT | PARTIAL | MISMATCH (profile name vs Aadhaar name). */
    val nameMatch: String = "EXACT",
    /** AADHAAR_XML | EAADHAAR_PDF | PAN | PASSPORT | DIGILOCKER */
    val documentType: String = "",
)

data class KycProperty(
    val id: String,
    val code: String = "",
    val title: String = "",
    val city: String = "",
    val publicationStatus: String = "",
    /** VERIFIED | PENDING | REJECTED | MISSING */
    val ownership: String = "MISSING",
    val documents: List<KycDocument> = emptyList(),
)

data class KycDocument(val id: String, val docType: String = "", val status: String = "PENDING", val rejectionReason: String? = null)

/** POST kyc/digilocker/start → open [url]; DigiLocker comes back to returnUrl?id=[sessionId]&success=… */
data class DigiLockerSession(val url: String, val sessionId: String, val returnUrl: String = "", val test: Boolean = false)

data class DigiLockerResult(val nameMatch: String? = null, val already: Boolean = false)

/** https://app.saferenttechnologies.com/app/kyc/return?id=…&success=True|False&errMessage=… (pure, unit-tested). */
object KycReturn {
    /** "True" / "False" (any case) → Boolean; missing / other → null (unknown). */
    fun success(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

/** Owner ID documents we accept (identity). Aadhaar ones are checked automatically (UIDAI signature). */
enum class IdDocType(val needsNumber: Boolean, val needsFile: Boolean, val photoSides: Int) {
    /** Aadhaar Paperless Offline e-KYC: ZIP from myAadhaar / Aadhaar app + 4 digit share code. */
    AADHAAR_XML(needsNumber = false, needsFile = true, photoSides = 0),
    /** e-Aadhaar PDF downloaded from UIDAI + its password. */
    EAADHAAR_PDF(needsNumber = false, needsFile = true, photoSides = 0),
    /** PAN card: number + photo of the front. */
    PAN(needsNumber = true, needsFile = false, photoSides = 1),
    /** Passport: number + photos of the first and last page. */
    PASSPORT(needsNumber = true, needsFile = false, photoSides = 2),
}

/** What the landlord filled in for the chosen ID. Files are local paths or content:// links. */
data class IdForm(
    val type: IdDocType,
    val number: String = "",
    /** Aadhaar XML share code or e-Aadhaar PDF password. */
    val secret: String = "",
    val file: String? = null,
    val fileName: String = "",
    /** The picked file wasn't the right kind (a PDF for the Aadhaar ZIP, …) → not kept, error shown. */
    val wrongFile: Boolean = false,
    val front: String? = null,
    val back: String? = null,
    val showErrors: Boolean = false,
)

/** Client checks (the server checks again). Pure, unit-tested. */
object IdRules {
    private val PAN = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
    private val PASSPORT = Regex("^[A-Z][0-9]{7}$")
    private val SHARE_CODE = Regex("^[0-9]{4}$")

    fun cleanNumber(type: IdDocType, input: String): String = input.uppercase().filter { it.isLetterOrDigit() }.take(if (type == IdDocType.PAN) 10 else 8)

    fun numberOk(type: IdDocType, number: String): Boolean = when (type) {
        IdDocType.PAN -> PAN.matches(number)
        IdDocType.PASSPORT -> PASSPORT.matches(number)
        else -> true
    }

    fun secretOk(type: IdDocType, secret: String): Boolean = when (type) {
        IdDocType.AADHAAR_XML -> SHARE_CODE.matches(secret)
        IdDocType.EAADHAAR_PDF -> secret.isNotBlank()
        else -> true
    }

    /** ZIP for offline e-KYC, PDF for e-Aadhaar. Checks the name and the type the phone reports. */
    fun fileOk(type: IdDocType, name: String, mime: String?): Boolean {
        val n = name.lowercase().trim()
        val m = mime.orEmpty().lowercase()
        return when (type) {
            IdDocType.AADHAAR_XML -> n.endsWith(".zip") || (n.isBlank() && "zip" in m)
            IdDocType.EAADHAAR_PDF -> n.endsWith(".pdf") || (n.isBlank() && m == "application/pdf")
            else -> false
        }
    }

    /** Some file managers prefix the name ("Downloading file x.pdf"): keep only the real file name. */
    fun cleanFileName(name: String): String = name.trim().removePrefix("Downloading file").removePrefix("downloading file").trim()

    fun isComplete(f: IdForm): Boolean = when (f.type) {
        IdDocType.AADHAAR_XML, IdDocType.EAADHAAR_PDF -> f.file != null && secretOk(f.type, f.secret)
        IdDocType.PAN -> numberOk(f.type, f.number) && f.front != null
        IdDocType.PASSPORT -> numberOk(f.type, f.number) && f.front != null && f.back != null
    }
}

enum class TutorialType { VIDEO, PDF, IMAGE }

/**
 * A verification guide set by SafeRent staff (GET kyc/tutorials), one per way to verify
 * ("Offline eKYC", "PAN verification", …). Title / description come from the server.
 * [url] is a public https file: video, PDF or picture.
 */
data class KycTutorial(
    val id: String,
    val title: String,
    val description: String = "",
    val type: TutorialType,
    val url: String,
    /** Which ID it explains (AADHAAR_XML, PAN, …), when the server says. */
    val idType: String = "",
)
