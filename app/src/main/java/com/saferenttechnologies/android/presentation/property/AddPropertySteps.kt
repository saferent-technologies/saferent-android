package com.saferenttechnologies.android.presentation.property

import com.saferenttechnologies.android.domain.model.FieldError
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UnitAvailability
import com.saferenttechnologies.android.domain.model.FormVisibility
import com.saferenttechnologies.android.domain.model.isFullyOccupied
import com.saferenttechnologies.android.domain.model.isOccupied
import com.saferenttechnologies.android.domain.model.ApartmentRules
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.domain.model.BuildingLimits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.Charge
import com.saferenttechnologies.android.domain.model.ChargeError
import com.saferenttechnologies.android.domain.model.ChargeRules
import com.saferenttechnologies.android.domain.model.PropertyForms
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.permission.LocalPermissionManager
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

// ---------------- shared bits ----------------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = modifier)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = AuthColors.Body,
        fontFamily = Inter,
        fontSize = 13.ssp,
        lineHeight = 19.ssp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Cream)
            .border(1.sdp, AuthColors.CreamBorder, RoundedCornerShape(12.sdp))
            .padding(12.sdp),
    )
}

/** Small selectable chip. */
@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) AuthColors.OrangeText else AuthColors.Ink,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.ssp,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AuthColors.OtpFilled else AuthColors.Field)
            .border(1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 9.sdp),
    )
}

/**
 * Amenities: 2 per column, scrolls sideways. About 2.3 columns fit, so the 3rd one peeks in
 * and it's clear there is more to scroll. Every chip fills its column (same width).
 */
@Composable
fun AmenityRail(items: List<String>, content: @Composable (item: String, modifier: Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.sdp
        val columnWidth = (maxWidth - gap * 2) / 2.3f
        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
            items(items.chunked(2)) { pair ->
                Column(Modifier.width(columnWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEach { content(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/** Chips that wrap to the next line only when the row is full (no gaps). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(items: List<String>, content: @Composable (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        items.forEach { content(it) }
    }
}

/** Fixed [columns] per row, every chip the same width (fills the row). */
@Composable
fun ChipGrid(items: List<String>, columns: Int = 2, content: @Composable (item: String, modifier: Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                row.forEach { content(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Chips wrapped 3 per row (no FlowRow dependency on experimental APIs). */
@Composable
fun ChipRows(items: List<String>, perRow: Int = 3, content: @Composable (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) { row.forEach { content(it) } }
        }
    }
}

@Composable
fun OutlineAction(text: String, icon: Int? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.sdp)
    Row(
        modifier
            .heightIn(min = 48.sdp)
            .clip(shape)
            // Disabled = grey border / muted text, not clickable (no faded orange).
            .border(1.5.sdp, if (enabled) AuthColors.Orange else AuthColors.Border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(painterResource(it), null, tint = if (enabled) AuthColors.OrangeText else AuthColors.Muted, modifier = Modifier.size(18.sdp))
            Spacer(Modifier.width(8.sdp))
        }
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
    }
}

@Composable
private fun ListRow(title: String, subtitle: String?, onClick: () -> Unit, locked: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(if (locked) AuthColors.Surface else AuthColors.Field)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
            .clickable(enabled = !locked, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.sdp)) {
                    Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.Muted, modifier = Modifier.size(12.sdp))
                    Spacer(Modifier.width(4.sdp))
                    Text(stringResource(R.string.add_property_occupied_locked), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                }
            }
        }
        if (locked) Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.Muted, modifier = Modifier.size(18.sdp)) else trailing()
    }
}

@Composable
private fun IconButtonSmall(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.sdp).clip(RoundedCornerShape(10.sdp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) }
}

// ---------------- 1. Category & type ----------------

@Composable
fun CategoryStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val types = state.types ?: return
    if (state.typeLocked) {
        Hint(stringResource(R.string.add_property_type_locked))
        Spacer(Modifier.height(14.sdp))
    }
    types.categories.forEach { category ->
        SectionTitle(category.label)
        Spacer(Modifier.height(10.sdp))
        Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            category.types.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                    row.forEach { type ->
                        val selected = type.key == (state.selectedTypeKey ?: state.property?.propertyType)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.sdp))
                                .background(if (selected) AuthColors.OtpFilled else AuthColors.Field)
                                .border(if (selected) 2.sdp else 1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
                                .selectable(selected = selected, enabled = !state.typeLocked, role = Role.RadioButton) {
                                    onIntent(AddPropertyIntent.SelectType(type.key))
                                }
                                .padding(14.sdp),
                        ) {
                            Icon(
                                painterResource(if (type.structure.usesRooms) R.drawable.ic_home_outline else R.drawable.ic_building),
                                null,
                                tint = AuthColors.OrangeText,
                                modifier = Modifier.size(24.sdp),
                            )
                            Spacer(Modifier.height(8.sdp))
                            Text(type.label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(18.sdp))
    }
    val listing = state.type?.listingTypes?.ifEmpty { null } ?: types.enum("listingTypes", listOf("RENT", "LEASE", "SALE"))
    if (state.selectedTypeKey != null || state.property != null) {
        SectionTitle(stringResource(R.string.add_property_listing_type))
        Spacer(Modifier.height(10.sdp))
        ChipRows(listing) { option ->
            Chip(optionLabel(option), selected = option == state.listingType) { onIntent(AddPropertyIntent.SelectListingType(option)) }
        }
    }
}

// ---------------- 2. Basic info ----------------

@Composable
fun BasicStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    DynamicFieldForm(
        fields = PropertyForms.basic(),
        values = state.basic,
        errors = state.fieldErrors,
        onChange = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.BASIC, k, v)) },
    )
    val length = state.basic["description"].orEmpty().trim().length
    if (length < 30) {
        Spacer(Modifier.height(8.sdp))
        Text(stringResource(R.string.add_property_description_hint, length), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
    }
}

