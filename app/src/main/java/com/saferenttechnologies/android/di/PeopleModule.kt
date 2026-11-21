package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.people.PeopleRepositoryImpl
import com.saferenttechnologies.android.data.remote.PeopleApi
import com.saferenttechnologies.android.domain.repository.PeopleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PeopleModule {
    @Binds
    @Singleton
    abstract fun bindPeopleRepository(impl: PeopleRepositoryImpl): PeopleRepository

    companion object {
        @Provides
        @Singleton
        fun providePeopleApi(retrofit: Retrofit): PeopleApi = retrofit.create(PeopleApi::class.java)
    }
}
