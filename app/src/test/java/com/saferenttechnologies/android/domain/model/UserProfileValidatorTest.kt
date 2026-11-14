package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileValidatorTest {

    private val today = SimpleDate(2026, 11, 14)
    private val valid = UserProfileForm(
        fullName = "Ravi Kumar",
        role = UserRole.TENANT,
        gender = Gender.MALE,
        dateOfBirth = "1998-04-12",
        city = "Hyderabad",
        state = "Telangana",
    )

    @Test
    fun `valid form has no errors and email is optional`() {
        assertTrue(UserProfileValidator.validate(valid, today).isEmpty())
        assertTrue(UserProfileValidator.validate(valid.copy(email = "  "), today).isEmpty())
    }

    @Test
    fun `all required fields are reported`() {
        val errors = UserProfileValidator.validate(UserProfileForm(), today)
        assertEquals(
            setOf(
                UserProfileField.FULL_NAME, UserProfileField.ROLE, UserProfileField.GENDER,
                UserProfileField.DATE_OF_BIRTH, UserProfileField.CITY, UserProfileField.STATE,
            ),
            errors.keys,
        )
    }

    @Test
    fun `must be 18 or older`() {
        assertNull(UserProfileValidator.validate(valid.copy(dateOfBirth = "2008-11-14"), today)[UserProfileField.DATE_OF_BIRTH])
        val under = UserProfileValidator.validate(valid.copy(dateOfBirth = "2008-11-15"), today)
        assertTrue(UserProfileField.DATE_OF_BIRTH in under)
        assertTrue(UserProfileField.DATE_OF_BIRTH in UserProfileValidator.validate(valid.copy(dateOfBirth = "2030-01-01"), today))
        assertTrue(UserProfileField.DATE_OF_BIRTH in UserProfileValidator.validate(valid.copy(dateOfBirth = "1999-02-30"), today))
    }

    @Test
    fun `bad email is rejected`() {
        assertTrue(UserProfileField.EMAIL in UserProfileValidator.validate(valid.copy(email = "ravi@"), today))
        assertTrue(UserProfileValidator.validate(valid.copy(email = " ravi@saferent.in "), today).isEmpty())
    }

    @Test
    fun `active role prefers saved role only when the account has it`() {
        val user = AccountUser(role = UserRole.TENANT, roles = listOf(UserRole.TENANT, UserRole.LANDLORD))
        assertEquals(UserRole.LANDLORD, ActiveRole.resolve(UserRole.LANDLORD, user))
        assertEquals(UserRole.TENANT, ActiveRole.resolve(UserRole.LANDLORD, user.copy(roles = listOf(UserRole.TENANT))))
        assertEquals(UserRole.TENANT, ActiveRole.resolve(null, user))
    }
}
