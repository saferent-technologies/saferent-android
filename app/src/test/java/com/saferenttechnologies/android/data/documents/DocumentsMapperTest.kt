package com.saferenttechnologies.android.data.documents

import com.saferenttechnologies.android.domain.model.DocType
import com.saferenttechnologies.android.domain.model.DocumentRules
import com.saferenttechnologies.android.domain.model.DocumentVault
import com.saferenttechnologies.android.domain.model.TenantDoc
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentsMapperTest {
    @Test fun `vault with shares, types and leases`() {
        val v = DocumentsMapper.vault(
            Json.parseToJsonElement(
                """{"documents":[{"id":"d1","type":"aadhaar","typeLabel":"Aadhaar","pages":[{"id":"p1","url":"/api/v1/me/documents/d1/pages/p1"},{"id":"p2","url":"/x"}],
                   "uploadedAt":"2026-11-16T10:00:00Z","shares":[{"leaseId":"l1","landlord":{"fullName":"Suresh"},"sharedAt":"2026-11-16T11:00:00Z"}]}],
                   "types":[{"key":"AADHAAR","label":"Aadhaar","pages":2},{"key":"PAN","label":"PAN card"}],
                   "leases":[{"leaseId":"l1","landlord":{"fullName":"Suresh"},"propertyTitle":"Green Heights","place":"Flat 302"}]}""",
            ),
        )!!
        val d = v.documents.single()
        assertEquals("AADHAAR", d.type)
        assertEquals(2, d.pages.size)
        assertTrue(d.sharedWith("l1"))
        assertFalse(d.sharedWith("l2"))
        assertEquals("Suresh", d.shares.single().landlordName)
        assertEquals("Green Heights · Flat 302", v.leases.single().subtitle)
        assertEquals(1, v.types[1].pages)
    }

    @Test fun `landlord shared list`() {
        val s = DocumentsMapper.shared(Json.parseToJsonElement("""{"tenant":{"fullName":"Ravi"},"documents":[{"id":"d1","type":"PAN","pages":[{"id":"p","url":"/u"}]}]}"""))!!
        assertEquals("Ravi", s.tenantName)
        assertEquals("PAN", s.documents.single().type)
    }

    @Test fun `rules - one per type except OTHER, page limits`() {
        val v = DocumentVault(documents = listOf(TenantDoc("d1", "AADHAAR"), TenantDoc("d2", "OTHER")), types = listOf(DocType("AADHAAR", "Aadhaar", 2), DocType("PAN", "PAN", 1), DocType("OTHER", "Other", 2)))
        assertEquals(listOf("PAN", "OTHER"), DocumentRules.addable(v).map { it.key })
        assertTrue(DocumentRules.canUpload(DocType("PAN", "PAN", 1), 1))
        assertFalse(DocumentRules.canUpload(DocType("PAN", "PAN", 1), 2))
        assertFalse(DocumentRules.canUpload(null, 1))
        assertFalse(DocumentRules.canUpload(DocType("AADHAAR", "Aadhaar", 2), 0))
    }

    @Test fun `pdf documents`() {
        val d = DocumentsMapper.doc(Json.parseToJsonElement("""{"id":"d9","type":"PAN","pages":[{"id":"p","url":"/api/v1/me/documents/d9/file","mimeType":"application/pdf"}]}""") as kotlinx.serialization.json.JsonObject)
        assertTrue(d.isPdf)
        assertTrue(DocumentRules.looksLikePdf("%PDF-1.7 rest".toByteArray()))
        assertFalse(DocumentRules.looksLikePdf("hello".toByteArray()))
        // PDF instead of photos: no photos allowed with it.
        assertTrue(DocumentRules.canUpload(DocType("AADHAAR", "Aadhaar", 2), 0, hasPdf = true))
        assertFalse(DocumentRules.canUpload(DocType("AADHAAR", "Aadhaar", 2), 1, hasPdf = true))
    }
}
