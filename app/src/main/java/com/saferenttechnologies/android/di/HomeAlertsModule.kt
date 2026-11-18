package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.homealerts.HomeAlertsRepositoryImpl
import com.saferenttechnologies.android.data.remote.HomeAlertsApi
import com.saferenttechnologies.android.domain.repository.HomeAlertsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeAlertsModule {
    @Binds
    @Singleton
    abstract fun bindHomeAlertsRepository(impl: HomeAlertsRepositoryImpl): HomeAlertsRepository

    companion object {
        @Provides
        @Singleton
        fun provideHomeAlertsApi(retrofit: Retrofit): HomeAlertsApi = retrofit.create(HomeAlertsApi::class.java)
    }
}
