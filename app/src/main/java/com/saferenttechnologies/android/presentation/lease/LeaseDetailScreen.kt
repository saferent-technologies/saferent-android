package com.saferenttechnologies.android.presentation.lease

import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.LeaseExitRequest
import com.saferenttechnologies.android.domain.model.LeaseExitRules
import com.saferenttechnologies.android.domain.model.LeaseExitState
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.DateInput
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipFlow
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** "Accepted — closes on …" card: a blue that reads on light and dark backgrounds. */
private val Blue = Color(0xFF2563EB)

/** Day only ("15 Dec 2026") for the server's UTC-midnight dates. */
private fun day(iso: String?): String = iso?.takeIf { it.isNotBlank() }?.let { prettyDate(it.take(10)) }.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaseDetailScreen(
    state: LeaseDetailState,
    effects: Flow<LeaseDetailEffect>,
    onIntent: (LeaseDetailIntent) -> Unit,
    onChanged: () -> Unit,
    /** Tenant: this lease's payments + receipts ([closed] → read-only). */
    onOpenPayments: ((leaseId: String, title: String, closed: Boolean) -> Unit)? = null,
) {
    BackHandler { onIntent(LeaseDetailIntent.Close) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { e ->
            when (e) {
                is LeaseDetailEffect.Message -> Toast.makeText(context, e.text, Toast.LENGTH_LONG).show()
                LeaseDetailEffect.Leave -> onIntent(LeaseDetailIntent.Close)
                LeaseDetailEffect.Changed -> onChanged()
            }
        }
    }
    val exit = state.exit

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(LeaseDetailIntent.Close) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
                Spacer(Modifier.width(12.sdp))
                Text(stringResource(R.string.lease_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            }
            when {
                state.loading && exit == null -> com.saferenttechnologies.android.presentation.common.LeaseSkeleton()
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { onIntent(LeaseDetailIntent.Refresh) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
                        // The other side asked → their request goes first (needs my answer).
                        exit?.open?.takeIf { it.canRespond && it.isPending }?.let { RespondCard(it, state, onIntent); Spacer(Modifier.height(12.sdp)) }
                        LeaseHeader(state.summary, exit)
                        // Tenant: rent paid / due + receipts for this lease (also for an ended one).
                        if (onOpenPayments != null && exit?.isTenant != false && state.summary != null) {
                            Spacer(Modifier.height(12.sdp))
                            OutlineAction(stringResource(R.string.lease_payments), R.drawable.ic_document, modifier = Modifier.fillMaxWidth()) {
                                state.summary?.let { s ->
                                    onOpenPayments(s.id, listOf(s.propertyTitle, s.place).filter { it.isNotBlank() }.joinToString(" · "), exit?.isClosed == true || !s.isActive)
                                }
                            }
                        }
                        DepositSection(state, onIntent)
                        if (exit == null && state.error != null) {
                            Spacer(Modifier.height(12.sdp))
                            Text(state.error, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                        }
                        if (exit != null && exit.side != null) ExitSection(state, exit, onIntent)
                        Spacer(Modifier.height(24.sdp))
                    }
                }
            }
        }
    }

    when (state.sheet) {
        LeaseSheet.REQUEST -> Sheet(onIntent) { RequestSheet(state, onIntent) }
        LeaseSheet.ACCEPT -> Sheet(onIntent) { AcceptSheet(state, onIntent) }
        LeaseSheet.DECLINE -> Sheet(onIntent) { DeclineSheet(state, onIntent) }
        LeaseSheet.REVIEW -> Sheet(onIntent) { WriteReviewSheet(state, onIntent) }
        LeaseSheet.DEPOSIT -> Sheet(onIntent) { DepositSheet(state, onIntent) }
        null -> Unit
    }
    state.confirm?.let { ConfirmDialog(it, state, onIntent) }
}

// ---------------- header ----------------

