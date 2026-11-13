package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {

    @Test
    fun `compareVersions handles padding, numbers and suffixes`() {
        assertEquals(0, AppInfo.compareVersions("1.0", "1.0.0"))
        assertTrue(AppInfo.compareVersions("1.2.10", "1.2.9") > 0)
        assertTrue(AppInfo.compareVersions("1.0.0-beta", "1.0.1") < 0)
    }

    @Test
    fun `update required below minimum or when forced to latest`() {
        assertTrue(AppInfo(minimumSupportedVersion = "1.5.0").isUpdateRequired("1.4.9"))
        assertFalse(AppInfo(minimumSupportedVersion = "1.0.0").isUpdateRequired("1.0"))
        assertTrue(AppInfo(latestVersion = "2.0.0", forceUpdate = true).isUpdateRequired("1.9.0"))
        assertFalse(AppInfo(latestVersion = "2.0.0", forceUpdate = false).isUpdateRequired("1.9.0"))
        assertFalse(AppInfo().isUpdateRequired("1.0.0")) // empty cache → never block
    }
}
