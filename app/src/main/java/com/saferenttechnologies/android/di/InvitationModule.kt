package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.invitation.InvitationRepositoryImpl
import com.saferenttechnologies.android.data.remote.InvitationApi
import com.saferenttechnologies.android.domain.repository.InvitationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InvitationModule {
    @Binds
    @Singleton
    abstract fun bindInvitationRepository(impl: InvitationRepositoryImpl): InvitationRepository

    companion object {
        @Provides
        @Singleton
        fun provideInvitationApi(retrofit: Retrofit): InvitationApi = retrofit.create(InvitationApi::class.java)
    }
}