// ---------------- 3. Location ----------------

@Composable
fun LocationStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val location = rememberPermissionRequester(AppPermission.LOCATION)
    val manager = LocalPermissionManager.current
    val current by rememberUpdatedState(state)
    // null = not decided yet, false = the landlord said "Not now" / denied.
    var permissionOk by remember { mutableStateOf<Boolean?>(null) }

    fun fetchIfNeeded() {
        if (current.latitude == null && !current.locating) onIntent(AddPropertyIntent.UseMyLocation)
    }

    // No button: on this step we ask for the permission (popup first) and fetch the location ourselves.
    LaunchedEffect(Unit) {
        if (state.latitude == null) {
            location.request { granted ->
                permissionOk = granted
                if (granted) fetchIfNeeded()
            }
        }
    }
    // Back from Settings (permanently denied → "Open settings") with the permission on → fetch.
    LifecycleResumeEffect(Unit) {
        if (manager?.isGranted(AppPermission.LOCATION) == true) {
            permissionOk = true
            fetchIfNeeded()
        }
        onPauseOrDispose { }
    }

    Hint(stringResource(R.string.add_property_location_hint))
    Spacer(Modifier.height(14.sdp))

    when {
        state.locating -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.5.sdp, modifier = Modifier.size(22.sdp))
            Spacer(Modifier.width(10.sdp))
            Text(stringResource(R.string.add_property_locating), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        }
        permissionOk == false && state.latitude == null -> {
            Text(stringResource(R.string.add_property_location_needed), color = AuthColors.Error, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(10.sdp))
            // Asks again: popup → system dialog, or "Open settings" when it was denied for good.
            OutlineAction(stringResource(R.string.add_property_allow_location), R.drawable.ic_location, modifier = Modifier.fillMaxWidth()) {
                location.request { granted ->
                    permissionOk = granted
                    if (granted) fetchIfNeeded()
                }
            }
        }
        else -> state.accuracyMeters?.let { acc ->
            Text(stringResource(R.string.add_property_pin_saved, acc.toInt()), color = AuthColors.Green, fontFamily = Inter, fontSize = 13.ssp)
        }
    }
    state.locationNote?.takeIf { !state.locating }?.let { note ->
        Spacer(Modifier.height(8.sdp))
        Text(
            stringResource(
                when (note) {
                    LocationNote.LOW_ACCURACY -> R.string.add_property_low_accuracy
                    LocationNote.NO_FIX -> R.string.add_property_no_fix
                    LocationNote.LOOKUP_UNAVAILABLE -> R.string.add_property_lookup_unavailable
                },
            ),
            color = AuthColors.Error,
            fontFamily = Inter,
            fontSize = 13.ssp,
        )
        if (note != LocationNote.LOOKUP_UNAVAILABLE) {
            Spacer(Modifier.height(8.sdp))
            OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) {
                location.request { granted -> if (granted) onIntent(AddPropertyIntent.UseMyLocation) }
            }
        }
    }
    Spacer(Modifier.height(16.sdp))
    // House / street is typed by the landlord; locality, city, state and PIN come from the GPS fix
    // (device Geocoder) and can't be edited — Retry above fetches them again. Only when this phone
    // has no Geocoder at all can they be typed (otherwise the property could never be saved).
    val lockFromLocation = state.locationNote != LocationNote.LOOKUP_UNAVAILABLE
    val (editable, fromLocation) = PropertyForms.address().partition { lockFromLocation && it.key !in PropertyForms.LOCATION_ADDRESS_KEYS || !lockFromLocation }
    DynamicFieldForm(
        fields = editable,
        values = state.address,
        errors = state.fieldErrors,
        onChange = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.ADDRESS, k, if (k == "pincode") v.filter(Char::isDigit) else v)) },
    )
    if (fromLocation.isNotEmpty()) Spacer(Modifier.height(14.sdp))
    DynamicFieldForm(
        fields = fromLocation,
        values = state.address,
        errors = state.fieldErrors,
        onChange = { _, _ -> },
        enabled = false,
    )
}

