package com.saferenttechnologies.android.presentation.rent

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.manage.copyToClipboard
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.domain.model.PaymentProofRules
import coil3.compose.AsyncImage
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.pluralStringResource
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
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(RentIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Column {
                Text(stringResource(R.string.rent_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                state.period?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
            }
        }
        // Tenants' "I paid" proofs waiting → banner on top (tap = review them).
        if (!state.showRequests && state.requests.isNotEmpty()) {
            Row(
                Modifier
                    .padding(horizontal = 16.sdp, vertical = 4.sdp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.sdp))
                    .background(Amber.copy(alpha = 0.1f))
                    .border(1.sdp, Amber.copy(alpha = 0.35f), RoundedCornerShape(12.sdp))
                    .clickable(role = Role.Button) { onIntent(RentIntent.ShowRequests) }
                    .padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_document), null, tint = Amber, modifier = Modifier.size(18.sdp))
                Spacer(Modifier.width(8.sdp))
                Text(
                    pluralStringResource(R.plurals.rent_requests_banner, state.requests.size, state.requests.size),
                    color = Amber,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.ssp,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.rent_review), color = Amber, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.ssp)
            }
        }
        // Expected / Collected / Overdue: three equal chips (none selected while reviewing proofs).
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            RentBucket.entries.forEach { b ->
                Chip(stringResource(bucketLabel(b)), selected = !state.showRequests && b == state.bucket, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(RentIntent.SelectBucket(b)) }
            }
        }
        if (state.showRequests) {
            Text(
                stringResource(R.string.rent_requests_title),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 16.ssp,
                modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
            )
            PullToRefreshBox(isRefreshing = false, onRefresh = { onIntent(RentIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.requestsLoading && state.requests.isEmpty() -> ListSkeleton(rows = 3, thumbnail = 44.sdp, roundThumb = true)
                    state.requests.isEmpty() -> Text(
                        stringResource(R.string.rent_requests_empty),
                        color = AuthColors.Muted,
                        fontFamily = Inter,
                        fontSize = 14.ssp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                        items(state.requests, key = { it.id }) { r -> RequestCard(r, state.requestBusy, onIntent) }
                    }
                }
            }
        }
        if (!state.showRequests && state.total.isNotBlank() && state.items.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 8.sdp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rent_total_n, state.items.size), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                Text(money(state.total), color = bucketColor(state.bucket), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            }
        }
        if (!state.showRequests) PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(RentIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
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
    RequestDialogs(state, onIntent)
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

/** A tenant's proof: who, which month, how much, transaction id, screenshot → Approve / Reject. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RequestCard(r: com.saferenttechnologies.android.domain.model.PaymentRequest, busy: Boolean, onIntent: (RentIntent) -> Unit) {
    val context = LocalContext.current
    val copied = stringResource(R.string.manage_code_copied)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .padding(12.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Text(initials(r.tenantName), color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 14.ssp)
            }
            Spacer(Modifier.width(12.sdp))
            Column(Modifier.weight(1f)) {
                Text(r.tenantName.ifBlank { stringResource(R.string.word_tenant) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(r.propertyTitle, r.place).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(r.period, if (r.createdAt.isBlank()) "" else stringResource(R.string.rent_sent_on, day(r.createdAt))).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(r.amount), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp)
                if (r.dueAmount.isNotBlank()) Text(stringResource(R.string.rent_of_due, money(r.dueAmount)), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
            }
        }
        if (r.fromDeposit) {
            Spacer(Modifier.height(10.sdp))
            Text(
                stringResource(R.string.rent_from_deposit),
                color = AuthColors.GreenText,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.GreenBg).padding(horizontal = 12.sdp, vertical = 10.sdp),
            )
        }
        if (r.transactionId.isNotBlank()) {
            Spacer(Modifier.height(10.sdp))
            // Long-press copies the id (to check it in the bank / UPI app).
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp))
                    .background(AuthColors.Surface)
                    .combinedClickable(onClick = {}, onLongClick = {
                        context.copyToClipboard(r.transactionId)
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    })
                    .padding(horizontal = 12.sdp, vertical = 10.sdp),
            ) {
                Text(stringResource(R.string.pay_txn_id), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.weight(1f))
                Text(r.transactionId, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
            }
        }
        if (r.proofUrl.isNotBlank()) {
            Spacer(Modifier.height(10.sdp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(AuthColors.Surface)
                    .clickable(role = Role.Button) { onIntent(RentIntent.ViewProof(r.proofUrl)) },
            ) {
                AsyncImage(model = absoluteUrl(r.proofUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Text(
                    stringResource(R.string.rent_view_screenshot),
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.sdp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.sdp, vertical = 4.sdp),
                )
            }
        }
        Spacer(Modifier.height(12.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .border(1.sdp, if (busy) AuthColors.Border else AuthColors.Error.copy(alpha = 0.5f), RoundedCornerShape(12.sdp))
                    .clickable(enabled = !busy, role = Role.Button) { onIntent(RentIntent.AskReject(r.id)) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.rent_reject), color = if (busy) AuthColors.Muted else AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp) }
            Box(Modifier.weight(1.3f)) {
                AuthPrimaryButton(text = stringResource(R.string.rent_approve), enabled = !busy, loading = false, onClick = { onIntent(RentIntent.AskApprove(r.id)) }, fontSize = 14.ssp, shadow = false)
            }
        }
    }
}

/** Approve (amount received, prefilled) / Reject (reason) dialogs + the full-screen screenshot. */
@Composable
private fun RequestDialogs(state: RentState, onIntent: (RentIntent) -> Unit) {
    state.approveId?.let {
        AlertDialog(
            onDismissRequest = { onIntent(RentIntent.DismissRequestDialog) },
            containerColor = AuthColors.Card,
            title = { Text(stringResource(R.string.rent_approve_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 18.ssp) },
            text = {
                Column {
                    Text(stringResource(R.string.rent_approve_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                    Spacer(Modifier.height(12.sdp))
                    OutlinedTextField(
                        value = state.approveAmount,
                        onValueChange = { onIntent(RentIntent.ApproveAmount(it)) },
                        label = { Text(stringResource(R.string.rent_amount_received), fontFamily = Inter) },
                        prefix = { Text("₹ ", fontFamily = Inter) },
                        singleLine = true,
                        enabled = !state.requestBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = propertyFieldColors(),
                        shape = RoundedCornerShape(12.sdp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !state.requestBusy && PaymentProofRules.amountOk(state.approveAmount), onClick = { onIntent(RentIntent.ConfirmApprove) }) {
                    if (state.requestBusy) CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
                    else Text(stringResource(R.string.rent_approve), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(RentIntent.DismissRequestDialog) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        )
    }
    state.rejectId?.let {
        AlertDialog(
            onDismissRequest = { onIntent(RentIntent.DismissRequestDialog) },
            containerColor = AuthColors.Card,
            title = { Text(stringResource(R.string.rent_reject_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 18.ssp) },
            text = {
                OutlinedTextField(
                    value = state.rejectReason,
                    onValueChange = { onIntent(RentIntent.RejectReason(it)) },
                    label = { Text(stringResource(R.string.rent_reject_reason), fontFamily = Inter) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.requestBusy,
                    colors = propertyFieldColors(),
                    shape = RoundedCornerShape(12.sdp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = !state.requestBusy && state.rejectReason.trim().length >= 3, onClick = { onIntent(RentIntent.ConfirmReject) }) {
                    if (state.requestBusy) CircularProgressIndicator(color = AuthColors.Error, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
                    else Text(stringResource(R.string.rent_reject), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(RentIntent.DismissRequestDialog) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        )
    }
    state.proofUrl?.let { url ->
        Dialog(onDismissRequest = { onIntent(RentIntent.ViewProof(null)) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = absoluteUrl(url), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.sdp).size(44.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                        .clickable(role = Role.Button) { onIntent(RentIntent.ViewProof(null)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(22.sdp)) }
            }
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
                Text(due.tenantName.ifBlank { stringResource(R.string.word_tenant) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (due.hasMaintenance) RentSplit(due)
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

