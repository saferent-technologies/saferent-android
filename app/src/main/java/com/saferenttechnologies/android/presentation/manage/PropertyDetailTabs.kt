package com.saferenttechnologies.android.presentation.manage

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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
    // Every tab's "nothing here" line: centred in the tab area.
    Text(
        text,
        color = AuthColors.Muted,
        fontFamily = Inter,
        fontSize = 14.ssp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.sdp),
    )
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
    if (state.canEdit && state.isUnitPriced) {
        // Flats tab: add a flat right here (wizard opens on a new flat), or edit the list.
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            OutlineAction(stringResource(R.string.manage_add_unit, state.unitWord.lowercase()), R.drawable.ic_add, modifier = Modifier.weight(1f)) {
                onIntent(PropertyDetailIntent.AddUnit)
            }
            OutlineAction(stringResource(R.string.add_property_edit), R.drawable.ic_edit, modifier = Modifier.weight(1f)) {
                onIntent(PropertyDetailIntent.Edit(WizardStep.UNITS))
            }
        }
        Spacer(Modifier.height(8.sdp))
    } else if (state.canEdit) {
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
                            // Not approved yet → grey, not clickable (note under the room).
                            OutlineAction(stringResource(R.string.manage_invite_tenant), enabled = state.canInvite) {
                                onIntent(PropertyDetailIntent.StartInvite(null, bed.id, "Room ${room.roomNumber} · ${bed.bedNumber}", bed.rent ?: room.values["rentPerBed"].orEmpty()))
                            }
                        }
                    }
                    if (state.canManage && bed.availabilityStatus.uppercase() != "OCCUPIED") {
                        AvailabilityPicker(bed.availabilityStatus) { onIntent(PropertyDetailIntent.SetBedAvailability(room.id, bed.id, it)) }
                    }
                }
                if (state.canManage && !state.canInvite && room.beds.any { it.availabilityStatus.uppercase() == "AVAILABLE" }) {
                    Spacer(Modifier.height(6.sdp))
                    InviteLockedNote()
                }
            }
        }
    } else {
        if (p.units.isEmpty()) Empty(stringResource(R.string.manage_no_units))
        p.units.forEach { unit ->
            val status = unit.values["availabilityStatus"].orEmpty()
            val rent = unit.values["monthlyRent"].orEmpty()
            // Apartment etc.: each flat card shows its photo and opens the flat sheet (photos, tenant, lease).
            Card(onClick = if (state.isUnitPriced) ({ onIntent(PropertyDetailIntent.OpenUnit(unit.id)) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isUnitPriced) {
                        UnitThumb(unitPhotoIds(p, unit).firstOrNull(), Modifier.size(72.sdp))
                        Spacer(Modifier.width(12.sdp))
                    }
                    Column(Modifier.weight(1f)) {
                        Line(
                            if (state.isUnitPriced) "${state.unitWord} ${unit.unitNumber}".trim()
                            else "${p.propertyTypeLabel.ifBlank { stringResource(R.string.add_property_unit) }} ${unit.unitNumber}".trim(),
                            strong = true,
                        )
                        Line(
                            listOfNotNull(
                                unit.values["floorNumber"]?.let { stringResource(R.string.add_property_floor_n, it) },
                                unit.values["bhkType"],
                                rent.takeIf { it.isNotBlank() }?.let { money(it) },
                            ).joinToString(" · "),
                        )
                    }
                    UnitAvailabilityChip(unit.values)
                }
                if (status.uppercase() == "OCCUPIED") OccupancyLines(unit.values)
                if (state.canManage && status.uppercase() != "OCCUPIED") {
                    Spacer(Modifier.height(8.sdp))
                    AvailabilityPicker(status) { onIntent(PropertyDetailIntent.SetUnitAvailability(unit.id, it)) }
                }
                if (state.canManage && unit.id in invitedUnits) {
                    Spacer(Modifier.height(8.sdp))
                    Line(stringResource(R.string.manage_invite_pending_note))
                } else if (state.canManage && status.uppercase() == "AVAILABLE") {
                    val allowed = state.canInvite && com.saferenttechnologies.android.domain.model.InviteRules.placeAllows(unit.values)
                    Spacer(Modifier.height(8.sdp))
                    OutlineAction(stringResource(R.string.manage_invite_tenant), enabled = allowed, modifier = Modifier.fillMaxWidth()) {
                        onIntent(PropertyDetailIntent.StartInvite(unit.id, null, unit.unitNumber, rent))
                    }
                    if (!allowed) {
                        Spacer(Modifier.height(6.sdp))
                        InviteLockedNote()
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
        Card(onClick = { onIntent(PropertyDetailIntent.OpenLease(lease)) }) {
            if (lease.id in state.closeRequested) {
                StatusPill(stringResource(R.string.lease_close_requested), com.saferenttechnologies.android.presentation.explore.Amber)
                Spacer(Modifier.height(6.sdp))
            }
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

/** Pending orange · accepted green · rejected red · revoked / expired grey. */
@Composable
private fun invitationStatusColor(status: String) = when (status.uppercase()) {
    "PENDING" -> AuthColors.OrangeText
    "ACCEPTED" -> AuthColors.Green
    "REJECTED" -> AuthColors.Error
    else -> AuthColors.Muted
}

@Composable
fun InvitationsTab(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    // Invites start from a flat / bed (Flats tab or the flat sheet), not from here.
    // One list (all statuses, pending first); each card carries its own status.
    if (state.invitations.isEmpty()) Empty(stringResource(R.string.manage_no_invitations))
    state.invitations.forEach { inv ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Line(inv.recipientName.ifBlank { inv.code }, strong = true)
                    Line(listOf(inv.code, inv.target, inv.monthlyRent.takeIf { it.isNotBlank() }?.let { money(it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                    if (inv.expiresAt.isNotBlank()) Line(stringResource(R.string.manage_expires, prettyDate(inv.expiresAt)))
                }
                StatusPill(optionLabel(inv.status), invitationStatusColor(inv.status))
            }
            // Accepted → what the tenant answered (private, owner / managers).
            if (state.canManage && inv.status.uppercase() == "ACCEPTED") {
                Spacer(Modifier.height(10.sdp))
                OutlineAction(stringResource(R.string.inv_tenant_details), modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(PropertyDetailIntent.OpenTenantDetails(inv.id)) }
            }
            if (state.canManage && inv.status.uppercase() == "PENDING") {
                Spacer(Modifier.height(10.sdp))
                // Two equal halves of the card width.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    OutlineAction(stringResource(R.string.manage_new_link), modifier = Modifier.weight(1f)) { onIntent(PropertyDetailIntent.RenewLink(inv.id)) }
                    OutlineAction(stringResource(R.string.manage_revoke), modifier = Modifier.weight(1f)) { onIntent(PropertyDetailIntent.Revoke(inv.id)) }
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
                // 1. Find the tenant on SafeRent (phone or SafeRent ID) → their details come in.
                val found = form.found
                if (found != null) {
                    com.saferenttechnologies.android.presentation.tenantcheck.PersonCard(found)
                    Text(
                        stringResource(R.string.manage_invite_found),
                        color = AuthColors.GreenText,
                        fontFamily = Inter,
                        fontSize = 12.ssp,
                    )
                    Text(
                        stringResource(R.string.manage_invite_change_person),
                        color = AuthColors.OrangeText,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.ssp,
                        modifier = Modifier.clickable { onIntent(PropertyDetailIntent.InviteClearFound) }.padding(vertical = 4.sdp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = form.lookup,
                            onValueChange = { change(form.copy(lookup = com.saferenttechnologies.android.domain.model.PeopleLookup.cleanInput(it), lookupInvalid = false, notOnSafeRent = false)) },
                            // One line label: a wrapped label made this box taller than the others.
                            label = { Text(stringResource(R.string.manage_invite_lookup), fontFamily = Inter, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            singleLine = true,
                            isError = form.lookupInvalid,
                            supportingText = if (form.lookupInvalid) ({ Text(stringResource(R.string.tc_invalid), fontFamily = Inter) }) else null,
                            colors = com.saferenttechnologies.android.presentation.property.propertyFieldColors(),
                            shape = RoundedCornerShape(12.sdp),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.sdp))
                        TextButton(enabled = form.lookup.isNotBlank() && !form.lookupBusy, onClick = { onIntent(PropertyDetailIntent.InviteLookup) }) {
                            Text(
                                stringResource(R.string.manage_invite_find),
                                color = if (form.lookup.isNotBlank() && !form.lookupBusy) AuthColors.OrangeText else AuthColors.Muted,
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (form.notOnSafeRent) Text(stringResource(R.string.manage_invite_not_found), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                Field(stringResource(R.string.manage_invite_name), form.recipientName) { change(form.copy(recipientName = it)) }
                if (found == null || form.recipientPhone.isNotBlank()) {
                    Field(stringResource(R.string.manage_invite_phone), form.recipientPhone, KeyboardType.Phone) { change(form.copy(recipientPhone = it.filter { c -> c.isDigit() || c == '+' }.take(13))) }
                }
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
                // What the tenant answers when accepting.
                AskTheTenant(form.requirements) { change(form.copy(requirements = it)) }
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

/** "Invite tenant" is grey until SafeRent approves the property / flat. */
@Composable
private fun InviteLockedNote() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.Muted, modifier = Modifier.size(14.sdp))
        Spacer(Modifier.width(6.sdp))
        Text(stringResource(R.string.manage_invite_after_approval), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
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
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface)) {
                        AsyncImage(model = absoluteUrl(img.url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
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
internal fun DateInput(
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
                .focusProperties { canFocus = false }
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


// ---------------- Flat sheet (unit-priced buildings) ----------------

/** The flat's own photos (unit.imageIds), else the property photos tagged with this unit. */
internal fun unitPhotoIds(p: com.saferenttechnologies.android.domain.model.Property, unit: com.saferenttechnologies.android.domain.model.PropertyUnit): List<String> =
    unit.imageIds.ifEmpty { p.images.filter { it.unitId == unit.id }.map { it.id } }

@Composable
private fun UnitThumb(imageId: String?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_building), null, tint = AuthColors.Muted, modifier = Modifier.size(26.sdp))
        coverUrl(imageId)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** "Flat 302": photos, details, what's in the flat, and who lives there (tenant + lease). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlatSheet(state: PropertyDetailState, unit: com.saferenttechnologies.android.domain.model.PropertyUnit, onIntent: (PropertyDetailIntent) -> Unit) {
    val p = state.detail?.property ?: return
    val word = state.unitWord
    val status = unit.values["availabilityStatus"].orEmpty()
    val photos = unitPhotoIds(p, unit)
    val lease = state.activeLeases.firstOrNull { it.unitId == unit.id }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { onIntent(PropertyDetailIntent.CloseUnit) },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.sdp), verticalAlignment = Alignment.CenterVertically) {
                Text("$word ${unit.unitNumber}".trim(), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.ssp, modifier = Modifier.weight(1f))
                UnitAvailabilityChip(unit.values)
            }
            Spacer(Modifier.height(12.sdp))

            // Photos: swipeable 4:3 strip.
            if (photos.isEmpty()) {
                Box(
                    Modifier.padding(horizontal = 20.sdp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_camera), null, tint = AuthColors.Muted, modifier = Modifier.size(28.sdp))
                        Spacer(Modifier.height(6.sdp))
                        Text(stringResource(R.string.manage_flat_no_photos, word.lowercase()), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.sdp),
                    horizontalArrangement = Arrangement.spacedBy(10.sdp),
                ) {
                    items(photos.size) { i ->
                        AsyncImage(
                            model = coverUrl(photos[i]),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(if (photos.size == 1) 320.sdp else 260.sdp)
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(16.sdp))
                                .background(AuthColors.Surface),
                        )
                    }
                }
                Spacer(Modifier.height(4.sdp))
                Text(stringResource(R.string.manage_flat_photos_n, photos.size), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(horizontal = 20.sdp))
            }

            Column(Modifier.padding(horizontal = 20.sdp)) {
                // Tenant first: it's what the landlord opens this for.
                Spacer(Modifier.height(16.sdp))
                SheetHeading(stringResource(R.string.manage_flat_tenant))
                when {
                    state.activeLeasesLoading && lease == null -> com.saferenttechnologies.android.presentation.common.ShimmerHost {
                        Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                            com.saferenttechnologies.android.presentation.common.BoneLine(0.5f, 16.sdp)
                            com.saferenttechnologies.android.presentation.common.BoneLine(0.8f)
                            com.saferenttechnologies.android.presentation.common.BoneLine(0.6f)
                        }
                    }
                    lease != null -> TenantBlock(state, lease, onIntent)
                    else -> {
                        Text(stringResource(R.string.manage_flat_no_tenant, word.lowercase()), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                        if (state.canManage && status.uppercase() == "AVAILABLE" && unit.id !in InviteLocks.units(state.pendingInvites)) {
                            val allowed = state.canInvite && com.saferenttechnologies.android.domain.model.InviteRules.placeAllows(unit.values)
                            Spacer(Modifier.height(10.sdp))
                            OutlineAction(stringResource(R.string.manage_invite_tenant), enabled = allowed, modifier = Modifier.fillMaxWidth()) {
                                onIntent(PropertyDetailIntent.CloseUnit)
                                onIntent(PropertyDetailIntent.StartInvite(unit.id, null, unit.unitNumber, unit.values["monthlyRent"].orEmpty()))
                            }
                            if (!allowed) {
                                Spacer(Modifier.height(6.sdp))
                                InviteLockedNote()
                            }
                        }
                    }
                }

                // Details
                val rows = listOfNotNull(
                    unit.values["bhkType"]?.ifBlank { null }?.let { optionLabel(it) to "" },
                    unit.values["floorNumber"]?.ifBlank { null }?.let { stringResource(R.string.add_property_floor_n, it) to "" },
                    unit.values["furnishing"]?.ifBlank { null }?.let { optionLabel(it) to "" },
                    unit.values["monthlyRent"]?.ifBlank { null }?.let { stringResource(R.string.lease_rent) to money(it) },
                    unit.values["securityDeposit"]?.ifBlank { null }?.let { stringResource(R.string.invitation_deposit) to money(it) },
                    unit.values["maintenanceCharges"]?.ifBlank { null }?.takeIf { it.toDoubleOrNull() != 0.0 }?.let { stringResource(R.string.invitation_maintenance) to money(it) },
                )
                if (rows.isNotEmpty()) {
                    Spacer(Modifier.height(18.sdp))
                    SheetHeading(stringResource(R.string.manage_flat_details))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(horizontal = 14.sdp, vertical = 6.sdp)) {
                        rows.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.sdp)) {
                                Text(k, color = if (v.isEmpty()) AuthColors.Ink else AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                                if (v.isNotEmpty()) Text(v, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
                            }
                        }
                    }
                }

                // In this flat (amenities marked YES)
                val inFlat = unit.amenities.filterValues { it.equals("YES", true) }.keys.toList()
                if (inFlat.isNotEmpty()) {
                    Spacer(Modifier.height(18.sdp))
                    SheetHeading(stringResource(R.string.manage_flat_amenities, word.lowercase()))
                    com.saferenttechnologies.android.presentation.property.ChipFlow(inFlat) { key ->
                        Text(
                            key.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            color = AuthColors.Ink,
                            fontFamily = Inter,
                            fontSize = 12.ssp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.Surface).padding(horizontal = 10.sdp, vertical = 6.sdp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
    Spacer(Modifier.height(8.sdp))
}

@Composable
private fun TenantBlock(state: PropertyDetailState, lease: com.saferenttechnologies.android.domain.model.Lease, onIntent: (PropertyDetailIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp)) {
        if (lease.id in state.closeRequested) {
            StatusPill(stringResource(R.string.lease_close_requested), com.saferenttechnologies.android.presentation.explore.Amber)
            Spacer(Modifier.height(6.sdp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Text(
                    lease.tenantName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" },
                    color = AuthColors.OrangeText,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.ssp,
                )
            }
            Spacer(Modifier.width(10.sdp))
            Column(Modifier.weight(1f)) {
                Text(lease.tenantName, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                lease.startDate.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.manage_since, prettyDate(it.take(10))), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
            }
        }
        Spacer(Modifier.height(10.sdp))
        listOfNotNull(
            lease.monthlyRent.takeIf { it.isNotBlank() }?.let { stringResource(R.string.lease_rent) to money(it) },
            lease.securityDeposit.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_deposit) to money(it) },
            lease.rentDueDay?.let { stringResource(R.string.invitation_rent_due) to stringResource(R.string.lease_due_day, it) },
            lease.endDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_lease_ends) to prettyDate(it.take(10)) },
        ).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.sdp)) {
                Text(k, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                Text(v, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
            }
        }
        lease.exitDate?.let {
            Spacer(Modifier.height(6.sdp))
            StatusPill(stringResource(R.string.manage_move_out_requested, prettyDate(it.take(10))), AuthColors.OrangeText)
        }
        Spacer(Modifier.height(12.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            lease.tenantPhone?.let { phone ->
                OutlineAction(stringResource(R.string.account_blocked_channel_call), R.drawable.ic_call, modifier = Modifier.weight(1f)) { onIntent(PropertyDetailIntent.CallTenant(phone)) }
                OutlineAction(stringResource(R.string.account_blocked_channel_whatsapp), R.drawable.ic_chat, modifier = Modifier.weight(1f)) { onIntent(PropertyDetailIntent.WhatsAppTenant(phone)) }
            }
        }
        Spacer(Modifier.height(8.sdp))
        // The lease screen draws under a bottom sheet → close the sheet first.
        OutlineAction(stringResource(R.string.lease_view), R.drawable.ic_document, modifier = Modifier.fillMaxWidth()) {
            onIntent(PropertyDetailIntent.CloseUnit)
            onIntent(PropertyDetailIntent.OpenLease(lease))
        }
    }
}

/** Occupied flat (read-only): "Occupied by Ravi since 01 Jun 2026" + "Moving out on …" / "Lease until …". */
@Composable
fun OccupancyLines(values: Map<String, String>) {
    val name = values["occupancy.tenantName"].orEmpty()
    val since = values["occupancy.since"].orEmpty()
    val movingOut = values["occupancy.movingOutOn"].orEmpty()
    val leaseEnds = values["occupancy.leaseEnds"].orEmpty()
    if (name.isBlank() && movingOut.isBlank() && leaseEnds.isBlank()) return
    Spacer(Modifier.height(8.sdp))
    if (name.isNotBlank()) {
        Text(
            if (since.isNotBlank()) stringResource(R.string.unit_occupied_by_since, name, calendarDate(since)) else stringResource(R.string.unit_occupied_by, name),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 12.ssp,
        )
    }
    when {
        movingOut.isNotBlank() -> Text(stringResource(R.string.unit_moving_out_on, calendarDate(movingOut)), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 12.ssp)
        leaseEnds.isNotBlank() -> Text(stringResource(R.string.unit_lease_until, calendarDate(leaseEnds)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
}