// ---------------- 4. Building details ----------------

@Composable
fun BuildingStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val fields = state.type?.buildingFields.orEmpty()
    if (fields.isEmpty()) {
        Hint(stringResource(R.string.add_property_no_building_fields))
        return
    }
    DynamicFieldForm(
        fields = fields,
        values = state.building,
        errors = state.fieldErrors,
        onChange = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.BUILDING, k, v)) },
    )
}

// ---------------- 5. Units / rooms & beds ----------------

@Composable
fun UnitsStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val property = state.property ?: return
    // "Flat" / "Portion" / "Shop" / "Villa" (server unitLabel first).
    val unitLabel = state.unitWord

    when {
        state.floorsUnitId != null -> FloorsEditor(state, onIntent)
        state.unitEditor != null -> UnitEditor(state, unitLabel, onIntent)
        state.flatPanel != null -> FlatPanelSection(state, if (state.flatPanel.roomId != null) state.unitWord else unitLabel, onIntent)
        state.roomEditor != null -> RoomEditor(state, onIntent)
        state.structure.usesRooms -> {
            // PG / hostel: each room has its own rent per bed (flagged when missing) + amenities / photos.
            val noRent = ApartmentRules.roomsWithoutPrice(property.rooms).map { it.id }.toSet()
            property.rooms.forEach { room ->
                ListRow(
                    title = stringResource(R.string.add_property_room_n, room.roomNumber),
                    subtitle = listOfNotNull(
                        stringResource(R.string.add_property_beds_n, room.beds.size),
                        room.values["sharingType"]?.ifBlank { null }?.let { optionLabel(it) },
                        if (room.id in noRent) stringResource(R.string.apartment_rent_missing)
                        else stringResource(R.string.units_per_bed, com.saferenttechnologies.android.domain.model.Money.inr(room.values["rentPerBed"])),
                    ).joinToString(" · "),
                    onClick = { onIntent(AddPropertyIntent.EditRoom(room.id)) },
                    locked = room.isFullyOccupied,
                ) {
                    IconButtonSmall(R.drawable.ic_camera, stringResource(R.string.apartment_flat_amenities_photos)) { onIntent(AddPropertyIntent.OpenRoomPanel(room.id)) }
                    IconButtonSmall(R.drawable.ic_add, stringResource(R.string.add_property_add_bed)) { onIntent(AddPropertyIntent.StartBed(room.id)) }
                }
                if (state.bedDraft?.first == room.id) BedEditor(state, onIntent)
                Spacer(Modifier.height(8.sdp))
            }
            OutlineAction(stringResource(R.string.add_property_add_room), R.drawable.ic_add, modifier = Modifier.fillMaxWidth()) {
                onIntent(AddPropertyIntent.NewRoom)
            }
        }
        else -> {
            // Apartment: flats without their own rent are flagged ("Add the rent." on submit).
            val noPrice = if (state.isUnitPriced) ApartmentRules.flatsWithoutPrice(property.units, state.listingType).map { it.id }.toSet() else emptySet()
            property.units.forEach { unit ->
                ListRow(
                    title = "$unitLabel ${unit.unitNumber}".trim(),
                    subtitle = if (state.isUnitPriced) flatSubtitle(unit, state.listingType, unit.id in noPrice) else unit.values["bhkType"],
                    onClick = { onIntent(AddPropertyIntent.EditUnit(unit.id)) },
                    locked = unit.isOccupied,
                ) {
                    if (state.isUnitPriced) {
                        IconButtonSmall(R.drawable.ic_camera, stringResource(R.string.apartment_flat_amenities_photos)) { onIntent(AddPropertyIntent.OpenFlatPanel(unit.id)) }
                    }
                    if (state.type?.floorWise == true) {
                        IconButtonSmall(R.drawable.ic_building, stringResource(R.string.add_property_floors)) { onIntent(AddPropertyIntent.EditFloors(unit.id)) }
                    }
                    if (!state.structure.singleUnit) {
                        IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.DeleteUnit(unit.id)) }
                    }
                }
                Spacer(Modifier.height(8.sdp))
            }
            // SINGLE_UNIT / VILLA: just one unit, no list.
            if (!state.structure.singleUnit || property.units.isEmpty()) {
                val max = BuildingLimits.maxUnits(state.building)
                val canAdd = BuildingLimits.canAddUnit(state.building, property.units.size)
                if (max != null) {
                    Text(
                        stringResource(R.string.add_property_units_of, property.units.size, max),
                        color = if (canAdd) AuthColors.Body else AuthColors.OrangeText,
                        fontFamily = Inter,
                        fontSize = 13.ssp,
                    )
                    Spacer(Modifier.height(6.sdp))
                }
                // All flats from Building details added → greyed out (not clickable).
                OutlineAction(stringResource(R.string.add_property_add_unit, unitLabel), R.drawable.ic_add, enabled = canAdd, modifier = Modifier.fillMaxWidth()) {
                    onIntent(AddPropertyIntent.NewUnit)
                }
            }
        }
    }
}

