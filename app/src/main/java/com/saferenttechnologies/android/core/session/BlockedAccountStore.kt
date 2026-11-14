package com.saferenttechnologies.android.core.session

import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the blocked-account details while the "Account blocked" screen is shown, so long text is
 * never passed through navigation. Non-null → MainActivity shows the screen (one screen, even
 * if several requests fail together — the same value is just set again).
 */
@Singleton
class BlockedAccountStore @Inject constructor() {
    private val _info = MutableStateFlow<AccountBlockedInfo?>(null)
    val info: StateFlow<AccountBlockedInfo?> = _info.asStateFlow()

    fun show(info: AccountBlockedInfo) {
        _info.value = info
    }

    fun clear() {
        _info.value = null
    }
}
