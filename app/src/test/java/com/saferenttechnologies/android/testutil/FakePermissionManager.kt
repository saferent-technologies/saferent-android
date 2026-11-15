package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.repository.PermissionManager

class FakePermissionManager : PermissionManager {
    val granted = mutableSetOf<AppPermission>()
    val unsupported = mutableSetOf<AppPermission>()
    val asked = mutableSetOf<AppPermission>()
    val lastPrompt = mutableMapOf<AppPermission, Long>()

    override fun isSupported(permission: AppPermission) = permission !in unsupported
    override fun isGranted(permission: AppPermission) = permission in granted || permission in unsupported
    override suspend fun askedBefore(permission: AppPermission) = permission in asked
    override suspend fun markAsked(permission: AppPermission) {
        asked += permission
    }
    override suspend fun lastPromptAt(permission: AppPermission) = lastPrompt[permission]
    override suspend fun markPrompted(permission: AppPermission, at: Long) {
        lastPrompt[permission] = at
    }
}
