package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KycReturnTest {
    @Test fun `digilocker return link opens owner verification`() {
        assertEquals(
            NavRoute.KycReturn("abc123", true, null),
            DeepLinkRouter.routeOrNull("https://app.saferenttechnologies.com/app/kyc/return?id=abc123&success=True"),
        )
        assertEquals(
            NavRoute.KycReturn("abc123", false, "User denied"),
            DeepLinkRouter.routeOrNull("/app/kyc/return?id=abc123&success=False&errMessage=User%20denied"),
        )
        assertEquals(null, KycReturn.success("maybe"))
    }
}

class IdRulesTest {
    @Test fun `aadhaar zip only accepts zip, e-aadhaar only pdf`() {
        org.junit.Assert.assertFalse(IdRules.fileOk(IdDocType.AADHAAR_XML, "labels.pdf", "application/pdf"))
        org.junit.Assert.assertFalse(IdRules.fileOk(IdDocType.AADHAAR_XML, "labels.pdf", "application/octet-stream"))
        org.junit.Assert.assertTrue(IdRules.fileOk(IdDocType.AADHAAR_XML, "offlineaadhaar2026.zip", "application/octet-stream"))
        org.junit.Assert.assertTrue(IdRules.fileOk(IdDocType.EAADHAAR_PDF, "EAadhaar.PDF", null))
        org.junit.Assert.assertFalse(IdRules.fileOk(IdDocType.EAADHAAR_PDF, "a.zip", "application/zip"))
        assertEquals("x.pdf", IdRules.cleanFileName("Downloading file x.pdf"))
    }

    @Test fun `server id types`() {
        org.junit.Assert.assertTrue(KycStatus().allows(IdDocType.AADHAAR_XML))
        val s = KycStatus(idTypes = setOf(IdDocType.PAN, IdDocType.PASSPORT))
        org.junit.Assert.assertFalse(s.allows(IdDocType.AADHAAR_XML))
        org.junit.Assert.assertTrue(s.allows(IdDocType.PAN))
    }

    @Test fun `pan and passport numbers`() {
        org.junit.Assert.assertTrue(IdRules.numberOk(IdDocType.PAN, "ABCDE1234F"))
        org.junit.Assert.assertFalse(IdRules.numberOk(IdDocType.PAN, "ABCD1234F"))
        org.junit.Assert.assertTrue(IdRules.numberOk(IdDocType.PASSPORT, "A1234567"))
        assertEquals("ABCDE1234F", IdRules.cleanNumber(IdDocType.PAN, "abcde 1234 f9"))
    }

    @Test fun `aadhaar xml needs file and 4 digit share code`() {
        val f = IdForm(IdDocType.AADHAAR_XML, file = "content://x", secret = "1234")
        org.junit.Assert.assertTrue(IdRules.isComplete(f))
        org.junit.Assert.assertFalse(IdRules.isComplete(f.copy(secret = "12")))
        org.junit.Assert.assertFalse(IdRules.isComplete(IdForm(IdDocType.PASSPORT, number = "A1234567", front = "/a")))
    }
}
