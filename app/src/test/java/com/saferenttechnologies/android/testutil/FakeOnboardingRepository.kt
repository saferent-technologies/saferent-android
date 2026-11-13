package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.repository.OnboardingRepository

class FakeOnboardingRepository(var completed: Boolean = false) : OnboardingRepository {
    var completedVersion: String? = null
        private set

    override suspend fun isCompleted(): Boolean = completed

    override suspend fun markCompleted(version: String) {
        completed = true
        completedVersion = version
    }
}
