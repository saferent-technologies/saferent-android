package com.saferenttechnologies.android.presentation.rent

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.domain.model.PaymentProofRules
import coil3.compose.AsyncImage
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.DepositInfo
import com.saferenttechnologies.android.domain.model.RentDue
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

private fun day(iso: String): String = if (iso.isBlank()) "" else prettyDate(iso.take(10))

/** Tenant → deposit card, then pending dues (top), then paid months (below). Paid → receipt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantPaymentsScreen(state: TenantPaymentsState, effects: Flow<RentEffect>, onIntent: (TenantPaymentsIntent) -> Unit) {
    BackHandler { onIntent(TenantPaymentsIntent.Close) }
    val context = LocalContext.current
    val noApp = stringResource(R.string.rent_no_pdf_app)
    val saved = stringResource(R.string.rent_receipt_saved)
    val onlineSoon = stringResource(R.string.pay_online_soon)
    val unreadable = stringResource(R.string.pay_proof_unreadable)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            when (e) {
                is RentEffect.Message -> Toast.makeText(
                    context,
                    when (e.text) {
                        TenantPaymentsViewModel.ONLINE_SOON -> onlineSoon
                        TenantPaymentsViewModel.PROOF_UNREADABLE -> unreadable
                        else -> e.text
                    },
                    Toast.LENGTH_LONG,
                ).show()
                RentEffect.Saved -> Toast.makeText(context, saved, Toast.LENGTH_LONG).show()
                is RentEffect.ViewPdf -> if (!context.viewPdf(e.uri)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                is RentEffect.SharePdf -> context.sharePdf(e.uri)
            }
        }
    }
    val data = state.data
    val depositLabel = stringResource(R.string.pay_deposit)

    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(TenantPaymentsIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Column {
                Text(stringResource(R.string.pay_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                if (state.title.isNotBlank()) Text(state.title, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(TenantPaymentsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && data == null -> ListSkeleton(rows = 5, thumbnail = 40.sdp, roundThumb = true)
                data == null -> Text(
                    state.error ?: stringResource(R.string.add_property_error_generic),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                    data.deposit?.let { d ->
                        item(key = "deposit") {
                            DepositCard(d) { r -> onIntent(TenantPaymentsIntent.ShowReceipt(r, depositLabel)) }
                        }
                    }
                    item(key = "h-pending") { Section(stringResource(R.string.pay_pending, data.pending.size)) }
                    if (data.pending.isEmpty()) {
                        item(key = "none-pending") { Note(stringResource(R.string.pay_all_clear)) }
                    }
                    items(data.pending, key = { "p-" + it.id }) { due -> DueRow(due, null, onPay = { onIntent(TenantPaymentsIntent.StartPay(due.id)) }) }
                    item(key = "h-paid") { Section(stringResource(R.string.pay_history)) }
                    if (data.paid.isEmpty()) {
                        item(key = "none-paid") { Note(stringResource(R.string.pay_no_history)) }
                    }
                    items(data.paid, key = { "d-" + it.id }) { due ->
                        DueRow(due, onReceipt = { r -> onIntent(TenantPaymentsIntent.ShowReceipt(r, due.period)) })
                    }
                }
            }
        }
    }

    state.payDue?.let { PaySheet(state, it, onIntent) }

    state.receipt?.let { r ->
        ReceiptSheet(
            receipt = r.copy(period = r.period.ifBlank { state.receiptFor }),
            tenantName = "",
            home = state.title,
            fallbackAmount = r.amount,
            busy = state.fileBusy,
            onAction = { onIntent(TenantPaymentsIntent.Receipt(it)) },
            onDismiss = { onIntent(TenantPaymentsIntent.HideReceipt) },
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp, modifier = Modifier.padding(top = 8.sdp, start = 2.sdp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        color = AuthColors.Muted,
        fontFamily = Inter,
        fontSize = 13.ssp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(14.sdp),
    )
}

/** Security deposit: paid (green, receipt) or not paid yet (amber). */
@Composable
private fun DepositCard(d: DepositInfo, onReceipt: (com.saferenttechnologies.android.domain.model.RentReceipt) -> Unit) {
    val color = if (d.paid) AuthColors.Green else Amber
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(16.sdp))
            .then(d.receipt?.let { r -> Modifier.clickable(role = Role.Button) { onReceipt(r) } } ?: Modifier)
            .padding(16.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.sdp).clip(RoundedCornerShape(12.sdp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (d.paid) R.drawable.ic_check_circle else R.drawable.ic_shield_check), null, tint = color, modifier = Modifier.size(22.sdp))
            }
            Spacer(Modifier.width(12.sdp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pay_deposit), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                Text(money(d.paidAmount.ifBlank { d.amount }), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            }
            StatusPill(stringResource(if (d.paid) R.string.pay_deposit_paid else R.string.pay_deposit_not_paid), color)
        }
        if (d.paid && d.paidAt.isNotBlank()) {
            Text(stringResource(R.string.rent_paid_on, day(d.paidAt)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 8.sdp))
        }
        if (!d.paid) {
            Text(stringResource(R.string.pay_deposit_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 8.sdp))
        }
        if (d.receipt != null) Text(stringResource(R.string.rent_receipt) + " →", color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))
    }
}

