package com.saferenttechnologies.android.presentation.society

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.CommonArea
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.ListingSociety
import com.saferenttechnologies.android.domain.model.ParkingSlot
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.SocietyRule
import com.saferenttechnologies.android.domain.model.SocietyRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

private fun rupees(v: Double) = money(ComplianceRules.amountText(v))

// ======================= owner: Society =======================

@Composable
fun SocietyScreen(state: SocietyState, effects: Flow<SocietyEffect>, onIntent: (SocietyIntent) -> Unit) {
    BackHandler { onIntent(SocietyIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.soc_saved)
    LaunchedEffect(effects) { effects.collect { e -> if (e is SocietyEffect.Message && e.text.isNotBlank()) snackbar.showSnackbar(if (e.text == SocietyViewModel.SAVED) saved else e.text) } }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(stringResource(R.string.soc_title), state.title) { onIntent(SocietyIntent.Close) }
            val data = state.data
            when {
                state.notApplicable != null -> Message(state.notApplicable.ifBlank { stringResource(R.string.soc_not_applicable) })
                data == null && state.error == null -> Box(Modifier.weight(1f)) { ListSkeleton(rows = 6, thumbnail = null, lines = 2) }
                data == null -> Box(Modifier.weight(1f)) { ErrorBox(state.error.orEmpty()) { onIntent(SocietyIntent.Retry) } }
                else -> {
                    if (state.form.isSociety || data.parkingSlots.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 16.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                            Chip(stringResource(R.string.soc_tab_details), selected = !state.parkingTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(SocietyIntent.Tab(false)) }
                            Chip(stringResource(R.string.soc_tab_parking, data.parkingSlots.size), selected = state.parkingTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(SocietyIntent.Tab(true)) }
                        }
                    }
                    if (state.parkingTab) {
                        ParkingList(state, onIntent)
                    } else {
                        SocietyForm(state, onIntent)
                        Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
                            AuthPrimaryButton(stringResource(R.string.soc_save), enabled = state.changed && !state.saving, loading = state.saving, onClick = { onIntent(SocietyIntent.Save) }, fontSize = 15.ssp)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp, start = 16.sdp, end = 16.sdp))
    }
    state.slot?.let { SlotSheet(it, state, onIntent) }
    state.deleteSlot?.let { s ->
        AlertDialog(
            onDismissRequest = { onIntent(SocietyIntent.AskDelete(null)) },
            title = { Text(stringResource(R.string.soc_slot_delete_title, s.slotNumber), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            confirmButton = { TextButton(onClick = { onIntent(SocietyIntent.ConfirmDelete) }) { Text(stringResource(R.string.reg_delete), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { onIntent(SocietyIntent.AskDelete(null)) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
            containerColor = AuthColors.Card,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SocietyForm(state: SocietyState, onIntent: (SocietyIntent) -> Unit) {
    val context = LocalContext.current
    val f = state.form
    val edit = !state.saving
    fun set(x: com.saferenttechnologies.android.domain.model.Society) = onIntent(SocietyIntent.Edit(x))
    val problemTexts = state.problems.mapValues { (_, p) -> problemText(p) }
    fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) problemTexts[path] else null
    var contactFor by remember { mutableStateOf<String?>(null) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val target = contactFor
        contactFor = null
        if (res.resultCode == Activity.RESULT_OK && uri != null && target != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val phone = c.getString(0).orEmpty()
                        val name = c.getString(1).orEmpty()
                        if (target == "contact") set(f.copy(contactPhone = phone, contactName = f.contactName.ifBlank { name }))
                        else set(f.copy(gateContactPhone = phone, gateContactName = f.gateContactName.ifBlank { name }))
                    }
                }
            }
        }
    }
    fun pick(target: String) {
        contactFor = target
        runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
    }
    val rulesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onIntent(SocietyIntent.UploadRules(it.toString())) } }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.soc_is_society), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                Text(stringResource(R.string.soc_is_society_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            Switch(checked = f.isSociety, onCheckedChange = { set(f.copy(isSociety = it)) }, enabled = edit, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
        }
        if (!f.isSociety) return@Column

        // Association
        Section(stringResource(R.string.soc_association))
        Input(stringResource(R.string.soc_name), f.associationName, edit, error = e("associationName"), placeholder = stringResource(R.string.soc_name_hint)) { set(f.copy(associationName = it)) }
        Input(stringResource(R.string.soc_reg_no), f.registrationNumber, edit, error = e("registrationNumber")) { set(f.copy(registrationNumber = it)) }
        Input(stringResource(R.string.soc_contact_name), f.contactName, edit, error = e("contactName")) { set(f.copy(contactName = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            SocietyRules.ROLES.forEach { r -> Chip(stringResource(roleLabel(r)), selected = f.contactRole == r, enabled = edit, maxLines = 1) { set(f.copy(contactRole = if (f.contactRole == r) null else r)) } }
        }
        PhoneRow(stringResource(R.string.soc_contact_phone), f.contactPhone, edit, e("contactPhone"), { set(f.copy(contactPhone = it)) }) { pick("contact") }
        Input(stringResource(R.string.soc_contact_email), f.contactEmail, edit, error = e("contactEmail"), keyboard = KeyboardType.Email) { set(f.copy(contactEmail = it)) }
        Input(stringResource(R.string.soc_office_hours), f.officeHours, edit, error = e("officeHours"), placeholder = stringResource(R.string.soc_office_hours_hint)) { set(f.copy(officeHours = it)) }

        // Maintenance
        Section(stringResource(R.string.soc_maintenance))
        Input(stringResource(R.string.soc_maint_amount), f.maintenanceAmount, edit, error = e("maintenanceAmount"), keyboard = KeyboardType.Decimal, prefix = "₹ ") { set(f.copy(maintenanceAmount = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
            SocietyRules.FREQUENCIES.forEach { q -> Chip(stringResource(frequencyLabel(q)), selected = f.maintenanceFrequency == q, enabled = edit, maxLines = 1) { set(f.copy(maintenanceFrequency = q)) } }
        }
        e("maintenanceFrequency")?.let { ErrorLine(it) }
        state.monthly?.takeIf { f.maintenanceFrequency != "MONTHLY" }?.let { Text(stringResource(R.string.soc_per_month, rupees(it)), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
        Label(stringResource(R.string.soc_who_pays))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
            SocietyRules.PAID_BY.forEach { p -> Chip(stringResource(paidByLabel(p)), selected = f.maintenancePaidBy == p, enabled = edit, maxLines = 1) { set(f.copy(maintenancePaidBy = p)) } }
        }
        if (f.maintenancePaidBy == "TENANT") {
            Label(stringResource(R.string.soc_pays_to))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                SocietyRules.PAY_TO.forEach { t ->
                    Chip(stringResource(if (t == "SOCIETY") R.string.soc_pay_society else R.string.soc_pay_owner), selected = f.maintenancePayTo == t, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(f.copy(maintenancePayTo = t)) }
                }
            }
            e("maintenancePayTo")?.let { ErrorLine(it) }
        }

        // Rules
        Section(stringResource(R.string.soc_rules))
        f.rules.forEachIndexed { i, r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(stringResource(R.string.soc_rule_title), r.title, edit, error = e("rules.$i.title")) { onIntent(SocietyIntent.EditRule(i, r.copy(title = it))) } }
                    DeleteIcon(edit) { onIntent(SocietyIntent.RemoveRule(i)) }
                }
                Input(stringResource(R.string.soc_rule_desc), r.description, edit, singleLine = false, error = e("rules.$i.description")) { onIntent(SocietyIntent.EditRule(i, r.copy(description = it))) }
            }
        }
        if (f.rules.size < SocietyRules.MAX_RULES) OutlineAction(stringResource(R.string.soc_add_rule), R.drawable.ic_add, enabled = edit, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(SocietyIntent.AddRule) }
        e("rules")?.let { ErrorLine(it) }
        // Rules PDF + share with tenants
        val doc = state.data?.rulesDocument
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (doc != null) R.string.soc_rules_pdf_uploaded else R.string.soc_rules_pdf), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                OutlineAction(
                    stringResource(if (state.uploadingRules) R.string.comp_uploading else if (doc != null) R.string.comp_replace else R.string.soc_upload_pdf), R.drawable.ic_document,
                    enabled = !state.uploadingRules, compact = true,
                ) { runCatching { rulesPicker.launch(arrayOf("application/pdf")) } }
            }
            if (doc != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.soc_share_rules), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                    Switch(checked = doc.sharedWithTenant, onCheckedChange = { onIntent(SocietyIntent.ShareRules(it)) }, enabled = !state.sharing, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
                }
            }
        }

        // Visitors & gate
        Section(stringResource(R.string.soc_visitors))
        Input(stringResource(R.string.soc_visitor_policy), f.visitorPolicy, edit, singleLine = false, error = e("visitorPolicy"), placeholder = stringResource(R.string.soc_visitor_policy_hint)) { set(f.copy(visitorPolicy = it)) }
        Label(stringResource(R.string.soc_gate_access))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
            SocietyRules.GATE_METHODS.forEach { m -> Chip(stringResource(gateLabel(m)), selected = m in f.gateAccessMethods, enabled = edit, maxLines = 1) { onIntent(SocietyIntent.ToggleGate(m)) } }
        }
        e("gateAccessMethods")?.let { ErrorLine(it) }
        Input(stringResource(R.string.soc_gate_name), f.gateContactName, edit, error = e("gateContactName"), placeholder = stringResource(R.string.soc_gate_name_hint)) { set(f.copy(gateContactName = it)) }
        PhoneRow(stringResource(R.string.soc_gate_phone), f.gateContactPhone, edit, e("gateContactPhone"), { set(f.copy(gateContactPhone = it)) }) { pick("gate") }

        // Amenities
        Section(stringResource(R.string.soc_amenities))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
            SocietyRules.AREA_SUGGESTIONS.filter { s -> f.commonAreas.none { it.name.equals(s, true) } }.forEach { s ->
                Chip("+ $s", selected = false, enabled = edit && f.commonAreas.size < SocietyRules.MAX_AREAS, maxLines = 1) { onIntent(SocietyIntent.AddArea(s)) }
            }
        }
        f.commonAreas.forEachIndexed { i, a ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(stringResource(R.string.soc_area_name), a.name, edit, error = e("commonAreas.$i.name")) { onIntent(SocietyIntent.EditArea(i, a.copy(name = it))) } }
                    DeleteIcon(edit) { onIntent(SocietyIntent.RemoveArea(i)) }
                }
                Input(stringResource(R.string.soc_area_timings), a.timings, edit, placeholder = stringResource(R.string.soc_area_timings_hint)) { onIntent(SocietyIntent.EditArea(i, a.copy(timings = it))) }
                Input(stringResource(R.string.soc_area_notes), a.notes, edit) { onIntent(SocietyIntent.EditArea(i, a.copy(notes = it))) }
            }
        }
        if (f.commonAreas.size < SocietyRules.MAX_AREAS) OutlineAction(stringResource(R.string.soc_add_area), R.drawable.ic_add, enabled = edit, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(SocietyIntent.AddArea()) }
        Input(stringResource(R.string.soc_clubhouse_rules), f.clubhouseRules, edit, singleLine = false, error = e("clubhouseRules")) { set(f.copy(clubhouseRules = it)) }

        // Move-in / move-out
        Section(stringResource(R.string.soc_move))
        YesNo(stringResource(R.string.soc_move_permission), f.moveRequiresPermission, edit) { set(f.copy(moveRequiresPermission = it)) }
        e("moveRequiresPermission")?.let { ErrorLine(it) }
        if (f.moveRequiresPermission == true) {
            Input(stringResource(R.string.soc_move_notice), f.moveNoticeDays?.toString().orEmpty(), edit, error = e("moveNoticeDays"), keyboard = KeyboardType.Number) { t ->
                set(f.copy(moveNoticeDays = t.filter(Char::isDigit).take(2).toIntOrNull()))
            }
            Input(stringResource(R.string.soc_move_charge), f.moveChargeAmount, edit, error = e("moveChargeAmount"), keyboard = KeyboardType.Decimal, prefix = "₹ ") { set(f.copy(moveChargeAmount = it)) }
            Input(stringResource(R.string.soc_move_deposit), f.moveDepositAmount, edit, error = e("moveDepositAmount"), keyboard = KeyboardType.Decimal, prefix = "₹ ") { set(f.copy(moveDepositAmount = it)) }
        }
        Input(stringResource(R.string.soc_move_timings), f.moveAllowedTimings, edit, error = e("moveAllowedTimings"), placeholder = stringResource(R.string.soc_move_timings_hint)) { set(f.copy(moveAllowedTimings = it)) }
        YesNo(stringResource(R.string.soc_move_lift), f.moveLiftBooking, edit) { set(f.copy(moveLiftBooking = it)) }
        Input(stringResource(R.string.soc_move_notes), f.moveNotes, edit, singleLine = false, error = e("moveNotes")) { set(f.copy(moveNotes = it)) }
        Spacer(Modifier.height(8.sdp))
    }
}

