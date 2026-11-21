package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.SmartApi
import com.saferenttechnologies.android.data.smart.SmartRepositoryImpl
import com.saferenttechnologies.android.domain.repository.SmartRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SmartModule {
    @Binds
    @Singleton
    abstract fun bindSmartRepository(impl: SmartRepositoryImpl): SmartRepository

    companion object {
        @Provides
        @Singleton
        fun provideSmartApi(retrofit: Retrofit): SmartApi = retrofit.create(SmartApi::class.java)
    }
}
