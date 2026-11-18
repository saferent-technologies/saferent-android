package com.saferenttechnologies.android.presentation.lease

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.SkeletonTrailing
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Tenant: active lease + past leases. Tap one → the lease screen (landlord, address, payments …). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantLeasesScreen(state: TenantLeasesState, onIntent: (TenantLeasesIntent) -> Unit, onOpenLease: (LeaseSummary) -> Unit) {
    BackHandler { onIntent(TenantLeasesIntent.Close) }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(TenantLeasesIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.leases_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(TenantLeasesIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.loading) {
                ListSkeleton(rows = 3, thumbnail = null, lines = 3, trailing = SkeletonTrailing.PILL)
                return@PullToRefreshBox
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp)) {
                Section(stringResource(R.string.leases_active))
                state.active?.let { LeaseCard(it, active = true) { onOpenLease(it) } } ?: Empty(state.error ?: stringResource(R.string.leases_none_active))
                Spacer(Modifier.height(20.sdp))
                Section(stringResource(R.string.leases_past))
                if (state.past.isEmpty()) Empty(stringResource(R.string.leases_none_past))
                state.past.forEach { l ->
                    LeaseCard(l, active = false) { onOpenLease(l) }
                    Spacer(Modifier.height(10.sdp))
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp, modifier = Modifier.padding(bottom = 8.sdp))
}

@Composable
private fun Empty(text: String) {
    Text(text, color = AuthColors.Muted, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(14.sdp))
}

@Composable
private fun LeaseCard(l: LeaseSummary, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(1.sdp, if (active) AuthColors.Orange.copy(alpha = 0.5f) else AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(l.propertyTitle.ifBlank { stringResource(R.string.lease_your_rental) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                if (l.place.isNotBlank()) Text(l.place, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            StatusPill(
                stringResource(if (active) R.string.lease_status_active else R.string.lease_status_closed),
                if (active) AuthColors.Green else AuthColors.Body,
            )
        }
        Spacer(Modifier.height(8.sdp))
        val facts = listOfNotNull(
            l.monthlyRent.takeIf { it.isNotBlank() }?.let { "${Money.inr(it)} ${stringResource(R.string.invitation_per_month)}" },
            l.otherName.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_landlord_name, it) },
        )
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        val period = when {
            !active && l.endedOn.isNotBlank() -> stringResource(R.string.leases_ended_on, calendarDate(l.endedOn))
            l.startDate.isNotBlank() && l.endDate.isNotBlank() -> stringResource(R.string.leases_period, calendarDate(l.startDate), calendarDate(l.endDate))
            else -> ""
        }
        if (period.isNotBlank()) Text(period, color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp, modifier = Modifier.padding(top = 2.sdp))
    }
}
