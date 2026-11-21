package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.inventory.RegisterRepositoryImpl
import com.saferenttechnologies.android.data.remote.RegisterApi
import com.saferenttechnologies.android.domain.repository.RegisterRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RegisterModule {
    @Binds
    @Singleton
    abstract fun bindRegisterRepository(impl: RegisterRepositoryImpl): RegisterRepository

    companion object {
        @Provides
        @Singleton
        fun provideRegisterApi(retrofit: Retrofit): RegisterApi = retrofit.create(RegisterApi::class.java)
    }
}
