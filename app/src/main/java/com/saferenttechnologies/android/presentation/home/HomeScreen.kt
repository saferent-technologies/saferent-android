package com.saferenttechnologies.android.presentation.home

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import com.saferenttechnologies.android.presentation.common.AutoSizeText
import androidx.compose.foundation.border
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.presentation.common.ShimmerHost
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.res.pluralStringResource
import com.saferenttechnologies.android.domain.model.TenantLease
import com.saferenttechnologies.android.presentation.notifications.BellWithBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertySummary
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.property.OutlineAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Placeholder Home: real Home comes later. Holds the WhatsApp consent setting + sign out. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onAddProperty: () -> Unit = {},
    onOpenProperties: (PropertyFilter) -> Unit = {},
    unreadNotifications: Int = 0,
    onOpenNotifications: () -> Unit = {},
    tenantLease: TenantLease? = null,
    onExploreHomes: () -> Unit = {},
    onOpenLease: (TenantLease) -> Unit = {},
    leaseCloseRequested: Boolean = false,
    onOpenTenants: () -> Unit = {},
    onOpenRent: (RentBucket, String) -> Unit = { _, _ -> },
    onOpenTransactions: () -> Unit = {},
    tenantPayments: com.saferenttechnologies.android.domain.model.TenantPayments? = null,
    onOpenPayments: (TenantLease) -> Unit = {},
    /** Landlord: requests needing action; tenant: my open requests. */
    requestsBadge: Int = 0,
    onOpenRequests: () -> Unit = {},
    onRaiseRequest: (TenantLease) -> Unit = {},
    /** Landlord: sent announcements; tenant: received ones. */
    onOpenAnnouncements: () -> Unit = {},
    /** Pull down → load everything on the dashboard again (otherwise it's served from memory). */
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /** Tenant: my ID documents. */
    onOpenDocuments: () -> Unit = {},
    /** Tenant: "Open to new homes" alert (null = not loaded). */
    homeAlertOn: Boolean? = null,
    onOpenHomeAlert: () -> Unit = {},
    homeAlertArea: String = "",
    homeAlertBusy: Boolean = false,
    onToggleHomeAlert: ((Boolean) -> Unit)? = null,
    /** Tenant: "My leases" (active + past). */
    onOpenLeases: () -> Unit = {},
    /** Tenant's lease is ending (notice period) → show "Open to homes". */
    tenantInNotice: Boolean = false,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().background(AuthColors.Background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Small "Good morning" + full name (not a big "Hi, Ravi").
            com.saferenttechnologies.android.presentation.common.Greeting(state.user?.fullName.orEmpty(), Modifier.weight(1f))
            Spacer(Modifier.width(8.sdp))
            BellWithBadge(unreadNotifications, onOpenNotifications)
        }
        if (state.showRoleSwitcher) {
            Spacer(Modifier.height(16.sdp))
            RoleSwitcher(roles = state.roles, active = state.activeRole, onSelect = { onIntent(HomeIntent.SwitchRole(it)) })
        }
        Spacer(Modifier.height(16.sdp))
        if (state.activeRole == UserRole.LANDLORD && state.summary == null && state.summaryLoading) {
            LandlordDashboardSkeleton()
        } else if (state.activeRole == UserRole.LANDLORD) {
            LandlordSummary(state.summary, onOpenProperties, onOpenRent, onOpenTenants)
            Spacer(Modifier.height(16.sdp))
            LandlordActions(
                propertyCount = state.summary?.total ?: 0,
                tenantCount = state.summary?.unitsOccupied ?: 0,
                onOpenProperties = { onOpenProperties(PropertyFilter.ALL) },
                onAddProperty = onAddProperty,
                onOpenTenants = onOpenTenants,
                onOpenTransactions = onOpenTransactions,
                requestsNeedAction = requestsBadge,
                onOpenRequests = onOpenRequests,
                onOpenAnnouncements = onOpenAnnouncements,
            )
        } else if (tenantLease != null) {
            TenantLeaseCard(tenantLease, leaseCloseRequested, onExploreHomes, onOpenLease, onOpenLeases)
            Spacer(Modifier.height(12.sdp))
            // "Open to new homes" only matters while the lease is ending (notice period).
            if (tenantInNotice) {
                com.saferenttechnologies.android.presentation.homealerts.HomeAlertBanner(
                    homeAlertOn,
                    homeAlertArea,
                    onOpenHomeAlert,
                    busy = homeAlertBusy,
                    onToggle = onToggleHomeAlert,
                )
                Spacer(Modifier.height(12.sdp))
            }
            TenantActions(
                tenantPayments,
                onOpenPayments = { onOpenPayments(tenantLease) },
                myOpenRequests = requestsBadge,
                onRaiseRequest = { onRaiseRequest(tenantLease) },
                onOpenRequests = onOpenRequests,
                onOpenAnnouncements = onOpenAnnouncements,
                onOpenDocuments = onOpenDocuments,
            )
        } else if (state.activeRole == UserRole.TENANT) {
            // No active lease: what's left for a tenant (explore, past leases, alerts, documents …).
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface).padding(18.sdp)) {
                Text(stringResource(R.string.tenant_no_lease_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
                Spacer(Modifier.height(6.sdp))
                Text(stringResource(R.string.tenant_no_lease_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                Spacer(Modifier.height(14.sdp))
                OutlineAction(stringResource(R.string.tenant_explore_homes), R.drawable.ic_search, modifier = Modifier.fillMaxWidth(), onClick = onExploreHomes)
                Spacer(Modifier.height(8.sdp))
                OutlineAction(stringResource(R.string.tenant_my_leases), R.drawable.ic_document, modifier = Modifier.fillMaxWidth(), onClick = onOpenLeases)
            }
            Spacer(Modifier.height(12.sdp))
            com.saferenttechnologies.android.presentation.homealerts.HomeAlertBanner(
                homeAlertOn,
                homeAlertArea,
                onOpenHomeAlert,
                busy = homeAlertBusy,
                onToggle = onToggleHomeAlert,
            )
            Spacer(Modifier.height(12.sdp))
            ActionGrid(
                listOf(
                    DashboardAction(R.drawable.ic_lock, stringResource(R.string.docs_title), stringResource(R.string.docs_box_tenant), primary = false, onClick = onOpenDocuments),
                    DashboardAction(R.drawable.ic_campaign, stringResource(R.string.ann_title), stringResource(R.string.ann_box_tenant), primary = false, onClick = onOpenAnnouncements),
                ),
            )
        } else Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.sdp))
                .background(AuthColors.Surface)
                .padding(20.sdp),
        ) {
            Text(
                "${state.activeRole.label()} dashboard",
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.ssp,
            )
            Spacer(Modifier.height(6.sdp))
            Text("Coming soon.", color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        }
        Spacer(Modifier.height(32.sdp))
        Text(
            if (state.isSigningOut) "Signing out…" else "Sign out",
            color = AuthColors.Error,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 15.ssp,
            modifier = Modifier
                .heightIn(min = 48.sdp)
                .clickable(enabled = !state.isSigningOut, role = Role.Button) { onIntent(HomeIntent.SignOut) }
                .padding(vertical = 14.sdp),
        )
    }
    }
}

