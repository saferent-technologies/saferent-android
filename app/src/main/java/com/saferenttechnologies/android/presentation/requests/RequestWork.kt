package com.saferenttechnologies.android.presentation.requests

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.RequestInvoice
import com.saferenttechnologies.android.domain.model.RequestRules
import com.saferenttechnologies.android.domain.model.ServiceRequest
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.WorkDetails
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.util.Calendar
import java.util.TimeZone

/** "Owner", "Me", "Shared", "No cost" (tenant) / "Owner", "Tenant", … (owner). */
@Composable
fun paidByLabel(key: String, landlord: Boolean): String = stringResource(
    when (key) {
        "OWNER" -> if (landlord) R.string.req_paid_you else R.string.req_paid_owner
        "TENANT" -> if (landlord) R.string.req_paid_tenant else R.string.req_paid_me
        "SHARED" -> R.string.req_paid_shared
        else -> R.string.req_paid_none
    },
)

/** "Paid by owner" / "Paid by tenant" / "Shared" / "No cost" for the summary line. */
@Composable
private fun paidBySummary(key: String): String = stringResource(
    when (key) {
        "OWNER" -> R.string.req_paidby_owner
        "TENANT" -> R.string.req_paidby_tenant
        "SHARED" -> R.string.req_paidby_shared
        else -> R.string.req_paidby_none
    },
)

private fun rupees(v: Double): String = money(RequestRules.costText(v))

/** Technician, estimate, work started; after resolution: completed, actual cost, who paid. Owner: "Edit". */
@Composable
fun WorkDetailsCard(r: ServiceRequest, landlord: Boolean, busy: Boolean, onEdit: () -> Unit) {
    val context = LocalContext.current
    val resolved = r.paidBy != null || r.actualCost != null || r.completedAt != null
    val hasWork = r.technician != null || r.estimatedCost != null || r.workStartedAt != null
    if (!hasWork && !resolved && !(landlord && r.can.update)) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.req_work_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp, modifier = Modifier.weight(1f))
            if (landlord && r.can.update) {
                Text(
                    stringResource(if (hasWork) R.string.req_work_edit else R.string.req_work_add),
                    color = if (busy) AuthColors.Muted else AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !busy, role = Role.Button, onClick = onEdit).padding(6.sdp),
                )
            }
        }
        if (!hasWork && !resolved) {
            Text(stringResource(R.string.req_work_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        }
        r.technician?.let { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(stringResource(R.string.req_work_technician))
                    Value(t.name)
                }
                t.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    Box(
                        Modifier.size(38.sdp).clip(CircleShape).background(AuthColors.OrangeSoft)
                            .clickable(role = Role.Button) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_call), stringResource(R.string.req_call_technician), tint = AuthColors.OrangeText, modifier = Modifier.size(18.sdp)) }
                }
            }
        }
        r.estimatedCost?.let { Pair(R.string.req_work_estimate, rupees(it)).let { (l, v) -> LabelValue(stringResource(l), v) } }
        r.workStartedAt?.let { LabelValue(stringResource(R.string.req_work_started), calendarDate(it)) }
        if (resolved) {
            r.completedAt?.let { LabelValue(stringResource(R.string.req_work_completed), prettyDate(it)) }
            r.actualCost?.let { LabelValue(stringResource(R.string.req_work_actual), rupees(it)) }
            r.paidBy?.let { LabelValue(stringResource(R.string.req_work_paid), paidBySummary(it)) }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun Value(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)

@Composable
private fun LabelValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
    }
}

