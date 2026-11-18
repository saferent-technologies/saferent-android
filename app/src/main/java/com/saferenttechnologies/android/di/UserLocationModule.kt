package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.location.UserLocationSyncImpl
import com.saferenttechnologies.android.data.remote.UserLocationApi
import com.saferenttechnologies.android.domain.repository.UserLocationSync
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserLocationModule {
    @Binds
    @Singleton
    abstract fun bindUserLocationSync(impl: UserLocationSyncImpl): UserLocationSync

    companion object {
        @Provides
        @Singleton
        fun provideUserLocationApi(retrofit: Retrofit): UserLocationApi = retrofit.create(UserLocationApi::class.java)
    }
}
