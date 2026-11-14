package com.saferenttechnologies.android.presentation.blocked

import com.saferenttechnologies.android.core.session.BlockedAccountStore
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.SupportContact
import com.saferenttechnologies.android.domain.usecase.SignOutBlockedAccountUseCase
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import com.saferenttechnologies.android.testutil.FakeAccountRepository
import com.saferenttechnologies.android.testutil.FakeAuthRepository
import com.saferenttechnologies.android.testutil.FakeDeviceTokenRepository
import com.saferenttechnologies.android.testutil.FakeSessionRepository
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountBlockedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = BlockedAccountStore()
    private val auth = FakeAuthRepository().apply { user = AuthUser("uid", null) }
    private val signOut = SignOutUseCase(
        auth, FakeAccountRepository(), FakeDeviceTokenRepository(), FakeSessionRepository(), SessionEvents(),
    )

    private fun viewModel() = AccountBlockedViewModel(store, SignOutBlockedAccountUseCase(store, signOut))

    private fun TestScope.effectsOf(vm: AccountBlockedViewModel): List<AccountBlockedEffect> {
        val effects = mutableListOf<AccountBlockedEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        return effects
    }

    @Test
    fun `Sign out signs out, clears the screen and emits NavigateHome`() = runTest {
        store.show(AccountBlockedInfo(status = AccountStatus.DELETED))
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(AccountBlockedIntent.SignOut)

        assertEquals(listOf<AccountBlockedEffect>(AccountBlockedEffect.NavigateHome), effects)
        assertNull(store.info.value)
        assertNull(auth.user)
    }

    @Test
    fun `Try again clears the screen but stays signed in`() = runTest {
        store.show(AccountBlockedInfo(status = AccountStatus.SUSPENDED))
        val vm = viewModel()
        vm.onIntent(AccountBlockedIntent.TryAgain)
        assertNull(store.info.value)
        assertTrue(auth.user != null)
    }

    @Test
    fun `Contact support opens the support screen with only the email option`() = runTest {
        store.show(AccountBlockedInfo(status = AccountStatus.DEACTIVATED, support = SupportContact(email = "help@saferent.in")))
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(AccountBlockedIntent.ContactSupport)
        assertTrue(vm.state.value.showContactSupport)
        assertEquals(listOf(SupportChannel.EMAIL), vm.state.value.channels)

        vm.onIntent(AccountBlockedIntent.OpenChannel(SupportChannel.EMAIL))
        assertEquals(listOf<AccountBlockedEffect>(AccountBlockedEffect.OpenEmail("help@saferent.in", AccountStatus.DEACTIVATED)), effects)

        vm.onIntent(AccountBlockedIntent.CloseContactSupport)
        assertFalse(vm.state.value.showContactSupport)
    }

    @Test
    fun `WhatsApp opens wa_me with digits only`() = runTest {
        store.show(AccountBlockedInfo(status = AccountStatus.DELETED, support = SupportContact(whatsapp = "+91 98765-43210")))
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.onIntent(AccountBlockedIntent.OpenChannel(SupportChannel.WHATSAPP))
        assertEquals(listOf<AccountBlockedEffect>(AccountBlockedEffect.OpenWhatsApp("https://wa.me/919876543210")), effects)
    }
}
