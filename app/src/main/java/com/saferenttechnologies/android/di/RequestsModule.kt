package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.remote.RequestsApi
import com.saferenttechnologies.android.data.requests.RequestsRepositoryImpl
import com.saferenttechnologies.android.domain.repository.RequestsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RequestsModule {
    @Binds
    @Singleton
    abstract fun bindRequestsRepository(impl: RequestsRepositoryImpl): RequestsRepository

    @Binds
    @Singleton
    abstract fun bindRequestMedia(impl: com.saferenttechnologies.android.data.requests.RequestMediaImpl): com.saferenttechnologies.android.domain.repository.RequestMedia

    companion object {
        @Provides
        @Singleton
        fun provideRequestsApi(retrofit: Retrofit): RequestsApi = retrofit.create(RequestsApi::class.java)
    }
}