@Composable
private fun UnitEditor(state: AddPropertyState, unitLabel: String, onIntent: (AddPropertyIntent) -> Unit) {
    val editor = state.unitEditor ?: return
    // "Availability" isn't asked: "Available from" + "Not ready to rent yet" below replace it.
    val fields = UnitAvailability.formFields(state.type?.unitFields?.ifEmpty { null } ?: PropertyForms.defaultUnit())
    SectionTitle(unitLabel)
    Spacer(Modifier.height(12.sdp))
    DynamicFieldForm(fields, editor.values, editor.errors, { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.UNIT, k, v)) })
    Spacer(Modifier.height(12.sdp))
    UnitAvailabilitySection(editor.values, editor.errors, enabled = !state.busy) { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.UNIT, k, v)) }
    if (state.isUnitPriced) {
        // The flat's own price (tenants pay this + the building's common charges).
        Spacer(Modifier.height(18.sdp))
        SectionTitle(stringResource(if (state.listingType == "SALE") R.string.apartment_flat_price else R.string.apartment_flat_rent, unitLabel.lowercase()))
        Spacer(Modifier.height(10.sdp))
        DynamicFieldForm(
            PropertyForms.flatPricing(state.listingType),
            editor.values,
            editor.errors,
            { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.UNIT, k, v)) },
        )
    }
    EditorButtons(state, onSave = { onIntent(AddPropertyIntent.SaveUnit) }, onCancel = { onIntent(AddPropertyIntent.CancelEditor) })
}

@Composable
private fun RoomEditor(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val editor = state.roomEditor ?: return
    SectionTitle(stringResource(R.string.add_property_room))
    Spacer(Modifier.height(4.sdp))
    Text(stringResource(R.string.add_property_beds_auto), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    Spacer(Modifier.height(12.sdp))
    DynamicFieldForm(PropertyForms.room(state.types), editor.values, editor.errors, { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.ROOM, k, v)) })
    EditorButtons(state, onSave = { onIntent(AddPropertyIntent.SaveRoom) }, onCancel = { onIntent(AddPropertyIntent.CancelEditor) })
}

