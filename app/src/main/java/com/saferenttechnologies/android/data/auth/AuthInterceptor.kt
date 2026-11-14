package com.saferenttechnologies.android.data.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds `Authorization: Bearer <Firebase ID token>` to requests for OUR API host when signed in.
 * Firebase refreshes an expired token automatically. A 401 on an authenticated request
 * emits [SessionEvent.Expired] → the app signs out and shows login.
 * Runs on OkHttp's background thread, so waiting for the token here is fine.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionEvents: SessionEvents,
) : Interceptor {

    /** Only our own API gets the token — never CDNs or other hosts (asset / image downloads). */
    private val apiHost: String? = BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host != apiHost) return chain.proceed(chain.request())
        val token = firebaseAuth.currentUser?.let { user ->
            runCatching { Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).token }.getOrNull()
        }
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) sessionEvents.emit(SessionEvent.Expired)
        return response
    }
}
