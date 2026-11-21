package com.saferenttechnologies.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.saferenttechnologies.android.core.di.KycPreferences
import com.saferenttechnologies.android.data.kyc.KycRepositoryImpl
import com.saferenttechnologies.android.data.remote.KycApi
import com.saferenttechnologies.android.domain.repository.KycRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KycModule {
    @Binds
    @Singleton
    abstract fun bindKycRepository(impl: KycRepositoryImpl): KycRepository

    companion object {
        @Provides
        @Singleton
        fun provideKycApi(retrofit: Retrofit): KycApi = retrofit.create(KycApi::class.java)

        @Provides
        @Singleton
        @KycPreferences
        fun provideKycDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(KycRepositoryImpl.NAME) },
            )
    }
}