@Composable
private fun BedEditor(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val draft = state.bedDraft ?: return
    Column(Modifier.padding(top = 8.sdp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            OutlinedTextField(
                value = draft.second,
                onValueChange = { onIntent(AddPropertyIntent.BedChanged(it.take(10), draft.third)) },
                label = { Text(stringResource(R.string.add_property_bed_number), fontFamily = Inter) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(),
                shape = RoundedCornerShape(12.sdp),
                colors = propertyFieldColors(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.third,
                onValueChange = { onIntent(AddPropertyIntent.BedChanged(draft.second, it.filter { c -> c.isDigit() || c == '.' })) },
                label = { Text(stringResource(R.string.add_property_bed_rent), fontFamily = Inter) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(),
                shape = RoundedCornerShape(12.sdp),
                colors = propertyFieldColors(),
                modifier = Modifier.weight(1f),
            )
        }
        EditorButtons(state, onSave = { onIntent(AddPropertyIntent.SaveBed) }, onCancel = { onIntent(AddPropertyIntent.CancelEditor) })
    }
}

@Composable
private fun FloorsEditor(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    SectionTitle(stringResource(R.string.add_property_floors))
    Spacer(Modifier.height(12.sdp))
    state.floors.forEachIndexed { index, row ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp))
                .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                .padding(12.sdp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.add_property_floor_n, row.floorNumber), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.RemoveFloor(index)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                SmallNumber(stringResource(R.string.add_property_bedrooms), row.bedrooms, Modifier.weight(1f)) { onIntent(AddPropertyIntent.FloorChanged(index, row.copy(bedrooms = it))) }
                SmallNumber(stringResource(R.string.add_property_bathrooms), row.bathrooms, Modifier.weight(1f)) { onIntent(AddPropertyIntent.FloorChanged(index, row.copy(bathrooms = it))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                SmallNumber(stringResource(R.string.add_property_kitchens), row.kitchens, Modifier.weight(1f)) { onIntent(AddPropertyIntent.FloorChanged(index, row.copy(kitchens = it))) }
                SmallNumber(stringResource(R.string.add_property_living_rooms), row.livingRooms, Modifier.weight(1f)) { onIntent(AddPropertyIntent.FloorChanged(index, row.copy(livingRooms = it))) }
            }
        }
        Spacer(Modifier.height(8.sdp))
    }
    OutlineAction(stringResource(R.string.add_property_add_floor), R.drawable.ic_add, modifier = Modifier.fillMaxWidth()) { onIntent(AddPropertyIntent.AddFloor) }
    EditorButtons(state, onSave = { onIntent(AddPropertyIntent.SaveFloors) }, onCancel = { onIntent(AddPropertyIntent.CancelEditor) })
}

@Composable
private fun SmallNumber(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label, fontFamily = Inter, fontSize = 12.ssp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(),
        shape = RoundedCornerShape(12.sdp),
        colors = propertyFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun EditorButtons(state: AddPropertyState, onSave: () -> Unit, onCancel: () -> Unit) {
    Spacer(Modifier.height(14.sdp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.sdp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.add_property_cancel),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 15.ssp,
            modifier = Modifier
                .weight(0.4f)
                .clip(RoundedCornerShape(12.sdp))
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(16.sdp),
        )
        AuthPrimaryButton(stringResource(R.string.add_property_save), enabled = !state.busy, loading = state.saving, onClick = onSave, modifier = Modifier.weight(0.6f))
    }
}

// ---------------- 6. Rent & charges ----------------

@Composable
fun PricingStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    if (state.isUnitPriced || state.isRoomPriced) {
        Hint(stringResource(R.string.apartment_common_charges_hint, state.unitWord.lowercase()))
        Spacer(Modifier.height(14.sdp))
    }
    DynamicFieldForm(
        // Apartment: no rent / deposit / sale price here — those are on each flat.
        fields = PropertyForms.pricingFor(state.isUnitPriced || state.isRoomPriced, state.listingType, state.types),
        values = state.pricing,
        errors = state.fieldErrors,
        onChange = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.PRICING, k, v)) },
    )
    Spacer(Modifier.height(22.sdp))
    SectionTitle(stringResource(R.string.add_property_charges))
    Spacer(Modifier.height(6.sdp))
    Text(stringResource(R.string.add_property_charges_rule), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, lineHeight = 17.ssp)
    Spacer(Modifier.height(10.sdp))
    state.charges.forEachIndexed { index, charge ->
        ChargeCard(state, index, charge, state.chargeErrors[index], onIntent)
        Spacer(Modifier.height(10.sdp))
    }
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        SelectField(
            label = stringResource(R.string.add_property_charge_kind),
            options = PropertyForms.chargeKinds(state.types),
            value = "",
            error = null,
            enabled = true,
        ) {
            adding = false
            onIntent(AddPropertyIntent.AddCharge(it))
        }
    } else {
        OutlineAction(stringResource(R.string.add_property_add_charge), R.drawable.ic_add, modifier = Modifier.fillMaxWidth()) { adding = true }
    }
}

