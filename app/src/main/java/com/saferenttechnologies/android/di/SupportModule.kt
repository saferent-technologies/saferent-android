package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.SupportApi
import com.saferenttechnologies.android.data.support.SupportRepositoryImpl
import com.saferenttechnologies.android.domain.repository.SupportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SupportModule {
    @Binds
    @Singleton
    abstract fun bindSupportRepository(impl: SupportRepositoryImpl): SupportRepository

    companion object {
        @Provides
        @Singleton
        fun provideSupportApi(retrofit: Retrofit): SupportApi = retrofit.create(SupportApi::class.java)
    }
}
