package com.saferenttechnologies.android.data.auth

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.saferenttechnologies.android.core.android.CurrentActivityHolder
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.model.AuthError
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import com.saferenttechnologies.android.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Firebase phone (SMS OTP) sign-in. */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val activityHolder: CurrentActivityHolder,
    private val logger: AppLogger,
) : AuthRepository {

    /** Needed to resend to the same number without a new reCAPTCHA. */
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun currentUser(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    override fun sendOtp(phoneE164: String, resend: Boolean): Flow<PhoneAuthEvent> = callbackFlow {
        val activity = activityHolder.activity
        if (activity == null) {
            trySend(PhoneAuthEvent.Failed(AuthError.Unknown("Please open the app and try again.")))
            close()
            return@callbackFlow
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                resendToken = token
                logger.d(TAG, "OTP sent to ${phoneE164.masked()}")
                trySend(PhoneAuthEvent.CodeSent(verificationId))
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // SMS auto-read (or instant verification): show the code, then sign in.
                credential.smsCode?.let { trySend(PhoneAuthEvent.CodeAutoRetrieved(it)) }
                launch {
                    when (val result = signIn(credential)) {
                        is VerifyOtpResult.Success -> trySend(PhoneAuthEvent.SignedIn(result.user))
                        is VerifyOtpResult.Failed -> trySend(PhoneAuthEvent.Failed(result.error))
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                logger.w(TAG, "OTP send failed", e)
                trySend(PhoneAuthEvent.Failed(e.toAuthError(sending = true)))
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                trySend(PhoneAuthEvent.AutoRetrievalTimedOut)
            }
        }
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneE164)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { if (resend) resendToken?.let { setForceResendingToken(it) } }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        awaitClose { }
    }

    override suspend fun verifyOtp(verificationId: String, code: String): VerifyOtpResult =
        signIn(PhoneAuthProvider.getCredential(verificationId, code))

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    private suspend fun signIn(credential: AuthCredential): VerifyOtpResult =
        suspendCancellableCoroutine { cont ->
            firebaseAuth.signInWithCredential(credential).addOnCompleteListener { task ->
                // task.result throws on failure — only read it when successful.
                val user = if (task.isSuccessful) task.result?.user else null
                val result = if (user != null) {
                    logger.d(TAG, "Signed in uid=${user.uid}")
                    VerifyOtpResult.Success(user.toAuthUser())
                } else {
                    val e = task.exception
                    logger.w(TAG, "OTP sign-in failed", e)
                    VerifyOtpResult.Failed((e as? FirebaseException)?.toAuthError(sending = false) ?: AuthError.Unknown())
                }
                if (cont.isActive) cont.resume(result)
            }
        }

    private fun FirebaseException.toAuthError(sending: Boolean): AuthError = when {
        this is FirebaseNetworkException -> AuthError.Network()
        this is FirebaseTooManyRequestsException -> AuthError.TooManyRequests()
        this is FirebaseAuthException && errorCode == "ERROR_SESSION_EXPIRED" -> AuthError.CodeExpired()
        this is FirebaseAuthException && errorCode == "ERROR_QUOTA_EXCEEDED" -> AuthError.TooManyRequests()
        this is FirebaseAuthInvalidCredentialsException && sending -> AuthError.InvalidPhone()
        this is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCode()
        else -> AuthError.Unknown()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, phoneNumber = phoneNumber)

    /** +91 98•••••345 — never log full phone numbers. */
    private fun String.masked(): String = if (length > 6) take(5) + "•".repeat(length - 8) + takeLast(3) else "•••"

    private companion object {
        const val TAG = "SafeRent-Auth"
        const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L
    }
}
