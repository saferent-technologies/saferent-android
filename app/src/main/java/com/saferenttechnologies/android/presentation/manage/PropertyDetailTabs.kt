package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.domain.model.InviteLocks
import com.saferenttechnologies.android.domain.model.SimpleDate
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PropertyActions
import com.saferenttechnologies.android.domain.model.PropertyForms
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.domain.model.humanize
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.CameraMode
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipGrid
import com.saferenttechnologies.android.presentation.property.ChipRows
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.SectionTitle
import com.saferenttechnologies.android.presentation.property.SelectField
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------------- shared ----------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .padding(14.sdp),
    ) { content() }
}

@Composable
private fun Line(text: String, strong: Boolean = false) {
    Text(
        text,
        color = if (strong) AuthColors.Ink else AuthColors.Body,
        fontFamily = if (strong) PlusJakartaSans else Inter,
        fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Normal,
        fontSize = if (strong) 15.ssp else 13.ssp,
    )
}

@Composable
private fun Empty(text: String) {
    Text(text, color = AuthColors.Muted, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.padding(vertical = 24.sdp))
}

/** "2026-11-14T10:32:05.120Z" / "2026-12-01" → "14 Nov 2026, 4:02 PM" / "1 Dec 2026" (device time zone). */
fun prettyDate(iso: String): String {
    if (iso.isBlank()) return ""
    val withTime = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull { p ->
        runCatching { SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso) }.getOrNull()
    }
    if (withTime != null) return SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(withTime)
    return runCatching { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))!!) }
        .getOrDefault(iso)
}

// ---------------- Overview ----------------

@Composable
fun OverviewTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val p = state.detail?.property ?: return
    if (p.description.isNotBlank()) {
        Card { Line(p.description) }
    }
    if (p.building.isNotEmpty()) {
        Card {
            Line(stringResource(R.string.step_building), strong = true)
            Spacer(Modifier.height(6.sdp))
            p.building.forEach { (k, v) -> Line("${humanize(k)}: ${optionLabel(v)}") }
        }
    }
    if (p.amenities.isNotEmpty()) {
        Card {
            Line(stringResource(R.string.add_property_amenities), strong = true)
            Spacer(Modifier.height(6.sdp))
            Line(p.amenities.filter { it.second.equals("YES", true) || it.second.isBlank() }.joinToString(" · ") { it.first })
        }
    }
    Card {
        Line(stringResource(R.string.add_property_completeness, p.completeness.score), strong = true)
        p.completeness.sections.forEach { s ->
            Spacer(Modifier.height(8.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Line("${s.label} · ${s.score}%")
                    s.missing.take(3).forEach { Text("• $it", color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp) }
                }
                if (state.canEdit) {
                    OutlineAction(stringResource(R.string.add_property_edit)) {
                        onIntent(PropertyDetailIntent.Edit(com.saferenttechnologies.android.domain.model.SubmitProblems.stepFor(s.key + " " + s.label)))
                    }
                }
            }
        }
    }
}

// ---------------- Units / rooms & beds ----------------

