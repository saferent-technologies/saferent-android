package com.saferenttechnologies.android.presentation.listing

import com.saferenttechnologies.android.presentation.common.rememberIdleHint
import com.saferenttechnologies.android.domain.model.Money
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.ApplyError
import com.saferenttechnologies.android.domain.model.REPORT_REASONS
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipGrid
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.util.Calendar
import java.util.TimeZone

// ---------------- bottom bar ----------------

/** "Ask a question" + "Apply"; or the state of my application (Pending → Withdraw, Approved → lease). */
@Composable
fun BottomBar(state: ListingDetailState, onIntent: (ListingDetailIntent) -> Unit) {
    HorizontalDivider(color = AuthColors.Border)
    Row(
        Modifier.fillMaxWidth().background(AuthColors.Background).navigationBarsPadding().padding(horizontal = 16.sdp, vertical = 12.sdp),
        horizontalArrangement = Arrangement.spacedBy(10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Apartment: the bar is about the SELECTED flat (an application for another flat doesn't block it).
        val apartment = state.detail?.isUnitPriced == true
        val status = if (apartment && !state.appliedToSelectedFlat) null else state.applicationStatus
        when (status) {
            "PENDING" -> {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.listing_application_sent), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                    Text(stringResource(R.string.listing_waiting_owner), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                OutlineButton(stringResource(R.string.listing_withdraw), enabled = !state.busy) { onIntent(ListingDetailIntent.Withdraw) }
            }
            "APPROVED" -> {
                // Lease screens aren't in the app yet → the status is shown.
                Text(
                    stringResource(R.string.listing_approved),
                    color = AuthColors.GreenText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.GreenBg).padding(16.sdp),
                )
            }
            else -> if (apartment) {
                val flat = state.selectedUnit
                Column(Modifier.weight(0.42f)) {
                    val price = Money.inr(flat?.let { f -> f.monthlyRent.ifBlank { f.salePrice } })
                    Text(price.ifBlank { stringResource(R.string.apartment_rent_not_disclosed) }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
                    if (flat?.monthlyRent?.isNotBlank() == true) Text(stringResource(R.string.explore_per_month).trim(), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
                    Text(
                        stringResource(R.string.listing_ask),
                        color = AuthColors.OrangeText,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.ssp,
                        modifier = Modifier.clickable { onIntent(ListingDetailIntent.StartEnquiry) }.padding(vertical = 2.sdp),
                    )
                }
                AuthPrimaryButton(
                    text = flat?.let { stringResource(R.string.apartment_apply_for, state.detail?.unitWord.orEmpty(), it.unitNumber) } ?: stringResource(R.string.listing_apply),
                    // Only an AVAILABLE flat can be applied for.
                    enabled = flat?.available == true && !state.busy,
                    loading = false,
                    onClick = { onIntent(ListingDetailIntent.StartApply) },
                    modifier = Modifier.weight(0.58f),
                    fontSize = 14.ssp,
                )
            } else {
                OutlineButton(stringResource(R.string.listing_ask), Modifier.weight(0.45f)) { onIntent(ListingDetailIntent.StartEnquiry) }
                val canApply = state.selectedUnitId != null || state.selectedBedId != null
                AuthPrimaryButton(
                    text = stringResource(R.string.listing_apply),
                    enabled = canApply && !state.busy,
                    loading = false,
                    onClick = { onIntent(ListingDetailIntent.StartApply) },
                    modifier = Modifier.weight(0.55f),
                )
            }
        }
    }
}

// ---------------- apply ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplySheet(state: ListingDetailState, form: ApplyForm, onIntent: (ListingDetailIntent) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picking by remember { mutableStateOf(false) }
    fun change(f: ApplyForm) = onIntent(ListingDetailIntent.ApplyChanged(f))
    ModalBottomSheet(onDismissRequest = { onIntent(ListingDetailIntent.CancelApply) }, sheetState = sheet, containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp).navigationBarsPadding().imePadding()) {
            Text(stringResource(R.string.listing_apply_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(4.sdp))
            val home = state.selectedUnit?.let { stringResource(R.string.listing_flat_n, it.unitNumber) }
                ?: state.selectedBed?.let { stringResource(R.string.listing_room_bed, it.roomNumber, it.bedNumber) }
            home?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp) }

            Spacer(Modifier.height(18.sdp))
            Text(stringResource(R.string.listing_move_in), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(6.sdp))
            Text(
                form.moveIn?.let { prettyDate(it.iso()) } ?: stringResource(R.string.listing_pick_date),
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Field).clickable { picking = true }.padding(14.sdp),
            )

            Spacer(Modifier.height(16.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.listing_occupants), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp, modifier = Modifier.weight(1f))
                StepButton("−", form.occupants > ListingDetailViewModel.OCCUPANTS.first) { change(form.copy(occupants = form.occupants - 1)) }
                Text(form.occupants.toString(), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp, textAlign = TextAlign.Center, modifier = Modifier.width(44.sdp))
                StepButton("+", form.occupants < ListingDetailViewModel.OCCUPANTS.last) { change(form.copy(occupants = form.occupants + 1)) }
            }

            Spacer(Modifier.height(16.sdp))
            OutlinedTextField(
                value = form.message,
                onValueChange = { change(form.copy(message = it.take(ListingDetailViewModel.MAX_MESSAGE))) },
                label = { Text(stringResource(R.string.listing_message_owner), fontFamily = Inter) },
                minLines = 3,
                shape = RoundedCornerShape(12.sdp),
                colors = propertyFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let {
                Spacer(Modifier.height(8.sdp))
                Text(applyErrorText(it), color = AuthColors.Error, fontFamily = Inter, fontSize = 13.ssp)
            }
            Spacer(Modifier.height(18.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.listing_send_application),
                enabled = !state.busy,
                loading = state.busy,
                onClick = { onIntent(ListingDetailIntent.SendApply) },
            )
            Spacer(Modifier.height(16.sdp))
        }
    }
    if (picking) MoveInPicker(form.moveIn, onPick = { change(form.copy(moveIn = it)); picking = false }, onDismiss = { picking = false })
}