@Composable
private fun ColumnScope.ParkingList(state: SocietyState, onIntent: (SocietyIntent) -> Unit) {
    val slots = state.data?.parkingSlots.orEmpty()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        if (slots.isEmpty()) Text(stringResource(R.string.soc_parking_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        SocietyRules.grouped(slots).forEach { (unitId, list) ->
            Text(
                if (unitId == null) stringResource(R.string.soc_unallocated) else list.first().unitLabel ?: state.units.firstOrNull { it.id == unitId }?.label ?: stringResource(R.string.soc_flat),
                color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp, modifier = Modifier.padding(top = 6.sdp),
            )
            list.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                        .clickable(enabled = !state.slotBusy, role = Role.Button) { onIntent(SocietyIntent.OpenSlot(s)) }.padding(start = 12.sdp, top = 6.sdp, bottom = 6.sdp, end = 4.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.slotNumber, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                        Text(slotDetails(s), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                    DeleteIcon(!state.slotBusy) { onIntent(SocietyIntent.AskDelete(s)) }
                }
            }
        }
    }
    Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
        AuthPrimaryButton(stringResource(R.string.soc_add_slot), enabled = !state.slotBusy, loading = false, onClick = { onIntent(SocietyIntent.OpenSlot(ParkingSlot())) }, fontSize = 15.ssp)
    }
}