@Composable
fun UnitsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val p = state.detail?.property ?: return
    val invitedUnits = InviteLocks.units(state.pendingInvites)
    val invitedBeds = InviteLocks.beds(state.pendingInvites)
    if (state.canEdit) {
        OutlineAction(stringResource(R.string.manage_edit_units), R.drawable.ic_add, modifier = Modifier.fillMaxWidth()) {
            onIntent(PropertyDetailIntent.Edit(WizardStep.UNITS))
        }
        Spacer(Modifier.height(8.sdp))
    }
    if (p.structure.usesRooms) {
        if (p.rooms.isEmpty()) Empty(stringResource(R.string.manage_no_rooms))
        p.rooms.forEach { room ->
            Card {
                Line(stringResource(R.string.add_property_room_n, room.roomNumber), strong = true)
                Line(listOfNotNull(room.values["sharingType"]?.let { optionLabel(it) }, room.values["floorNumber"]?.let { stringResource(R.string.add_property_floor_n, it) }).joinToString(" · "))
                room.beds.forEach { bed ->
                    Spacer(Modifier.height(8.sdp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bed.bedNumber, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.width(48.sdp))
                        AvailabilityChip(bed.availabilityStatus)
                        Spacer(Modifier.weight(1f))
                        if (state.canManage && bed.availabilityStatus.uppercase() == "AVAILABLE" && bed.id !in invitedBeds) {
                            OutlineAction(stringResource(R.string.manage_invite_tenant)) {
                                onIntent(PropertyDetailIntent.StartInvite(null, bed.id, "Room ${room.roomNumber} · ${bed.bedNumber}", bed.rent ?: room.values["rentPerBed"].orEmpty()))
                            }
                        }
                    }
                    if (state.canManage && bed.availabilityStatus.uppercase() != "OCCUPIED") {
                        AvailabilityPicker(bed.availabilityStatus) { onIntent(PropertyDetailIntent.SetBedAvailability(room.id, bed.id, it)) }
                    }
                }
            }
        }
    } else {
        if (p.units.isEmpty()) Empty(stringResource(R.string.manage_no_units))
        p.units.forEach { unit ->
            val status = unit.values["availabilityStatus"].orEmpty()
            val rent = unit.values["monthlyRent"].orEmpty()
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Line("${p.propertyTypeLabel.ifBlank { stringResource(R.string.add_property_unit) }} ${unit.unitNumber}".trim(), strong = true)
                        Line(
                            listOfNotNull(
                                unit.values["floorNumber"]?.let { stringResource(R.string.add_property_floor_n, it) },
                                unit.values["bhkType"],
                                rent.takeIf { it.isNotBlank() }?.let { money(it) },
                            ).joinToString(" · "),
                        )
                    }
                    AvailabilityChip(status)
                }
                if (state.canManage && status.uppercase() != "OCCUPIED") {
                    Spacer(Modifier.height(8.sdp))
                    AvailabilityPicker(status) { onIntent(PropertyDetailIntent.SetUnitAvailability(unit.id, it)) }
                }
                if (state.canManage && unit.id in invitedUnits) {
                    Spacer(Modifier.height(8.sdp))
                    Line(stringResource(R.string.manage_invite_pending_note))
                } else if (state.canManage && status.uppercase() == "AVAILABLE") {
                    Spacer(Modifier.height(8.sdp))
                    OutlineAction(stringResource(R.string.manage_invite_tenant), modifier = Modifier.fillMaxWidth()) {
                        onIntent(PropertyDetailIntent.StartInvite(unit.id, null, unit.unitNumber, rent))
                    }
                }
            }
        }
    }
}

/** Availability by hand (OCCUPIED only comes from a lease). */
@Composable
private fun AvailabilityPicker(current: String, onSelect: (String) -> Unit) {
    SelectField(
        label = stringResource(R.string.manage_availability),
        options = PropertyActions.MANUAL_AVAILABILITY,
        value = current,
        error = null,
        enabled = true,
    ) { if (it != current) onSelect(it) }
}

// ---------------- Tenants ----------------

@Composable
fun TenantsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    // Current / Past: two equal halves of the row.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        Chip(stringResource(R.string.manage_current_tenants), selected = !state.pastTenants, modifier = Modifier.weight(1f)) {
            onIntent(PropertyDetailIntent.ShowPastTenants(false))
        }
        Chip(stringResource(R.string.manage_past_tenants), selected = state.pastTenants, modifier = Modifier.weight(1f)) {
            onIntent(PropertyDetailIntent.ShowPastTenants(true))
        }
    }
    Spacer(Modifier.height(8.sdp))
    if (state.leases.isEmpty()) Empty(stringResource(R.string.manage_no_tenants))
    state.leases.forEach { lease ->
        Card {
            Line(lease.tenantName, strong = true)
            Line(listOf(lease.place, lease.placeCode).filter { it.isNotBlank() }.joinToString(" · "))
            Line(
                listOfNotNull(
                    lease.monthlyRent.takeIf { it.isNotBlank() }?.let { money(it) },
                    lease.rentDueDay?.let { stringResource(R.string.manage_due_day, it) },
                    lease.startDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.manage_since, prettyDate(it)) },
                ).joinToString(" · "),
            )
            lease.exitDate?.let {
                Spacer(Modifier.height(6.sdp))
                StatusPill(stringResource(R.string.manage_move_out_requested, prettyDate(it)), AuthColors.OrangeText)
            }
            lease.tenantPhone?.let { phone ->
                Spacer(Modifier.height(10.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    OutlineAction(stringResource(R.string.account_blocked_channel_call), R.drawable.ic_call) { onIntent(PropertyDetailIntent.CallTenant(phone)) }
                    OutlineAction(stringResource(R.string.account_blocked_channel_whatsapp), R.drawable.ic_chat) { onIntent(PropertyDetailIntent.WhatsAppTenant(phone)) }
                }
            }
        }
    }
}

