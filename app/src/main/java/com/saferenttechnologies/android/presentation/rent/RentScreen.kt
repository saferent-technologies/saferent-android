package com.saferenttechnologies.android.presentation.rent

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.domain.model.RentDue
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

private fun day(iso: String): String = if (iso.isBlank()) "" else prettyDate(iso.take(10))

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
fun bucketColor(b: RentBucket): Color = when (b) {
    RentBucket.EXPECTED -> AuthColors.Ink
    RentBucket.COLLECTED -> AuthColors.Green
    RentBucket.OVERDUE -> AuthColors.Error
}

/** Landlord rent: Expected / Collected / Overdue → tenant cards; collected → receipt; overdue → reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentScreen(state: RentState, effects: Flow<RentEffect>, onIntent: (RentIntent) -> Unit) {
    BackHandler { onIntent(RentIntent.Close) }
    val context = LocalContext.current
    val noApp = stringResource(R.string.rent_no_pdf_app)
    val saved = stringResource(R.string.rent_receipt_saved)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            when (e) {
                is RentEffect.Message -> Toast.makeText(context, e.text, Toast.LENGTH_LONG).show()
                RentEffect.Saved -> Toast.makeText(context, saved, Toast.LENGTH_LONG).show()
                is RentEffect.ViewPdf -> if (!context.viewPdf(e.uri)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                is RentEffect.SharePdf -> context.sharePdf(e.uri)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(RentIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp)) }
            Spacer(Modifier.width(12.sdp))
            Column {
                Text(stringResource(R.string.rent_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                state.period?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
            }
        }
        // Expected / Collected / Overdue: three equal chips.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            RentBucket.entries.forEach { b ->
                Chip(stringResource(bucketLabel(b)), selected = b == state.bucket, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(RentIntent.SelectBucket(b)) }
            }
        }
        if (state.total.isNotBlank() && state.items.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 8.sdp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rent_total_n, state.items.size), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                Text(money(state.total), color = bucketColor(state.bucket), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            }
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(RentIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton(rows = 5, thumbnail = 44.sdp, roundThumb = true)
                state.items.isEmpty() -> Text(
                    state.error ?: stringResource(emptyText(state.bucket)),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { due -> DueCard(due, state, onIntent) }
                }
            }
        }
    }
    state.receiptDue?.let { due ->
        due.receipt?.let { r ->
            ReceiptSheet(
                receipt = r,
                tenantName = due.tenantName,
                home = listOf(due.propertyTitle, due.place).filter { it.isNotBlank() }.joinToString(" · "),
                fallbackAmount = due.paidAmount.ifBlank { due.amount },
                busy = state.fileBusy,
                onAction = { onIntent(RentIntent.Receipt(it)) },
                onDismiss = { onIntent(RentIntent.CloseReceipt) },
            )
        }
    }
}

private fun bucketLabel(b: RentBucket) = when (b) {
    RentBucket.EXPECTED -> R.string.home_rent_expected
    RentBucket.COLLECTED -> R.string.home_rent_collected
    RentBucket.OVERDUE -> R.string.rent_overdue
}

private fun emptyText(b: RentBucket) = when (b) {
    RentBucket.EXPECTED -> R.string.rent_empty_expected
    RentBucket.COLLECTED -> R.string.rent_empty_collected
    RentBucket.OVERDUE -> R.string.rent_empty_overdue
}

/** Tenant + home + amount; collected → tap for the receipt; overdue → "Send reminder". */
@Composable
private fun DueCard(due: RentDue, state: RentState, onIntent: (RentIntent) -> Unit) {
    val collected = state.bucket == RentBucket.COLLECTED && due.receipt != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .then(if (collected) Modifier.clickable(role = Role.Button) { onIntent(RentIntent.OpenReceipt(due.id)) } else Modifier)
            .padding(12.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Text(initials(due.tenantName), color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 14.ssp)
            }
            Spacer(Modifier.width(12.sdp))
            Column(Modifier.weight(1f)) {
                Text(due.tenantName.ifBlank { "—" }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(due.propertyTitle, due.place).filter { it.isNotBlank() }.joinToString(" · "),
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (state.bucket) {
                        RentBucket.EXPECTED -> due.dueDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_due_on, day(it)) }.orEmpty()
                        RentBucket.COLLECTED -> due.paidAt.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_paid_on, day(it)) }.orEmpty()
                        RentBucket.OVERDUE -> due.daysOverdue?.let { stringResource(R.string.rent_days_overdue, it) }
                            ?: due.dueDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_due_on, day(it)) }.orEmpty()
                    },
                    color = if (state.bucket == RentBucket.OVERDUE) AuthColors.Error else AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(if (state.bucket == RentBucket.COLLECTED) due.paidAmount.ifBlank { due.amount } else due.amount),
                    color = bucketColor(state.bucket),
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.ssp,
                )
                if (state.bucket == RentBucket.EXPECTED && due.status.isNotBlank()) {
                    StatusPill(optionLabel(due.status), if (due.status == "PAID") AuthColors.Green else if (due.status == "OVERDUE") AuthColors.Error else AuthColors.Body)
                }
                if (collected) Text(stringResource(R.string.rent_receipt), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
            }
        }
        if (state.bucket == RentBucket.OVERDUE) {
            Spacer(Modifier.height(10.sdp))
            val sent = due.id in state.reminded
            val busy = state.remindingId == due.id
            val can = (due.canRemind || due.lastReminderAt == null) && !sent
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(if (can && !busy) AuthColors.Error.copy(alpha = 0.08f) else AuthColors.Border)
                    .clickable(enabled = can && state.remindingId == null, role = Role.Button) { onIntent(RentIntent.Remind(due.id)) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = AuthColors.Error, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_notifications), null, tint = if (can) AuthColors.Error else AuthColors.Muted, modifier = Modifier.size(16.sdp))
                        Spacer(Modifier.width(6.sdp))
                        Text(
                            when {
                                sent -> stringResource(R.string.rent_reminder_sent)
                                !can && due.lastReminderAt != null -> stringResource(R.string.rent_reminded_on, day(due.lastReminderAt))
                                else -> stringResource(R.string.rent_send_reminder)
                            },
                            color = if (can) AuthColors.Error else AuthColors.Muted,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.ssp,
                        )
                    }
                }
            }
        }
    }
}

