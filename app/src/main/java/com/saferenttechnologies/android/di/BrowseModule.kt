package com.saferenttechnologies.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.saferenttechnologies.android.core.di.BrowsePreferences
import com.saferenttechnologies.android.data.browse.BrowseRepositoryImpl
import com.saferenttechnologies.android.data.browse.RecentSearchStoreImpl
import com.saferenttechnologies.android.data.remote.BrowseApi
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.repository.RecentSearchStore
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
abstract class BrowseModule {
    @Binds
    @Singleton
    abstract fun bindBrowseRepository(impl: BrowseRepositoryImpl): BrowseRepository

    @Binds
    @Singleton
    abstract fun bindRecentSearchStore(impl: RecentSearchStoreImpl): RecentSearchStore

    companion object {
        @Provides
        @Singleton
        fun provideBrowseApi(retrofit: Retrofit): BrowseApi = retrofit.create(BrowseApi::class.java)

        @Provides
        @Singleton
        @BrowsePreferences
        fun provideBrowseDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(RecentSearchStoreImpl.NAME) },
            )
    }
}
