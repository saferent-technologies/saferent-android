package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.compliance.ComplianceRepositoryImpl
import com.saferenttechnologies.android.data.remote.ComplianceApi
import com.saferenttechnologies.android.domain.repository.ComplianceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ComplianceModule {
    @Binds
    @Singleton
    abstract fun bindComplianceRepository(impl: ComplianceRepositoryImpl): ComplianceRepository

    companion object {
        @Provides
        @Singleton
        fun provideComplianceApi(retrofit: Retrofit): ComplianceApi = retrofit.create(ComplianceApi::class.java)
    }
}
