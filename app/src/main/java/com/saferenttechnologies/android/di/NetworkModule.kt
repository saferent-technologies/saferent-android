package com.saferenttechnologies.android.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import com.saferenttechnologies.android.BuildConfig
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.auth.AuthInterceptor
import com.saferenttechnologies.android.data.remote.SafeRentJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Shared HTTP stack. Base URL = BuildConfig.API_BASE_URL (from local.properties). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = SafeRentJson.instance

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // First: loaded data is served from memory (no network) until refresh / a change / a push.
        .addInterceptor(com.saferenttechnologies.android.data.remote.ApiCache.interceptor)
        .addInterceptor(authInterceptor) // Firebase ID token on every request when signed in
        .apply {
            if (BuildConfig.DEBUG) {
                // Chucker: every call shows in a notification → tap it to open the inspector.
                // Release builds use library-no-op, so this is a no-op there.
                addInterceptor(
                    ChuckerInterceptor.Builder(context)
                        .collector(
                            ChuckerCollector(
                                context = context,
                                showNotification = true,
                                retentionPeriod = RetentionManager.Period.ONE_DAY,
                            ),
                        )
                        .redactHeaders("Authorization", "Cookie", "Set-Cookie")
                        .alwaysReadResponseBody(true)
                        .build(),
                )
                addInterceptor(
                    // Coordinates in query strings (geo/reverse?lat=…&lng=…) are masked in the log.
                    HttpLoggingInterceptor { line ->
                        HttpLoggingInterceptor.Logger.DEFAULT.log(line.replace(COORDINATE_QUERY, "$1=•••"))
                    }.apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                        // Never log credentials, even in debug.
                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, logger: AppLogger): Retrofit {
        logger.d("SafeRent-Network", "Retrofit base URL: ${BuildConfig.API_BASE_URL}")
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

private val COORDINATE_QUERY = Regex("""\b(lat|lng|latitude|longitude)=[^&\s]+""")