/** "Car, covered" / "Bike". */
@Composable
private fun slotDetails(s: ParkingSlot): String = listOfNotNull(
    stringResource(vehicleLabel(s.vehicleType)),
    if (s.covered) stringResource(R.string.soc_covered).lowercase() else null,
    s.notes.ifBlank { null },
).joinToString(", ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSheet(slot: ParkingSlot, state: SocietyState, onIntent: (SocietyIntent) -> Unit) {
    val busy = state.slotBusy
    val problemTexts = state.slotProblems.mapValues { (_, p) -> problemText(p) }
    fun set(s: ParkingSlot) = onIntent(SocietyIntent.EditSlot(s))
    var flatMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onIntent(SocietyIntent.OpenSlot(null)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            Text(stringResource(if (slot.id.isBlank()) R.string.soc_add_slot else R.string.soc_edit_slot), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Input(stringResource(R.string.soc_slot_number), slot.slotNumber, !busy, error = if (state.slotErrors) problemTexts["slotNumber"] else null, placeholder = stringResource(R.string.soc_slot_number_hint)) { set(slot.copy(slotNumber = it)) }
            Label(stringResource(R.string.soc_slot_flat))
            Box {
                val label = slot.unitId?.let { id -> state.units.firstOrNull { it.id == id }?.label ?: slot.unitLabel } ?: stringResource(R.string.soc_not_allocated)
                OutlineAction(label, R.drawable.ic_edit, enabled = !busy, modifier = Modifier.fillMaxWidth(), compact = true) { flatMenu = true }
                DropdownMenu(expanded = flatMenu, onDismissRequest = { flatMenu = false }, modifier = Modifier.background(AuthColors.Card)) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.soc_not_allocated), fontFamily = Inter) }, onClick = { flatMenu = false; set(slot.copy(unitId = null, unitLabel = null)) })
                    state.units.forEach { u -> DropdownMenuItem(text = { Text(u.label, fontFamily = Inter) }, onClick = { flatMenu = false; set(slot.copy(unitId = u.id, unitLabel = u.label)) }) }
                }
            }
            Label(stringResource(R.string.soc_vehicle))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                SocietyRules.VEHICLES.forEach { v -> Chip(stringResource(vehicleLabel(v)), selected = slot.vehicleType == v, enabled = !busy, modifier = Modifier.weight(1f), maxLines = 1) { set(slot.copy(vehicleType = v)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.soc_covered), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Switch(checked = slot.covered, onCheckedChange = { set(slot.copy(covered = it)) }, enabled = !busy, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
            }
            Input(stringResource(R.string.soc_slot_notes), slot.notes, !busy, error = if (state.slotErrors) problemTexts["notes"] else null) { set(slot.copy(notes = it)) }
            state.slotServerError?.let { ErrorLine(it) }
            AuthPrimaryButton(stringResource(R.string.soc_save), enabled = !busy, loading = busy, onClick = { onIntent(SocietyIntent.SaveSlot) }, fontSize = 15.ssp)
        }
    }
}

