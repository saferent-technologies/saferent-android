package com.saferenttechnologies.android.domain.model

/** A kind of ID / proof a tenant can keep (Aadhaar, PAN …). [pages] = how many photos (front / back). */
data class DocType(val key: String, val label: String, val pages: Int = 1)

/** One page (photo) of a document; [url] is our API's protected file url (never public). */
data class DocPage(val id: String, val url: String, val mimeType: String = "") {
    val isPdf: Boolean get() = mimeType.contains("pdf", ignoreCase = true) || url.substringBefore('?').endsWith(".pdf", ignoreCase = true)
}

/** The tenant's document is visible to this landlord (only while that lease is active). */
data class DocShare(
    val leaseId: String,
    val landlordName: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    val sharedAt: String = "",
)

/** A landlord the tenant can share with = one of the tenant's ACTIVE leases. */
data class ShareTarget(val leaseId: String, val landlordName: String = "", val propertyTitle: String = "", val place: String = "") {
    val title: String get() = landlordName.ifBlank { propertyTitle }
    val subtitle: String get() = listOf(propertyTitle, place).filter { it.isNotBlank() }.joinToString(" · ")
}

data class TenantDoc(
    val id: String,
    val type: String,
    val typeLabel: String = "",
    val pages: List<DocPage> = emptyList(),
    val uploadedAt: String = "",
    val shares: List<DocShare> = emptyList(),
) {
    fun sharedWith(leaseId: String) = shares.any { it.leaseId == leaseId }
    val isPdf: Boolean get() = pages.any { it.isPdf }
}

/** GET me/documents: my documents + the types I can add + landlords I can share with. */
data class DocumentVault(
    val documents: List<TenantDoc> = emptyList(),
    val types: List<DocType> = emptyList(),
    val leases: List<ShareTarget> = emptyList(),
)

/** Landlord view of one lease: documents the tenant shared (empty / 403 once the lease ended). */
data class SharedDocuments(val tenantName: String = "", val documents: List<TenantDoc> = emptyList())

object DocumentRules {
    const val MAX_PAGES = 2

    /** Used when the server sends no list. */
    val DEFAULT_TYPES = listOf(
        DocType("AADHAAR", "Aadhaar", 2),
        DocType("PAN", "PAN card", 1),
        DocType("PASSPORT", "Passport", 2),
        DocType("DRIVING_LICENCE", "Driving licence", 2),
        DocType("VOTER_ID", "Voter ID", 2),
        DocType("EMPLOYEE_ID", "Office ID card", 2),
        DocType("OTHER", "Other", 2),
    )

    /** Types not uploaded yet (one document per type; OTHER can repeat). */
    fun addable(vault: DocumentVault): List<DocType> {
        val have = vault.documents.map { it.type }.toSet()
        return vault.types.ifEmpty { DEFAULT_TYPES }.filter { it.key == "OTHER" || it.key !in have }
    }

    /** Real PDF files start with "%PDF". */
    fun looksLikePdf(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

    /** One PDF (≤ [PDF_MAX_BYTES]) instead of photos. */
    const val PDF_MAX_BYTES = 10 * 1024 * 1024

    /** Photos: 1..pages of the type; or exactly one PDF (no photos with it). */
    fun canUpload(type: DocType?, pages: Int, hasPdf: Boolean = false): Boolean =
        type != null && if (hasPdf) pages == 0 else pages in 1..type.pages.coerceIn(1, MAX_PAGES)
}