/** Tenant linked to a home (placeholder until the tenant dashboard design). */
@Composable
private fun TenantLeaseCard(lease: TenantLease, closeRequested: Boolean, onExploreHomes: () -> Unit, onOpenLease: (TenantLease) -> Unit, onOpenLeases: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(AuthColors.Surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenLease(lease) }
            .padding(18.sdp),
    ) {
        if (closeRequested) {
            com.saferenttechnologies.android.presentation.manage.StatusPill(
                stringResource(R.string.lease_close_requested),
                com.saferenttechnologies.android.presentation.explore.Amber,
            )
            Spacer(Modifier.height(8.sdp))
        }
        Text(
            stringResource(R.string.tenant_you_live_at, lease.propertyTitle, lease.place),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.ssp,
        )
        Spacer(Modifier.height(6.sdp))
        lease.rentDueDay?.let {
            Text(stringResource(R.string.tenant_rent_due, money(lease.monthlyRent), it), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        }
        if (lease.landlordName.isNotBlank()) {
            Text(stringResource(R.string.tenant_landlord, lease.landlordName), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        }
        Spacer(Modifier.height(14.sdp))
        // Active + past leases (tap the card itself for this lease).
        OutlineAction(stringResource(R.string.lease_view), R.drawable.ic_document, modifier = Modifier.fillMaxWidth(), onClick = onOpenLeases)
        Spacer(Modifier.height(8.sdp))
        OutlineAction(stringResource(R.string.tenant_explore_homes), R.drawable.ic_search, modifier = Modifier.fillMaxWidth(), onClick = onExploreHomes)
    }
}

/** Landlord dashboard while GET properties/summary loads: number cards, rent card, shortcut boxes. */
@Composable
private fun LandlordDashboardSkeleton() {
    // Same blocks as the loaded dashboard: "Your properties" (7 boxes), rent (3 boxes), 5 shortcut boxes.
    ShimmerHost {
        Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            com.saferenttechnologies.android.presentation.common.FigureCardSkeleton(7)
            com.saferenttechnologies.android.presentation.common.FigureCardSkeleton(3)
            Spacer(Modifier.height(6.sdp))
            com.saferenttechnologies.android.presentation.common.ActionGridSkeleton(5)
        }
    }
}

