package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.push.DeviceTokenRepositoryImpl
import com.saferenttechnologies.android.domain.repository.DeviceTokenRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @Singleton
    abstract fun bindDeviceTokenRepository(impl: DeviceTokenRepositoryImpl): DeviceTokenRepository
}
