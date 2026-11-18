package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

private fun day(iso: String): String = if (iso.isBlank()) "" else prettyDate(iso.take(10))

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

/** Landlord → all tenants (Current / Past). Card → tenant sheet → lease screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantsScreen(
    state: TenantsState,
    onIntent: (TenantsIntent) -> Unit,
    onOpenLease: (Lease) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<TenantsEffect>? = null,
    onOpenDocuments: (Lease) -> Unit = {},
) {
    BackHandler { onIntent(TenantsIntent.Close) }
    androidx.compose.runtime.LaunchedEffect(effects) {
        effects?.collect { e -> if (e is TenantsEffect.OpenLease) onOpenLease(e.lease) }
    }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(TenantsIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.home_tenants), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        }
        // Search (filters the loaded list as you type).
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(TenantsIntent.Search(it)) },
            placeholder = { Text(stringResource(R.string.tenants_search), fontFamily = Inter) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
            trailingIcon = if (state.query.isNotEmpty()) {
                @Composable {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        stringResource(R.string.explore_clear),
                        tint = AuthColors.Body,
                        modifier = Modifier.size(20.sdp).clip(CircleShape).clickable(role = Role.Button) { onIntent(TenantsIntent.Search("")) },
                    )
                }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(14.sdp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 4.sdp),
        )
        // Current / Past: two equal halves.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Chip(stringResource(R.string.manage_current_tenants), selected = !state.past, modifier = Modifier.weight(1f)) { onIntent(TenantsIntent.ShowPast(false)) }
            Chip(stringResource(R.string.manage_past_tenants), selected = state.past, modifier = Modifier.weight(1f)) { onIntent(TenantsIntent.ShowPast(true)) }
        }
        // Property type tabs (All · Apartment · PG …): only types that have tenants; none when there's just one type.
        val tabs = state.types
        if (tabs.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.sdp, vertical = 4.sdp),
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                item(key = "all") {
                    Chip("${stringResource(R.string.filter_all)} (${state.items.size})", selected = state.type == null, maxLines = 1) { onIntent(TenantsIntent.SelectType(null)) }
                }
                items(tabs, key = { it.type }) { t ->
                    Chip(
                        "${t.label.ifBlank { com.saferenttechnologies.android.presentation.property.optionLabel(t.type) }} (${t.count})",
                        selected = state.type == t.type,
                        maxLines = 1,
                    ) { onIntent(TenantsIntent.SelectType(t.type)) }
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(TenantsIntent.Refresh) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton(rows = 5, thumbnail = 44.sdp, roundThumb = true, lines = 3)
                state.items.isNotEmpty() && state.visible.isEmpty() -> Text(
                    stringResource(R.string.tenants_no_match, state.query.trim()),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                )
                state.items.isEmpty() -> Text(
                    state.error ?: stringResource(if (state.past) R.string.tenants_empty_past else R.string.tenants_empty_current),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.sdp),
                    verticalArrangement = Arrangement.spacedBy(10.sdp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.visible, key = { it.id }) { lease -> TenantCard(lease, lease.id in state.closeRequested) { onIntent(TenantsIntent.OpenTenant(lease.id)) } }
                }
            }
        }
    }
    state.open?.let { lease -> TenantSheet(lease, lease.id in state.closeRequested, onIntent, onOpenLease, onOpenDocuments) }
}

@Composable
private fun Avatar(name: String, sizeDp: Int) {
    Box(Modifier.size(sizeDp.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
        Text(initials(name), color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = (sizeDp / 3).ssp)
    }
}

@Composable
private fun TenantCard(lease: Lease, closeRequested: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(lease.tenantName, 44)
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(lease.tenantName.ifBlank { stringResource(R.string.word_tenant) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(lease.propertyTitle, lease.place).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    lease.monthlyRent.takeIf { it.isNotBlank() }?.let { money(it) },
                    lease.startDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.manage_since, day(it)) },
                ).joinToString(" · "),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        // Status chips sit in the card's top-right corner.
        when {
            closeRequested -> StatusPill(stringResource(R.string.lease_close_requested), Amber, Modifier.align(Alignment.Top))
            lease.exitDate != null -> StatusPill(stringResource(R.string.tenants_moving_out), AuthColors.OrangeText, Modifier.align(Alignment.Top))
        }
    }
}

/** Tenant details: who, where, rent and dates, contact (current only), → lease screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenantSheet(lease: Lease, closeRequested: Boolean, onIntent: (TenantsIntent) -> Unit, onOpenLease: (Lease) -> Unit, onOpenDocuments: (Lease) -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { onIntent(TenantsIntent.CloseTenant) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(lease.tenantName, 56)
                Spacer(Modifier.width(14.sdp))
                Column(Modifier.weight(1f)) {
                    Text(lease.tenantName, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                    Text(
                        stringResource(if (lease.status.equals("ACTIVE", true)) R.string.tenants_current_tenant else R.string.tenants_past_tenant),
                        color = AuthColors.Body,
                        fontFamily = Inter,
                        fontSize = 13.ssp,
                    )
                }
            }
            if (closeRequested || lease.exitDate != null) {
                Spacer(Modifier.height(10.sdp))
                if (closeRequested) StatusPill(stringResource(R.string.lease_close_requested), Amber)
                lease.exitDate?.let { StatusPill(stringResource(R.string.manage_move_out_requested, day(it)), AuthColors.OrangeText, Modifier.padding(top = 4.sdp)) }
            }

            Spacer(Modifier.height(16.sdp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(horizontal = 14.sdp, vertical = 8.sdp)) {
                listOfNotNull(
                    lease.propertyTitle.takeIf { it.isNotBlank() }?.let { stringResource(R.string.tenants_home) to it },
                    listOf(lease.place, lease.placeCode).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { stringResource(R.string.tenants_unit) to it },
                    lease.monthlyRent.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_rent) to money(it) },
                    lease.securityDeposit.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_deposit) to money(it) },
                    lease.rentDueDay?.let { stringResource(R.string.invitation_rent_due) to stringResource(R.string.lease_due_day, it) },
                    lease.startDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_started) to day(it) },
                    lease.endDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_lease_ends) to day(it) },
                ).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.sdp)) {
                        Text(k, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                        Text(v, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
                    }
                }
            }

            // Phone only comes for current tenants.
            lease.tenantPhone?.let { phone ->
                Spacer(Modifier.height(14.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    OutlineAction(stringResource(R.string.account_blocked_channel_call), R.drawable.ic_call, modifier = Modifier.weight(1f)) {
                        context.tryStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }
                    OutlineAction(stringResource(R.string.account_blocked_channel_whatsapp), R.drawable.ic_chat, modifier = Modifier.weight(1f)) {
                        context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phone.filter(Char::isDigit)}")))
                    }
                }
            }
            Spacer(Modifier.height(10.sdp))
            // The lease screen draws under a bottom sheet → close the sheet first.
            OutlineAction(stringResource(R.string.lease_view), R.drawable.ic_document, modifier = Modifier.fillMaxWidth()) {
                onIntent(TenantsIntent.CloseTenant)
                onOpenLease(lease)
            }
            // What the tenant shared (only while the lease is active — past tenants: no access).
            if (lease.exitDate == null && !lease.status.equals("CLOSED", true) && !lease.status.equals("ENDED", true)) {
                Spacer(Modifier.height(8.sdp))
                OutlineAction(stringResource(R.string.docs_landlord_btn), R.drawable.ic_lock, modifier = Modifier.fillMaxWidth()) {
                    onIntent(TenantsIntent.CloseTenant)
                    onOpenDocuments(lease)
                }
            }
        }
    }
}