// ---------------- Invitations ----------------

private val INVITATION_STATUSES = listOf("PENDING", "ACCEPTED", "REJECTED", "REVOKED", "EXPIRED")

@Composable
fun InvitationsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    if (state.canManage) {
        AuthPrimaryButton(
            text = stringResource(R.string.manage_invite_tenant),
            enabled = !state.busy,
            loading = false,
            leadingIcon = R.drawable.ic_add,
            onClick = { onIntent(PropertyDetailIntent.StartInvite(null, null, "", state.detail?.property?.rentFrom.orEmpty())) },
        )
        Spacer(Modifier.height(10.sdp))
    }
    // Status filters fill the width (3 per row, equal widths).
    ChipGrid(INVITATION_STATUSES, columns = 3) { s, mod ->
        Chip(optionLabel(s), selected = s == state.invitationStatus, modifier = mod, maxLines = 1) { onIntent(PropertyDetailIntent.InvitationFilter(s)) }
    }
    Spacer(Modifier.height(8.sdp))
    if (state.invitations.isEmpty()) Empty(stringResource(R.string.manage_no_invitations))
    state.invitations.forEach { inv ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Line(inv.recipientName.ifBlank { inv.code }, strong = true)
                    Line(listOf(inv.code, inv.target, inv.monthlyRent.takeIf { it.isNotBlank() }?.let { money(it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                    if (inv.expiresAt.isNotBlank()) Line(stringResource(R.string.manage_expires, prettyDate(inv.expiresAt)))
                }
                StatusPill(optionLabel(inv.status), AuthColors.Body)
            }
            if (state.canManage && inv.status.uppercase() == "PENDING") {
                Spacer(Modifier.height(10.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    OutlineAction(stringResource(R.string.manage_new_link)) { onIntent(PropertyDetailIntent.RenewLink(inv.id)) }
                    OutlineAction(stringResource(R.string.manage_revoke)) { onIntent(PropertyDetailIntent.Revoke(inv.id)) }
                }
            }
        }
    }
}

@Composable
fun InviteDialog(state: PropertyDetailState, form: InviteForm, onIntent: (PropertyDetailIntent) -> Unit) {
    fun change(f: InviteForm) = onIntent(PropertyDetailIntent.InviteChanged(f))
    AlertDialog(
        onDismissRequest = { onIntent(PropertyDetailIntent.CancelInvite) },
        title = {
            Text(
                if (form.target.isBlank()) stringResource(R.string.manage_invite_tenant) else stringResource(R.string.manage_invite_for, form.target),
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                Field(stringResource(R.string.manage_invite_name), form.recipientName) { change(form.copy(recipientName = it)) }
                Field(stringResource(R.string.manage_invite_phone), form.recipientPhone, KeyboardType.Phone) { change(form.copy(recipientPhone = it.filter { c -> c.isDigit() || c == '+' }.take(13))) }
                Field(stringResource(R.string.manage_invite_email), form.recipientEmail, KeyboardType.Email) { change(form.copy(recipientEmail = it)) }
                Field(stringResource(R.string.prop_field_monthly_rent), form.monthlyRent, KeyboardType.Decimal) { change(form.copy(monthlyRent = it.filter { c -> c.isDigit() || c == '.' })) }
                Field(stringResource(R.string.prop_field_security_deposit), form.securityDeposit, KeyboardType.Decimal) { change(form.copy(securityDeposit = it.filter { c -> c.isDigit() || c == '.' })) }
                Field(stringResource(R.string.manage_invite_maintenance), form.maintenanceCharges, KeyboardType.Decimal) { change(form.copy(maintenanceCharges = it.filter { c -> c.isDigit() || c == '.' })) }
                // Calendar pickers: start from today, end after the start.
                DateInput(stringResource(R.string.manage_invite_start), form.startDate, min = SimpleDate.today()) {
                    val endBefore = SimpleDate.parse(form.endDate)?.let { end -> end <= it }
                    change(form.copy(startDate = it.iso(), endDate = if (endBefore == true) "" else form.endDate))
                }
                DateInput(
                    stringResource(R.string.manage_invite_end),
                    form.endDate,
                    min = SimpleDate.parse(form.startDate)?.plusDays(1) ?: SimpleDate.today(),
                    clearable = true,
                    onClear = { change(form.copy(endDate = "")) },
                ) { change(form.copy(endDate = it.iso())) }
                Field(stringResource(R.string.prop_field_rent_due_day), form.rentDueDay, KeyboardType.Number) { change(form.copy(rentDueDay = it.filter(Char::isDigit).take(2))) }
                Field(stringResource(R.string.prop_field_notice_days), form.noticePeriodDays, KeyboardType.Number) { change(form.copy(noticePeriodDays = it.filter(Char::isDigit).take(3))) }
                Field(stringResource(R.string.prop_field_lock_in_months), form.lockInMonths, KeyboardType.Number) { change(form.copy(lockInMonths = it.filter(Char::isDigit).take(3))) }
                Field(stringResource(R.string.manage_invite_terms), form.additionalTerms) { change(form.copy(additionalTerms = it.take(1000))) }
                form.error?.let {
                    Text(
                        stringResource(
                            when (it) {
                                PropertyDetailViewModel.InviteError.NAME.name -> R.string.manage_invite_error_name
                                PropertyDetailViewModel.InviteError.PHONE.name -> R.string.manage_invite_error_phone
                                PropertyDetailViewModel.InviteError.RENT.name -> R.string.manage_invite_error_rent
                                else -> R.string.manage_invite_error_start
                            },
                        ),
                        color = AuthColors.Error,
                        fontFamily = Inter,
                        fontSize = 13.ssp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { onIntent(PropertyDetailIntent.SendInvite) }) {
                Text(stringResource(R.string.manage_invite_send), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.CancelInvite) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        containerColor = AuthColors.Card,
    )
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter) },
        placeholder = placeholder?.let { hint -> @Composable { Text(hint, fontFamily = Inter) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.sdp),
        colors = propertyFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------- Applications ----------------

@Composable
fun ApplicationsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    if (state.applications.isEmpty()) Empty(stringResource(R.string.manage_no_applications))
    state.applications.forEach { app ->
        Card {
            Line(app.tenantName, strong = true)
            Line(listOf(app.occupation, app.currentCity, app.familySize?.let { stringResource(R.string.manage_family, it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
            Line(
                listOf(
                    listOf(app.unitNumber, app.bedNumber).filter { it.isNotBlank() }.joinToString(" · "),
                    app.proposedMoveIn.takeIf { it.isNotBlank() }?.let { stringResource(R.string.manage_move_in, prettyDate(it)) }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · "),
            )
            if (app.message.isNotBlank()) {
                Spacer(Modifier.height(6.sdp))
                Text("“${app.message}”", color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
            }
            if (state.canManage) {
                Spacer(Modifier.height(10.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    OutlineAction(stringResource(R.string.manage_approve)) { onIntent(PropertyDetailIntent.StartDecision(app, approve = true)) }
                    OutlineAction(stringResource(R.string.manage_reject)) { onIntent(PropertyDetailIntent.StartDecision(app, approve = false)) }
                }
            }
        }
    }
}

@Composable
fun DecisionDialog(state: PropertyDetailState, form: DecisionForm, onIntent: (PropertyDetailIntent) -> Unit) {
    fun change(f: DecisionForm) = onIntent(PropertyDetailIntent.DecisionChanged(f))
    AlertDialog(
        onDismissRequest = { onIntent(PropertyDetailIntent.CancelDecision) },
        title = {
            Text(
                stringResource(if (form.approve) R.string.manage_approve_title else R.string.manage_reject_title, form.application.tenantName),
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                if (form.approve) {
                    DateInput(stringResource(R.string.manage_invite_start), form.startDate, min = SimpleDate.today()) { change(form.copy(startDate = it.iso())) }
                    Field(stringResource(R.string.manage_lease_months), form.months, KeyboardType.Number) { change(form.copy(months = it.filter(Char::isDigit).take(3))) }
                    Field(stringResource(R.string.prop_field_rent_due_day), form.rentDueDay, KeyboardType.Number) { change(form.copy(rentDueDay = it.filter(Char::isDigit).take(2))) }
                }
                Field(stringResource(R.string.manage_note), form.note) { change(form.copy(note = it.take(500))) }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { onIntent(PropertyDetailIntent.SubmitDecision) }) {
                Text(
                    stringResource(if (form.approve) R.string.manage_approve else R.string.manage_reject),
                    color = if (form.approve) AuthColors.Green else AuthColors.Error,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.CancelDecision) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        containerColor = AuthColors.Card,
    )
}

// ---------------- Photos ----------------

@Composable
fun PhotosTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val p = state.detail?.property ?: return
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    if (state.canEdit) {
        ChipRows(PropertyForms.imageCategories(null), perRow = 2) { c ->
            Chip(optionLabel(c), selected = c == state.photoCategory) { onIntent(PropertyDetailIntent.SelectPhotoCategory(c)) }
        }
        Spacer(Modifier.height(10.sdp))
        AuthPrimaryButton(
            text = stringResource(R.string.add_property_take_photo),
            enabled = !state.busy,
            loading = false,
            leadingIcon = R.drawable.ic_camera,
            onClick = { camera.request { ok -> if (ok) onIntent(PropertyDetailIntent.OpenCamera(CameraMode.PHOTO)) } },
        )
        if (p.publicationStatus == "PUBLISHED") {
            Spacer(Modifier.height(6.sdp))
            Line(stringResource(R.string.manage_new_photos_pending))
        }
        Spacer(Modifier.height(10.sdp))
    }
    if (p.images.isEmpty()) Empty(stringResource(R.string.manage_no_photos))
    p.images.sortedBy { it.displayOrder ?: Int.MAX_VALUE }.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(vertical = 4.sdp)) {
            row.forEach { img ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface)) {
                        AsyncImage(model = absoluteUrl(img.url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        StatusPill(optionLabel(img.status.ifBlank { "PENDING" }), photoStatusColor(img.status), Modifier.align(Alignment.TopStart).padding(6.sdp).background(AuthColors.Card, RoundedCornerShape(50)))
                    }
                    Text(optionLabel(img.category) + (img.slot?.let { " $it" } ?: ""), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    if (state.canEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                            OutlineAction(stringResource(R.string.manage_cover)) { onIntent(PropertyDetailIntent.SetCover(img.id)) }
                            OutlineAction(stringResource(R.string.add_property_delete)) { onIntent(PropertyDetailIntent.DeleteImage(img.id)) }
                        }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun photoStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status.uppercase()) {
    "APPROVED" -> AuthColors.Green
    "REJECTED" -> AuthColors.Error
    else -> androidx.compose.ui.graphics.Color(0xFF2563EB)
}

// ---------------- Documents ----------------

@Composable
fun DocumentsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    if (state.canEdit) {
        // 2 per row, each half the width (same as the wizard).
        ChipGrid(PropertyForms.documentTypes(null), columns = 2) { t, mod ->
            Chip(optionLabel(t), selected = t == state.docType, modifier = mod) { onIntent(PropertyDetailIntent.SelectDocType(t)) }
        }
        Spacer(Modifier.height(10.sdp))
        AuthPrimaryButton(
            text = stringResource(R.string.add_property_scan_document),
            enabled = !state.busy,
            loading = false,
            leadingIcon = R.drawable.ic_document,
            onClick = { camera.request { ok -> if (ok) onIntent(PropertyDetailIntent.OpenCamera(CameraMode.DOCUMENT)) } },
        )
        Spacer(Modifier.height(10.sdp))
    }
    if (state.documents.isEmpty()) Empty(stringResource(R.string.manage_no_documents))
    state.documents.forEach { doc ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Line(optionLabel(doc.docType), strong = true)
                    Line(doc.code)
                }
                StatusPill(optionLabel(doc.status.ifBlank { "PENDING" }), photoStatusColor(doc.status))
            }
            if (state.canEdit) {
                Spacer(Modifier.height(8.sdp))
                OutlineAction(stringResource(R.string.add_property_retake)) {
                    camera.request { ok -> if (ok) onIntent(PropertyDetailIntent.OpenCamera(CameraMode.DOCUMENT, replaceDocId = doc.id)) }
                }
            }
        }
    }
}

// ---------------- History ----------------

@Composable
fun HistoryTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    if (state.history.isEmpty()) Empty(stringResource(R.string.manage_no_history))
    state.history.forEach { h ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.sdp)) {
            Box(Modifier.padding(top = 4.sdp).size(10.sdp).clip(CircleShape).background(AuthColors.Orange))
            Spacer(Modifier.width(12.sdp))
            Column {
                val what = when {
                    h.field.equals("publicationStatus", true) || h.field.equals("activeStatus", true) ->
                        stringResource(R.string.manage_history_status, optionLabel(h.to), h.actorName.ifBlank { "SafeRent" })
                    else -> "${humanize(h.entityType)} ${humanize(h.field)} → ${optionLabel(h.to)}"
                }
                Text(what, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
                if (h.reason.isNotBlank()) Text(h.reason, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                Text(prettyDate(h.createdAt), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
    }
    if (state.historyPage < state.historyTotalPages) {
        OutlineAction(stringResource(R.string.manage_load_more), modifier = Modifier.fillMaxWidth()) { onIntent(PropertyDetailIntent.LoadMoreHistory) }
    }
}

// ---------------- date picker field ----------------

/**
 * Read-only field that opens the Material calendar. Days before [min] can't be picked.
 * Shows "1 Dec 2026"; the form keeps "YYYY-MM-DD".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInput(
    label: String,
    value: String,
    min: SimpleDate,
    clearable: Boolean = false,
    onClear: () -> Unit = {},
    onPick: (SimpleDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box {
        OutlinedTextField(
            value = if (value.isBlank()) "" else prettyDate(value),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label, fontFamily = Inter) },
            placeholder = { Text(stringResource(R.string.listing_pick_date), fontFamily = Inter) },
            trailingIcon = if (clearable && value.isNotBlank()) {
                @Composable {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        stringResource(R.string.explore_clear),
                        tint = AuthColors.Body,
                        modifier = Modifier.size(18.sdp).clickable(onClick = onClear),
                    )
                }
            } else {
                @Composable { Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) }
            },
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // Disabled fields don't take taps → overlay opens the calendar (keyboard closed first).
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (clearable && value.isNotBlank()) 40.sdp else 0.sdp)
                .clip(RoundedCornerShape(12.sdp))
                .clickable(role = Role.Button) {
                    focusManager.clearFocus()
                    open = true
                },
        )
    }
    if (open) {
        val minUtc = remember(min) { utcMillis(min) }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = SimpleDate.parse(value)?.let(::utcMillis)?.takeIf { it >= minUtc } ?: minUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onPick(SimpleDate.fromMillis(it)) }
                    open = false
                }) { Text(stringResource(android.R.string.ok), color = AuthColors.OrangeText) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        ) { DatePicker(state = picker) }
    }
}

private fun utcMillis(d: SimpleDate): Long = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
    clear()
    set(d.year, d.month - 1, d.day)
}.timeInMillis
