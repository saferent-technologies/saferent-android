package com.saferenttechnologies.android.presentation.blocked

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.session.BlockedAccountStore
import com.saferenttechnologies.android.domain.usecase.SignOutBlockedAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Account blocked" + "Contact support" screens. Details come from [BlockedAccountStore]. */
@HiltViewModel
class AccountBlockedViewModel @Inject constructor(
    private val store: BlockedAccountStore,
    private val signOutBlockedAccount: SignOutBlockedAccountUseCase,
) : MviViewModel<AccountBlockedState, AccountBlockedIntent, AccountBlockedEffect>(AccountBlockedState()) {

    init {
        viewModelScope.launch {
            store.info.collect { info ->
                // A new block (or the same one again after Try again) starts on the main screen.
                if (info != null) setState { AccountBlockedState(info = info) }
            }
        }
    }

    override suspend fun handleIntent(intent: AccountBlockedIntent) {
        when (intent) {
            // The screen also asks MainActivity to re-run POST auth/phone/complete;
            // still blocked → that check shows this screen again.
            AccountBlockedIntent.TryAgain -> store.clear()
            AccountBlockedIntent.ContactSupport -> setState { copy(showContactSupport = true) }
            AccountBlockedIntent.CloseContactSupport -> setState { copy(showContactSupport = false) }
            is AccountBlockedIntent.OpenChannel -> openChannel(intent.channel)
            AccountBlockedIntent.SignOut -> if (!currentState.isSigningOut) {
                setState { copy(isSigningOut = true) }
                signOutBlockedAccount()
                sendEffect(AccountBlockedEffect.NavigateHome)
            }
        }
    }

    private fun openChannel(channel: SupportChannel) {
        val info = currentState.info ?: return
        val support = info.support ?: return
        val effect = when (channel) {
            SupportChannel.EMAIL -> support.email?.let { AccountBlockedEffect.OpenEmail(it, info.status) }
            SupportChannel.CALL -> support.phone?.let { AccountBlockedEffect.OpenDialer(it) }
            SupportChannel.WHATSAPP -> support.whatsapp?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
                ?.let { AccountBlockedEffect.OpenWhatsApp("https://wa.me/$it") }
            SupportChannel.HELP_CENTER -> support.helpCenterUrl?.let { AccountBlockedEffect.OpenUrl(it) }
        }
        effect?.let(::sendEffect)
    }
}
