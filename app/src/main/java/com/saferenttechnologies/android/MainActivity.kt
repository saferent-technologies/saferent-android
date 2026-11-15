package com.saferenttechnologies.android

import com.saferenttechnologies.android.presentation.announcements.AnnouncementsViewModel
import com.saferenttechnologies.android.presentation.announcements.AnnouncementsScreen
import com.saferenttechnologies.android.presentation.announcements.AnnouncementsIntent
import com.saferenttechnologies.android.presentation.requests.RequestsViewModel
import com.saferenttechnologies.android.presentation.requests.RequestsScreen
import com.saferenttechnologies.android.presentation.requests.RequestsIntent
import com.saferenttechnologies.android.presentation.tenant.TenantReviewGateScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.presentation.rent.TenantPaymentsViewModel
import com.saferenttechnologies.android.presentation.rent.TenantPaymentsScreen
import com.saferenttechnologies.android.presentation.rent.TenantPaymentsIntent
import com.saferenttechnologies.android.presentation.rent.TransactionsViewModel
import com.saferenttechnologies.android.presentation.rent.TransactionsScreen
import com.saferenttechnologies.android.presentation.rent.TransactionsIntent
import com.saferenttechnologies.android.presentation.rent.RentViewModel
import com.saferenttechnologies.android.presentation.rent.RentScreen
import com.saferenttechnologies.android.presentation.rent.RentIntent
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.presentation.manage.TenantsViewModel
import com.saferenttechnologies.android.presentation.manage.TenantsScreen
import com.saferenttechnologies.android.presentation.manage.TenantsIntent
import com.saferenttechnologies.android.presentation.common.HomeSkeleton
import com.saferenttechnologies.android.domain.model.toSummary
import com.saferenttechnologies.android.presentation.lease.LeaseDetailViewModel
import com.saferenttechnologies.android.presentation.lease.LeaseDetailScreen
import com.saferenttechnologies.android.presentation.lease.LeaseDetailIntent
import com.saferenttechnologies.android.presentation.invitation.InvitationIntent
import com.saferenttechnologies.android.presentation.invitation.InvitationScreen
import com.saferenttechnologies.android.presentation.invitation.InvitationViewModel
import com.saferenttechnologies.android.domain.model.DeepLinks
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.usecase.TenantHomeState
import com.saferenttechnologies.android.presentation.explore.ExploreScreen
import com.saferenttechnologies.android.presentation.explore.ExploreViewModel
import com.saferenttechnologies.android.presentation.listing.ListingDetailIntent
import com.saferenttechnologies.android.presentation.listing.ListingDetailScreen
import com.saferenttechnologies.android.presentation.listing.ListingDetailViewModel
import com.saferenttechnologies.android.presentation.tenant.MyApplicationsIntent
import com.saferenttechnologies.android.presentation.tenant.MyApplicationsScreen
import com.saferenttechnologies.android.presentation.tenant.MyApplicationsViewModel
import com.saferenttechnologies.android.presentation.tenant.TenantHomeIntent
import com.saferenttechnologies.android.presentation.tenant.TenantHomeViewModel
import android.content.Intent
import com.saferenttechnologies.android.data.push.PushPayload
import com.saferenttechnologies.android.domain.model.NavRoute
import com.saferenttechnologies.android.presentation.manage.DetailTab
import com.saferenttechnologies.android.presentation.notifications.InAppPushBanner
import com.saferenttechnologies.android.presentation.notifications.NotificationsEffect
import com.saferenttechnologies.android.presentation.notifications.NotificationsIntent
import com.saferenttechnologies.android.presentation.notifications.NotificationsScreen
import com.saferenttechnologies.android.presentation.notifications.NotificationsViewModel
import com.saferenttechnologies.android.core.ui.ResponsiveScale
import com.saferenttechnologies.android.core.ui.sdp
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.repository.PermissionManager
import com.saferenttechnologies.android.presentation.permission.LaunchPermissionsHost
import com.saferenttechnologies.android.presentation.permission.LaunchPermissionsViewModel
import com.saferenttechnologies.android.presentation.permission.LocalPermissionManager
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.AddPropertyIntent
import com.saferenttechnologies.android.presentation.property.AddPropertyScreen
import com.saferenttechnologies.android.presentation.property.AddPropertyViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.saferenttechnologies.android.presentation.manage.MyPropertiesIntent
import com.saferenttechnologies.android.presentation.manage.MyPropertiesScreen
import com.saferenttechnologies.android.presentation.manage.MyPropertiesViewModel
import com.saferenttechnologies.android.presentation.manage.PropertyDetailIntent
import com.saferenttechnologies.android.presentation.manage.PropertyDetailScreen
import com.saferenttechnologies.android.presentation.manage.PropertyDetailViewModel
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
    private val addPropertyViewModel: AddPropertyViewModel by viewModels()
    private val launchPermissionsViewModel: LaunchPermissionsViewModel by viewModels()
    private val myPropertiesViewModel: MyPropertiesViewModel by viewModels()
    private val propertyDetailViewModel: PropertyDetailViewModel by viewModels()
    private val notificationsViewModel: NotificationsViewModel by viewModels()
    private val tenantHomeViewModel: TenantHomeViewModel by viewModels()
    private val exploreViewModel: ExploreViewModel by viewModels()
    private val listingDetailViewModel: ListingDetailViewModel by viewModels()
    private val myApplicationsViewModel: MyApplicationsViewModel by viewModels()
    private val invitationViewModel: InvitationViewModel by viewModels()
    private val leaseDetailViewModel: LeaseDetailViewModel by viewModels()
    private val tenantsViewModel: TenantsViewModel by viewModels()
    private val rentViewModel: RentViewModel by viewModels()
    private val transactionsViewModel: TransactionsViewModel by viewModels()
    private val tenantPaymentsViewModel: TenantPaymentsViewModel by viewModels()
    private val requestsViewModel: RequestsViewModel by viewModels()
    private val announcementsViewModel: AnnouncementsViewModel by viewModels()

    @Inject
    lateinit var permissionManager: PermissionManager

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
        // Tapped a notification while the app was closed (system tray → launcher with data extras).
        if (savedInstanceState == null) handleNotificationIntent(intent)
        setContent {
            CompositionLocalProvider(LocalPermissionManager provides permissionManager) {
                SafeRentTheme {
                    val splashState by splashViewModel.state.collectAsState()
                    val onboardingState by onboardingViewModel.state.collectAsState()
                    val authState by authViewModel.state.collectAsState()
                    val postLoginState by postLoginViewModel.state.collectAsState()
                    val userProfileState by userProfileViewModel.state.collectAsState()
                    val homeState by homeViewModel.state.collectAsState()
                    val addPropertyState by addPropertyViewModel.state.collectAsState()
                    val myPropertiesState by myPropertiesViewModel.state.collectAsState()
                    val propertyDetailState by propertyDetailViewModel.state.collectAsState()
                    val notificationsState by notificationsViewModel.state.collectAsState()
                    val tenantHomeState by tenantHomeViewModel.state.collectAsState()
                    val exploreState by exploreViewModel.state.collectAsState()
                    val listingDetailState by listingDetailViewModel.state.collectAsState()
                    val myApplicationsState by myApplicationsViewModel.state.collectAsState()
                    val invitationState by invitationViewModel.state.collectAsState()
                    val leaseDetailState by leaseDetailViewModel.state.collectAsState()
                    val tenantsState by tenantsViewModel.state.collectAsState()
                    val rentState by rentViewModel.state.collectAsState()
                    val transactionsState by transactionsViewModel.state.collectAsState()
                    val tenantPaymentsState by tenantPaymentsViewModel.state.collectAsState()
                    val requestsState by requestsViewModel.state.collectAsState()
                    val announcementsState by announcementsViewModel.state.collectAsState()
                    // Notification links → screens (only property detail exists today).
                    LaunchedEffect(Unit) {
                        notificationsViewModel.effect.collect { effect ->
                            when (effect) {
                                is NotificationsEffect.LinkUnavailable -> toast(
                                    if (effect.notInApp) R.string.notifications_not_available else R.string.deep_link_not_for_account,
                                )
                                // Wrong role (a tenant opening /properties/{id} …) → message, stay home.
                                is NotificationsEffect.Navigate -> if (effect.fromLink &&
                                    !DeepLinks.allowedFor(effect.route, homeViewModel.state.value.activeRole == UserRole.TENANT)
                                ) {
                                    toast(R.string.deep_link_not_for_account)
                                } else when (val route = effect.route) {
                                    is NavRoute.PropertyDetail -> propertyDetailViewModel.also { openedFromLink = effect.fromLink }.onIntent(
                                        PropertyDetailIntent.Open(route.propertyId, DetailTab.fromLink(route.tab)),
                                    )
                                    is NavRoute.ListingDetail -> listingDetailViewModel.onIntent(ListingDetailIntent.Open(route.listingId, route.unitId))
                                    is NavRoute.Invitation -> invitationViewModel.onIntent(InvitationIntent.Open(route.token))
                                    is NavRoute.LeaseDetail -> leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(route.leaseId))
                                    // "/leases" (close-request pushes): tenant → their lease; landlord → properties (leases live per property).
                                    // Rent pushes: landlord → Rent (proofs to check first); tenant → their payments.
                                    NavRoute.Rent, NavRoute.RentRequests -> if (homeViewModel.state.value.activeRole == UserRole.TENANT) {
                                        (tenantHomeViewModel.state.value.home as? TenantHomeState.Linked)?.lease?.let { l ->
                                            tenantPaymentsViewModel.onIntent(
                                                TenantPaymentsIntent.Open(l.id, listOf(l.propertyTitle, l.place).filter { it.isNotBlank() }.joinToString(" · ")),
                                            )
                                        }
                                    } else {
                                        rentViewModel.onIntent(RentIntent.Open(RentBucket.EXPECTED, null, requests = route == NavRoute.RentRequests))
                                    }
                                    // "/maintenance/{id}" → that request; "/maintenance" → the list (role decides tenant / landlord).
                                    is NavRoute.RequestDetail -> requestsViewModel.onIntent(
                                        RequestsIntent.OpenDetail(route.id, landlord = homeViewModel.state.value.activeRole != UserRole.TENANT),
                                    )
                                    is NavRoute.Announcements -> {
                                        val landlord = homeViewModel.state.value.activeRole != UserRole.TENANT
                                        announcementsViewModel.onIntent(
                                            route.id?.let { AnnouncementsIntent.OpenDetail(it, landlord) } ?: AnnouncementsIntent.Open(landlord),
                                        )
                                    }
                                    NavRoute.Maintenance -> requestsViewModel.onIntent(RequestsIntent.Open(landlord = homeViewModel.state.value.activeRole != UserRole.TENANT))
                                    NavRoute.Leases -> if (homeViewModel.state.value.activeRole == UserRole.TENANT) {
                                        // Tenant has one active lease: open it (wait for the dashboard's lease if still loading).
                                        launch {
                                            if (tenantHomeViewModel.state.value.home == null) tenantHomeViewModel.onIntent(TenantHomeIntent.Start)
                                            val home = tenantHomeViewModel.state.first { it.home != null && !it.loading }.home
                                            (home as? TenantHomeState.Linked)?.lease?.let {
                                                leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(it.id, it.toSummary()))
                                            }
                                        }
                                    } else {
                                        // Landlord: no lease id in the link → the tenant who asked to close.
                                        tenantsViewModel.onIntent(TenantsIntent.OpenCloseRequest)
                                    }
                                    NavRoute.MyProperties -> myPropertiesViewModel.onIntent(MyPropertiesIntent.Open(PropertyFilter.ALL))
                                    // Tenant → my applications; landlord → their properties (applications live per property).
                                    NavRoute.Applications ->
                                        if (homeViewModel.state.value.activeRole == UserRole.TENANT) {
                                            myApplicationsViewModel.onIntent(MyApplicationsIntent.Open)
                                        } else {
                                            myPropertiesViewModel.onIntent(MyPropertiesIntent.Open(PropertyFilter.ALL))
                                        }
                                    NavRoute.Explore -> if (homeViewModel.state.value.activeRole == UserRole.TENANT) {
                                        tenantHomeViewModel.onIntent(TenantHomeIntent.OpenExplore)
                                    }
                                    else -> Unit // Home / Notifications: nothing to open on top
                                }
                            }
                        }
                    }
                    // Something changed on a property → list + dashboard numbers reload.
                    val onPropertyChanged = {
                        myPropertiesViewModel.onIntent(MyPropertiesIntent.Invalidate)
                        homeViewModel.onIntent(HomeIntent.RefreshSummary)
                    }
                    // Wizard closed (new property or an edit) → refresh what's on screen.
                    LaunchedEffect(addPropertyState.isOpen) {
                        if (!addPropertyState.isOpen) {
                            if (propertyDetailState.isOpen) propertyDetailViewModel.onIntent(PropertyDetailIntent.Refresh)
                            onPropertyChanged()
                        }
                    }
                    // "Add property": location popup first (the flow still opens without it).
                    val locationForAddProperty = rememberPermissionRequester(AppPermission.LOCATION)
                    // Non-null = account deleted / deactivated / suspended (login or while using the app).
                    val blockedInfo by blockedAccountStore.info.collectAsState()
                    val blockedState by accountBlockedViewModel.state.collectAsState()
                    // Opened from a notification → once the dashboard is ready (after splash / login).
                    val dashboardReady = homeState.user != null && blockedInfo == null
                    LaunchedEffect(dashboardReady, notificationsState.hasPending) {
                        if (dashboardReady && notificationsState.hasPending) {
                            notificationsViewModel.onIntent(NotificationsIntent.ConsumePending)
                        }
                    }

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

                    Box(Modifier.fillMaxSize()) {
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

                        addPropertyState.isOpen -> AddPropertyScreen(
                            state = addPropertyState,
                            onIntent = addPropertyViewModel::onIntent,
                        )

                        // Above the property screen: a tenant card opens the lease on top, Back returns.
                        leaseDetailState.isOpen -> LeaseDetailScreen(
                            state = leaseDetailState,
                            effects = leaseDetailViewModel.effect,
                            onIntent = leaseDetailViewModel::onIntent,
                            onChanged = {
                                tenantHomeViewModel.onIntent(TenantHomeIntent.Refresh)
                                if (propertyDetailState.isOpen) propertyDetailViewModel.onIntent(PropertyDetailIntent.Refresh)
                                tenantsViewModel.onIntent(TenantsIntent.Refresh)
                                onPropertyChanged()
                            },
                        )

                        // Landlord → this month's rent (Expected / Collected / Overdue).
                        rentState.isOpen -> RentScreen(state = rentState, effects = rentViewModel.effect, onIntent = rentViewModel::onIntent)

                        // Requests (maintenance / service / complaint): tenant raises, landlord updates.
                        // Announcements: landlord sends + sees who opened; tenant reads (view recorded on open).
                        announcementsState.isOpen -> AnnouncementsScreen(
                            state = announcementsState,
                            effects = announcementsViewModel.effect,
                            onIntent = announcementsViewModel::onIntent,
                        )

                        requestsState.isOpen -> RequestsScreen(state = requestsState, effects = requestsViewModel.effect, onIntent = requestsViewModel::onIntent)

                        // Tenant → deposit + rent history.
                        tenantPaymentsState.isOpen -> TenantPaymentsScreen(
                            state = tenantPaymentsState,
                            effects = tenantPaymentsViewModel.effect,
                            onIntent = tenantPaymentsViewModel::onIntent,
                        )

                        // Landlord → all payments / refunds.
                        transactionsState.isOpen -> TransactionsScreen(
                            state = transactionsState,
                            effects = transactionsViewModel.effect,
                            onIntent = transactionsViewModel::onIntent,
                        )

                        // Landlord → all tenants (under the lease screen it opens).
                        tenantsState.isOpen -> TenantsScreen(
                            state = tenantsState,
                            onIntent = tenantsViewModel::onIntent,
                            effects = tenantsViewModel.effect,
                            onOpenLease = { l ->
                                leaseDetailViewModel.onIntent(
                                    LeaseDetailIntent.Open(
                                        l.id,
                                        LeaseSummary(
                                            id = l.id,
                                            status = l.status,
                                            propertyTitle = l.propertyTitle,
                                            place = listOf(l.place, l.placeCode).filter { it.isNotBlank() }.joinToString(" · "),
                                            monthlyRent = l.monthlyRent,
                                            securityDeposit = l.securityDeposit,
                                            rentDueDay = l.rentDueDay,
                                            startDate = l.startDate,
                                            endDate = l.endDate,
                                            otherName = l.tenantName,
                                        ),
                                    ),
                                )
                            },
                        )

                        propertyDetailState.isOpen -> PropertyDetailScreen(
                            state = propertyDetailState,
                            effects = propertyDetailViewModel.effect,
                            onIntent = propertyDetailViewModel::onIntent,
                            onOpenEditor = { id, step, newUnit -> addPropertyViewModel.onIntent(AddPropertyIntent.Edit(id, step, newUnit)) },
                            onChanged = onPropertyChanged,
                            onNotFound = {
                                propertyDetailViewModel.onIntent(PropertyDetailIntent.Close)
                                // App link → stay home with a note; notification → back to the list.
                                if (openedFromLink) toast(R.string.deep_link_not_for_account) else notificationsViewModel.onIntent(NotificationsIntent.ItemGone)
                            },
                            onOpenLease = { id, preview -> leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(id, preview)) },
                            onReviewLease = { id -> leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(id, startReview = true)) },
                        )

                        myPropertiesState.isOpen -> MyPropertiesScreen(
                            state = myPropertiesState,
                            onIntent = myPropertiesViewModel::onIntent,
                            onOpenProperty = { propertyDetailViewModel.onIntent(PropertyDetailIntent.Open(it)) },
                            onEditDraft = { addPropertyViewModel.onIntent(AddPropertyIntent.ResumeDraft(it)) },
                            onAddProperty = {
                                locationForAddProperty.request { addPropertyViewModel.onIntent(AddPropertyIntent.Open) }
                            },
                        )

                        listingDetailState.isOpen -> ListingDetailScreen(
                            state = listingDetailState,
                            effects = listingDetailViewModel.effect,
                            onIntent = listingDetailViewModel::onIntent,
                            onApplicationsChanged = {
                                tenantHomeViewModel.onIntent(TenantHomeIntent.Refresh)
                                myApplicationsViewModel.onIntent(MyApplicationsIntent.Refresh)
                            },
                        )

                        // Under the listing: "View the home" opens the listing on top, Back returns here.
                        invitationState.isOpen -> InvitationScreen(
                            state = invitationState,
                            effects = invitationViewModel.effect,
                            onIntent = invitationViewModel::onIntent,
                            onChanged = {
                                tenantHomeViewModel.onIntent(TenantHomeIntent.Refresh)
                                myApplicationsViewModel.onIntent(MyApplicationsIntent.Refresh)
                            },
                            onOpenHome = { listingDetailViewModel.onIntent(ListingDetailIntent.Open(it, null)) },
                        )

                        myApplicationsState.isOpen -> MyApplicationsScreen(
                            state = myApplicationsState,
                            onIntent = myApplicationsViewModel::onIntent,
                            onOpenListing = { listingDetailViewModel.onIntent(ListingDetailIntent.Open(it)) },
                        )

                        notificationsState.isOpen -> NotificationsScreen(
                            state = notificationsState,
                            onIntent = notificationsViewModel::onIntent,
                        )

                        // Tenant: an ended lease not reviewed yet → the review (+ deposit refund) screen first, no skip.
                        homeState.activeRole == UserRole.TENANT && tenantHomeState.reviewQueue.isNotEmpty() -> {
                            TenantReviewGateScreen(
                                due = tenantHomeState.reviewQueue.first(),
                                left = tenantHomeState.reviewQueue.size,
                                state = tenantHomeState.gate,
                                effects = tenantHomeViewModel.effect,
                                onIntent = tenantHomeViewModel::onIntent,
                            )
                        }

                        // Tenant: active lease → dashboard placeholder; none (or lease check failed) → Explore.
                        homeState.activeRole == UserRole.TENANT && (tenantHomeState.showExplore || (tenantHomeState.home == null && tenantHomeState.error != null)) -> {
                            LaunchedEffect(Unit) { tenantHomeViewModel.onIntent(TenantHomeIntent.Start) }
                            val linked = tenantHomeState.home is TenantHomeState.Linked
                            ExploreScreen(
                                state = exploreState,
                                effects = exploreViewModel.effect,
                                onIntent = exploreViewModel::onIntent,
                                firstName = homeState.user?.fullName?.trim().orEmpty(),
                                unreadNotifications = notificationsState.unread,
                                onOpenNotifications = { notificationsViewModel.onIntent(NotificationsIntent.Open) },
                                onOpenListing = { listingDetailViewModel.onIntent(ListingDetailIntent.Open(it)) },
                                pendingReviews = tenantHomeState.pendingReviews,
                                hasApplications = tenantHomeState.hasApplications,
                                onOpenApplications = { myApplicationsViewModel.onIntent(MyApplicationsIntent.Open) },
                                onBack = if (linked) ({ tenantHomeViewModel.onIntent(TenantHomeIntent.CloseExplore) }) else null,
                                menu = if (linked) emptyList() else buildList<Pair<String, () -> Unit>> {
                                    homeState.roles.filter { it != homeState.activeRole }.forEach { role ->
                                        add(getString(R.string.explore_switch_to, roleName(role)) to { homeViewModel.onIntent(HomeIntent.SwitchRole(role)) })
                                    }
                                    add(getString(R.string.explore_sign_out) to { homeViewModel.onIntent(HomeIntent.SignOut) })
                                },
                            )
                            LaunchPermissionsHost(launchPermissionsViewModel)
                            LifecycleResumeEffect(Unit) {
                                homeViewModel.onIntent(HomeIntent.Visible)
                                notificationsViewModel.onIntent(NotificationsIntent.RefreshBadge)
                                onPauseOrDispose { }
                            }
                        }

                        homeState.activeRole == UserRole.TENANT && tenantHomeState.home == null -> {
                            LaunchedEffect(Unit) { tenantHomeViewModel.onIntent(TenantHomeIntent.Start) }
                            HomeSkeleton(Modifier.statusBarsPadding())
                        }

                        else -> {
                            HomeScreen(
                                state = homeState,
                                onIntent = homeViewModel::onIntent,
                                onAddProperty = {
                                    locationForAddProperty.request { addPropertyViewModel.onIntent(AddPropertyIntent.Open) }
                                },
                                onOpenProperties = { myPropertiesViewModel.onIntent(MyPropertiesIntent.Open(it)) },
                                unreadNotifications = notificationsState.unread,
                                onOpenNotifications = { notificationsViewModel.onIntent(NotificationsIntent.Open) },
                                tenantLease = (tenantHomeState.home as? TenantHomeState.Linked)?.lease
                                    ?.takeIf { homeState.activeRole == UserRole.TENANT },
                                onExploreHomes = { tenantHomeViewModel.onIntent(TenantHomeIntent.OpenExplore) },
                                onOpenLease = { leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(it.id, it.toSummary())) },
                                leaseCloseRequested = tenantHomeState.closeRequested,
                                onOpenTenants = { tenantsViewModel.onIntent(TenantsIntent.Open) },
                                onOpenRent = { bucket, period -> rentViewModel.onIntent(RentIntent.Open(bucket, period)) },
                                onOpenTransactions = { transactionsViewModel.onIntent(TransactionsIntent.Open) },
                                tenantPayments = tenantHomeState.payments,
                                requestsBadge = if (homeState.activeRole == UserRole.TENANT) requestsState.summary.myOpen else requestsState.summary.needsAction,
                                onOpenRequests = { requestsViewModel.onIntent(RequestsIntent.Open(landlord = homeState.activeRole != UserRole.TENANT)) },
                                onOpenAnnouncements = { announcementsViewModel.onIntent(AnnouncementsIntent.Open(landlord = homeState.activeRole != UserRole.TENANT)) },
                                onRaiseRequest = { l ->
                                    requestsViewModel.onIntent(RequestsIntent.OpenRaise(listOf(l.propertyTitle, l.place).filter { it.isNotBlank() }.joinToString(" · ")))
                                },
                                onOpenPayments = { l ->
                                    tenantPaymentsViewModel.onIntent(
                                        TenantPaymentsIntent.Open(l.id, listOf(l.propertyTitle, l.place).filter { it.isNotBlank() }.joinToString(" · ")),
                                    )
                                },
                            )
                            // Signed in + active: our popups for location, then notifications (one at a time).
                            LaunchPermissionsHost(launchPermissionsViewModel)
                            // Every time the dashboard is shown / comes back: FCM token check (updates the server only if it changed).
                            LifecycleResumeEffect(Unit) {
                                homeViewModel.onIntent(HomeIntent.Visible)
                                notificationsViewModel.onIntent(NotificationsIntent.RefreshBadge)
                                requestsViewModel.onIntent(RequestsIntent.RefreshSummary(homeState.activeRole != UserRole.TENANT))
                                onPauseOrDispose { }
                            }
                        }
                    }
                    // Push while the app is open (and signed in) → "Title — tap to open".
                    if (dashboardReady) {
                        InAppPushBanner(
                            push = notificationsState.banner,
                            onOpen = { notificationsViewModel.onIntent(NotificationsIntent.OpenBanner) },
                            onDismiss = { notificationsViewModel.onIntent(NotificationsIntent.DismissBanner) },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Notification tap → remember link + id; opened once the session / dashboard is ready. */
    /** Set when the property screen was opened by an App Link (a 404 then goes home with a toast). */
    private var openedFromLink = false

    private fun toast(@androidx.annotation.StringRes res: Int) = android.widget.Toast.makeText(this, res, android.widget.Toast.LENGTH_LONG).show()

    private fun handleNotificationIntent(intent: Intent?) {
        intent ?: return
        // Deep link (https://…saferenttechnologies.com/homes/{id}, saferent://homes/{id} …):
        // same routing as a notification link, opened once the session / dashboard is ready.
        DeepLinks.linkOf(intent.action, intent.data?.scheme, intent.data?.host, intent.data?.encodedPath, intent.data?.encodedQuery)?.let { link ->
            notificationsViewModel.onIntent(NotificationsIntent.FromLink(link))
            intent.data = null // handled once (recreate / back doesn't reopen it)
            return
        }
        val link = intent.getStringExtra(PushPayload.EXTRA_LINK)
        val id = intent.getStringExtra(PushPayload.EXTRA_NOTIFICATION_ID)
        if (link == null && id == null) return
        notificationsViewModel.onIntent(NotificationsIntent.FromPush(link, id, intent.getStringExtra(PushPayload.EXTRA_TYPE)))
        // Handled once — a later recreate must not open it again.
        intent.removeExtra(PushPayload.EXTRA_LINK)
        intent.removeExtra(PushPayload.EXTRA_NOTIFICATION_ID)
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

private fun android.content.Context.roleName(role: UserRole): String =
    getString(if (role == UserRole.LANDLORD) R.string.role_landlord else R.string.role_tenant)