@Composable
private fun ChargeCard(state: AddPropertyState, index: Int, charge: Charge, error: ChargeError?, onIntent: (AddPropertyIntent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .border(1.sdp, if (error != null) AuthColors.Error else AuthColors.Border, RoundedCornerShape(12.sdp))
            .padding(12.sdp),
        verticalArrangement = Arrangement.spacedBy(10.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(optionLabel(charge.kind), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.RemoveCharge(index)) }
        }
        SelectField(stringResource(R.string.add_property_charge_billing), PropertyForms.chargeBilling(state.types), charge.billing, null, true) { billing ->
            val amount = if (ChargeRules.amountAllowed(billing)) charge.amount else ""
            onIntent(AddPropertyIntent.ChargeChanged(index, charge.copy(billing = billing, amount = amount)))
        }
        if (ChargeRules.amountAllowed(charge.billing)) {
            OutlinedTextField(
                value = charge.amount,
                onValueChange = { onIntent(AddPropertyIntent.ChargeChanged(index, charge.copy(amount = it.filter { c -> c.isDigit() || c == '.' }))) },
                label = {
                    Text(
                        stringResource(R.string.add_property_charge_amount) + if (ChargeRules.needsAmount(charge.billing)) " *" else "",
                        fontFamily = Inter,
                    )
                },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) { { Text(chargeErrorText(error), fontFamily = Inter) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(),
                shape = RoundedCornerShape(12.sdp),
                colors = propertyFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            SelectField(stringResource(R.string.add_property_charge_frequency), PropertyForms.chargeFrequency(state.types), charge.frequency, null, true) {
                onIntent(AddPropertyIntent.ChargeChanged(index, charge.copy(frequency = it)))
            }
        } else if (error != null) {
            Text(chargeErrorText(error), color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)
        }
    }
}

@Composable
private fun chargeErrorText(error: ChargeError): String = stringResource(
    when (error) {
        ChargeError.AMOUNT_REQUIRED -> R.string.add_property_charge_amount_required
        ChargeError.AMOUNT_NOT_ALLOWED -> R.string.add_property_charge_amount_not_allowed
        ChargeError.NOT_A_NUMBER -> R.string.field_error_number
    },
)

// ---------------- 7. Utilities, rules & amenities (optional) ----------------

@Composable
fun OptionalStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    Hint(stringResource(R.string.add_property_optional_hint))
    Spacer(Modifier.height(16.sdp))
    // 1. Amenities
    val buildingWide = state.isUnitPriced || state.isRoomPriced
    SectionTitle(stringResource(if (buildingWide) R.string.apartment_common_amenities else R.string.add_property_amenities))
    if (buildingWide) {
        Spacer(Modifier.height(4.sdp))
        Text(stringResource(R.string.apartment_common_amenities_hint, state.unitWord.lowercase()), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
    Spacer(Modifier.height(10.sdp))
    state.amenityGroups.forEach { group ->
        if (group.label.isNotBlank()) {
            Text(group.label, color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
            Spacer(Modifier.height(6.sdp))
        }
        AmenityRail(group.items.map { it.key }) { key, mod ->
            val item = group.items.first { it.key == key }
            Chip(item.label, selected = key in state.amenities, modifier = mod, maxLines = 1) { onIntent(AddPropertyIntent.ToggleAmenity(key)) }
        }
        Spacer(Modifier.height(12.sdp))
    }
    state.customAmenities.forEachIndexed { index, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
            IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.RemoveCustomAmenity(index)) }
        }
    }
    var custom by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.take(60) },
            label = { Text(stringResource(R.string.add_property_custom_amenity), fontFamily = Inter) },
            singleLine = true,
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.sdp))
        IconButtonSmall(R.drawable.ic_add, stringResource(R.string.add_property_add)) {
            onIntent(AddPropertyIntent.AddCustomAmenity(custom))
            custom = ""
        }
    }

    // 2. Utilities (grouped; follow-up questions appear only when needed)
    val types = state.types
    val utilityChange: (String, String) -> Unit = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.UTILITIES, k, v)) }
    val policyChange: (String, String) -> Unit = { k, v -> onIntent(AddPropertyIntent.FieldChanged(FormScope.POLICIES, k, v)) }
    Spacer(Modifier.height(24.sdp))
    SectionTitle(stringResource(R.string.add_property_utilities))
    listOf(
        R.string.add_property_util_electricity to PropertyForms.utilElectricity(types),
        R.string.add_property_util_water to PropertyForms.utilWater(types),
        R.string.add_property_util_gas_internet to PropertyForms.utilGasInternet(types),
        R.string.add_property_util_building to PropertyForms.utilBuilding(types),
    ).forEach { (title, fields) ->
        Spacer(Modifier.height(12.sdp))
        GroupCard(stringResource(title)) { DynamicFieldForm(fields, state.utilities, state.fieldErrors, utilityChange) }
    }

    // 3. Guests & house rules
    Spacer(Modifier.height(24.sdp))
    SectionTitle(stringResource(R.string.add_property_rules))
    Spacer(Modifier.height(12.sdp))
    GroupCard(stringResource(R.string.add_property_rules_guests)) { DynamicFieldForm(PropertyForms.policyGuests(types), state.policies, state.fieldErrors, policyChange) }
    Spacer(Modifier.height(12.sdp))
    GroupCard(stringResource(R.string.add_property_rules_house)) { DynamicFieldForm(PropertyForms.policyHouse(types, pg = state.structure.usesRooms), state.policies, state.fieldErrors, policyChange) }
    Spacer(Modifier.height(12.sdp))
    GroupCard(stringResource(R.string.add_property_rules_extra)) {
    state.rules.forEachIndexed { index, (title, description) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { onIntent(AddPropertyIntent.RuleChanged(index, it.take(PropertyForms.RULE_TITLE_MAX), description)) },
                    label = { Text(stringResource(R.string.add_property_rule_title), fontFamily = Inter) },
                    singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldActions(),
                    shape = RoundedCornerShape(12.sdp),
                    colors = propertyFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.sdp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { onIntent(AddPropertyIntent.RuleChanged(index, title, it.take(PropertyForms.RULE_DESCRIPTION_MAX))) },
                    label = { Text(stringResource(R.string.add_property_rule_description), fontFamily = Inter) },
                    shape = RoundedCornerShape(12.sdp),
                    colors = propertyFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.RemoveRule(index)) }
        }
        Spacer(Modifier.height(10.sdp))
    }
        if (state.rules.size < PropertyForms.RULES_MAX) {
            OutlineAction(stringResource(R.string.add_property_add_rule), R.drawable.ic_add, modifier = Modifier.fillMaxWidth()) { onIntent(AddPropertyIntent.AddRule) }
        }
    }

    // Unanswered → tenants see "Not disclosed" (all optional; just a nudge).
    val missing = FormVisibility.unanswered(PropertyForms.utilities(types), state.utilities) + FormVisibility.unanswered(PropertyForms.policies(types, pg = state.structure.usesRooms), state.policies)
    if (missing > 0) {
        Spacer(Modifier.height(14.sdp))
        Text(
            stringResource(R.string.add_property_missing_answers, missing),
            color = com.saferenttechnologies.android.presentation.explore.Amber,
            fontFamily = Inter,
            fontSize = 12.ssp,
        )
    }
}

