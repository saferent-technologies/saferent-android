package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.SocietyApi
import com.saferenttechnologies.android.data.society.SocietyRepositoryImpl
import com.saferenttechnologies.android.domain.repository.SocietyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SocietyModule {
    @Binds
    @Singleton
    abstract fun bindSocietyRepository(impl: SocietyRepositoryImpl): SocietyRepository

    companion object {
        @Provides
        @Singleton
        fun provideSocietyApi(retrofit: Retrofit): SocietyApi = retrofit.create(SocietyApi::class.java)
    }
}
