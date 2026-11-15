package com.saferenttechnologies.android.presentation.tenant

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.DepositRefund
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.domain.model.PaymentProofRules
import com.saferenttechnologies.android.domain.model.PendingReview
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.lease.ReviewFormSection
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/**
 * Tenant: a lease has ended → this screen first (before Explore / the dashboard):
 * review the rental + "Did your landlord refund your deposit?". No skip.
 */
@Composable
fun TenantReviewGateScreen(
    due: PendingReview,
    left: Int,
    state: ReviewGate,
    effects: Flow<TenantHomeEffect>,
    onIntent: (TenantHomeIntent) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { effects.collect { e -> if (e is TenantHomeEffect.Message) Toast.makeText(context, e.text, Toast.LENGTH_LONG).show() } }
    val depositAsked = due.asksDeposit
    val landlord = due.otherName.ifBlank { stringResource(R.string.lease_the_landlord) }

    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp)) {
            Spacer(Modifier.height(24.sdp))
            Box(Modifier.size(64.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_star), null, tint = AuthColors.Orange, modifier = Modifier.size(32.sdp))
            }
            Spacer(Modifier.height(14.sdp))
            Text(
                stringResource(R.string.gate_title, due.propertyTitle.ifBlank { due.place.ifBlank { stringResource(R.string.lease_your_rental) } }),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.ssp,
            )
            Spacer(Modifier.height(6.sdp))
            Text(
                stringResource(R.string.gate_body, landlord) + (due.endedAt.takeIf { it.isNotBlank() }?.let { " " + stringResource(R.string.gate_ended_on, prettyDate(it.take(10))) }.orEmpty()),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 14.ssp,
            )
            if (left > 1) Text(stringResource(R.string.gate_left, left), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))

            Spacer(Modifier.height(18.sdp))
            Text(stringResource(R.string.lease_review_required_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
            Text(stringResource(R.string.lease_review_hidden_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Spacer(Modifier.height(8.sdp))
            ReviewFormSection(
                form = state.form,
                draft = state.draft,
                showError = state.showErrors,
                enabled = !state.busy,
                onRate = { k, n -> onIntent(TenantHomeIntent.GateRate(k, n)) },
                onText = { onIntent(TenantHomeIntent.GateText(it)) },
                onTag = { onIntent(TenantHomeIntent.GateTag(it)) },
            )

            if (depositAsked) {
                Spacer(Modifier.height(14.sdp))
                Text(
                    stringResource(R.string.gate_deposit_q, landlord, Money.inr(due.depositAmount)),
                    color = AuthColors.Ink,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.ssp,
                )
                Spacer(Modifier.height(8.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    listOf(
                        DepositRefund.YES to R.string.gate_refund_yes,
                        DepositRefund.PARTIAL to R.string.gate_refund_partial,
                        DepositRefund.NO to R.string.gate_refund_no,
                    ).forEach { (answer, label) ->
                        Chip(stringResource(label), selected = state.refund == answer, enabled = !state.busy, modifier = Modifier.weight(1f), maxLines = 1) {
                            onIntent(TenantHomeIntent.GateRefund(answer))
                        }
                    }
                }
                if (state.showErrors && state.refund == null) {
                    Text(stringResource(R.string.gate_refund_needed), color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
                }
                if (state.refund == DepositRefund.PARTIAL) {
                    Spacer(Modifier.height(10.sdp))
                    OutlinedTextField(
                        value = state.refundAmount,
                        onValueChange = { onIntent(TenantHomeIntent.GateRefundAmount(it)) },
                        label = { Text(stringResource(R.string.gate_refund_amount), fontFamily = Inter) },
                        prefix = { Text("₹ ", fontFamily = Inter) },
                        isError = state.showErrors && !PaymentProofRules.amountOk(state.refundAmount),
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = propertyFieldColors(),
                        shape = RoundedCornerShape(12.sdp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.refund == DepositRefund.NO) {
                    Text(stringResource(R.string.gate_refund_no_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
                }
            }
            Spacer(Modifier.height(20.sdp))
        }
        // Pinned: the only way forward.
        Column(Modifier.fillMaxWidth().background(AuthColors.Background).padding(horizontal = 20.sdp, vertical = 12.sdp)) {
            AuthPrimaryButton(
                text = stringResource(R.string.gate_submit),
                enabled = state.form != null && state.ready(depositAsked),
                loading = state.busy,
                onClick = { onIntent(TenantHomeIntent.GateSubmit) },
                fontSize = 15.ssp,
            )
            Text(
                stringResource(R.string.gate_footer),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 11.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.sdp),
            )
        }
    }
}