// ======================= tenant: listing "Society" =======================

/** Listing: maintenance, gate access, amenities, rules, visitors, moving in, parking. No contacts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingSocietySection(s: ListingSociety?) {
    if (s == null) return
    var allRules by remember { mutableStateOf(false) }
    val maintenance = maintenanceLine(s.maintenanceAmount, s.maintenanceFrequency, s.maintenanceMonthly, s.maintenancePaidBy)
    if (maintenance == null && s.rules.isEmpty() && s.commonAreas.isEmpty() && s.gateAccessMethods.isEmpty() && s.visitorPolicy.isBlank() && s.move == null && s.parking.isEmpty() && s.associationName.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Text(stringResource(R.string.soc_l_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
        if (s.associationName.isNotBlank()) Text(s.associationName, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        maintenance?.let { LabelValue(stringResource(R.string.soc_maintenance), it) }
        if (s.gateAccessMethods.isNotEmpty()) {
            Label(stringResource(R.string.soc_gate_access))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                s.gateAccessMethods.forEach { m -> Pill(stringResource(gateLabel(m)), AuthColors.Ink, AuthColors.Surface) }
            }
        }
        if (s.commonAreas.isNotEmpty()) {
            Label(stringResource(R.string.soc_amenities))
            s.commonAreas.forEach { a ->
                Row {
                    Text(a.name, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                    if (a.timings.isNotBlank()) Text(a.timings, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                }
                if (a.notes.isNotBlank()) Text(a.notes, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
        if (s.clubhouseRules.isNotBlank()) Note(s.clubhouseRules)
        if (s.parking.isNotEmpty()) {
            Label(stringResource(R.string.soc_l_parking))
            s.parking.forEach { p ->
                Text(
                    pluralStringResource(R.plurals.soc_l_parking_line, p.count, p.count, stringResource(vehicleLabel(p.vehicleType)).lowercase(), if (p.covered) stringResource(R.string.soc_covered).lowercase() else stringResource(R.string.soc_open).lowercase()),
                    color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp,
                )
            }
        }
        if (s.rules.isNotEmpty()) {
            Label(stringResource(R.string.soc_rules))
            (if (allRules) s.rules else s.rules.take(3)).forEach { r ->
                Text("• ${r.title}", color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
                if (r.description.isNotBlank()) Text(r.description, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(start = 12.sdp))
            }
            if (s.rules.size > 3) {
                Text(
                    stringResource(if (allRules) R.string.soc_l_less else R.string.soc_l_see_all, s.rules.size), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button) { allRules = !allRules }.padding(vertical = 4.sdp),
                )
            }
        }
        if (s.visitorPolicy.isNotBlank()) {
            Label(stringResource(R.string.soc_visitors))
            Text(s.visitorPolicy, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        }
        s.move?.let { m ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
                verticalArrangement = Arrangement.spacedBy(4.sdp),
            ) {
                Text(stringResource(R.string.soc_l_moving_in), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                m.requiresPermission?.let { Text(stringResource(if (it) R.string.soc_l_permission_needed else R.string.soc_l_no_permission), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp) }
                m.noticeDays?.let { LabelValue(stringResource(R.string.soc_l_notice), pluralStringResource(R.plurals.soc_days, it, it)) }
                m.charge?.let { LabelValue(stringResource(R.string.soc_l_charges), rupees(it)) }
                m.deposit?.let { LabelValue(stringResource(R.string.soc_l_deposit), rupees(it)) }
                m.allowedTimings.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(R.string.soc_l_timings), it) }
                m.liftBooking?.let { LabelValue(stringResource(R.string.soc_move_lift), stringResource(if (it) R.string.inv_yes else R.string.inv_no)) }
                if (m.notes.isNotBlank()) Text(m.notes, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
    }
}

/** "₹9,000 quarterly (≈ ₹3,000/month) · Tenant pays" / "Included in rent". */
@Composable
fun maintenanceLine(amount: Double?, frequency: String?, monthly: Double?, paidBy: String?): String? {
    if (paidBy == "INCLUDED_IN_RENT") return stringResource(R.string.soc_paid_included)
    if (amount == null) return paidBy?.let { stringResource(paidByShort(it)) }
    val main = buildString {
        append(rupees(amount))
        frequency?.let { append(" "); append(stringResource(frequencyLabel(it)).lowercase()) }
        val m = monthly ?: SocietyRules.monthly(amount, frequency)
        if (m != null && frequency != null && frequency != "MONTHLY") append(" ").append(stringResource(R.string.soc_per_month_short, rupees(m)))
    }
    return listOfNotNull(main, paidBy?.let { stringResource(paidByShort(it)) }).joinToString(" · ")
}

