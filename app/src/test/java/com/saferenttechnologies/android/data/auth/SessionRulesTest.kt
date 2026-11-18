package com.saferenttechnologies.android.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    @Test fun `only real auth failures sign out`() {
        assertTrue(SessionRules.isAuthFailure(null))
        assertTrue(SessionRules.isAuthFailure("UNAUTHORIZED"))
        assertTrue(SessionRules.isAuthFailure("token_expired"))
        assertFalse(SessionRules.isAuthFailure("NOT_LEASE_MEMBER"))
        assertFalse(SessionRules.isAuthFailure("FORBIDDEN"))
    }
}