/** One month: pending (due date / overdue in red) or paid (date + receipt). */
@Composable
private fun DueRow(due: RentDue, onReceipt: ((com.saferenttechnologies.android.domain.model.RentReceipt) -> Unit)?, onPay: (() -> Unit)? = null) {
    val overdue = due.status == "OVERDUE"
    val receipt = due.receipt
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, if (overdue) AuthColors.Error.copy(alpha = 0.4f) else AuthColors.Border, RoundedCornerShape(14.sdp)),
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (receipt != null && onReceipt != null) Modifier.clickable(role = Role.Button) { onReceipt(receipt) } else Modifier)
            .padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint, bg) = when {
            due.isPaid -> Triple(R.drawable.ic_check_circle, AuthColors.Green, AuthColors.GreenBg)
            overdue -> Triple(R.drawable.ic_pause_circle, AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
            else -> Triple(R.drawable.ic_pause_circle, Amber, Amber.copy(alpha = 0.1f))
        }
        Box(Modifier.size(40.sdp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(20.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(due.period.ifBlank { day(due.dueDate) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Text(
                when {
                    due.isPaid -> stringResource(R.string.rent_paid_on, day(due.paidAt))
                    overdue -> due.daysOverdue?.let { stringResource(R.string.rent_days_overdue, it) } ?: stringResource(R.string.rent_due_on, day(due.dueDate))
                    else -> stringResource(R.string.rent_due_on, day(due.dueDate))
                },
                color = if (overdue) AuthColors.Error else AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                money(if (due.isPaid) due.paidAmount.ifBlank { due.amount } else due.amount),
                color = if (due.isPaid) AuthColors.Green else if (overdue) AuthColors.Error else AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.ssp,
            )
            if (due.hasMaintenance) RentSplit(due)
            if (due.isPaid && receipt != null) Text(stringResource(R.string.rent_receipt), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.ssp)
            if (due.status == "PARTIAL") StatusPill(stringResource(R.string.pay_partial), Amber)
        }
    }
    // Pending month: "Pay" (or the proof already sent / rejected).
    if (!due.isPaid && onPay != null) {
        Column(Modifier.fillMaxWidth().padding(start = 12.sdp, end = 12.sdp, bottom = 12.sdp)) {
            when {
                due.requestPending -> Text(
                    stringResource(R.string.pay_proof_waiting),
                    color = Amber,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(Amber.copy(alpha = 0.08f)).padding(10.sdp),
                )
                else -> {
                    if (due.requestStatus == "REJECTED") {
                        Text(
                            stringResource(R.string.pay_proof_rejected, due.requestNote.orEmpty()).trimEnd(' ', ':'),
                            color = AuthColors.Error,
                            fontFamily = Inter,
                            fontSize = 12.ssp,
                            modifier = Modifier.padding(bottom = 6.sdp),
                        )
                    }
                    AuthPrimaryButton(
                        text = stringResource(R.string.pay_cta, money(due.amount)),
                        enabled = true,
                        loading = false,
                        onClick = onPay,
                        fontSize = 15.ssp,
                        shadow = false,
                    )
                }
            }
        }
    }
    }
}

/** "Use my security deposit (₹36,000 available)" — the landlord approves it. Hidden without a usable deposit. */
@Composable
private fun DepositOption(state: TenantPaymentsState, onIntent: (TenantPaymentsIntent) -> Unit, top: Boolean = true) {
    val d = state.data?.deposit?.takeIf { it.canUseForRent } ?: return
    if (top) Spacer(Modifier.height(14.sdp))
    OutlineAction(stringResource(R.string.pay_use_deposit, money(d.usable)), R.drawable.ic_shield_check, modifier = Modifier.fillMaxWidth()) {
        onIntent(TenantPaymentsIntent.AskUseDeposit)
    }
    Spacer(Modifier.height(if (top) 6.sdp else 14.sdp))
    if (top) Text(stringResource(R.string.pay_use_deposit_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    if (state.confirmDeposit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onIntent(TenantPaymentsIntent.CancelUseDeposit) },
            containerColor = AuthColors.Card,
            title = { Text(stringResource(R.string.pay_use_deposit_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 18.ssp) },
            text = { Text(stringResource(R.string.pay_use_deposit_body, money(state.payDue?.amount.orEmpty())), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp) },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = !state.paySending, onClick = { onIntent(TenantPaymentsIntent.ConfirmUseDeposit) }) {
                    Text(stringResource(R.string.pay_use_deposit_send), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onIntent(TenantPaymentsIntent.CancelUseDeposit) }) {
                    Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter)
                }
            },
        )
    }
}