@Composable
private fun LeaseHeader(summary: LeaseSummary?, exit: LeaseExitState?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(16.sdp)).padding(16.sdp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary?.propertyTitle?.ifBlank { null } ?: stringResource(R.string.lease_your_rental),
                    color = AuthColors.Ink,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.ssp,
                )
                summary?.place?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
            }
            val closed = exit?.isClosed == true
            StatusPill(
                stringResource(if (closed) R.string.lease_status_closed else R.string.lease_status_active),
                if (closed) AuthColors.Body else AuthColors.Green,
            )
        }
        summary?.otherName?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.sdp))
            Text(
                stringResource(if (exit?.isTenant == false) R.string.lease_tenant_name else R.string.lease_landlord_name, it),
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontSize = 14.ssp,
            )
        }
        val rows = listOfNotNull(
            summary?.monthlyRent?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_rent) to "${Money.inr(it)} ${stringResource(R.string.invitation_per_month)}" },
            summary?.securityDeposit?.takeIf { Money.inr(it).isNotBlank() }?.let { stringResource(R.string.invitation_deposit) to Money.inr(it) },
            summary?.rentDueDay?.let { stringResource(R.string.invitation_rent_due) to stringResource(R.string.lease_due_day, it) },
            summary?.address?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_address) to it },
            summary?.otherPhone?.takeIf { it.isNotBlank() && exit?.isTenant != false }?.let { stringResource(R.string.lease_landlord_phone) to it },
            summary?.startDate?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_started) to day(it) },
            (exit?.endDate ?: summary?.endDate)?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_lease_ends) to day(it) },
            exit?.noticePeriodDays?.let { stringResource(R.string.invitation_notice) to stringResource(R.string.invitation_days, it) },
        )
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(10.sdp))
            rows.forEach { (k, v) -> InfoRow(k, v) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}

// ---------------- security deposit ----------------

/** Deposit received (green, date) or not yet (amber). Landlord: "Mark as received" with the amount. */
@Composable
private fun DepositSection(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    val d = state.deposit
    val expected = d?.amount?.ifBlank { null } ?: state.summary?.securityDeposit?.ifBlank { null }
    if (d == null && expected == null) return
    val paid = d?.paid == true
    val color = if (paid) AuthColors.Green else Amber
    Spacer(Modifier.height(12.sdp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pay_deposit), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                Text(Money.inr(d?.paidAmount?.ifBlank { null } ?: expected), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            }
            StatusPill(stringResource(if (paid) R.string.lease_deposit_received else R.string.lease_deposit_not_received), color)
        }
        if (paid && !d?.paidAt.isNullOrBlank()) {
            Text(stringResource(R.string.lease_deposit_received_on, day(d?.paidAt)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
        }
        if (!paid && state.isLandlord && state.exit?.isClosed != true) {
            Spacer(Modifier.height(10.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.lease_mark_deposit),
                enabled = !state.busy,
                loading = false,
                onClick = { onIntent(LeaseDetailIntent.StartDeposit) },
                fontSize = 14.ssp,
                shadow = false,
            )
        }
    }
}

@Composable
private fun DepositSheet(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    SheetTitle(stringResource(R.string.lease_mark_deposit))
    Text(stringResource(R.string.lease_deposit_info), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(14.sdp))
    OutlinedTextField(
        value = state.depositAmount,
        onValueChange = { onIntent(LeaseDetailIntent.DepositAmount(it)) },
        label = { Text(stringResource(R.string.rent_amount_received), fontFamily = Inter) },
        prefix = { Text("₹ ", fontFamily = Inter) },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.sdp))
    // Received any day up to today.
    DateInput(stringResource(R.string.lease_deposit_date), state.depositDate?.iso().orEmpty(), min = SimpleDate(2000, 1, 1)) { onIntent(LeaseDetailIntent.DepositDate(it)) }
    Spacer(Modifier.height(16.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.lease_deposit_save),
        enabled = !state.busy && com.saferenttechnologies.android.domain.model.PaymentProofRules.amountOk(state.depositAmount) && state.depositDate != null,
        loading = state.busy,
        onClick = { onIntent(LeaseDetailIntent.SaveDeposit) },
        fontSize = 15.ssp,
    )
}

// ---------------- close request states ----------------