// ======================= lease: Society card =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaseSocietyCard(state: LeaseSocietyState, onIntent: (LeaseSocietyIntent) -> Unit, onOpenRules: () -> Unit) {
    val context = LocalContext.current
    val d = state.data ?: return
    val s = d.society?.takeIf { it.isSociety }
    if (s == null && d.myParking.isEmpty() && d.approval.status == null && !d.canEditApproval) return
    Column(
        Modifier.fillMaxWidth().padding(top = 12.sdp).clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.soc_l_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.weight(1f))
            ApprovalChip(d.approval.status)
        }
        s?.associationName?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
        if (d.approval.status == "REJECTED" && d.approval.note.isNotBlank()) {
            Text(stringResource(R.string.soc_rejected_note, d.approval.note), color = AuthColors.Error, fontFamily = Inter, fontSize = 13.ssp)
        }
        d.approval.date?.takeIf { d.approval.status == "APPROVED" || d.approval.status == "REJECTED" }?.let { Text(calendarDate(it), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        if (d.canEditApproval) {
            OutlineAction(stringResource(R.string.soc_update_approval), R.drawable.ic_edit, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(LeaseSocietyIntent.OpenApproval) }
        }
        if (s != null) {
            if (s.contactName.isNotBlank() || s.contactPhone.isNotBlank()) {
                ContactRow(
                    title = listOfNotNull(s.contactName.ifBlank { null }, s.contactRole?.let { stringResource(roleLabel(it)) }).joinToString(" · "),
                    sub = s.officeHours,
                    phone = s.contactPhone,
                    email = s.contactEmail,
                )
            }
            if (s.gateContactPhone.isNotBlank() || s.gateContactName.isNotBlank()) {
                ContactRow(title = s.gateContactName.ifBlank { stringResource(R.string.soc_gate) }, sub = "", phone = s.gateContactPhone, email = "")
            }
            maintenanceLine(ComplianceRules.amount(s.maintenanceAmount)?.takeUnless { it.isNaN() }, s.maintenanceFrequency, s.maintenanceMonthly, s.maintenancePaidBy)?.let {
                LabelValue(stringResource(R.string.soc_maintenance), it)
            }
        }
        if (d.myParking.isNotEmpty()) {
            Label(stringResource(R.string.soc_your_parking))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                d.myParking.forEach { p -> Pill("${p.slotNumber} · ${slotDetails(p)}", AuthColors.OrangeText, AuthColors.OrangeSoft) }
            }
        }
        if (d.rulesDocument != null) {
            OutlineAction(stringResource(R.string.soc_rules_open), R.drawable.ic_document, modifier = Modifier.fillMaxWidth(), compact = true, onClick = onOpenRules)
        }
    }
    state.approval?.let { ApprovalSheet(it, state, onIntent) }
}

