package com.saferenttechnologies.android.presentation.blocked

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus

enum class SupportChannel { EMAIL, CALL, WHATSAPP, HELP_CENTER }

data class AccountBlockedState(
    val info: AccountBlockedInfo? = null,
    /** Contact support screen open (ticket system comes later). */
    val showContactSupport: Boolean = false,
    val isSigningOut: Boolean = false,
) : UiState {
    /** Only the channels the server sent. */
    val channels: List<SupportChannel>
        get() {
            val s = info?.support ?: return emptyList()
            return buildList {
                if (!s.email.isNullOrBlank()) add(SupportChannel.EMAIL)
                if (!s.phone.isNullOrBlank()) add(SupportChannel.CALL)
                if (!s.whatsapp.isNullOrBlank()) add(SupportChannel.WHATSAPP)
                if (!s.helpCenterUrl.isNullOrBlank()) add(SupportChannel.HELP_CENTER)
            }
        }
}

sealed interface AccountBlockedIntent : UiIntent {
    /** Re-check the account (POST auth/phone/complete). */
    data object TryAgain : AccountBlockedIntent
    data object ContactSupport : AccountBlockedIntent
    data object CloseContactSupport : AccountBlockedIntent
    data class OpenChannel(val channel: SupportChannel) : AccountBlockedIntent
    /** Top-right "Sign out" (and system back) → login screen. */
    data object SignOut : AccountBlockedIntent
}

sealed interface AccountBlockedEffect : UiEffect {
    data class OpenEmail(val email: String, val status: AccountStatus) : AccountBlockedEffect
    data class OpenDialer(val phone: String) : AccountBlockedEffect
    data class OpenWhatsApp(val url: String) : AccountBlockedEffect
    data class OpenUrl(val url: String) : AccountBlockedEffect
    data object NavigateHome : AccountBlockedEffect
}
