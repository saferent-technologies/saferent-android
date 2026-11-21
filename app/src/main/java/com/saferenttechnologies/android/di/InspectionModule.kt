package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.inspection.InspectionRepositoryImpl
import com.saferenttechnologies.android.data.remote.InspectionApi
import com.saferenttechnologies.android.domain.repository.InspectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectionModule {
    @Binds
    @Singleton
    abstract fun bindInspectionRepository(impl: InspectionRepositoryImpl): InspectionRepository

    companion object {
        @Provides
        @Singleton
        fun provideInspectionApi(retrofit: Retrofit): InspectionApi = retrofit.create(InspectionApi::class.java)
    }
}