@Composable
private fun ContactRow(title: String, sub: String, phone: String, email: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).padding(10.sdp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (email.isNotBlank()) {
            RoundIcon(R.drawable.ic_mail, stringResource(R.string.soc_email)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.trim()}"))) }
            }
            Spacer(Modifier.width(6.sdp))
        }
        if (phone.isNotBlank()) {
            RoundIcon(R.drawable.ic_call, stringResource(R.string.inv_call)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalSheet(a: com.saferenttechnologies.android.domain.model.SocietyApproval, state: LeaseSocietyState, onIntent: (LeaseSocietyIntent) -> Unit) {
    val busy = state.saving
    val problemTexts = state.problems.mapValues { (_, p) -> problemText(p) }
    fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) problemTexts[path] else null
    var picking by remember { mutableStateOf(false) }
    fun set(x: com.saferenttechnologies.android.domain.model.SocietyApproval) = onIntent(LeaseSocietyIntent.EditApproval(x))
    ModalBottomSheet(
        onDismissRequest = { onIntent(LeaseSocietyIntent.CloseApproval) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            Text(stringResource(R.string.soc_approval_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                SocietyRules.APPROVALS.forEach { st -> Chip(stringResource(approvalLabel(st)), selected = a.status == st, enabled = !busy, maxLines = 1) { set(a.copy(status = st)) } }
            }
            if (a.status == "APPROVED" || a.status == "REJECTED") {
                Label(stringResource(R.string.soc_approval_date))
                OutlineAction(a.date?.let { calendarDate(it) } ?: stringResource(R.string.comp_pick_date), R.drawable.ic_edit, enabled = !busy, modifier = Modifier.fillMaxWidth(), compact = true) { picking = true }
                e("date")?.let { ErrorLine(it) }
            }
            Input(
                stringResource(if (a.status == "REJECTED") R.string.soc_approval_note_required else R.string.soc_approval_note), a.note, !busy, singleLine = false, error = e("note"),
            ) { set(a.copy(note = it)) }
            AuthPrimaryButton(stringResource(R.string.soc_save), enabled = !busy, loading = busy, onClick = { onIntent(LeaseSocietyIntent.SaveApproval) }, fontSize = 15.ssp)
        }
    }
    if (picking) PastDatePicker(a.date, onDismiss = { picking = false }) { d -> picking = false; set(a.copy(date = d)) }
}

// ======================= small parts =======================

@Composable
private fun TopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Message(text: String) = Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
}

@Composable
private fun Section(text: String) =
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.padding(top = 8.sdp))

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun Note(text: String) = Text(
    text, color = AuthColors.Ink, fontFamily = Inter, fontSize = 12.ssp,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).padding(10.sdp),
)

