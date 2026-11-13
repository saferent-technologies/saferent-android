package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.BuildConfig
import com.saferenttechnologies.android.core.di.AppVersionName
import com.saferenttechnologies.android.core.di.IoDispatcher
import com.saferenttechnologies.android.core.log.AndroidAppLogger
import com.saferenttechnologies.android.core.log.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @AppVersionName
    fun provideAppVersionName(): String = BuildConfig.VERSION_NAME

    @Provides
    fun provideAppLogger(logger: AndroidAppLogger): AppLogger = logger
}
