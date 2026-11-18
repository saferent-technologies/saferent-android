package com.saferenttechnologies.android.data.auth

import com.saferenttechnologies.android.data.mapper.accountBlockedFromBody
import kotlinx.serialization.json.Json
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds `Authorization: Bearer <Firebase ID token>` to requests for OUR API host when signed in.
 * Firebase refreshes an expired token automatically. A 401 is retried once with a fresh token;
 * only if that is rejected too with an auth error code ([SessionRules]) does it emit
 * [SessionEvent.Expired] → the app signs out and shows login.
 * Runs on OkHttp's background thread, so waiting for the token here is fine.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val sessionEvents: SessionEvents,
    private val json: Json,
) : Interceptor {

    /** Only our own API gets the token — never CDNs or other hosts (asset / image downloads). */
    private val apiHost: String? = BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host != apiHost) return chain.proceed(chain.request())
        val token = idToken(forceRefresh = false)
        var response = chain.proceed(chain.request().withToken(token))
        var blocked = blockedInfo(response, token)

        // 401 with a cached token: it may be stale (e.g. account restored after a block, or the
        // server revoked old tokens). Get a fresh token from Firebase and try once more before
        // treating the session as expired.
        var refreshed = false
        if (token != null && response.code == 401 && blocked == null) {
            val fresh = idToken(forceRefresh = true)
            if (fresh != null) {
                refreshed = true
                response.close()
                response = chain.proceed(chain.request().withToken(fresh))
                blocked = blockedInfo(response, fresh)
            }
        }

        if (token != null) {
            when {
                blocked != null -> sessionEvents.emit(SessionEvent.AccountBlocked(blocked))
                // Sign out ONLY when the session is really gone: a brand-new token was rejected too
                // AND the server says it's an auth problem. Not when the refresh failed (no network /
                // timeout) and not for a 401 that one screen's API uses for something else.
                response.code == 401 && refreshed && SessionRules.isAuthFailure(errorCode(response)) ->
                    sessionEvents.emit(SessionEvent.Expired)
            }
        }
        return response
    }

    /** Firebase ID token (null when signed out / unavailable). Never throws. */
    private fun idToken(forceRefresh: Boolean): String? = firebaseAuth.currentUser?.let { user ->
        runCatching { Tasks.await(user.getIdToken(forceRefresh), 10, TimeUnit.SECONDS).token }
            .onFailure(::reportIfAccountRemoved)
            .getOrNull()
    }

    private fun Request.withToken(token: String?): Request =
        if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()

    /** Blocked account (any signed-in API, usually 403). peekBody keeps the body readable for Retrofit. */
    private fun blockedInfo(response: Response, token: String?): AccountBlockedInfo? =
        if (token != null && response.code in 400..499) {
            accountBlockedFromBody(json, response.code, runCatching { response.peekBody(MAX_PEEK).string() }.getOrNull())
        } else {
            null
        }

    /**
     * The backend deleted / disabled the Firebase user itself → Firebase can't refresh the token
     * (USER_NOT_FOUND / USER_DISABLED). Show the blocked screen (strings.xml texts) instead of
     * silently dropping to login. Server details (reason, support) only come with a 403 response.
     */
    private fun reportIfAccountRemoved(error: Throwable) {
        val invalidUser = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseAuthInvalidUserException>()
            .firstOrNull() ?: return
        val status = when (invalidUser.errorCode) {
            "ERROR_USER_NOT_FOUND" -> AccountStatus.DELETED
            "ERROR_USER_DISABLED" -> AccountStatus.DEACTIVATED
            else -> return
        }
        sessionEvents.emit(SessionEvent.AccountBlocked(AccountBlockedInfo(status = status)))
    }

    /** errorCode / reason of an error body (peek → Retrofit can still read it). */
    private fun errorCode(response: Response): String? = runCatching {
        val body = json.parseToJsonElement(response.peekBody(MAX_PEEK).string()) as? kotlinx.serialization.json.JsonObject
        listOf("errorCode", "reason", "code").firstNotNullOfOrNull { k ->
            (body?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        }
    }.getOrNull()

    private companion object {
        const val MAX_PEEK = 64L * 1024
    }
}

/** When a 401 really means "signed out" (pure, unit-tested). */
object SessionRules {
    private val AUTH_CODES = setOf(
        "UNAUTHORIZED", "UNAUTHENTICATED", "AUTH_REQUIRED", "TOKEN_EXPIRED", "TOKEN_INVALID",
        "INVALID_TOKEN", "TOKEN_REVOKED", "SESSION_EXPIRED", "ID_TOKEN_EXPIRED", "ID_TOKEN_REVOKED",
    )

    /** No code (plain 401) or an auth code → session gone; any other code → that screen's error only. */
    fun isAuthFailure(errorCode: String?): Boolean = errorCode.isNullOrBlank() || errorCode.uppercase() in AUTH_CODES
}
