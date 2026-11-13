package com.saferenttechnologies.android.domain.repository

/** Remembers whether the user finished (or skipped) onboarding. Local only. */
interface OnboardingRepository {
    suspend fun isCompleted(): Boolean
    suspend fun markCompleted(version: String)
}
