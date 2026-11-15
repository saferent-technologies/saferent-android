package com.saferenttechnologies.android.presentation.home

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.sdp),
    ) {
        val name = state.user?.fullName?.trim()?.substringBefore(' ').orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (name.isEmpty()) "Welcome to SafeRent" else "Hi, $name",
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.ssp,
                modifier = Modifier.weight(1f),
            )
            BellWithBadge(unreadNotifications, onOpenNotifications)
        }
        if (state.showRoleSwitcher) {
            Spacer(Modifier.height(16.sdp))
            RoleSwitcher(roles = state.roles, active = state.activeRole, onSelect = { onIntent(HomeIntent.SwitchRole(it)) })
        }
        Spacer(Modifier.height(16.sdp))
        if (state.activeRole == UserRole.LANDLORD) {
            LandlordSummary(state.summary, onOpenProperties)
            Spacer(Modifier.height(12.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                OutlineAction(stringResource(R.string.home_manage_properties), R.drawable.ic_building, modifier = Modifier.weight(1f)) {
                    onOpenProperties(PropertyFilter.ALL)
                }
            }
        } else if (tenantLease != null) {
            TenantLeaseCard(tenantLease, onExploreHomes)
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
        if (state.activeRole == UserRole.LANDLORD) {
            Spacer(Modifier.height(16.sdp))
            // Landlord → Add property wizard.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp))
                    .background(AuthColors.OrangeSoft)
                    .clickable(role = Role.Button, onClick = onAddProperty)
                    .padding(18.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.sdp).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_add), null, tint = Color.White, modifier = Modifier.size(24.sdp))
                }
                Spacer(Modifier.width(14.sdp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_add_property), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp)
                    Text(stringResource(R.string.home_add_property_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                }
            }
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

/** Tenant linked to a home (placeholder until the tenant dashboard design). */
@Composable
private fun TenantLeaseCard(lease: TenantLease, onExploreHomes: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(AuthColors.Surface)
            .padding(18.sdp),
    ) {
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
        OutlineAction(stringResource(R.string.tenant_explore_homes), R.drawable.ic_search, modifier = Modifier.fillMaxWidth(), onClick = onExploreHomes)
    }
}

/** Landlord numbers; every card opens "My properties" with that filter. */
@Composable
private fun LandlordSummary(summary: PropertySummary?, onOpen: (PropertyFilter) -> Unit) {
    val s = summary ?: PropertySummary()
    val cards = listOf(
        Triple(stringResource(R.string.home_card_total), s.total.toString(), PropertyFilter.ALL),
        Triple(stringResource(R.string.filter_live), s.count("PUBLISHED").toString(), PropertyFilter.LIVE),
        Triple(stringResource(R.string.filter_under_verification), s.count("PENDING_VERIFICATION").toString(), PropertyFilter.UNDER_VERIFICATION),
        Triple(stringResource(R.string.filter_needs_changes), s.count("REJECTED").toString(), PropertyFilter.NEEDS_CHANGES),
        Triple(stringResource(R.string.filter_drafts), s.count("DRAFT").toString(), PropertyFilter.DRAFTS),
        Triple(stringResource(R.string.home_card_units), "${s.unitsAvailable} / ${s.unitsOccupied}", PropertyFilter.ALL),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                row.forEach { (label, value, filter) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.sdp))
                            .background(AuthColors.Surface)
                            .clickable(role = Role.Button) { onOpen(filter) }
                            .padding(14.sdp),
                    ) {
                        Text(value, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.ssp)
                        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                }
            }
        }
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
        // This month's rent
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.sdp))
                .background(AuthColors.Surface)
                .clickable(role = Role.Button) { onOpen(PropertyFilter.ALL) }
                .padding(14.sdp),
        ) {
            Text(stringResource(R.string.home_card_rent, s.rentPeriod), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Spacer(Modifier.height(6.sdp))
            Row {
                RentFigure(stringResource(R.string.home_rent_expected), money(s.rentExpected), AuthColors.Ink, Modifier.weight(1f))
                RentFigure(stringResource(R.string.home_rent_collected), money(s.rentCollected), AuthColors.Green, Modifier.weight(1f))
                RentFigure(stringResource(R.string.home_rent_overdue, s.overdueCount), money(s.rentOverdue), AuthColors.Error, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RentFigure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = color, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp)
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
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
