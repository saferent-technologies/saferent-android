package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.core.session.BlockedAccountStore
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.LoginResult
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.testutil.FakeAccountRepository
import com.saferenttechnologies.android.testutil.FakeAuthRepository
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeDeviceTokenRepository
import com.saferenttechnologies.android.testutil.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletePhoneLoginUseCaseTest {

    private val calls = mutableListOf<String>()
    private val account = FakeAccountRepository(calls)
    private val deviceTokens = FakeDeviceTokenRepository(calls)
    private val useCase = CompletePhoneLoginUseCase(account, deviceTokens, NoOpLogger)

    private suspend fun result() = (useCase() as AppResult.Success).data

    @Test
    fun `active and completed goes to dashboard`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(accountStatus = AccountStatus.ACTIVE, isProfileCompleted = true))
        assertTrue(result() is LoginResult.GoToDashboard)
    }

    @Test
    fun `active and not completed goes to user profile`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(isProfileCompleted = false))
        assertTrue(result() is LoginResult.GoToUserProfile)
    }

    @Test
    fun `registrationRequired (no accountStatus = ACTIVE) goes to user profile`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(registrationRequired = true, isProfileCompleted = false))
        assertTrue(result() is LoginResult.GoToUserProfile)
    }

    @Test
    fun `403 ACCOUNT_DELETED is Blocked with status, reason and date`() = runTest {
        val info = AccountBlockedInfo(
            status = AccountStatus.DELETED,
            title = "Account deleted",
            reason = "Fake listings reported by several tenants",
            changedAtMillis = 1_794_652_325_120,
        )
        account.completion = AppResult.Failure(AppError.AccountBlocked(info))
        val blocked = result() as LoginResult.Blocked
        assertEquals(AccountStatus.DELETED, blocked.info.status)
        assertEquals("Fake listings reported by several tenants", blocked.info.reason)
        assertEquals(1_794_652_325_120, blocked.info.changedAtMillis)
    }

    @Test
    fun `403 ACCOUNT_DISABLED is Blocked DEACTIVATED with no reason`() = runTest {
        account.completion = AppResult.Failure(AppError.AccountBlocked(AccountBlockedInfo(status = AccountStatus.DEACTIVATED)))
        val blocked = result() as LoginResult.Blocked
        assertEquals(AccountStatus.DEACTIVATED, blocked.info.status)
        assertNull(blocked.info.reason)
    }

    @Test
    fun `200 with a non-active status is still Blocked (defensive)`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(accountStatus = AccountStatus.SUSPENDED, isProfileCompleted = true))
        assertEquals(AccountStatus.SUSPENDED, (result() as LoginResult.Blocked).info.status)
    }

    @Test
    fun `other failures stay failures`() = runTest {
        account.completion = AppResult.Failure(AppError.Network())
        assertTrue(useCase() is AppResult.Failure)
    }

    @Test
    fun `blocked login shows the screen and stays signed in for Try again`() = runTest {
        val auth = FakeAuthRepository().apply { user = AuthUser("uid", null) }
        val store = BlockedAccountStore()
        val info = AccountBlockedInfo(status = AccountStatus.DELETED)
        account.completion = AppResult.Failure(AppError.AccountBlocked(info))

        val r = ResolvePostLoginUseCase(useCase, account, FakeConfigRepository())() as AppResult.Failure
        HandleAccountBlockedUseCase(store)((r.error as AppError.AccountBlocked).info)

        assertEquals(info, store.info.value)
        assertTrue(auth.user != null) // Try again can re-check
    }

    @Test
    fun `sign out from the blocked screen signs out locally and clears the screen`() = runTest {
        val auth = FakeAuthRepository().apply { user = AuthUser("uid", null) }
        val store = BlockedAccountStore().apply { show(AccountBlockedInfo(status = AccountStatus.DEACTIVATED)) }
        val signOut = SignOutUseCase(auth, account, deviceTokens, FakeSessionRepository(), SessionEvents())

        SignOutBlockedAccountUseCase(store, signOut)()

        assertNull(auth.user)
        assertNull(store.info.value)
        assertTrue(calls.none { it.startsWith("logout") }) // no server logout for a blocked account
    }
}
