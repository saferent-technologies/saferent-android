package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.VisitApi
import com.saferenttechnologies.android.data.visits.VisitRepositoryImpl
import com.saferenttechnologies.android.domain.repository.VisitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisitModule {
    @Binds
    @Singleton
    abstract fun bindVisitRepository(impl: VisitRepositoryImpl): VisitRepository

    companion object {
        @Provides
        @Singleton
        fun provideVisitApi(retrofit: Retrofit): VisitApi = retrofit.create(VisitApi::class.java)
    }
}