@Composable
private fun ExitSection(state: LeaseDetailState, exit: LeaseExitState, onIntent: (LeaseDetailIntent) -> Unit) {
    val open = exit.open
    val other = stringResource(if (exit.isTenant) R.string.lease_the_landlord else R.string.lease_the_tenant)
    when {
        // F. Closed lease
        exit.isClosed -> {
            Spacer(Modifier.height(12.sdp))
            Banner(stringResource(R.string.lease_closed_on, day(exit.closedOn)), AuthColors.Surface, AuthColors.Ink)
            if (state.canWriteReview) {
                Spacer(Modifier.height(12.sdp))
                AuthPrimaryButton(
                    text = stringResource(R.string.lease_write_review),
                    enabled = !state.busy,
                    loading = false,
                    onClick = { onIntent(LeaseDetailIntent.StartReview) },
                    leadingIcon = R.drawable.ic_star,
                    fontSize = 15.ssp,
                )
                Spacer(Modifier.height(4.sdp))
                Text(stringResource(R.string.lease_review_required), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            } else if (state.reviewInfo?.hasMyReview == true) {
                Spacer(Modifier.height(8.sdp))
                Text(stringResource(R.string.lease_review_done), color = AuthColors.GreenText, fontFamily = Inter, fontSize = 13.ssp)
            }
        }
        // E. Agreed — closes on the move-out date
        open != null && open.isAccepted -> {
            Spacer(Modifier.height(12.sdp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(Blue.copy(alpha = 0.08f))
                    .border(1.sdp, Blue.copy(alpha = 0.3f), RoundedCornerShape(14.sdp)).padding(14.sdp),
            ) {
                Text(stringResource(R.string.lease_closes_on, day(open.moveOutDate)), color = Blue, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
                if (open.respondedBy != null) {
                    Text(stringResource(R.string.lease_agreed_by, open.respondedBy, day(open.respondedAt)), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
                }
                open.responseNote?.let { Text("“$it”", color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(top = 4.sdp)) }
                if (open.hasMyReview || state.reviewSaved) ReviewSavedNote()
            }
        }
        // C. I asked — waiting for the other side
        open != null && open.isPending && !open.canRespond -> {
            Spacer(Modifier.height(12.sdp))
            AmberCard(stringResource(R.string.lease_waiting_for, other)) {
                RequestDetails(open)
                if (open.hasMyReview || state.reviewSaved) ReviewSavedNote()
                if (open.canCancel) {
                    Spacer(Modifier.height(10.sdp))
                    OutlinedDanger(stringResource(R.string.lease_cancel_request), enabled = !state.busy) { onIntent(LeaseDetailIntent.AskCancel) }
                }
            }
        }
        // D is drawn at the top of the screen.
    }

    // A. Nothing open → "Leave lease" / "Close lease"
    if (!exit.isClosed && open == null && exit.canRequest) {
        Spacer(Modifier.height(16.sdp))
        OutlinedDanger(stringResource(if (exit.isTenant) R.string.lease_leave else R.string.lease_close), enabled = !state.busy) {
            onIntent(LeaseDetailIntent.StartRequest)
        }
    }

    // G. History
    if (exit.history.isNotEmpty()) {
        Spacer(Modifier.height(18.sdp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).clickable(role = Role.Button) { onIntent(LeaseDetailIntent.ToggleHistory) }.padding(vertical = 8.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lease_history, exit.history.size), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                null,
                tint = AuthColors.Body,
                modifier = Modifier.size(20.sdp).rotate(if (state.showHistory) 180f else 0f),
            )
        }
        if (state.showHistory) {
            exit.history.forEach { h ->
                Spacer(Modifier.height(8.sdp))
                HistoryRow(h)
            }
        }
    }
}

/** D. "{name} wants to close this lease" + Accept / Decline. */
@Composable
private fun RespondCard(open: LeaseExitRequest, state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    AmberCard(stringResource(R.string.lease_wants_to_close, open.requestedBy.ifBlank { stringResource(if (open.requestedByRole == "TENANT") R.string.lease_the_tenant else R.string.lease_the_landlord) })) {
        RequestDetails(open)
        Spacer(Modifier.height(12.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.sdp)
                    .clip(RoundedCornerShape(14.sdp))
                    .background(AuthColors.Background)
                    .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
                    .clickable(enabled = !state.busy, role = Role.Button) { onIntent(LeaseDetailIntent.StartDecline) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.invitation_decline), color = if (state.busy) AuthColors.Muted else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp) }
            AuthPrimaryButton(
                text = stringResource(R.string.lease_accept),
                enabled = !state.busy,
                loading = false,
                onClick = { onIntent(LeaseDetailIntent.StartAccept) },
                modifier = Modifier.weight(1.3f),
                fontSize = 15.ssp,
                shadow = false,
            )
        }
    }
}

@Composable
private fun RequestDetails(r: LeaseExitRequest) {
    InfoRow(stringResource(R.string.lease_move_out), day(r.moveOutDate))
    if (r.createdAt.isNotBlank()) InfoRow(stringResource(R.string.lease_requested_on), day(r.createdAt))
    if (r.reason.isNotBlank()) {
        Spacer(Modifier.height(6.sdp))
        Text(
            r.reason,
            color = AuthColors.Ink,
            fontFamily = Inter,
            fontSize = 13.ssp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Background.copy(alpha = 0.7f)).padding(10.sdp),
        )
    }
}

