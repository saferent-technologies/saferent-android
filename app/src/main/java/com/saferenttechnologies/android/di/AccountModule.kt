package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.AccountApi
import com.saferenttechnologies.android.data.repository.AccountRepositoryImpl
import com.saferenttechnologies.android.domain.repository.AccountRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    companion object {
        @Provides
        @Singleton
        fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)
    }
}