/** A titled card around one group of questions (Electricity, Water, Guests …). */
@Composable
private fun GroupCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .padding(14.sdp),
    ) {
        Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
        Spacer(Modifier.height(10.sdp))
        content()
    }
}

/** Keyboard "Next": go to the next field; nothing after it → close the keyboard. */
@Composable
fun nextFieldActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions(onNext = { if (!focusManager.moveFocus(FocusDirection.Down)) keyboard?.hide() })
}

// ---------------- apartment: a flat's rent line + amenities / photos ----------------

/** "2BHK · Floor 1 · ₹18,000 / month" or "Rent missing". */
@Composable
private fun flatSubtitle(unit: com.saferenttechnologies.android.domain.model.PropertyUnit, listingType: String, noPrice: Boolean): String {
    val price = if (listingType == "SALE") unit.values["salePrice"] else unit.values["monthlyRent"]
    return listOfNotNull(
        unit.values["bhkType"]?.ifBlank { null },
        unit.values["floorNumber"]?.ifBlank { null }?.let { stringResource(R.string.add_property_floor_n, it) },
        if (noPrice) stringResource(R.string.apartment_rent_missing)
        else com.saferenttechnologies.android.domain.model.Money.inr(price).let { if (listingType == "SALE") it else stringResource(R.string.apartment_per_month, it) },
        unit.values["availabilityLabel"]?.ifBlank { null } ?: unit.values["availabilityStatus"]?.ifBlank { null }?.let { optionLabel(it) },
    ).joinToString(" · ")
}

/**
 * One flat: "Amenities in this flat" (tap cycles Yes / No / Not disclosed; building amenities are
 * marked "(building)") + "Photos of this flat" (its room slots → camera, uploaded with its unitId).
 */