@Composable
private fun applyErrorText(e: ApplyError) = stringResource(
    when (e) {
        ApplyError.CHOOSE_HOME -> R.string.listing_err_choose
        ApplyError.MOVE_IN_REQUIRED -> R.string.listing_err_move_in
        ApplyError.MOVE_IN_PAST -> R.string.listing_err_move_in_past
        ApplyError.OCCUPANTS -> R.string.listing_err_occupants
    },
)

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.sdp)
            .clip(CircleShape)
            .background(if (enabled) AuthColors.OrangeSoft else AuthColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp) }
}

/** Material date picker; days before today can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveInPicker(current: SimpleDate?, onPick: (SimpleDate) -> Unit, onDismiss: () -> Unit) {
    val today = SimpleDate.today()
    val todayUtc = remember { utcMillis(today) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current?.let(::utcMillis) ?: todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(SimpleDate.fromMillis(it)) } ?: onDismiss() }) {
                Text(stringResource(android.R.string.ok), color = AuthColors.OrangeText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

private fun utcMillis(d: SimpleDate): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    clear()
    set(d.year, d.month - 1, d.day)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppliedSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().padding(24.sdp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.sdp).clip(CircleShape).background(AuthColors.GreenBg), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check_circle), null, tint = AuthColors.Green, modifier = Modifier.size(34.sdp))
            }
            Spacer(Modifier.height(14.sdp))
            Text(stringResource(R.string.listing_applied_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(6.sdp))
            Text(stringResource(R.string.listing_applied_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.sdp))
            AuthPrimaryButton(text = stringResource(android.R.string.ok), enabled = true, loading = false, onClick = onDismiss)
        }
    }
}

// ---------------- ask / report ----------------

@Composable
fun EnquiryDialog(state: ListingDetailState, text: String, onIntent: (ListingDetailIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ListingDetailIntent.CancelEnquiry) },
        title = { Text(stringResource(R.string.listing_ask), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = {
            val askHint = rememberIdleHint()
            OutlinedTextField(
                value = text,
                onValueChange = { onIntent(ListingDetailIntent.EnquiryChanged(it)) },
                placeholder = askHint.placeholder(stringResource(R.string.listing_ask_hint)),
                interactionSource = askHint.source,
                minLines = 3,
                supportingText = { Text("${text.length} / ${ListingDetailViewModel.MAX_MESSAGE}", fontFamily = Inter) },
                shape = RoundedCornerShape(12.sdp),
                colors = propertyFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = !state.busy && text.trim().length >= ListingDetailViewModel.MIN_ENQUIRY, onClick = { onIntent(ListingDetailIntent.SendEnquiry) }) {
                Text(stringResource(R.string.listing_send), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(ListingDetailIntent.CancelEnquiry) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        containerColor = AuthColors.Card,
    )
}

@Composable
fun ReportDialog(state: ListingDetailState, form: ReportForm, onIntent: (ListingDetailIntent) -> Unit) {
    fun change(f: ReportForm) = onIntent(ListingDetailIntent.ReportChanged(f))
    AlertDialog(
        onDismissRequest = { onIntent(ListingDetailIntent.CancelReport) },
        title = { Text(stringResource(R.string.listing_report_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChipGrid(REPORT_REASONS, columns = 2) { r, mod ->
                    Chip(optionLabel(r), selected = form.reason == r, modifier = mod, maxLines = 1) { change(form.copy(reason = r)) }
                }
                Spacer(Modifier.height(12.sdp))
                val reportHint = rememberIdleHint()
                OutlinedTextField(
                    value = form.details,
                    onValueChange = { change(form.copy(details = it.take(ListingDetailViewModel.MAX_MESSAGE))) },
                    placeholder = reportHint.placeholder(stringResource(R.string.listing_report_hint)),
                    interactionSource = reportHint.source,
                    minLines = 3,
                    isError = form.tooShort,
                    supportingText = if (form.tooShort) {
                        @Composable { Text(stringResource(R.string.listing_report_short), fontFamily = Inter) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(12.sdp),
                    colors = propertyFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { onIntent(ListingDetailIntent.SendReport) }) {
                Text(stringResource(R.string.listing_report_send), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(ListingDetailIntent.CancelReport) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        containerColor = AuthColors.Card,
    )
}
