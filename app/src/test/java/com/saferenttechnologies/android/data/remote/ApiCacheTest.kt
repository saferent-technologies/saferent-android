package com.saferenttechnologies.android.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCacheTest {
    @Test fun `plain API GETs are kept, files and config are not`() {
        assertTrue(ApiCache.cacheable("GET", "/api/v1/properties"))
        assertTrue(ApiCache.cacheable("GET", "/api/v1/rent/dues"))
        assertFalse(ApiCache.cacheable("POST", "/api/v1/properties"))
        assertFalse(ApiCache.cacheable("GET", "/api/v1/files/announcements/i1"))
        assertFalse(ApiCache.cacheable("GET", "/api/v1/receipts/r1/pdf"))
        assertFalse(ApiCache.cacheable("GET", "/api/v1/config"))
        assertFalse(ApiCache.cacheable("GET", "/api/v1/app/splash"))
    }
}
