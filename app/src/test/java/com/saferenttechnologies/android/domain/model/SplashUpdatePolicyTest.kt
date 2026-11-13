package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashUpdatePolicyTest {

    private val defaults = SplashAppearance() // version "0"

    @Test
    fun `first server version is newer than bundled 0`() {
        assertTrue(SplashUpdatePolicy.needsUpdate(defaults, SplashSettings(version = "1"), true))
    }

    @Test
    fun `same version with files present needs nothing`() {
        val saved = SplashAppearance(version = "1.4")
        assertFalse(SplashUpdatePolicy.needsUpdate(saved, SplashSettings(version = "1.4"), true))
        assertFalse(SplashUpdatePolicy.needsUpdate(saved, SplashSettings(version = "1.4.0"), true))
    }

    @Test
    fun `version change downloads - upgrade and rollback`() {
        val saved = SplashAppearance(version = "1.4")
        assertTrue(SplashUpdatePolicy.needsUpdate(saved, SplashSettings(version = "2"), true))
        assertTrue(SplashUpdatePolicy.needsUpdate(saved, SplashSettings(version = "1.3"), true))
    }

    @Test
    fun `same version but files missing downloads again`() {
        val saved = SplashAppearance(version = "1.4", logoPath = "/gone/logo.png")
        assertTrue(SplashUpdatePolicy.needsUpdate(saved, SplashSettings(version = "1.4"), false))
    }

    @Test
    fun `blank server version never downloads`() {
        assertFalse(SplashUpdatePolicy.needsUpdate(defaults, SplashSettings(version = ""), false))
    }
}
