package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppPermission

/**
 * Permission state that doesn't need an Activity. "Should show rationale" does, so the UI
 * combines this with it via PermissionRules.status.
 */
interface PermissionManager {
    /** False when the permission doesn't exist on this Android version (e.g. notifications < 13). */
    fun isSupported(permission: AppPermission): Boolean

    /** Location counts as granted with either fine or coarse. */
    fun isGranted(permission: AppPermission): Boolean

    suspend fun askedBefore(permission: AppPermission): Boolean
    suspend fun markAsked(permission: AppPermission)

    /** Last time our popup was shown for it (epoch ms), null = never. */
    suspend fun lastPromptAt(permission: AppPermission): Long?
    suspend fun markPrompted(permission: AppPermission, at: Long)
}
