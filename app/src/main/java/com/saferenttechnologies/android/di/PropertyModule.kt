package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.property.FusedLocationProvider
import com.saferenttechnologies.android.data.property.ManagePropertyRepositoryImpl
import com.saferenttechnologies.android.data.remote.ManagePropertyApi
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import com.saferenttechnologies.android.data.property.PropertyDraftStoreImpl
import com.saferenttechnologies.android.data.property.PropertyRepositoryImpl
import com.saferenttechnologies.android.data.property.WorkManagerPhotoUploadQueue
import com.saferenttechnologies.android.data.remote.PropertyApi
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.PhotoUploadQueue
import com.saferenttechnologies.android.domain.repository.PropertyDraftStore
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PropertyModule {

    @Binds @Singleton
    abstract fun bindPropertyRepository(impl: PropertyRepositoryImpl): PropertyRepository

    @Binds @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindPanoramaFiles(impl: com.saferenttechnologies.android.data.property.PanoramaFilesImpl): com.saferenttechnologies.android.domain.repository.PanoramaFiles

    @Binds @Singleton
    abstract fun bindDraftStore(impl: PropertyDraftStoreImpl): PropertyDraftStore

    @Binds @Singleton
    abstract fun bindManagePropertyRepository(impl: ManagePropertyRepositoryImpl): ManagePropertyRepository

    @Binds @Singleton
    abstract fun bindPhotoUploadQueue(impl: WorkManagerPhotoUploadQueue): PhotoUploadQueue

    @Binds @Singleton
    abstract fun bindVideoUploadQueue(impl: com.saferenttechnologies.android.data.property.WorkManagerVideoUploadQueue): com.saferenttechnologies.android.domain.repository.VideoUploadQueue

    companion object {
        @Provides
        @Singleton
        fun providePropertyApi(retrofit: Retrofit): PropertyApi = retrofit.create(PropertyApi::class.java)

        @Provides
        @Singleton
        fun provideManagePropertyApi(retrofit: Retrofit): ManagePropertyApi = retrofit.create(ManagePropertyApi::class.java)
    }
}