@Composable
private fun HistoryRow(h: LeaseExitRequest) {
    val (label, color) = when (h.status.uppercase()) {
        "DECLINED" -> R.string.lease_h_declined to AuthColors.Error
        "COMPLETED" -> R.string.lease_h_completed to AuthColors.Green
        "ACCEPTED" -> R.string.lease_h_accepted to Blue
        else -> R.string.lease_h_cancelled to AuthColors.Body
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(h.requestedBy, day(h.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier.weight(1f),
            )
            StatusPill(stringResource(label), color)
        }
        if (h.moveOutDate.isNotBlank()) Text(stringResource(R.string.lease_h_move_out, day(h.moveOutDate)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        if (h.reason.isNotBlank()) Text(h.reason, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(top = 4.sdp))
        h.responseNote?.let { Text("“$it”", color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp)) }
    }
}

@Composable
private fun AmberCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(Amber.copy(alpha = 0.08f))
            .border(1.sdp, Amber.copy(alpha = 0.35f), RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pause_circle), null, tint = Amber, modifier = Modifier.size(18.sdp))
            Spacer(Modifier.width(8.sdp))
            Text(title, color = Amber, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp)
        }
        Spacer(Modifier.height(8.sdp))
        content()
    }
}

@Composable
private fun Banner(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(bg).padding(14.sdp))
}

@Composable
private fun ReviewSavedNote() {
    Text(stringResource(R.string.lease_review_saved), color = AuthColors.GreenText, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
}

/** Outlined, red text: "Leave lease" / "Close lease" / "Cancel request". Disabled → grey. */
@Composable
private fun OutlinedDanger(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, if (enabled) AuthColors.Error.copy(alpha = 0.5f) else AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) AuthColors.Error else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp) }
}

// ---------------- sheets ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onIntent: (LeaseDetailIntent) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(LeaseDetailIntent.DismissSheet) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
    Spacer(Modifier.height(6.sdp))
}

@Composable
private fun RequestSheet(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    val tenant = state.isTenant
    val notice = state.exit?.noticePeriodDays
    SheetTitle(stringResource(if (tenant) R.string.lease_leave else R.string.lease_close))
    Text(stringResource(if (tenant) R.string.lease_request_info_tenant else R.string.lease_request_info_landlord), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    notice?.let {
        Spacer(Modifier.height(4.sdp))
        Text(stringResource(R.string.lease_notice_in_lease, it), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
    }
    Spacer(Modifier.height(14.sdp))
    DateInput(stringResource(R.string.lease_move_out), state.moveOut?.iso().orEmpty(), min = SimpleDate.today()) { onIntent(LeaseDetailIntent.PickMoveOut(it)) }
    if (state.isShortNotice && notice != null) {
        Spacer(Modifier.height(6.sdp))
        Text(stringResource(R.string.lease_short_notice, notice), color = Amber, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
    }
    Spacer(Modifier.height(12.sdp))
    ChipFlow(if (tenant) LeaseExitRules.TENANT_REASONS else LeaseExitRules.LANDLORD_REASONS) { r ->
        Chip(r, selected = state.reason == r, enabled = !state.busy) { onIntent(LeaseDetailIntent.QuickReason(r)) }
    }
    Spacer(Modifier.height(10.sdp))
    OutlinedTextField(
        value = state.reason,
        onValueChange = { onIntent(LeaseDetailIntent.ReasonChanged(it)) },
        label = { Text(stringResource(R.string.lease_reason), fontFamily = Inter) },
        enabled = !state.busy,
        isError = state.reasonError,
        minLines = 3,
        maxLines = 6,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(if (state.reasonError) stringResource(R.string.lease_reason_min, LeaseExitRules.REASON_MIN) else "", fontFamily = Inter, modifier = Modifier.weight(1f))
                Text("${state.reason.length} / ${LeaseExitRules.REASON_MAX}", fontFamily = Inter)
            }
        },
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.lease_send_request),
        enabled = !state.busy,
        loading = state.busy,
        onClick = { onIntent(LeaseDetailIntent.SendRequest) },
        fontSize = 15.ssp,
    )
}

