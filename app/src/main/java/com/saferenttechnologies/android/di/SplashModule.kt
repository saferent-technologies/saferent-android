package com.saferenttechnologies.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.saferenttechnologies.android.core.di.SplashPreferences
import com.saferenttechnologies.android.data.local.SplashDataStore
import com.saferenttechnologies.android.data.repository.SplashRepositoryImpl
import com.saferenttechnologies.android.data.work.WorkManagerSplashAssetsScheduler
import com.saferenttechnologies.android.domain.repository.SplashAssetsScheduler
import com.saferenttechnologies.android.domain.repository.SplashRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SplashModule {

    @Binds
    @Singleton
    abstract fun bindSplashRepository(impl: SplashRepositoryImpl): SplashRepository

    @Binds
    @Singleton
    abstract fun bindSplashAssetsScheduler(impl: WorkManagerSplashAssetsScheduler): SplashAssetsScheduler

    companion object {
        @Provides
        @Singleton
        @SplashPreferences
        fun provideSplashDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(SplashDataStore.NAME) },
            )
    }
}