/**
 * Tenant shortcuts (same boxes as the landlord's): Payments = deposit paid or not + pending dues.
 * A lone box fills the row.
 */
@Composable
private fun TenantActions(
    payments: com.saferenttechnologies.android.domain.model.TenantPayments?,
    onOpenPayments: () -> Unit,
    myOpenRequests: Int = 0,
    onRaiseRequest: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
) {
    val pending = payments?.pending.orEmpty()
    val body = when {
        payments == null -> stringResource(R.string.pay_box_default)
        payments.overdueCount > 0 -> pluralStringResource(R.plurals.pay_box_overdue, payments.overdueCount, payments.overdueCount)
        pending.isNotEmpty() -> stringResource(R.string.pay_box_next, money(pending.first().amount))
        payments.deposit?.paid == false -> stringResource(R.string.pay_box_deposit_due)
        else -> stringResource(R.string.pay_box_all_paid)
    }
    val actions = listOf(
        DashboardAction(R.drawable.ic_document, stringResource(R.string.pay_title), body, primary = (payments?.overdueCount ?: 0) > 0, onClick = onOpenPayments),
        DashboardAction(R.drawable.ic_edit, stringResource(R.string.req_box_raise), stringResource(R.string.req_box_raise_body), primary = false, onClick = onRaiseRequest),
        DashboardAction(
            R.drawable.ic_help,
            stringResource(R.string.req_box_mine),
            if (myOpenRequests > 0) pluralStringResource(R.plurals.req_box_open, myOpenRequests, myOpenRequests) else stringResource(R.string.req_box_none_open),
            primary = false,
            onClick = onOpenRequests,
            badge = myOpenRequests,
        ),
        DashboardAction(R.drawable.ic_campaign, stringResource(R.string.ann_title), stringResource(R.string.ann_box_tenant), primary = false, onClick = onOpenAnnouncements),
        DashboardAction(R.drawable.ic_lock, stringResource(R.string.docs_title), stringResource(R.string.docs_box_tenant), primary = false, onClick = onOpenDocuments),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                row.forEach { ActionBox(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** One dashboard box: icon tile, title, one line. */
private data class DashboardAction(
    val icon: Int,
    val title: String,
    val body: String,
    val primary: Boolean,
    val onClick: () -> Unit,
    /** Red count on the icon (requests waiting). */
    val badge: Int = 0,
)

/**
 * Landlord shortcuts as a grid of boxes, two per row (equal heights). "Manage properties" only when
 * there is at least one property; new shortcuts just go into the list.
 */
@Composable
private fun LandlordActions(
    propertyCount: Int,
    tenantCount: Int,
    onOpenProperties: () -> Unit,
    onAddProperty: () -> Unit,
    onOpenTenants: () -> Unit,
    onOpenTransactions: () -> Unit,
    requestsNeedAction: Int = 0,
    onOpenRequests: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
) {
    // Always shown. Properties open from the "Your properties" numbers, so the first box is "Add property".
    val actions = listOf(
        DashboardAction(
            R.drawable.ic_add,
            stringResource(R.string.home_add_property),
            stringResource(R.string.home_add_property_short),
            primary = true,
            onClick = onAddProperty,
        ),
        DashboardAction(
            R.drawable.ic_home_outline,
            stringResource(R.string.home_tenants),
            pluralStringResource(R.plurals.home_tenants_count, tenantCount, tenantCount),
            primary = false,
            onClick = onOpenTenants,
        ),
        DashboardAction(
            R.drawable.ic_edit,
            stringResource(R.string.req_title_landlord),
            if (requestsNeedAction > 0) pluralStringResource(R.plurals.req_box_needs_action, requestsNeedAction, requestsNeedAction)
            else stringResource(R.string.req_box_landlord_body),
            primary = false,
            onClick = onOpenRequests,
            badge = requestsNeedAction,
        ),
        DashboardAction(
            R.drawable.ic_document,
            stringResource(R.string.transactions_title),
            stringResource(R.string.transactions_box),
            primary = false,
            onClick = onOpenTransactions,
        ),
        DashboardAction(
            R.drawable.ic_campaign,
            stringResource(R.string.ann_title),
            stringResource(R.string.ann_box_landlord),
            primary = false,
            onClick = onOpenAnnouncements,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                // A lone box (odd count) fills the whole row.
                row.forEach { ActionBox(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ActionBox(action: DashboardAction, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.sdp))
            .background(if (action.primary) AuthColors.OrangeSoft else AuthColors.Surface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(14.sdp),
    ) {
        Box {
            Box(
                Modifier.size(42.sdp).clip(RoundedCornerShape(12.sdp)).background(if (action.primary) AuthColors.Orange else AuthColors.Card),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(action.icon), null, tint = if (action.primary) Color.White else AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
            }
            if (action.badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.sdp, y = (-6).sdp).size(20.sdp).clip(CircleShape).background(AuthColors.Error)
                        .border(1.5.sdp, if (action.primary) AuthColors.OrangeSoft else AuthColors.Surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (action.badge > 9) "9+" else action.badge.toString(),
                        color = Color.White,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.ssp,
                        style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.sdp))
        Text(action.title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp, maxLines = 2)
        Spacer(Modifier.height(2.sdp))
        Text(action.body, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 2)
    }
}

/** Landlord numbers; every card opens "My properties" with that filter. */
@Composable
private fun LandlordSummary(
    summary: PropertySummary?,
    onOpen: (PropertyFilter) -> Unit,
    onOpenRent: (RentBucket, String) -> Unit,
    onOpenTenants: () -> Unit,
) {
    val s = summary ?: PropertySummary()
    // Every number, 0 included. Each opens its own list:
    // status → My properties with that filter, available → properties with a free flat / bed, occupied → Tenants.
    val properties = listOf(
        Triple(stringResource(R.string.home_card_total), s.total.toString(), { onOpen(PropertyFilter.ALL) }),
        Triple(stringResource(R.string.filter_live), s.count("PUBLISHED").toString(), { onOpen(PropertyFilter.LIVE) }),
        Triple(stringResource(R.string.filter_under_verification), s.count("PENDING_VERIFICATION").toString(), { onOpen(PropertyFilter.UNDER_VERIFICATION) }),
        Triple(stringResource(R.string.filter_needs_changes), s.count("REJECTED").toString(), { onOpen(PropertyFilter.NEEDS_CHANGES) }),
        Triple(stringResource(R.string.filter_drafts), s.count("DRAFT").toString(), { onOpen(PropertyFilter.DRAFTS) }),
        Triple(stringResource(R.string.home_card_available), s.unitsAvailable.toString(), { onOpen(PropertyFilter.AVAILABLE_UNITS) }),
        Triple(stringResource(R.string.home_card_occupied), s.unitsOccupied.toString(), onOpenTenants),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        // Same look as the rent card: one card, small bordered boxes, three per row.
        // Properties + available / occupied flats & beds, all in one card.
        FigureCard(stringResource(R.string.home_card_properties), properties)
        // Live listings whose changes aren't sent yet / were rejected → My properties.
        if (s.changesDraftOrRejected > 0) {
            Text(
                pluralStringResource(R.plurals.changes_dashboard, s.changesDraftOrRejected, s.changesDraftOrRejected),
                color = com.saferenttechnologies.android.presentation.explore.Amber,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.sdp))
                    .background(com.saferenttechnologies.android.presentation.explore.Amber.copy(alpha = 0.1f))
                    .clickable(role = Role.Button) { onOpen(PropertyFilter.ALL) }
                    .padding(14.sdp),
            )
        }
        // This month's rent: three bordered boxes, each opens its list (Expected / Collected / Overdue).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.sdp))
                .background(AuthColors.Surface)
                .padding(12.sdp),
        ) {
            Text(stringResource(R.string.home_card_rent, s.rentPeriod), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(start = 2.sdp))
            Spacer(Modifier.height(8.sdp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                RentFigure(stringResource(R.string.home_rent_expected), money(s.rentExpected), AuthColors.Ink, Modifier.weight(1f).fillMaxHeight()) {
                    onOpenRent(RentBucket.EXPECTED, s.rentPeriod)
                }
                RentFigure(stringResource(R.string.home_rent_collected), money(s.rentCollected), AuthColors.Green, Modifier.weight(1f).fillMaxHeight()) {
                    onOpenRent(RentBucket.COLLECTED, s.rentPeriod)
                }
                RentFigure(stringResource(R.string.home_rent_overdue, s.overdueCount), money(s.rentOverdue), AuthColors.Error, Modifier.weight(1f).fillMaxHeight()) {
                    onOpenRent(RentBucket.OVERDUE, s.rentPeriod)
                }
            }
        }
    }
}

/** A card of small number boxes (3 per row, same size as the rent boxes; a short last row keeps the size). */
@Composable
private fun FigureCard(title: String, items: List<Triple<String, String, () -> Unit>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Surface)
            .padding(12.sdp),
    ) {
        Text(title, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(start = 2.sdp))
        Spacer(Modifier.height(8.sdp))
        Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    row.forEach { (label, value, open) ->
                        RentFigure(label, value, AuthColors.Ink, Modifier.weight(1f).fillMaxHeight(), open)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One rent box: own border, tap → that list. */
@Composable
private fun RentFigure(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.sdp, vertical = 10.sdp),
    ) {
        // Big amounts ("₹12,45,000") shrink to fit the box instead of wrapping.
        AutoSizeText(value, color = color, fontSize = 15.ssp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.sdp))
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp, maxLines = 2, lineHeight = 13.ssp)
    }
}

private fun UserRole?.label(): String = when (this) {
    UserRole.LANDLORD -> "Landlord"
    UserRole.TENANT -> "Tenant"
    null -> "Your"
}

/** Landlord | Tenant pill switcher (only for accounts with both roles). */
@Composable
private fun RoleSwitcher(roles: List<UserRole>, active: UserRole?, onSelect: (UserRole) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Surface)
            .padding(4.sdp),
    ) {
        roles.forEach { role ->
            val selected = role == active
            Text(
                role.label(),
                color = if (selected) Color.White else AuthColors.Body,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.sdp))
                    .background(if (selected) AuthColors.Orange else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(role) })
                    .padding(vertical = 10.sdp),
            )
        }
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() {
    HomeScreen(
        HomeState(
            user = AccountUser(fullName = "Ravi Kumar", role = UserRole.LANDLORD, roles = listOf(UserRole.LANDLORD, UserRole.TENANT)),
            activeRole = UserRole.LANDLORD,
        ),
        onIntent = {},
    )
}

/** Dashboard boxes, 2 per row (a lone last box fills its row). */
@Composable
private fun ActionGrid(actions: List<DashboardAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                row.forEach { ActionBox(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}
