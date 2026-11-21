package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.owner.OwnerRepositoryImpl
import com.saferenttechnologies.android.data.remote.OwnerApi
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OwnerModule {
    @Binds
    @Singleton
    abstract fun bindOwnerRepository(impl: OwnerRepositoryImpl): OwnerRepository

    companion object {
        @Provides
        @Singleton
        fun provideOwnerApi(retrofit: Retrofit): OwnerApi = retrofit.create(OwnerApi::class.java)
    }
}
