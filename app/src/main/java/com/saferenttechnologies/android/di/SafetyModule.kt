package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.SafetyApi
import com.saferenttechnologies.android.data.safety.SafetyRepositoryImpl
import com.saferenttechnologies.android.domain.repository.SafetyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SafetyModule {
    @Binds
    @Singleton
    abstract fun bindSafetyRepository(impl: SafetyRepositoryImpl): SafetyRepository

    companion object {
        @Provides
        @Singleton
        fun provideSafetyApi(retrofit: Retrofit): SafetyApi = retrofit.create(SafetyApi::class.java)
    }
}
