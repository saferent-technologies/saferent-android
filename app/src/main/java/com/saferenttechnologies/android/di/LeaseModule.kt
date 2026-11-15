package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.lease.LeaseRepositoryImpl
import com.saferenttechnologies.android.data.remote.LeaseApi
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LeaseModule {
    @Binds
    @Singleton
    abstract fun bindLeaseRepository(impl: LeaseRepositoryImpl): LeaseRepository

    companion object {
        @Provides
        @Singleton
        fun provideLeaseApi(retrofit: Retrofit): LeaseApi = retrofit.create(LeaseApi::class.java)
    }
}
