package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.announcements.AnnouncementsRepositoryImpl
import com.saferenttechnologies.android.data.remote.AnnouncementsApi
import com.saferenttechnologies.android.domain.repository.AnnouncementsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnouncementsModule {
    @Binds
    @Singleton
    abstract fun bindAnnouncementsRepository(impl: AnnouncementsRepositoryImpl): AnnouncementsRepository

    companion object {
        @Provides
        @Singleton
        fun provideAnnouncementsApi(retrofit: Retrofit): AnnouncementsApi = retrofit.create(AnnouncementsApi::class.java)
    }
}