@Composable
private fun LabelValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(0.4f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun ErrorBox(text: String, onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh, onClick = onRetry)
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) = Text(
    text, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.ssp, maxLines = 1,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 4.sdp),
)

/** Pending amber, Approved green, Rejected red, Not required grey. */
@Composable
private fun ApprovalChip(status: String?) = when (status) {
    "APPROVED" -> Pill(stringResource(R.string.soc_appr_approved), AuthColors.GreenText, AuthColors.GreenBg)
    "REJECTED" -> Pill(stringResource(R.string.soc_appr_rejected), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
    "PENDING" -> Pill(stringResource(R.string.soc_appr_pending), Amber, Amber.copy(alpha = 0.12f))
    "NOT_REQUIRED" -> Pill(stringResource(R.string.soc_appr_not_required), AuthColors.Body, AuthColors.Surface)
    else -> Unit
}

@Composable
private fun DeleteIcon(enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.sdp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.reg_delete), tint = if (enabled) AuthColors.Error else AuthColors.Muted, modifier = Modifier.size(18.sdp))
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(Modifier.size(36.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, tint = AuthColors.OrangeText, modifier = Modifier.size(16.sdp))
    }
}

/** Yes / No / Skip (Skip = not answered). */
@Composable
private fun YesNo(label: String, value: Boolean?, enabled: Boolean, onChange: (Boolean?) -> Unit) {
    Column {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.padding(bottom = 6.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Chip(stringResource(R.string.inv_yes), selected = value == true, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(true) }
            Chip(stringResource(R.string.inv_no), selected = value == false, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(false) }
            Chip(stringResource(R.string.safety_skip), selected = value == null, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(null) }
        }
    }
}

@Composable
private fun PhoneRow(label: String, value: String, enabled: Boolean, error: String?, onChange: (String) -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Input(label, value, enabled, error = error, keyboard = KeyboardType.Phone) { onChange(it.take(16)) } }
        Spacer(Modifier.width(8.sdp))
        Box(
            Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(enabled = enabled, role = Role.Button, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_person), stringResource(R.string.inv_pick_contact), tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp)) }
    }
}