/**
 * "Pay ₹18,000": Pay online (config payments.enabled) or "Update paid amount" → amount, transaction id,
 * screenshot → the landlord approves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(state: TenantPaymentsState, due: RentDue, onIntent: (TenantPaymentsIntent) -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onIntent(TenantPaymentsIntent.PayProof(it.toString())) } }
    ModalBottomSheet(
        onDismissRequest = { onIntent(TenantPaymentsIntent.ClosePay) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(stringResource(R.string.pay_sheet_title, due.period.ifBlank { day(due.dueDate) }), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
            Text(money(due.amount), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 28.ssp)
            if (due.hasMaintenance) {
                Spacer(Modifier.height(8.sdp))
                BillBreakup(due)
            }
            Spacer(Modifier.height(16.sdp))
            if (!state.payForm) {
                if (state.onlineEnabled) {
                    AuthPrimaryButton(text = stringResource(R.string.pay_online), enabled = true, loading = false, onClick = { onIntent(TenantPaymentsIntent.PayOnline) }, fontSize = 15.ssp)
                    Spacer(Modifier.height(10.sdp))
                }
                OutlineAction(stringResource(R.string.pay_update_paid), R.drawable.ic_document, modifier = Modifier.fillMaxWidth()) { onIntent(TenantPaymentsIntent.ShowProofForm) }
                Spacer(Modifier.height(8.sdp))
                Text(stringResource(R.string.pay_update_paid_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                DepositOption(state, onIntent)
            } else {
                DepositOption(state, onIntent, top = false)
                Text(stringResource(R.string.pay_form_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
                Text(stringResource(R.string.pay_update_paid_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                Spacer(Modifier.height(12.sdp))
                val bad = state.payShowErrors
                OutlinedTextField(
                    value = state.payAmount,
                    onValueChange = { onIntent(TenantPaymentsIntent.PayAmount(it)) },
                    label = { Text(stringResource(R.string.pay_amount_paid), fontFamily = Inter) },
                    prefix = { Text("₹ ", fontFamily = Inter) },
                    isError = bad && !PaymentProofRules.amountOk(state.payAmount),
                    singleLine = true,
                    enabled = !state.paySending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    colors = propertyFieldColors(),
                    shape = RoundedCornerShape(12.sdp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.sdp))
                OutlinedTextField(
                    value = state.payTxn,
                    onValueChange = { onIntent(TenantPaymentsIntent.PayTxn(it)) },
                    label = { Text(stringResource(R.string.pay_txn_id), fontFamily = Inter) },
                    isError = bad && !PaymentProofRules.txnOk(state.payTxn),
                    supportingText = if (bad && !PaymentProofRules.txnOk(state.payTxn)) {
                        @Composable { Text(stringResource(R.string.pay_txn_error), fontFamily = Inter) }
                    } else null,
                    singleLine = true,
                    enabled = !state.paySending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = propertyFieldColors(),
                    shape = RoundedCornerShape(12.sdp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.sdp))
                // Screenshot of the payment (system photo picker: no storage permission needed).
                val proof = state.payProofUri
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.sdp)
                        .clip(RoundedCornerShape(14.sdp))
                        .background(AuthColors.Surface)
                        .border(1.sdp, if (bad && proof == null) AuthColors.Error else AuthColors.Border, RoundedCornerShape(14.sdp))
                        .clickable(enabled = !state.paySending, role = Role.Button) {
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (proof != null) {
                        AsyncImage(model = proof, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.sdp))
                        Text(
                            stringResource(R.string.pay_change_screenshot),
                            color = Color.White,
                            fontFamily = Inter,
                            fontSize = 12.ssp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.sdp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.sdp, vertical = 4.sdp),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_camera), null, tint = if (bad) AuthColors.Error else AuthColors.Body, modifier = Modifier.size(28.sdp))
                            Spacer(Modifier.height(6.sdp))
                            Text(stringResource(R.string.pay_add_screenshot), color = if (bad) AuthColors.Error else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                            Text(stringResource(R.string.pay_screenshot_hint), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
                        }
                    }
                }
                Spacer(Modifier.height(16.sdp))
                AuthPrimaryButton(
                    text = stringResource(R.string.pay_send_proof),
                    // Amount + transaction id + screenshot → enabled; otherwise the grey disabled state.
                    enabled = state.canSendProof,
                    loading = state.paySending,
                    onClick = { onIntent(TenantPaymentsIntent.SendProof) },
                    fontSize = 15.ssp,
                )
            }
        }
    }
}

/** "Rent ₹15,000 + Maint. ₹2,000" under the amount on a month row. */
@Composable
internal fun RentSplit(due: RentDue) {
    Text(
        stringResource(R.string.pay_split, money(due.rentAmount), money(due.maintenanceAmount)),
        color = AuthColors.Muted,
        fontFamily = Inter,
        fontSize = 11.ssp,
    )
}

/** Pay sheet: Rent / Maintenance / Total lines. */
@Composable
private fun BillBreakup(due: RentDue) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
        BillLine(stringResource(R.string.pay_line_rent), money(due.rentAmount), bold = false)
        BillLine(stringResource(R.string.pay_line_maintenance), money(due.maintenanceAmount), bold = false)
        Spacer(Modifier.height(6.sdp))
        Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Border))
        Spacer(Modifier.height(6.sdp))
        BillLine(stringResource(R.string.pay_line_total), money(due.amount), bold = true)
    }
}

@Composable
private fun BillLine(label: String, value: String, bold: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.sdp)) {
        Text(label, color = if (bold) AuthColors.Ink else AuthColors.Body, fontFamily = Inter, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontSize = 13.ssp)
    }
}