@Composable
private fun AcceptSheet(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    val open = state.exit?.open
    SheetTitle(stringResource(R.string.lease_accept_title))
    Text(stringResource(R.string.lease_will_close_on, day(open?.moveOutDate)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlinedTextField(
        value = state.note,
        onValueChange = { onIntent(LeaseDetailIntent.NoteChanged(it)) },
        label = { Text(stringResource(R.string.lease_note_optional), fontFamily = Inter) },
        enabled = !state.busy,
        minLines = 2,
        maxLines = 4,
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.lease_accept),
        enabled = !state.busy,
        loading = state.busy,
        onClick = { onIntent(LeaseDetailIntent.SubmitAccept) },
        fontSize = 15.ssp,
    )
}

@Composable
private fun DeclineSheet(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    SheetTitle(stringResource(R.string.lease_decline_title))
    Text(stringResource(R.string.lease_decline_info), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlinedTextField(
        value = state.note,
        onValueChange = { onIntent(LeaseDetailIntent.NoteChanged(it)) },
        label = { Text(stringResource(R.string.lease_decline_reason), fontFamily = Inter) },
        enabled = !state.busy,
        isError = state.noteError,
        minLines = 2,
        maxLines = 5,
        supportingText = { if (state.noteError) Text(stringResource(R.string.lease_reason_min, LeaseExitRules.DECLINE_MIN), fontFamily = Inter) },
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.invitation_decline),
        enabled = !state.busy,
        loading = state.busy,
        onClick = { onIntent(LeaseDetailIntent.SubmitDecline) },
        fontSize = 15.ssp,
    )
}

@Composable
private fun WriteReviewSheet(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    SheetTitle(stringResource(R.string.lease_write_review))
    Text(stringResource(R.string.lease_review_hidden_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    Spacer(Modifier.height(12.sdp))
    ReviewFormSection(
        form = state.reviewInfo?.form,
        draft = state.review,
        showError = state.reviewError,
        enabled = !state.busy,
        onRate = { k, n -> onIntent(LeaseDetailIntent.Rate(k, n)) },
        onText = { onIntent(LeaseDetailIntent.ReviewText(it)) },
        onTag = { onIntent(LeaseDetailIntent.ToggleTag(it)) },
    )
    Spacer(Modifier.height(12.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.lease_post_review),
        enabled = !state.busy,
        loading = state.busy,
        onClick = { onIntent(LeaseDetailIntent.SubmitReview) },
        fontSize = 15.ssp,
    )
}

/** Required review (leave / close / accept): stars per category + text + tags, right in the sheet. */
@Composable
private fun ReviewToggle(state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    Spacer(Modifier.height(14.sdp))
    Text(stringResource(R.string.lease_review_required_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
    Text(stringResource(R.string.lease_review_hidden_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    Spacer(Modifier.height(8.sdp))
    ReviewFormSection(
        form = state.reviewInfo?.form,
        draft = state.review,
        showError = state.reviewError,
        enabled = !state.busy,
        onRate = { k, n -> onIntent(LeaseDetailIntent.Rate(k, n)) },
        onText = { onIntent(LeaseDetailIntent.ReviewText(it)) },
        onTag = { onIntent(LeaseDetailIntent.ToggleTag(it)) },
    )
}

@Composable
private fun ConfirmDialog(kind: LeaseConfirm, state: LeaseDetailState, onIntent: (LeaseDetailIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(LeaseDetailIntent.DismissConfirm) },
        containerColor = AuthColors.Card,
        title = {
            Text(
                when (kind) {
                    LeaseConfirm.SEND -> stringResource(R.string.lease_confirm_send, day(state.moveOut?.iso()))
                    LeaseConfirm.CANCEL -> stringResource(R.string.lease_confirm_cancel)
                },
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 17.ssp,
            )
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { onIntent(if (kind == LeaseConfirm.SEND) LeaseDetailIntent.ConfirmSend else LeaseDetailIntent.ConfirmCancel) }) {
                Text(
                    stringResource(if (kind == LeaseConfirm.SEND) R.string.lease_send_request else R.string.lease_cancel_request),
                    color = if (kind == LeaseConfirm.SEND) AuthColors.OrangeText else AuthColors.Error,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(LeaseDetailIntent.DismissConfirm) }) { Text(stringResource(R.string.lease_not_now), color = AuthColors.Body, fontFamily = Inter) }
        },
    )
}