@Composable
private fun Input(label: String, value: String, enabled: Boolean, singleLine: Boolean = true, error: String? = null, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        placeholder = placeholder?.let { p -> { Text(p, fontFamily = Inter) } },
        prefix = prefix?.let { p -> { Text(p, fontFamily = Inter) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, enabled = enabled, isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(current: String?, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val utc = TimeZone.getTimeZone("UTC")
    fun ms(d: SimpleDate) = Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
    val today = ms(SimpleDate.today())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ms(current?.let { SimpleDate.parse(it) } ?: SimpleDate.today()),
        selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

private fun roleLabel(r: String) = when (r) {
    "SECRETARY" -> R.string.soc_role_secretary
    "PRESIDENT" -> R.string.soc_role_president
    "MANAGER" -> R.string.soc_role_manager
    "FACILITY_MANAGER" -> R.string.soc_role_facility
    else -> R.string.soc_role_other
}

private fun frequencyLabel(q: String) = when (q) {
    "MONTHLY" -> R.string.soc_freq_monthly
    "QUARTERLY" -> R.string.soc_freq_quarterly
    "HALF_YEARLY" -> R.string.soc_freq_half_yearly
    else -> R.string.soc_freq_yearly
}

private fun paidByLabel(p: String) = when (p) {
    "INCLUDED_IN_RENT" -> R.string.soc_paid_included
    "TENANT" -> R.string.soc_paid_tenant
    else -> R.string.soc_paid_owner
}

private fun paidByShort(p: String) = when (p) {
    "INCLUDED_IN_RENT" -> R.string.soc_paid_included
    "TENANT" -> R.string.soc_tenant_pays
    else -> R.string.soc_owner_pays
}

private fun gateLabel(m: String) = when (m) {
    "MYGATE" -> R.string.soc_gate_mygate
    "NOBROKERHOOD" -> R.string.soc_gate_nobrokerhood
    "ADDA" -> R.string.soc_gate_adda
    "APNACOMPLEX" -> R.string.soc_gate_apnacomplex
    "ACCESS_CARD" -> R.string.soc_gate_card
    "BIOMETRIC" -> R.string.soc_gate_biometric
    "GUARD_REGISTER" -> R.string.soc_gate_register
    else -> R.string.soc_gate_other
}

private fun vehicleLabel(v: String) = when (v) {
    "BIKE" -> R.string.soc_vehicle_bike
    "BOTH" -> R.string.soc_vehicle_both
    else -> R.string.soc_vehicle_car
}

private fun approvalLabel(s: String) = when (s) {
    "APPROVED" -> R.string.soc_appr_approved
    "REJECTED" -> R.string.soc_appr_rejected
    "NOT_REQUIRED" -> R.string.soc_appr_not_required
    else -> R.string.soc_appr_pending
}

@Composable
private fun problemText(p: SocietyRules.Problem) = stringResource(
    when (p) {
        SocietyRules.Problem.TOO_LONG -> R.string.soc_err_too_long
        SocietyRules.Problem.NAME_REQUIRED -> R.string.soc_err_contact_name
        SocietyRules.Problem.PHONE -> R.string.safety_err_phone
        SocietyRules.Problem.EMAIL -> R.string.soc_err_email
        SocietyRules.Problem.AMOUNT -> R.string.comp_err_amount
        SocietyRules.Problem.FREQUENCY -> R.string.soc_err_frequency
        SocietyRules.Problem.PAY_TO -> R.string.soc_err_pay_to
        SocietyRules.Problem.RULE_TITLE -> R.string.soc_err_rule_title
        SocietyRules.Problem.AREA_NAME -> R.string.soc_err_area_name
        SocietyRules.Problem.GATE_NAME -> R.string.soc_err_gate_name
        SocietyRules.Problem.SLOT_NUMBER -> R.string.soc_err_slot
        SocietyRules.Problem.DATE_REQUIRED -> R.string.soc_err_date
        SocietyRules.Problem.NOTE_REQUIRED -> R.string.soc_err_note
        SocietyRules.Problem.NOTICE_DAYS -> R.string.soc_err_notice
    },
)
