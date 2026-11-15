package com.saferenttechnologies.android.presentation.rent

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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.saferenttechnologies.android.domain.model.RentTransaction
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale

/** "2026-11" → "November 2026". */
private fun monthTitle(ym: String): String = runCatching {
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.US).parse(ym)!!)
}.getOrDefault(ym)

/** Landlord → all transactions, grouped by month. Tap → details (+ receipt View / Download / Share). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: TransactionsState, effects: Flow<RentEffect>, onIntent: (TransactionsIntent) -> Unit) {
    BackHandler { onIntent(TransactionsIntent.Close) }
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
                Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(TransactionsIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.transactions_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(TransactionsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton(rows = 6, thumbnail = 40.sdp, roundThumb = true)
                state.items.isEmpty() -> Text(
                    state.error ?: stringResource(R.string.transactions_empty),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                )
                else -> {
                    val listState = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= listState.layoutInfo.totalItemsCount - 4
                        }
                    }
                    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(TransactionsIntent.LoadMore) }
                    val groups = state.items.groupBy { it.month }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.sdp, end = 16.sdp, bottom = 24.sdp),
                        verticalArrangement = Arrangement.spacedBy(10.sdp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { (month, list) ->
                            item(key = "h-$month") {
                                Text(
                                    monthTitle(month),
                                    color = AuthColors.Muted,
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.ssp,
                                    modifier = Modifier.padding(start = 4.sdp, top = 10.sdp),
                                )
                            }
                            items(list, key = { it.id }) { t -> TransactionCard(t) { onIntent(TransactionsIntent.Select(t.id)) } }
                        }
                        if (state.loading) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(12.sdp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(28.sdp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.open?.let { t ->
        val receipt = t.receipt
        if (receipt != null) {
            ReceiptSheet(
                receipt = receipt,
                tenantName = t.tenantName,
                home = listOf(t.propertyTitle, t.place).filter { it.isNotBlank() }.joinToString(" · "),
                fallbackAmount = t.amount,
                busy = state.fileBusy,
                onAction = { onIntent(TransactionsIntent.Receipt(it)) },
                onDismiss = { onIntent(TransactionsIntent.Dismiss) },
            )
        } else {
            TransactionSheet(t) { onIntent(TransactionsIntent.Dismiss) }
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status.uppercase()) {
    "FAILED" -> AuthColors.Error
    "PENDING" -> AuthColors.OrangeText
    else -> AuthColors.Green
}

/** In = green ↓ "+₹", out (refund) = red ↑ "−₹"; failed / pending carry a pill. */
@Composable
private fun TransactionCard(t: RentTransaction, onClick: () -> Unit) {
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
        Box(
            Modifier.size(40.sdp).clip(CircleShape).background(if (t.isIn) AuthColors.GreenBg else AuthColors.Error.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_upward),
                null,
                tint = if (t.isIn) AuthColors.Green else AuthColors.Error,
                modifier = Modifier.size(20.sdp).rotate(if (t.isIn) 180f else 0f),
            )
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(
                t.tenantName.ifBlank { optionLabel(t.kind.ifBlank { "RENT" }) },
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(optionLabel(t.kind.ifBlank { "RENT" }), t.propertyTitle, t.place).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(if (t.at.isBlank()) "" else prettyDate(t.at), t.mode.takeIf { it.isNotBlank() }?.let { optionLabel(it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 11.ssp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (t.isIn) "+" else "−") + money(t.amount),
                color = if (t.status == "FAILED") AuthColors.Muted else if (t.isIn) AuthColors.Green else AuthColors.Error,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.ssp,
            )
            if (t.status != "SUCCESS") StatusPill(optionLabel(t.status), statusColor(t.status))
            else if (t.receipt != null) Text(stringResource(R.string.rent_receipt), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.ssp)
        }
    }
}

/** No receipt (refund, failed, pending): just the details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSheet(t: RentTransaction, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(optionLabel(t.kind.ifBlank { "RENT" }), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp, modifier = Modifier.weight(1f))
                StatusPill(optionLabel(t.status), statusColor(t.status))
            }
            Spacer(Modifier.height(12.sdp))
            Text(
                (if (t.isIn) "+" else "−") + money(t.amount),
                color = if (t.isIn) AuthColors.Green else AuthColors.Error,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.ssp,
            )
            Spacer(Modifier.height(12.sdp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(horizontal = 14.sdp, vertical = 8.sdp)) {
                listOf(
                    stringResource(R.string.rent_r_tenant) to t.tenantName,
                    stringResource(R.string.tenants_home) to listOf(t.propertyTitle, t.place).filter { it.isNotBlank() }.joinToString(" · "),
                    stringResource(R.string.transactions_date) to (if (t.at.isBlank()) "" else prettyDate(t.at)),
                    stringResource(R.string.rent_r_mode) to (t.mode.takeIf { it.isNotBlank() }?.let { optionLabel(it) }.orEmpty()),
                    stringResource(R.string.transactions_reference) to t.reference,
                ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.sdp)) {
                        Text(k, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                        Text(v, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
                    }
                }
            }
        }
    }
}