@Composable
private fun FlatPanelSection(state: AddPropertyState, unitLabel: String, onIntent: (AddPropertyIntent) -> Unit) {
    val panel = state.flatPanel ?: return
    // A unit (flat / portion / shop / villa) or a PG room.
    val number = panel.unitId?.let { id -> state.property?.units?.firstOrNull { it.id == id }?.unitNumber }
        ?: panel.roomId?.let { id -> state.property?.rooms?.firstOrNull { it.id == id }?.roomNumber }
        ?: return
    val word = unitLabel.lowercase()
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("$unitLabel $number".trim(), Modifier.weight(1f))
        OutlineAction(stringResource(R.string.apartment_done)) { onIntent(AddPropertyIntent.CloseFlatPanel) }
    }
    Spacer(Modifier.height(14.sdp))

    // Amenities in this flat
    Text(stringResource(R.string.apartment_flat_amenities, word), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
    Text(stringResource(R.string.apartment_flat_amenities_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    Spacer(Modifier.height(10.sdp))
    state.amenityGroups.forEach { group ->
        if (group.label.isNotBlank()) {
            Text(group.label, color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
            Spacer(Modifier.height(6.sdp))
        }
        AmenityRail(group.items.map { it.key }) { key, mod ->
            val item = group.items.first { it.key == key }
            val status = panel.amenities[key]
            val building = key in state.amenities
            val label = item.label + (status?.let { " · ${optionLabel(it)}" } ?: "") + if (building) " " + stringResource(R.string.apartment_building_mark) else ""
            Chip(label, selected = status != null, modifier = mod, maxLines = 1) { onIntent(AddPropertyIntent.CycleFlatAmenity(key)) }
        }
        Spacer(Modifier.height(10.sdp))
    }
    panel.custom.forEachIndexed { i, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• $label", color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
            IconButtonSmall(R.drawable.ic_delete, stringResource(R.string.add_property_delete)) { onIntent(AddPropertyIntent.RemoveFlatCustom(i)) }
        }
    }
    var custom by remember(panel.unitId, panel.roomId) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.take(60) },
            label = { Text(stringResource(R.string.add_property_custom_amenity), fontFamily = Inter) },
            singleLine = true,
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.sdp))
        IconButtonSmall(R.drawable.ic_add, stringResource(R.string.add_property_add)) {
            onIntent(AddPropertyIntent.AddFlatCustom(custom))
            custom = ""
        }
    }
    Spacer(Modifier.height(10.sdp))
    AuthPrimaryButton(
        text = stringResource(if (panel.saved) R.string.apartment_amenities_saved else R.string.apartment_save_amenities),
        enabled = !state.busy && !panel.saved,
        loading = state.saving,
        onClick = { onIntent(AddPropertyIntent.SaveFlatAmenities) },
    )

    // Photos of this flat
    Spacer(Modifier.height(24.sdp))
    Text(stringResource(R.string.apartment_flat_photos, word), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
    Spacer(Modifier.height(8.sdp))
    val needs = state.requiredPhotos.filter { (panel.unitId != null && it.unitId == panel.unitId) || (panel.roomId != null && it.roomId == panel.roomId) }
    val missing = state.missingRequiredPhotos
    if (needs.isEmpty()) Text(stringResource(R.string.apartment_flat_photos_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    needs.forEach { need ->
        val done = need !in missing
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.sdp)
                .clip(RoundedCornerShape(12.sdp))
                .border(1.sdp, if (done) AuthColors.Border else AuthColors.Orange, RoundedCornerShape(12.sdp))
                .clickable(enabled = !done && state.imageCount < state.maxImages, role = Role.Button) {
                    camera.request { granted ->
                        if (granted) {
                            onIntent(AddPropertyIntent.SelectRequiredPhoto(need.unitId, need.slot, need.category, need.roomId))
                            onIntent(AddPropertyIntent.OpenCamera(CameraMode.PHOTO))
                        }
                    }
                }
                .padding(12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_camera),
                null,
                tint = if (done) AuthColors.Green else AuthColors.OrangeText,
                modifier = Modifier.size(20.sdp),
            )
            Spacer(Modifier.width(10.sdp))
            Text(countedPhotoName(need.category, need.slot), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (done) R.string.add_property_photo_done else R.string.add_property_required),
                color = if (done) AuthColors.Green else AuthColors.OrangeText,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
    }
}

/**
 * "Available from" (default + earliest = today) + "Not ready to rent yet" switch → Under maintenance /
 * Reserved / Not available. Occupied flat → read-only "Occupied by … since …" (status comes from the lease).
 */
@Composable
private fun UnitAvailabilitySection(
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    enabled: Boolean,
    onChange: (String, String) -> Unit,
) {
    if (UnitAvailability.isOccupied(values)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
            Text(stringResource(R.string.unit_occupied), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            com.saferenttechnologies.android.presentation.manage.OccupancyLines(values)
        }
        return
    }
    val today = SimpleDate.today()
    val from = values[UnitAvailability.FROM].orEmpty()
    fun errorText(key: String): @Composable () -> String? = {
        when (val e = errors[key]) {
            null -> null
            is FieldError.Server -> when (e.message) {
                UnitAvailability.ERR_DATE -> stringResource(R.string.unit_err_date)
                UnitAvailability.ERR_REASON -> stringResource(R.string.unit_err_reason)
                else -> e.message
            }
            else -> fieldErrorText(e)
        }
    }
    DateField(
        label = stringResource(R.string.unit_available_from_label),
        value = from,
        error = errorText(UnitAvailability.FROM)(),
        enabled = enabled,
        notBefore = today,
        helper = stringResource(R.string.unit_available_from_help),
        display = from.takeIf { it.isNotBlank() }?.let { com.saferenttechnologies.android.presentation.manage.calendarDate(it) },
    ) { onChange(UnitAvailability.FROM, it) }
    Spacer(Modifier.height(8.sdp))
    val notReady = UnitAvailability.notReady(values)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Field)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(UnitAvailability.NOT_READY, if (notReady) "" else "true") }
            .padding(horizontal = 14.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.unit_not_ready), color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = notReady,
            onCheckedChange = { onChange(UnitAvailability.NOT_READY, if (it) "true" else "") },
            enabled = enabled,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
        )
    }
    if (notReady) {
        Spacer(Modifier.height(8.sdp))
        val selected = values[UnitAvailability.STATUS].orEmpty().uppercase()
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            listOf(
                "UNDER_MAINTENANCE" to R.string.unit_st_maintenance,
                "RESERVED" to R.string.unit_st_reserved,
                "NOT_AVAILABLE" to R.string.unit_st_not_available,
            ).forEach { (key, label) ->
                Chip(stringResource(label), selected = selected == key, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 2) { onChange(UnitAvailability.STATUS, key) }
            }
        }
        errorText(UnitAvailability.STATUS)()?.let {
            Text(it, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
        }
    }
}
