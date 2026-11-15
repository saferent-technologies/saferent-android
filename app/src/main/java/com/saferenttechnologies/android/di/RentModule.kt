package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.RentApi
import com.saferenttechnologies.android.data.rent.ReceiptFilesImpl
import com.saferenttechnologies.android.data.rent.RentRepositoryImpl
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RentModule {
    @Binds
    @Singleton
    abstract fun bindRentRepository(impl: RentRepositoryImpl): RentRepository

    @Binds
    @Singleton
    abstract fun bindReceiptFiles(impl: ReceiptFilesImpl): ReceiptFiles

    companion object {
        @Provides
        @Singleton
        fun provideRentApi(retrofit: Retrofit): RentApi = retrofit.create(RentApi::class.java)
    }
}
