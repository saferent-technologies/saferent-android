package com.saferenttechnologies.android

import com.saferenttechnologies.android.core.ui.ResponsiveScale
import com.saferenttechnologies.android.core.ui.sdp
import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.presentation.account.PostLoginErrorScreen
import com.saferenttechnologies.android.presentation.account.PostLoginIntent
import com.saferenttechnologies.android.presentation.account.PostLoginViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saferenttechnologies.android.presentation.auth.AuthIntent
import com.saferenttechnologies.android.presentation.auth.AuthScreen
import com.saferenttechnologies.android.presentation.auth.AuthViewModel
import com.saferenttechnologies.android.presentation.profile.UserProfileIntent
import com.saferenttechnologies.android.presentation.home.HomeIntent
import com.saferenttechnologies.android.presentation.home.HomeScreen
import com.saferenttechnologies.android.presentation.home.HomeViewModel
import com.saferenttechnologies.android.presentation.profile.UserProfileScreen
import com.saferenttechnologies.android.presentation.profile.UserProfileViewModel
import com.saferenttechnologies.android.presentation.onboarding.OnboardingIntent
import com.saferenttechnologies.android.presentation.onboarding.OnboardingScreen
import com.saferenttechnologies.android.presentation.onboarding.OnboardingViewModel
import com.saferenttechnologies.android.presentation.splash.SplashScreen
import com.saferenttechnologies.android.presentation.splash.SplashViewModel
import com.saferenttechnologies.android.ui.theme.SafeRentTheme
import com.saferenttechnologies.android.core.session.BlockedAccountStore
import com.saferenttechnologies.android.presentation.blocked.AccountBlockedScreen
import com.saferenttechnologies.android.presentation.blocked.AccountBlockedViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val postLoginViewModel: PostLoginViewModel by viewModels()
    private val userProfileViewModel: UserProfileViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val accountBlockedViewModel: AccountBlockedViewModel by viewModels()

    @Inject
    lateinit var blockedAccountStore: BlockedAccountStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (colour only) stays until the saved splash is read → no flash.
        installSplashScreen().setKeepOnScreenCondition { !splashViewModel.state.value.isReady }
        super.onCreate(savedInstanceState)
        ResponsiveScale.update(resources.configuration) // re-read after rotation / fold / window resize
        if (BuildConfig.DEBUG) {
            Log.d("SafeRent-UI", "smallestWidth=${resources.configuration.smallestScreenWidthDp}dp → scale ${ResponsiveScale.factor}")
        }
        // Splash backgrounds (orange / near-black) both need light status-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            run {
                SafeRentTheme {
                    val splashState by splashViewModel.state.collectAsState()
                    val onboardingState by onboardingViewModel.state.collectAsState()
                    val authState by authViewModel.state.collectAsState()
                    val postLoginState by postLoginViewModel.state.collectAsState()
                    val userProfileState by userProfileViewModel.state.collectAsState()
                    val homeState by homeViewModel.state.collectAsState()
                    // Non-null = account deleted / deactivated / suspended (login or while using the app).
                    val blockedInfo by blockedAccountStore.info.collectAsState()
                    val blockedState by accountBlockedViewModel.state.collectAsState()

                    // After the splash (→ config ready on first launch) decide the next steps.
                    LaunchedEffect(splashState.isFinished) {
                        if (splashState.isFinished) {
                            onboardingViewModel.onIntent(OnboardingIntent.Load)
                            authViewModel.onIntent(AuthIntent.Load)
                        }
                    }
                    // Every sign-in / app start while signed in: phone/complete (+ FCM token) → auth/me.
                    LaunchedEffect(authState.isSignedIn) {
                        if (authState.isSignedIn) {
                            postLoginViewModel.onIntent(PostLoginIntent.Load)
                        }
                    }
                    // Android 13+: ask for notifications once signed in (push for rent reminders etc.).
                    // Denied → nothing changes; the app never blocks on it.
                    val notificationPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted -> Log.d("SafeRent-Push", "POST_NOTIFICATIONS granted=$granted") }
                    LaunchedEffect(authState.isSignedIn) {
                        if (authState.isSignedIn &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // UserProfile or straight to the active role's dashboard.
                    LaunchedEffect(postLoginState.destination) {
                        when (val destination = postLoginState.destination) {
                            is PostLoginDestination.UserProfile ->
                                userProfileViewModel.onIntent(UserProfileIntent.Start(destination))
                            is PostLoginDestination.Dashboard -> homeViewModel.onIntent(HomeIntent.Open(destination))
                            null -> Unit
                        }
                    }
                    // UserProfile saved / skipped → dashboard of the returned role.
                    LaunchedEffect(userProfileState.dashboard) {
                        userProfileState.dashboard?.let { homeViewModel.onIntent(HomeIntent.Open(it)) }
                    }

                    // Splash → Onboarding → Login/OTP → phone/complete → UserProfile? → Dashboard
                    val showSplash = !splashState.isFinished || !onboardingState.isLoaded ||
                        (onboardingState.isFinished && !authState.isLoaded)
                    val onLightScreen = !showSplash && onboardingState.isFinished
                    // Splash / onboarding always want light bar icons; login & later screens follow the
                    // system theme (dark icons on the light screens, light icons in dark mode).
                    LaunchedEffect(onLightScreen) {
                        if (onLightScreen) {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                            )
                        }
                    }

                    when {
                        showSplash -> SplashScreen(state = splashState)

                        // Above everything else. Try again → re-check; Sign out / back → login (never OTP).
                        blockedInfo != null -> AccountBlockedScreen(
                            state = blockedState,
                            effects = accountBlockedViewModel.effect,
                            onIntent = accountBlockedViewModel::onIntent,
                            onRetry = { postLoginViewModel.onIntent(PostLoginIntent.Retry) },
                        )

                        !onboardingState.isFinished -> OnboardingScreen(
                            state = onboardingState,
                            effects = onboardingViewModel.effect,
                            onIntent = onboardingViewModel::onIntent,
                        )

                        !authState.isSignedIn -> AuthScreen(
                            state = authState,
                            effects = authViewModel.effect,
                            onIntent = authViewModel::onIntent,
                        )

                        postLoginState.error != null -> PostLoginErrorScreen(
                            message = postLoginState.error.orEmpty(),
                            onIntent = postLoginViewModel::onIntent,
                        )

                        postLoginState.destination == null -> LoadingScreen()

                        postLoginState.destination is PostLoginDestination.UserProfile &&
                            userProfileState.dashboard == null ->
                            if (userProfileState.isStarted) {
                                UserProfileScreen(state = userProfileState, onIntent = userProfileViewModel::onIntent)
                            } else {
                                LoadingScreen()
                            }

                        homeState.user == null -> LoadingScreen()

                        else -> HomeScreen(state = homeState, onIntent = homeViewModel::onIntent)
                    }
                }
            }
        }
    }
}

/** Short wait between screens (themed so there is no flash). */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 3.sdp, modifier = Modifier.size(36.sdp))
    }
}
