package com.saferenttechnologies.android

import com.saferenttechnologies.android.presentation.support.SupportViewModel
import com.saferenttechnologies.android.presentation.support.SupportScreen
import com.saferenttechnologies.android.presentation.support.SupportIntent
import com.saferenttechnologies.android.presentation.account.AccountLink
import com.saferenttechnologies.android.presentation.account.AccountIntent
import com.saferenttechnologies.android.presentation.account.AccountViewModel
import com.saferenttechnologies.android.presentation.account.AccountScreen
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import com.saferenttechnologies.android.presentation.common.AppTabs
import com.saferenttechnologies.android.presentation.common.AppTab
import com.saferenttechnologies.android.presentation.common.AppBottomBar
import com.saferenttechnologies.android.presentation.lease.TenantLeasesViewModel
import com.saferenttechnologies.android.presentation.lease.TenantLeasesScreen
import com.saferenttechnologies.android.presentation.lease.TenantLeasesIntent
import com.saferenttechnologies.android.presentation.homealerts.HomeAlertViewModel
import com.saferenttechnologies.android.presentation.homealerts.HomeAlertIntent
import com.saferenttechnologies.android.presentation.homealerts.HomeAlertHost
import com.saferenttechnologies.android.presentation.documents.DocumentsViewModel
import com.saferenttechnologies.android.presentation.documents.DocumentsScreen
import com.saferenttechnologies.android.presentation.documents.DocumentsIntent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
    private val documentsViewModel: DocumentsViewModel by viewModels()
    private val homeAlertViewModel: HomeAlertViewModel by viewModels()
    private val tenantLeasesViewModel: TenantLeasesViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val supportViewModel: SupportViewModel by viewModels()
    private val tenantCheckViewModel: com.saferenttechnologies.android.presentation.tenantcheck.TenantCheckViewModel by viewModels()
    private val kycViewModel: com.saferenttechnologies.android.presentation.kyc.KycViewModel by viewModels()
    private val ownerDetailsViewModel: com.saferenttechnologies.android.presentation.owner.OwnerDetailsViewModel by viewModels()
    private val ownershipViewModel: com.saferenttechnologies.android.presentation.owner.OwnershipViewModel by viewModels()
    private val inspectionsViewModel: com.saferenttechnologies.android.presentation.inspection.InspectionsViewModel by viewModels()
    private val registerViewModel: com.saferenttechnologies.android.presentation.furnishings.RegisterViewModel by viewModels()
    private val tenantVisitsViewModel: com.saferenttechnologies.android.presentation.visits.TenantVisitsViewModel by viewModels()
    private val hostVisitsViewModel: com.saferenttechnologies.android.presentation.visits.HostVisitsViewModel by viewModels()
    private val safetyViewModel: com.saferenttechnologies.android.presentation.safety.SafetyViewModel by viewModels()
    private val emergencyViewModel: com.saferenttechnologies.android.presentation.safety.EmergencyViewModel by viewModels()
    private val complianceViewModel: com.saferenttechnologies.android.presentation.compliance.ComplianceViewModel by viewModels()
    private val leaseComplianceViewModel: com.saferenttechnologies.android.presentation.compliance.LeaseComplianceViewModel by viewModels()
    private val societyViewModel: com.saferenttechnologies.android.presentation.society.SocietyViewModel by viewModels()
    private val leaseSocietyViewModel: com.saferenttechnologies.android.presentation.society.LeaseSocietyViewModel by viewModels()
    private val comparisonViewModel: com.saferenttechnologies.android.presentation.smart.ComparisonViewModel by viewModels()
    private val qrViewModel: com.saferenttechnologies.android.presentation.smart.QrViewModel by viewModels()
    private val reminderViewModel: com.saferenttechnologies.android.presentation.smart.ReminderViewModel by viewModels()
    private val tenantFurnitureViewModel: com.saferenttechnologies.android.presentation.furnishings.TenantFurnitureViewModel by viewModels()

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
                    val documentsState by documentsViewModel.state.collectAsState()
                    val homeAlertState by homeAlertViewModel.state.collectAsState()
                    val tenantLeasesState by tenantLeasesViewModel.state.collectAsState()
                    val accountState by accountViewModel.state.collectAsState()
                    val supportState by supportViewModel.state.collectAsState()
                    val tenantCheckState by tenantCheckViewModel.state.collectAsState()
                    val kycState by kycViewModel.state.collectAsState()
                    val ownerDetailsState by ownerDetailsViewModel.state.collectAsState()
                    val ownershipState by ownershipViewModel.state.collectAsState()
                    val inspectionsState by inspectionsViewModel.state.collectAsState()
                    val registerState by registerViewModel.state.collectAsState()
                    val tenantVisitsState by tenantVisitsViewModel.state.collectAsState()
                    val hostVisitsState by hostVisitsViewModel.state.collectAsState()
                    val safetyState by safetyViewModel.state.collectAsState()
                    val emergencyState by emergencyViewModel.state.collectAsState()
                    val complianceState by complianceViewModel.state.collectAsState()
                    val leaseComplianceState by leaseComplianceViewModel.state.collectAsState()
                    val societyState by societyViewModel.state.collectAsState()
                    val leaseSocietyState by leaseSocietyViewModel.state.collectAsState()
                    val comparisonState by comparisonViewModel.state.collectAsState()
                    val qrState by qrViewModel.state.collectAsState()
                    val reminderState by reminderViewModel.state.collectAsState()
                    // QR action that needs the property loaded first (team: inspection / furniture).
                    var qrPending by remember { mutableStateOf<Pair<String, String>?>(null) }
                    val notSafeRentQr = stringResource(R.string.qr_not_saferent)
                    LaunchedEffect(Unit) {
                        qrViewModel.effect.collect { e ->
                            if (e is com.saferenttechnologies.android.presentation.smart.QrEffect.Message) {
                                android.widget.Toast.makeText(this@MainActivity, if (e.text == com.saferenttechnologies.android.presentation.smart.QrViewModel.NOT_SAFERENT) notSafeRentQr else e.text, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    // Lease open → its Society card (contacts, my parking, society approval).
                    LaunchedEffect(leaseDetailState.isOpen, leaseDetailState.summary?.id) {
                        val id = leaseDetailState.summary?.id
                        if (leaseDetailState.isOpen && id != null) leaseSocietyViewModel.onIntent(com.saferenttechnologies.android.presentation.society.LeaseSocietyIntent.Load(id))
                        else if (!leaseDetailState.isOpen) leaseSocietyViewModel.onIntent(com.saferenttechnologies.android.presentation.society.LeaseSocietyIntent.Clear)
                    }
                    LaunchedEffect(Unit) {
                        leaseSocietyViewModel.effect.collect { e ->
                            if (e is com.saferenttechnologies.android.presentation.society.LeaseSocietyEffect.Message && e.text.isNotBlank()) {
                                android.widget.Toast.makeText(this@MainActivity, e.text, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    // Lease open → agreement + police verification (landlord and tenant).
                    LaunchedEffect(leaseDetailState.isOpen, leaseDetailState.summary?.id, leaseDetailState.summary?.propertyId) {
                        val s = leaseDetailState.summary
                        if (leaseDetailState.isOpen && s != null) {
                            leaseComplianceViewModel.onIntent(com.saferenttechnologies.android.presentation.compliance.LeaseComplianceIntent.Load(s.id, s.propertyId))
                        } else if (!leaseDetailState.isOpen) {
                            leaseComplianceViewModel.onIntent(com.saferenttechnologies.android.presentation.compliance.LeaseComplianceIntent.Clear)
                        }
                    }
                    LaunchedEffect(Unit) {
                        leaseComplianceViewModel.effect.collect { e ->
                            if (e is com.saferenttechnologies.android.presentation.compliance.LeaseComplianceEffect.Message && e.text.isNotBlank()) {
                                android.widget.Toast.makeText(this@MainActivity, e.text, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    // Tenant without an active lease → the saved Emergency copy goes.
                    LaunchedEffect(tenantHomeState.home) {
                        if (tenantHomeState.home == com.saferenttechnologies.android.domain.usecase.TenantHomeState.Explore) {
                            emergencyViewModel.onIntent(com.saferenttechnologies.android.presentation.safety.EmergencyIntent.Forget)
                        }
                    }
                    // Listing opened → is visit booking on? (and my upcoming visit).
                    LaunchedEffect(listingDetailState.isOpen, listingDetailState.listingId) {
                        val id = listingDetailState.listingId
                        if (listingDetailState.isOpen && id != null) {
                            tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.LoadForListing(id))
                        }
                    }
                    // Back from the document vault during "Share documents with your owner" → fresh list + ticks.
                    LaunchedEffect(documentsState.isOpen) {
                        if (!documentsState.isOpen && invitationState.sharingStep) {
                            invitationViewModel.onIntent(com.saferenttechnologies.android.presentation.invitation.InvitationIntent.RefreshVault)
                        }
                    }
                    val tenantFurnitureState by tenantFurnitureViewModel.state.collectAsState()
                    // Furniture register places: building first, then flats / rooms (with their furnishing).
                    fun registerPlaces(units: List<com.saferenttechnologies.android.domain.model.PropertyUnit>, rooms: List<com.saferenttechnologies.android.domain.model.PropertyRoom>, word: String) =
                        listOf(com.saferenttechnologies.android.presentation.furnishings.RegisterPlace(label = getString(R.string.listing_building_video))) +
                            units.map { com.saferenttechnologies.android.presentation.furnishings.RegisterPlace(unitId = it.id, label = "$word ${it.unitNumber}".trim(), furnishing = it.values["furnishing"].orEmpty()) } +
                            rooms.map { com.saferenttechnologies.android.presentation.furnishings.RegisterPlace(roomId = it.id, label = getString(R.string.add_property_room_n, it.roomNumber), furnishing = it.values["furnishing"].orEmpty()) }
                    // QR team actions: open the property, then its inspections / furniture once it's loaded.
                    LaunchedEffect(qrPending, propertyDetailState.detail, propertyDetailState.propertyId) {
                        val (action, pid) = qrPending ?: return@LaunchedEffect
                        val d = propertyDetailState.detail ?: return@LaunchedEffect
                        if (propertyDetailState.propertyId != pid) return@LaunchedEffect
                        qrPending = null
                        val word = propertyDetailState.unitWord
                        if (action == "OPEN_FURNITURE") {
                            registerViewModel.onIntent(
                                com.saferenttechnologies.android.presentation.furnishings.RegisterIntent.Open(
                                    propertyId = pid, title = d.property.title, canManage = d.can.manage,
                                    places = registerPlaces(d.property.units, d.property.rooms, word),
                                ),
                            )
                        } else {
                            inspectionsViewModel.onIntent(
                                com.saferenttechnologies.android.presentation.inspection.InspectionsIntent.Open(
                                    propertyId = pid,
                                    title = d.property.title,
                                    canManage = d.can.manage || d.can.inspect,
                                    units = d.property.units.map { com.saferenttechnologies.android.presentation.inspection.InspectionPlace(it.id, "$word ${it.unitNumber}".trim()) },
                                    rooms = d.property.rooms.map { com.saferenttechnologies.android.presentation.inspection.InspectionPlace(it.id, getString(R.string.add_property_room_n, it.roomNumber)) },
                                    leases = propertyDetailState.activeLeases.map { l ->
                                        com.saferenttechnologies.android.presentation.inspection.InspectionLease(
                                            l.id, listOf(l.tenantName, l.place).filter { it.isNotBlank() }.joinToString(" · "), l.unitId, l.bedId,
                                        )
                                    },
                                ),
                            )
                            // "Start inspection" → straight to scheduling a new one.
                            if (d.can.manage || d.can.inspect) {
                                inspectionsViewModel.onIntent(com.saferenttechnologies.android.presentation.inspection.InspectionsIntent.StartSchedule)
                            }
                        }
                    }
                    // Scan result: "<title> · <flat>" + one button per allowed action.
                    qrState.resolution?.let { r ->
                        com.saferenttechnologies.android.presentation.smart.QrResultSheet(
                            r,
                            onAction = { a ->
                                qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.DismissResult)
                                when (a) {
                                    "OPEN_PROPERTY" -> r.propertyId?.let { propertyDetailViewModel.onIntent(PropertyDetailIntent.Open(it)) }
                                    "START_INSPECTION", "OPEN_FURNITURE" -> r.propertyId?.let { pid ->
                                        qrPending = a to pid
                                        propertyDetailViewModel.onIntent(PropertyDetailIntent.Open(pid))
                                    }
                                    "OPEN_LEASE" -> r.leaseId?.let { leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(it)) }
                                    "RAISE_REQUEST" -> requestsViewModel.onIntent(RequestsIntent.OpenRaise(r.title))
                                    "EMERGENCY" -> r.leaseId?.let { emergencyViewModel.onIntent(com.saferenttechnologies.android.presentation.safety.EmergencyIntent.Open(it)) }
                                    "VIEW_LISTING" -> r.propertyId?.let { listingDetailViewModel.onIntent(ListingDetailIntent.Open(it, r.unitId)) }
                                    "BOOK_VISIT" -> r.propertyId?.let { pid ->
                                        listingDetailViewModel.onIntent(ListingDetailIntent.Open(pid, r.unitId))
                                        tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.StartBooking(pid, r.title, emptyList()))
                                    }
                                }
                            },
                            onDismiss = { qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.DismissResult) },
                        )
                    }
                    // Wizard flat panel → "Furniture & appliances" for that flat (change request copy on a live listing).
                    LaunchedEffect(addPropertyState.openFurniture) {
                        val target = addPropertyState.openFurniture ?: return@LaunchedEffect
                        val p = addPropertyState.property
                        if (p != null) {
                            val places = registerPlaces(p.units, p.rooms, addPropertyState.unitWord)
                            registerViewModel.onIntent(
                                com.saferenttechnologies.android.presentation.furnishings.RegisterIntent.Open(
                                    propertyId = p.id, title = p.title, canManage = true, places = places,
                                    draft = addPropertyState.isLiveEdit,
                                    focus = places.firstOrNull { it.unitId == target.unitId && it.roomId == target.roomId && (target.unitId != null || target.roomId != null) },
                                ),
                            )
                        }
                        addPropertyViewModel.onIntent(AddPropertyIntent.FurnitureOpened)
                    }
                    // Ownership messages ("Ownership details saved", errors) wherever the form is.
                    LaunchedEffect(Unit) {
                        ownershipViewModel.effect.collect { e ->
                            if (e is com.saferenttechnologies.android.presentation.owner.OwnershipEffect.Message) {
                                android.widget.Toast.makeText(this@MainActivity, e.text, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    // Wizard reached the Ownership step → load that property's ownership.
                    LaunchedEffect(addPropertyState.isOpen, addPropertyState.step, addPropertyState.property?.id) {
                        val pid = addPropertyState.property?.id
                        if (addPropertyState.isOpen && pid != null && addPropertyState.step == com.saferenttechnologies.android.domain.model.WizardStep.OWNERSHIP) {
                            ownershipViewModel.onIntent(com.saferenttechnologies.android.presentation.owner.OwnershipIntent.Load(pid))
                        }
                    }
                    // Owner's property screen → its Ownership card (fresh each time it opens).
                    LaunchedEffect(propertyDetailState.isOpen, propertyDetailState.propertyId, propertyDetailState.canManage) {
                        val pid = propertyDetailState.propertyId
                        if (propertyDetailState.isOpen && pid != null && propertyDetailState.canManage && !addPropertyState.isOpen) {
                            ownershipViewModel.onIntent(com.saferenttechnologies.android.presentation.owner.OwnershipIntent.Load(pid, force = true))
                        }
                    }
                    // "Report a problem" / Help & support → tickets to SafeRent.
                    val supportLandlord = homeState.activeRole != UserRole.TENANT
                    val supportMe = homeState.user?.id.orEmpty()
                    val reportProblem: (category: String, propertyId: String?) -> Unit = { category, propertyId ->
                        supportViewModel.onIntent(SupportIntent.OpenNew(supportLandlord, supportMe, category, propertyId))
                    }
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
                                    NavRoute.OwnerVerification -> kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.Open)
                                    is NavRoute.KycReturn -> kycViewModel.onIntent(
                                        com.saferenttechnologies.android.presentation.kyc.KycIntent.DigiLockerReturned(route.sessionId, route.success, route.errMessage),
                                    )
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
                                    NavRoute.MyDocuments -> documentsViewModel.onIntent(DocumentsIntent.OpenMine)
                                    // Scanned / opened QR sticker → resolve → actions sheet.
                                    is NavRoute.Qr -> qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.Resolve(route.code, route.unit))
                                    // Extinguisher refill push → the property's Safety form.
                                    // Document expiry push → the property's Compliance screen.
                                    is NavRoute.Compliance -> complianceViewModel.onIntent(com.saferenttechnologies.android.presentation.compliance.ComplianceIntent.Open(route.propertyId))
                                    is NavRoute.Safety -> safetyViewModel.onIntent(com.saferenttechnologies.android.presentation.safety.SafetyIntent.Open(route.propertyId))
                                    // Visit pushes: tenant → My visits (feedback sheet when asked); host → Visits.
                                    is NavRoute.Visit -> if (homeViewModel.state.value.activeRole == UserRole.TENANT) {
                                        tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.OpenMine(route.visitId, route.feedback))
                                    } else {
                                        hostVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.HostVisitsIntent.Open(null, focusId = route.visitId))
                                    }
                                    // "/support/{id}" → that ticket's chat; "/support" → the list.
                                    is NavRoute.SupportTicket -> {
                                        val landlord = homeViewModel.state.value.activeRole != UserRole.TENANT
                                        val me = homeViewModel.state.value.user?.id.orEmpty()
                                        supportViewModel.onIntent(
                                            route.ticketId?.let { SupportIntent.OpenDetail(it, landlord, me) } ?: SupportIntent.Open(landlord, me),
                                        )
                                    }
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
                    // "Scan QR": camera permission through our popup first.
                    val cameraForQr = rememberPermissionRequester(AppPermission.CAMERA)
                    val scanQr: () -> Unit = { cameraForQr.request { granted -> if (granted) qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.Scan(true)) } }
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
                            is PostLoginDestination.Dashboard -> {
                                homeViewModel.onIntent(HomeIntent.Open(destination))
                                // Landlord signed in (this or another phone) → owner verification before the dashboard.
                                if (destination.activeRole == UserRole.LANDLORD) kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.OpenAfterProfile)
                            }
                            null -> Unit
                        }
                    }
                    // Edit profile saved → new details on the dashboard / Profile tab, back to where they were.
                    LaunchedEffect(userProfileState.saved) {
                        userProfileState.saved?.let {
                            homeViewModel.onIntent(HomeIntent.UserUpdated(it))
                            userProfileViewModel.onIntent(UserProfileIntent.CloseEdit)
                        }
                    }
                    // UserProfile saved / skipped → dashboard of the returned role.
                    LaunchedEffect(userProfileState.dashboard) {
                        userProfileState.dashboard?.let {
                            homeViewModel.onIntent(HomeIntent.Open(it))
                            // Landlord just signed up → owner verification right away ("Skip for now" → dashboard).
                            if (it.activeRole == UserRole.LANDLORD) kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.OpenAfterProfile)
                        }
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
                    // ---------------- bottom bar (tab screens only) ----------------
                    val landlordRole = homeState.activeRole != UserRole.TENANT
                    val atAppLevel = !showSplash && blockedInfo == null && onboardingState.isFinished && authState.isSignedIn &&
                        postLoginState.error == null && postLoginState.destination != null && homeState.user != null &&
                        !(postLoginState.destination is PostLoginDestination.UserProfile && userProfileState.dashboard == null)
                    val overlayOpen = addPropertyState.isOpen || supportState.isOpen || kycState.isOpen || userProfileState.editing || ownerDetailsState.isOpen || inspectionsState.isOpen || registerState.isOpen ||
                        tenantVisitsState.isOpen || hostVisitsState.isOpen || safetyState.isOpen || emergencyState.isOpen || complianceState.isOpen || societyState.isOpen ||
                        comparisonState.isOpen || qrState.isOpen || qrState.scanning || tenantCheckState.isOpen || leaseDetailState.isOpen || tenantPaymentsState.isOpen ||
                        tenantLeasesState.isOpen || rentState.isOpen || announcementsState.isOpen || transactionsState.isOpen ||
                        propertyDetailState.isOpen || listingDetailState.isOpen || invitationState.isOpen ||
                        myApplicationsState.isOpen || notificationsState.isOpen ||
                        (documentsState.isOpen && documentsState.landlord) ||
                        (requestsState.isOpen && requestsState.mode != com.saferenttechnologies.android.presentation.requests.RequestsMode.LIST) ||
                        (!landlordRole && tenantHomeState.reviewQueue.isNotEmpty())
                    val currentTab = if (!atAppLevel) null else AppTabs.current(
                        AppTabs.Screens(
                            landlord = landlordRole,
                            overlayOpen = overlayOpen,
                            myPropertiesOpen = myPropertiesState.isOpen,
                            tenantsOpen = tenantsState.isOpen,
                            requestsListOpen = requestsState.isOpen && requestsState.mode == com.saferenttechnologies.android.presentation.requests.RequestsMode.LIST,
                            documentsVaultOpen = documentsState.isOpen && !documentsState.landlord,
                            exploreShown = !landlordRole && (tenantHomeState.showExplore || (tenantHomeState.home == null && tenantHomeState.error != null)),
                            dashboardShown = landlordRole || tenantHomeState.home != null,
                            profileOpen = accountState.isOpen,
                        ),
                    )
                    val selectTab: (AppTab) -> Unit = { tab ->
                        if (tab != AppTab.PROFILE && accountState.isOpen) accountViewModel.onIntent(AccountIntent.Close)
                        if (tab == AppTab.PROFILE && !accountState.isOpen) accountViewModel.onIntent(AccountIntent.Open)
                        val keepRequests = tab == AppTab.REQUESTS
                        if (!keepRequests && requestsState.isOpen) requestsViewModel.onIntent(RequestsIntent.Close)
                        if (landlordRole) {
                            if (tab != AppTab.PROPERTIES && myPropertiesState.isOpen) myPropertiesViewModel.onIntent(MyPropertiesIntent.Close)
                            if (tab != AppTab.TENANTS && tenantsState.isOpen) tenantsViewModel.onIntent(TenantsIntent.Close)
                            when (tab) {
                                AppTab.PROPERTIES -> if (!myPropertiesState.isOpen) myPropertiesViewModel.onIntent(MyPropertiesIntent.Open())
                                AppTab.TENANTS -> if (!tenantsState.isOpen) tenantsViewModel.onIntent(TenantsIntent.Open)
                                AppTab.REQUESTS -> if (!requestsState.isOpen) requestsViewModel.onIntent(RequestsIntent.Open(landlord = true))
                                else -> Unit
                            }
                        } else {
                            if (tab != AppTab.DOCUMENTS && documentsState.isOpen) documentsViewModel.onIntent(DocumentsIntent.Close)
                            val linked = tenantHomeState.home is TenantHomeState.Linked
                            when (tab) {
                                AppTab.HOME -> if (linked) tenantHomeViewModel.onIntent(TenantHomeIntent.CloseExplore) else tenantHomeViewModel.onIntent(TenantHomeIntent.OpenDashboard)
                                AppTab.EXPLORE -> tenantHomeViewModel.onIntent(TenantHomeIntent.OpenExplore)
                                AppTab.REQUESTS -> if (!requestsState.isOpen) requestsViewModel.onIntent(RequestsIntent.Open(landlord = false))
                                AppTab.DOCUMENTS -> if (!documentsState.isOpen) documentsViewModel.onIntent(DocumentsIntent.OpenMine)
                                else -> Unit
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            // The bar handles the system nav bar → screens above don't add it again.
                            .then(if (currentTab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                    ) {
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

                        // Help & support and owner verification: above everything that opens them
                        // (add property wizard, lease, rent, property, profile).
                        supportState.isOpen -> SupportScreen(state = supportState, effects = supportViewModel.effect, onIntent = supportViewModel::onIntent)
                        // Edit profile (from the Profile tab card).
                        userProfileState.editing -> UserProfileScreen(state = userProfileState, onIntent = userProfileViewModel::onIntent)
                        // Profile → Owner details (landlords).
                        ownerDetailsState.isOpen -> com.saferenttechnologies.android.presentation.owner.OwnerDetailsScreen(
                            ownerDetailsState, ownerDetailsViewModel.effect, ownerDetailsViewModel::onIntent,
                        )
                        kycState.checking -> LoadingScreen()
                        kycState.isOpen -> com.saferenttechnologies.android.presentation.kyc.KycScreen(kycState, kycViewModel.effect, kycViewModel::onIntent)

                        // Furniture & appliances (from the property screen or a wizard flat panel): above both.
                        registerState.isOpen -> com.saferenttechnologies.android.presentation.furnishings.RegisterScreen(
                            registerState, registerViewModel.effect, registerViewModel::onIntent,
                        )
                        addPropertyState.isOpen -> AddPropertyScreen(
                            state = addPropertyState,
                            onIntent = addPropertyViewModel::onIntent,
                            onOpenOwnerVerification = { kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.Open) },
                            onContactSupport = { pid -> reportProblem("ACCOUNT", pid) },
                            ownership = ownershipState,
                            onOwnershipIntent = ownershipViewModel::onIntent,
                        )

                        // Screens opened on top of property / lease detail, Rent, home (QR, safety, compliance, society,
                        // visits, comparison, emergency): they must come before those branches to be shown.
                        // QR scanner: full screen camera, above everything.
                        qrState.scanning -> com.saferenttechnologies.android.presentation.smart.QrScannerScreen(
                            onText = { qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.Scanned(it)) },
                            onClose = { qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.Scan(false)) },
                        )
                        comparisonState.isOpen -> com.saferenttechnologies.android.presentation.smart.ComparisonScreen(
                            comparisonState, comparisonViewModel::onIntent,
                            onStartMoveOut = comparisonState.propertyId.takeIf { it.isNotBlank() }?.let { pid ->
                                {
                                    comparisonViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.ComparisonIntent.Close)
                                    leaseDetailViewModel.onIntent(LeaseDetailIntent.Close)
                                    qrPending = "START_INSPECTION" to pid
                                    propertyDetailViewModel.onIntent(PropertyDetailIntent.Open(pid))
                                }
                            },
                        )
                        qrState.isOpen -> com.saferenttechnologies.android.presentation.smart.QrCodesScreen(qrState, qrViewModel::onIntent)
                        // Emergency: above everything else it can be opened from.
                        emergencyState.isOpen -> com.saferenttechnologies.android.presentation.safety.EmergencyScreen(emergencyState, emergencyViewModel::onIntent)
                        societyState.isOpen -> com.saferenttechnologies.android.presentation.society.SocietyScreen(societyState, societyViewModel.effect, societyViewModel::onIntent)
                        complianceState.isOpen -> com.saferenttechnologies.android.presentation.compliance.ComplianceScreen(complianceState, complianceViewModel.effect, complianceViewModel::onIntent)
                        safetyState.isOpen -> com.saferenttechnologies.android.presentation.safety.SafetyScreen(safetyState, safetyViewModel.effect, safetyViewModel::onIntent)
                        // My visits (tenant) / Visits (host): above the listing and property screens.
                        tenantVisitsState.isOpen -> com.saferenttechnologies.android.presentation.visits.MyVisitsScreen(
                            tenantVisitsState, tenantVisitsViewModel.effect, tenantVisitsViewModel::onIntent,
                        ) { pid ->
                            tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.Close)
                            listingDetailViewModel.onIntent(ListingDetailIntent.Open(pid, null))
                        }
                        hostVisitsState.isOpen -> com.saferenttechnologies.android.presentation.visits.HostVisitsScreen(
                            hostVisitsState, hostVisitsViewModel.effect, hostVisitsViewModel::onIntent,
                        )
                        // Landlord: "Review tenant".
                        tenantCheckState.isOpen -> com.saferenttechnologies.android.presentation.tenantcheck.TenantCheckScreen(tenantCheckState, tenantCheckViewModel::onIntent)

                        // Above the property screen: a tenant card opens the lease on top, Back returns.
                        // Tenant → deposit + rent history (also opened from a lease, so it sits above it).
                        tenantPaymentsState.isOpen -> TenantPaymentsScreen(
                            state = tenantPaymentsState,
                            effects = tenantPaymentsViewModel.effect,
                            onIntent = tenantPaymentsViewModel::onIntent,
                            onReportProblem = { reportProblem("PAYMENT", null) },
                        )

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
                            onOpenPayments = { id, title, closed ->
                                tenantPaymentsViewModel.onIntent(TenantPaymentsIntent.Open(id, title, readOnly = closed))
                            },
                            onReportProblem = { tenantSide -> reportProblem(if (tenantSide) "LANDLORD_COMPLAINT" else "TENANT_COMPLAINT", null) },
                            complianceSection = { landlord ->
                                com.saferenttechnologies.android.presentation.society.LeaseSocietyCard(
                                    leaseSocietyState.takeIf { it.leaseId == leaseDetailState.summary?.id } ?: com.saferenttechnologies.android.presentation.society.LeaseSocietyState(),
                                    leaseSocietyViewModel::onIntent,
                                ) { leaseDetailState.summary?.id?.let { documentsViewModel.onIntent(DocumentsIntent.OpenShared(it)) } }
                                com.saferenttechnologies.android.presentation.compliance.LeaseComplianceSection(leaseComplianceState.takeIf { it.leaseId == leaseDetailState.summary?.id } ?: com.saferenttechnologies.android.presentation.compliance.LeaseComplianceState(), landlord, leaseComplianceViewModel::onIntent)
                            },
                            onOpenComparison = { leaseId, landlord ->
                                val s = leaseDetailState.summary
                                comparisonViewModel.onIntent(
                                    com.saferenttechnologies.android.presentation.smart.ComparisonIntent.Open(leaseId, listOfNotNull(s?.propertyTitle, s?.place).filter { it.isNotBlank() }.joinToString(" · "), landlord, s?.propertyId.orEmpty()),
                                )
                            },
                            onEmergency = { leaseId -> emergencyViewModel.onIntent(com.saferenttechnologies.android.presentation.safety.EmergencyIntent.Open(leaseId)) },
                        )

                        // Tenant → "My leases": active + past (each opens the lease above).
                        tenantLeasesState.isOpen -> TenantLeasesScreen(
                            state = tenantLeasesState,
                            onIntent = tenantLeasesViewModel::onIntent,
                            onOpenLease = { l -> leaseDetailViewModel.onIntent(LeaseDetailIntent.Open(l.id, l)) },
                        )

                        // Landlord → this month's rent (Expected / Collected / Overdue).
                        rentState.isOpen -> {
                            RentScreen(
                                state = rentState,
                                effects = rentViewModel.effect,
                                onIntent = rentViewModel::onIntent,
                                onReportProblem = { reportProblem("PAYMENT", null) },
                                onOpenReminders = { reminderViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.ReminderIntent.Open) },
                            )
                            if (reminderState.isOpen) com.saferenttechnologies.android.presentation.smart.ReminderSheet(reminderState, reminderViewModel.effect, reminderViewModel::onIntent)
                        }

                        // Requests (maintenance / service / complaint): tenant raises, landlord updates.
                        // ID documents: tenant's encrypted vault / what a tenant shared with this landlord.
                        documentsState.isOpen -> DocumentsScreen(
                            state = documentsState,
                            effects = documentsViewModel.effect,
                            onIntent = documentsViewModel::onIntent,
                        )

                        // Announcements: landlord sends + sees who opened; tenant reads (view recorded on open).
                        announcementsState.isOpen -> AnnouncementsScreen(
                            state = announcementsState,
                            effects = announcementsViewModel.effect,
                            onIntent = announcementsViewModel::onIntent,
                        )

                        requestsState.isOpen -> RequestsScreen(state = requestsState, effects = requestsViewModel.effect, onIntent = requestsViewModel::onIntent)

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
                            onOpenDocuments = { l -> documentsViewModel.onIntent(DocumentsIntent.OpenShared(l.id)) },
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

                        // Inspection reports of the open property (above it; Back returns to it).
                        inspectionsState.isOpen -> com.saferenttechnologies.android.presentation.inspection.InspectionsScreen(
                            inspectionsState, inspectionsViewModel.effect, inspectionsViewModel::onIntent,
                            onCompare = { leaseId -> comparisonViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.ComparisonIntent.Open(leaseId, inspectionsState.propertyTitle, landlord = true, propertyId = inspectionsState.propertyId.orEmpty())) },
                        )
                        propertyDetailState.isOpen -> PropertyDetailScreen(
                            state = propertyDetailState,
                            effects = propertyDetailViewModel.effect,
                            onIntent = propertyDetailViewModel::onIntent,
                            onOpenEditor = { id, step, newUnit -> addPropertyViewModel.onIntent(AddPropertyIntent.Edit(id, step, newUnit)) },
                            onChanged = onPropertyChanged,
                            onReportProblem = { pid -> reportProblem("PROPERTY", pid) },
                            onOpenOwnerVerification = { kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.Open) },
                            onVerificationSupport = { pid -> reportProblem("ACCOUNT", pid) },
                            ownership = ownershipState,
                            onOpenFurniture = {
                                val d = propertyDetailState.detail
                                val pid = propertyDetailState.propertyId
                                if (d != null && pid != null) {
                                    registerViewModel.onIntent(
                                        com.saferenttechnologies.android.presentation.furnishings.RegisterIntent.Open(
                                            propertyId = pid, title = d.property.title, canManage = d.can.manage,
                                            places = registerPlaces(d.property.units, d.property.rooms, propertyDetailState.unitWord),
                                        ),
                                    )
                                }
                            },
                            onOpenSociety = propertyDetailState.detail?.property
                                ?.takeIf { com.saferenttechnologies.android.domain.model.SocietyRules.applies(it.propertyType) }
                                ?.let { p ->
                                    {
                                        val word = com.saferenttechnologies.android.domain.model.UnitPricing.unitWord(p.propertyType)
                                        societyViewModel.onIntent(
                                            com.saferenttechnologies.android.presentation.society.SocietyIntent.Open(
                                                p.id, p.title,
                                                p.units.map { u -> com.saferenttechnologies.android.presentation.society.SocietyUnit(u.id, "$word ${u.unitNumber}".trim()) },
                                            ),
                                        )
                                    }
                                },
                            onOpenQr = {
                                propertyDetailState.propertyId?.let { pid ->
                                    qrViewModel.onIntent(com.saferenttechnologies.android.presentation.smart.QrIntent.OpenCodes(pid, propertyDetailState.detail?.property?.title.orEmpty()))
                                }
                            },
                            onOpenCompliance = {
                                propertyDetailState.propertyId?.let { pid ->
                                    complianceViewModel.onIntent(com.saferenttechnologies.android.presentation.compliance.ComplianceIntent.Open(pid, propertyDetailState.detail?.property?.title.orEmpty()))
                                }
                            },
                            onOpenSafety = {
                                propertyDetailState.propertyId?.let { pid ->
                                    val p = propertyDetailState.detail?.property
                                    safetyViewModel.onIntent(
                                        com.saferenttechnologies.android.presentation.safety.SafetyIntent.Open(pid, p?.title.orEmpty(), listOfNotNull(p?.locality, p?.city).filter { it.isNotBlank() }.joinToString(", ")),
                                    )
                                }
                            },
                            onOpenVisits = {
                                propertyDetailState.propertyId?.let { pid ->
                                    hostVisitsViewModel.onIntent(
                                        com.saferenttechnologies.android.presentation.visits.HostVisitsIntent.Open(pid, propertyDetailState.detail?.property?.title.orEmpty()),
                                    )
                                }
                            },
                            onOpenInspections = {
                                val d = propertyDetailState.detail
                                val pid = propertyDetailState.propertyId
                                if (d != null && pid != null) {
                                    val word = propertyDetailState.unitWord
                                    inspectionsViewModel.onIntent(
                                        com.saferenttechnologies.android.presentation.inspection.InspectionsIntent.Open(
                                            propertyId = pid,
                                            title = d.property.title,
                                            canManage = d.can.manage || d.can.inspect,
                                            units = d.property.units.map { com.saferenttechnologies.android.presentation.inspection.InspectionPlace(it.id, "$word ${it.unitNumber}".trim()) },
                                            rooms = d.property.rooms.map { com.saferenttechnologies.android.presentation.inspection.InspectionPlace(it.id, getString(R.string.add_property_room_n, it.roomNumber)) },
                                            leases = propertyDetailState.activeLeases.map { l ->
                                                com.saferenttechnologies.android.presentation.inspection.InspectionLease(
                                                    l.id, listOf(l.tenantName, l.place).filter { it.isNotBlank() }.joinToString(" · "), l.unitId, l.bedId,
                                                )
                                            },
                                        ),
                                    )
                                }
                            },
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

                        listingDetailState.isOpen -> {
                            ListingDetailScreen(
                                state = listingDetailState,
                                effects = listingDetailViewModel.effect,
                                onIntent = listingDetailViewModel::onIntent,
                                onApplicationsChanged = {
                                    tenantHomeViewModel.onIntent(TenantHomeIntent.Refresh)
                                    myApplicationsViewModel.onIntent(MyApplicationsIntent.Refresh)
                                },
                                visitSection = {
                                    val d = listingDetailState.detail
                                    com.saferenttechnologies.android.presentation.visits.ListingVisitButton(
                                        tenantVisitsState.listingSlots.takeIf { tenantVisitsState.listingId == listingDetailState.listingId },
                                        onBook = {
                                            if (d != null) {
                                                val places = if (d.isUnitPriced && d.units.size > 1) {
                                                    d.units.map { com.saferenttechnologies.android.presentation.visits.VisitPlace("${d.unitWord} ${it.unitNumber}".trim(), unitId = it.id) }
                                                } else emptyList()
                                                tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.StartBooking(d.id, d.title, places))
                                            }
                                        },
                                        onView = {
                                            tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.OpenMine(tenantVisitsState.listingSlots?.myUpcomingVisit?.id))
                                        },
                                    )
                                },
                            )
                            tenantVisitsState.booking?.let { b ->
                                com.saferenttechnologies.android.presentation.visits.BookingSheet(b, tenantVisitsViewModel::onIntent)
                            }
                        }

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
                            // Share step "Add": my document vault upload (type pre-picked); back here → the list refreshes.
                            onOpenUpload = { type ->
                                documentsViewModel.onIntent(com.saferenttechnologies.android.presentation.documents.DocumentsIntent.OpenMine)
                                if (type != null) {
                                    documentsViewModel.onIntent(com.saferenttechnologies.android.presentation.documents.DocumentsIntent.StartUpload)
                                    documentsViewModel.onIntent(com.saferenttechnologies.android.presentation.documents.DocumentsIntent.PickType(type))
                                }
                            },
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

                        // Profile tab: account, role switch, shortcuts, help, legal, sign out.
                        accountState.isOpen -> AccountScreen(
                            state = accountState,
                            user = homeState.user,
                            activeRole = homeState.activeRole,
                            signingOut = homeState.isSigningOut,
                            onIntent = accountViewModel::onIntent,
                            onSwitchRole = { homeViewModel.onIntent(HomeIntent.SwitchRole(it)) },
                            onSignOut = { homeViewModel.onIntent(HomeIntent.SignOut) },
                            onOpenSupport = { supportViewModel.onIntent(SupportIntent.Open(supportLandlord, supportMe)) },
                            onEditProfile = {
                                homeState.user?.let { u ->
                                    // Verified (server flag or owner verification approved) → name can't change.
                                    val locked = u.nameLocked || kycState.status?.isApproved == true
                                    userProfileViewModel.onIntent(UserProfileIntent.StartEdit(u, homeState.activeRole, locked))
                                }
                            },
                            shortcuts = if (homeState.activeRole == UserRole.TENANT) listOf(
                                AccountLink(R.drawable.ic_document, getString(R.string.tenant_my_leases), { tenantLeasesViewModel.onIntent(TenantLeasesIntent.Open) }),
                                AccountLink(R.drawable.ic_home_outline, getString(R.string.explore_my_applications), { myApplicationsViewModel.onIntent(MyApplicationsIntent.Open) }),
                                AccountLink(R.drawable.ic_my_location, getString(R.string.visit_my_title), { tenantVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.TenantVisitsIntent.OpenMine()) }),
                                AccountLink(R.drawable.ic_campaign, getString(R.string.ann_title), { announcementsViewModel.onIntent(AnnouncementsIntent.Open(landlord = false)) }),
                                AccountLink(R.drawable.ic_notifications, getString(R.string.account_notifications), { notificationsViewModel.onIntent(NotificationsIntent.Open) }),
                            ) else listOf(
                                AccountLink(R.drawable.ic_document, getString(R.string.account_transactions), { transactionsViewModel.onIntent(TransactionsIntent.Open) }),
                                AccountLink(R.drawable.ic_my_location, getString(R.string.visit_host_title), { hostVisitsViewModel.onIntent(com.saferenttechnologies.android.presentation.visits.HostVisitsIntent.Open(null)) }),
                            ) + listOfNotNull(
                                // Landlords only (hidden when the server says 403).
                                AccountLink(R.drawable.ic_person, getString(R.string.owner_title), { ownerDetailsViewModel.onIntent(com.saferenttechnologies.android.presentation.owner.OwnerDetailsIntent.Open) })
                                    .takeIf { homeState.user?.allRoles?.contains(UserRole.LANDLORD) == true && !ownerDetailsState.forbidden },
                            ) + listOf(
                                AccountLink(R.drawable.ic_campaign, getString(R.string.ann_title), { announcementsViewModel.onIntent(AnnouncementsIntent.Open(landlord = true)) }),
                                AccountLink(R.drawable.ic_notifications, getString(R.string.account_notifications), { notificationsViewModel.onIntent(NotificationsIntent.Open) }),
                            ),
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
                                onOpenDashboard = if (linked) null else ({ tenantHomeViewModel.onIntent(TenantHomeIntent.OpenDashboard) }),
                                homeAlertOn = homeAlertState.saved?.enabled,
                                homeAlertArea = homeAlertState.saved?.areaLabel.orEmpty(),
                                onOpenHomeAlert = { homeAlertViewModel.onIntent(HomeAlertIntent.Open) },
                                onToggleHomeAlert = { homeAlertViewModel.onIntent(HomeAlertIntent.QuickToggle(it)) },
                                homeAlertBusy = homeAlertState.saving,
                                // No overflow menu: switch role + sign out live in the Profile tab.
                                topContent = {
                                    com.saferenttechnologies.android.presentation.invitation.MyInvitationsCard(
                                        tenantHomeState.invitations,
                                        onOpen = { invitationViewModel.onIntent(InvitationIntent.Open(it)) },
                                        modifier = Modifier.padding(top = 12.sdp),
                                    )
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
                            // No-lease tenant on the dashboard: Back → Explore (their start screen).
                            if (homeState.activeRole == UserRole.TENANT && tenantHomeState.dashboardOpen && !tenantHomeState.hasActiveLease) {
                                androidx.activity.compose.BackHandler { tenantHomeViewModel.onIntent(TenantHomeIntent.CloseDashboard) }
                            }
                            HomeScreen(
                                state = homeState,
                                onIntent = homeViewModel::onIntent,
                                onAddProperty = {
                                    locationForAddProperty.request { addPropertyViewModel.onIntent(AddPropertyIntent.Open) }
                                },
                                onOpenProperties = { myPropertiesViewModel.onIntent(MyPropertiesIntent.Open(it)) },
                                unreadNotifications = notificationsState.unread,
                                onOpenNotifications = { notificationsViewModel.onIntent(NotificationsIntent.Open) },
                                onScanQr = scanQr,
                                tenantExtra = {
                                    // "Furniture in your home" (reduced register items of the leased home).
                                    val lease = (tenantHomeState.home as? TenantHomeState.Linked)?.lease
                                    // Red "Emergency": numbers + nearest hospital / police (works offline once loaded).
                                    if (lease != null) {
                                        com.saferenttechnologies.android.presentation.safety.EmergencyButton(Modifier.padding(top = 12.sdp)) { emergencyViewModel.onIntent(com.saferenttechnologies.android.presentation.safety.EmergencyIntent.Open(lease.id)) }
                                    }
                                    if (lease != null && lease.propertyId.isNotBlank()) {
                                        LaunchedEffect(lease.propertyId) {
                                            tenantFurnitureViewModel.onIntent(com.saferenttechnologies.android.presentation.furnishings.TenantFurnitureIntent.Load(lease.propertyId))
                                        }
                                        com.saferenttechnologies.android.presentation.furnishings.WhatsIncluded(
                                            tenantFurnitureState.items.takeIf { tenantFurnitureState.propertyId == lease.propertyId }.orEmpty(),
                                            getString(R.string.reg_tenant_title),
                                        ) { item ->
                                            requestsViewModel.onIntent(RequestsIntent.OpenRaise(listOf(lease.propertyTitle, lease.place).filter { it.isNotBlank() }.joinToString(" · ")))
                                            requestsViewModel.onIntent(RequestsIntent.RaiseDescription(getString(R.string.reg_report_prefill, item.name)))
                                        }
                                    }
                                },
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
                                onOpenDocuments = { documentsViewModel.onIntent(DocumentsIntent.OpenMine) },
                                homeAlertOn = homeAlertState.saved?.enabled,
                                onOpenHomeAlert = { homeAlertViewModel.onIntent(HomeAlertIntent.Open) },
                                homeAlertArea = homeAlertState.saved?.areaLabel.orEmpty(),
                                homeAlertBusy = homeAlertState.saving,
                                onToggleHomeAlert = { homeAlertViewModel.onIntent(HomeAlertIntent.QuickToggle(it)) },
                                onOpenLeases = { tenantLeasesViewModel.onIntent(TenantLeasesIntent.Open) },
                                tenantInNotice = tenantHomeState.inNotice,
                                landlordTop = {
                                    LaunchedEffect(Unit) { kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.LoadStatus) }
                                    com.saferenttechnologies.android.presentation.kyc.KycDashboardCard(
                                        kycState.status,
                                        onOpen = { kycViewModel.onIntent(com.saferenttechnologies.android.presentation.kyc.KycIntent.Open) },
                                        modifier = Modifier.padding(bottom = 12.sdp),
                                    )
                                    // "Complete owner details" while language, contact method or emergency phone is missing.
                                    LaunchedEffect(Unit) { ownerDetailsViewModel.onIntent(com.saferenttechnologies.android.presentation.owner.OwnerDetailsIntent.LoadSummary) }
                                    if (ownerDetailsState.showHomeCard) {
                                        Box(Modifier.padding(bottom = 12.sdp)) {
                                            com.saferenttechnologies.android.presentation.owner.CompleteOwnerDetailsCard {
                                                ownerDetailsViewModel.onIntent(com.saferenttechnologies.android.presentation.owner.OwnerDetailsIntent.Open)
                                            }
                                        }
                                    }
                                },
                                onCheckTenant = { tenantCheckViewModel.onIntent(com.saferenttechnologies.android.presentation.tenantcheck.TenantCheckIntent.Open) },
                                tenantTop = {
                                    com.saferenttechnologies.android.presentation.invitation.MyInvitationsCard(
                                        tenantHomeState.invitations,
                                        onOpen = { invitationViewModel.onIntent(InvitationIntent.Open(it)) },
                                        modifier = Modifier.padding(bottom = 12.sdp),
                                    )
                                },
                                refreshing = homeState.summaryLoading || tenantHomeState.loading,
                                onRefresh = {
                                    com.saferenttechnologies.android.data.remote.ApiCache.clear()
                                    homeViewModel.onIntent(HomeIntent.RefreshSummary)
                                    notificationsViewModel.onIntent(NotificationsIntent.RefreshBadge)
                                    requestsViewModel.onIntent(RequestsIntent.RefreshSummary(homeState.activeRole != UserRole.TENANT))
                                    if (homeState.activeRole == UserRole.TENANT) tenantHomeViewModel.onIntent(TenantHomeIntent.Refresh)
                                },
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
                    }
                    currentTab?.let { tab ->
                        AppBottomBar(
                            // Tenant without an active lease has nothing to raise requests against → no Requests tab.
                            tabs = when {
                                landlordRole -> AppTab.LANDLORD
                                tenantHomeState.hasActiveLease -> AppTab.TENANT
                                else -> AppTab.TENANT - AppTab.REQUESTS
                            },
                            selected = tab,
                            onSelect = selectTab,
                            badges = mapOf(AppTab.REQUESTS to if (landlordRole) requestsState.summary.needsAction else requestsState.summary.myOpen),
                        )
                    }
                    }
                    // Tenant "Open to new homes": load the On / Off once, the sheet opens over any screen.
                    if (dashboardReady && homeState.activeRole == UserRole.TENANT) {
                        LaunchedEffect(Unit) { homeAlertViewModel.onIntent(HomeAlertIntent.Load) }
                        // Active lease (not in notice) → "Open to homes" asks before turning on.
                        LaunchedEffect(tenantHomeState.homeAlertNeedsConfirm) {
                            homeAlertViewModel.onIntent(HomeAlertIntent.LeaseContext(tenantHomeState.homeAlertNeedsConfirm))
                        }
                    }
                    HomeAlertHost(homeAlertState, homeAlertViewModel.effect, homeAlertViewModel::onIntent)
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
