package com.saferenttechnologies.android.di

import com.saferenttechnologies.android.data.documents.DocumentsRepositoryImpl
import com.saferenttechnologies.android.data.remote.DocumentsApi
import com.saferenttechnologies.android.domain.repository.DocumentsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentsModule {
    @Binds
    @Singleton
    abstract fun bindDocumentsRepository(impl: DocumentsRepositoryImpl): DocumentsRepository

    @Binds
    @Singleton
    abstract fun bindDocumentFiles(impl: com.saferenttechnologies.android.data.documents.DocumentFilesImpl): com.saferenttechnologies.android.domain.repository.DocumentFiles

    companion object {
        @Provides
        @Singleton
        fun provideDocumentsApi(retrofit: Retrofit): DocumentsApi = retrofit.create(DocumentsApi::class.java)
    }
}