/** Invoices / receipts: photo → viewer, PDF → opens; "Add receipt" when the server allows. */
@Composable
fun InvoicesCard(
    r: ServiceRequest,
    landlord: Boolean,
    busy: Boolean,
    opening: String?,
    onOpen: (RequestInvoice) -> Unit,
    onAdd: (List<String>) -> Unit,
) {
    if (r.invoices.isEmpty() && !r.can.addInvoice) return
    val picker = rememberPhotoPicker(RequestRules.MAX_PHOTOS, receipts = true, onPicked = onAdd)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Text(stringResource(R.string.req_invoices_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
        if (r.invoices.isEmpty()) Text(stringResource(R.string.req_invoices_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        r.invoices.forEachIndexed { i, inv ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface)
                    .clickable(enabled = opening == null, role = Role.Button) { onOpen(inv) }.padding(10.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(if (inv.isPdf) R.drawable.ic_document else R.drawable.ic_camera), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
                Spacer(Modifier.width(10.sdp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (inv.isPdf) R.string.req_invoice_pdf else R.string.req_invoice_photo, i + 1),
                        color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp,
                    )
                    Text(
                        listOfNotNull(
                            stringResource(
                                when {
                                    inv.byTenant && landlord -> R.string.req_invoice_by_tenant
                                    inv.byTenant -> R.string.req_invoice_by_you
                                    landlord -> R.string.req_invoice_by_you
                                    else -> R.string.req_invoice_by_owner
                                },
                            ),
                            inv.createdAt.takeIf { it.isNotBlank() }?.let { prettyDate(it) },
                        ).joinToString(" · "),
                        color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp,
                    )
                }
                Text(
                    stringResource(if (opening == inv.id) R.string.req_opening else R.string.req_open),
                    color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                )
            }
        }
        if (r.can.addInvoice) {
            Text(stringResource(R.string.req_add_receipt), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, modifier = Modifier.padding(top = 4.sdp))
            AddPhotoButtons(picker, enabled = !busy, receipts = true)
        }
    }
}

/** "Who paid for the repair?" chips (+ amount with the estimate as a hint). */
@Composable
fun WhoPaid(
    paidBy: String?,
    amount: String,
    estimate: Double?,
    landlord: Boolean,
    enabled: Boolean,
    amountError: String?,
    onPaidBy: (String) -> Unit,
    onAmount: (String) -> Unit,
) {
    Text(
        stringResource(if (landlord) R.string.req_who_paid_optional else R.string.req_who_paid),
        color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp,
    )
    Spacer(Modifier.height(8.sdp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        RequestRules.PAID_BY.forEach { k -> Chip(paidByLabel(k, landlord), selected = paidBy == k, enabled = enabled, modifier = Modifier.widthIn(min = 72.sdp), maxLines = 1) { onPaidBy(k) } }
    }
    if (paidBy != null && paidBy != "NO_COST") {
        Spacer(Modifier.height(10.sdp))
        estimate?.let { Text(stringResource(R.string.req_estimate_hint, rupees(it)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(bottom = 4.sdp)) }
        OutlinedTextField(
            value = amount,
            onValueChange = onAmount,
            label = { Text(stringResource(R.string.req_amount), fontFamily = Inter) },
            prefix = { Text("₹ ", fontFamily = Inter) },
            singleLine = true,
            enabled = enabled,
            isError = amountError != null,
            supportingText = amountError?.let { e -> { Text(e, fontFamily = Inter) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Owner: technician (name + phone, from contacts), estimated cost, work start date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDetailsSheet(work: WorkDetails, problems: Set<String>, showErrors: Boolean, busy: Boolean, onIntent: (RequestsIntent) -> Unit) {
    val context = LocalContext.current
    fun set(w: WorkDetails) = onIntent(RequestsIntent.EditWork(w))
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) set(work.copy(technicianPhone = c.getString(0).orEmpty(), technicianName = work.technicianName.ifBlank { c.getString(1).orEmpty() }))
                }
            }
        }
    }
    var picking by remember { mutableStateOf(false) }
    fun err(key: String, text: String) = if (showErrors && key in problems) text else null
    ModalBottomSheet(
        onDismissRequest = { onIntent(RequestsIntent.CloseWork) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            Text(stringResource(R.string.req_work_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Field(stringResource(R.string.req_work_technician_name), work.technicianName, !busy, err("technicianName", stringResource(R.string.req_err_technician))) { set(work.copy(technicianName = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field(stringResource(R.string.req_work_technician_phone), work.technicianPhone, !busy, err("technicianPhone", stringResource(R.string.req_err_phone)), KeyboardType.Phone) { set(work.copy(technicianPhone = it.take(16))) }
                }
                Spacer(Modifier.width(8.sdp))
                Box(
                    Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(enabled = !busy, role = Role.Button) {
                        runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_person), stringResource(R.string.inv_pick_contact), tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp)) }
            }
            Field(stringResource(R.string.req_work_estimate), work.estimatedCost, !busy, err("estimatedCost", stringResource(R.string.req_err_cost)), KeyboardType.Decimal, prefix = "₹ ") { set(work.copy(estimatedCost = it)) }
            Text(stringResource(R.string.req_work_start_date), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalAlignment = Alignment.CenterVertically) {
                OutlineAction(
                    work.workStartDate?.let { calendarDate(it) } ?: stringResource(R.string.req_pick_date), R.drawable.ic_edit, enabled = !busy, modifier = Modifier.weight(1f), compact = true,
                ) { picking = true }
                if (work.workStartDate != null) OutlineAction(stringResource(R.string.req_clear), enabled = !busy, compact = true) { set(work.copy(workStartDate = null)) }
            }
            err("workStartDate", stringResource(R.string.req_err_start))?.let { Text(it, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp) }
            Text(stringResource(R.string.req_work_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Spacer(Modifier.height(4.sdp))
            AuthPrimaryButton(text = stringResource(R.string.req_work_save), enabled = !busy, loading = busy, onClick = { onIntent(RequestsIntent.SaveWork) }, fontSize = 15.ssp)
        }
    }
    if (picking) WorkDatePicker(work.workStartDate, onDismiss = { picking = false }) { d -> picking = false; set(work.copy(workStartDate = d)) }
}

@Composable
private fun Field(label: String, value: String, enabled: Boolean, error: String?, keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        prefix = prefix?.let { p -> { Text(p, fontFamily = Inter) } },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Work start: 60 days back to 60 days ahead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDatePicker(current: String?, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val today = SimpleDate.today()
    val utc = TimeZone.getTimeZone("UTC")
    fun ms(d: SimpleDate) = Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
    val from = ms(today.plusDays(-RequestRules.WORK_START_DAYS))
    val to = ms(today.plusDays(RequestRules.WORK_START_DAYS))
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current?.let { SimpleDate.parse(it) }?.let(::ms) ?: ms(today),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in from..to
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}
