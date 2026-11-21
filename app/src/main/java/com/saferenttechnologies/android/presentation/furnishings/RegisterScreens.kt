package com.saferenttechnologies.android.presentation.furnishings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.Condition
import com.saferenttechnologies.android.domain.model.RegisterItem
import com.saferenttechnologies.android.domain.model.RegisterItemForm
import com.saferenttechnologies.android.domain.model.RegisterRepair
import com.saferenttechnologies.android.domain.model.RegisterRules
import com.saferenttechnologies.android.domain.model.RepairForm
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.WorkingStatus
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.CameraCaptureScreen
import com.saferenttechnologies.android.presentation.property.CameraMode
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

/** Owner / manager: "Furniture & appliances" of one property. */
@Composable
fun RegisterScreen(state: RegisterState, effects: Flow<RegisterEffect>, onIntent: (RegisterIntent) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) { effects.collect { e -> if (e is RegisterEffect.Message) snackbar.showSnackbar(e.text) } }
    if (state.camera) {
        CameraCaptureScreen(mode = CameraMode.PHOTO, onCaptured = { onIntent(RegisterIntent.PhotoTaken(it)) }, onClose = { onIntent(RegisterIntent.Camera(false)) })
        return
    }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        val selected = state.selected
        when {
            state.form != null -> ItemEditor(state, state.form, onIntent)
            selected != null -> ItemDetail(state, selected, onIntent)
            else -> RegisterList(state, onIntent)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp))
    }
    state.repairForm?.let { RepairSheet(state, it, onIntent) }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onIntent(RegisterIntent.AskDelete(false)) },
            title = { Text(stringResource(R.string.reg_delete_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.reg_delete_body), fontFamily = Inter, color = AuthColors.Body) },
            confirmButton = { TextButton(onClick = { onIntent(RegisterIntent.DeleteItem) }) { Text(stringResource(R.string.reg_delete), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { onIntent(RegisterIntent.AskDelete(false)) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
            containerColor = AuthColors.Card,
        )
    }
}

// ---------------- list ----------------

@Composable
private fun RegisterList(state: RegisterState, onIntent: (RegisterIntent) -> Unit) {
    BackHandler { onIntent(RegisterIntent.Close) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.reg_title), state.focus?.label ?: state.title) { onIntent(RegisterIntent.Close) }
        val reg = state.register
        Box(Modifier.weight(1f)) {
            when {
                reg == null && state.error == null -> ListSkeleton(rows = 5, thumbnail = 56.sdp, lines = 2, trailing = com.saferenttechnologies.android.presentation.common.SkeletonTrailing.PILL)
                reg == null -> Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error.orEmpty(), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                    Spacer(Modifier.height(12.sdp))
                    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) { onIntent(RegisterIntent.Refresh) }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
                    // Summary bar
                    Text(
                        listOfNotNull(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.reg_items, reg.summary.total, reg.summary.total),
                            if (reg.summary.needsRepair > 0) stringResource(R.string.reg_needs_repair_n, reg.summary.needsRepair) else null,
                            if (reg.summary.openRepairs > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.reg_open_repairs, reg.summary.openRepairs, reg.summary.openRepairs) else null,
                        ).joinToString(" · "),
                        color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
                    )
                    state.shownPlaces.forEach { place ->
                        val items = reg.items.filter { it.unitId == place.unitId && it.roomId == place.roomId }
                        PlaceGroup(state, place, items, onIntent)
                    }
                    Spacer(Modifier.height(24.sdp))
                }
            }
        }
        if (state.canManage && state.register != null) {
            Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
                AuthPrimaryButton(stringResource(R.string.reg_add), enabled = !state.busy, loading = false, onClick = { onIntent(RegisterIntent.StartAdd()) }, leadingIcon = R.drawable.ic_add, fontSize = 15.ssp)
            }
        }
    }
}

@Composable
private fun PlaceGroup(state: RegisterState, place: RegisterPlace, items: List<RegisterItem>, onIntent: (RegisterIntent) -> Unit) {
    Spacer(Modifier.height(18.sdp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(place.label, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.weight(1f))
        if (place.furnishing.isNotBlank()) Text(com.saferenttechnologies.android.presentation.explore.furnishingLabel(place.furnishing), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
    Spacer(Modifier.height(6.sdp))
    if (items.isEmpty()) {
        // Unfurnished flats usually have nothing; furnished ones are asked to add their items.
        Text(
            stringResource(if (place.key != "b" && !place.furnished) R.string.reg_empty_unfurnished else R.string.reg_empty_place),
            color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp,
        )
        if (state.canManage) {
            Spacer(Modifier.height(6.sdp))
            OutlineAction(stringResource(R.string.reg_add_here), R.drawable.ic_add, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), compact = true) {
                onIntent(RegisterIntent.StartAdd(place))
            }
        }
        return
    }
    RegisterRules.CATEGORIES.forEach { cat ->
        val inCat = items.filter { it.category == cat }.sortedWith(compareBy({ it.displayOrder }, { it.name }))
        if (inCat.isEmpty()) return@forEach
        Text(stringResource(categoryLabel(cat)), color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, modifier = Modifier.padding(top = 8.sdp, bottom = 4.sdp))
        inCat.forEach { ItemRow(it) { onIntent(RegisterIntent.Select(it.id)) } }
    }
}

@Composable
private fun ItemRow(i: RegisterItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).clickable(role = Role.Button, onClick = onClick).padding(10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(i.photos.firstOrNull()?.url, i.category, 56.sdp)
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text("${i.name} × ${i.quantity}", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (i.brandModel.isNotBlank()) Text(i.brandModel, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.sdp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                WorkingChip(i.workingStatus)
                ConditionChip(i.condition)
                if (i.openRepairs > 0) Pill(androidx.compose.ui.res.pluralStringResource(R.plurals.reg_open_repairs, i.openRepairs, i.openRepairs), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
            }
        }
    }
}

// ---------------- item detail ----------------

@Composable
private fun ItemDetail(state: RegisterState, i: RegisterItem, onIntent: (RegisterIntent) -> Unit) {
    BackHandler { onIntent(RegisterIntent.Select(null)) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val place = state.places.firstOrNull { it.unitId == i.unitId && it.roomId == i.roomId }?.label.orEmpty()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(i.name, listOf(place, stringResource(categoryLabel(i.category))).filter { it.isNotBlank() }.joinToString(" · ")) { onIntent(RegisterIntent.Select(null)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
            // Photos
            if (i.photos.isNotEmpty()) {
                val pager = rememberPagerState { i.photos.size }
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface)) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
                        AsyncImage(model = absoluteUrl(i.photos[p].url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable { viewer = p })
                    }
                    Text(
                        "${pager.currentPage + 1}/${i.photos.size}", color = Color.White, fontFamily = Inter, fontSize = 11.ssp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.sdp).clip(RoundedCornerShape(6.sdp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.sdp, vertical = 2.sdp),
                    )
                    if (state.canManage) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.sdp).size(32.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                .clickable(enabled = !state.saving, role = Role.Button) { onIntent(RegisterIntent.DeletePhoto(i.photos[pager.currentPage].id)) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.reg_delete_photo), tint = Color.White, modifier = Modifier.size(16.sdp)) }
                    }
                }
                Spacer(Modifier.height(10.sdp))
            }
            if (state.uploading > 0) Text(stringResource(R.string.insp_uploading, state.uploading), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 12.ssp)
            if (state.canManage) {
                val camera = rememberPermissionRequester(AppPermission.CAMERA)
                OutlineAction(
                    stringResource(R.string.reg_add_photos, i.photos.size, RegisterRules.MAX_PHOTOS), R.drawable.ic_camera,
                    enabled = !state.saving && i.photos.size + state.uploading < RegisterRules.MAX_PHOTOS, modifier = Modifier.fillMaxWidth(), compact = true,
                ) { camera.request { granted -> if (granted) onIntent(RegisterIntent.Camera(true)) } }
            }

            // Fields
            Spacer(Modifier.height(12.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                WorkingChip(i.workingStatus)
                ConditionChip(i.condition)
            }
            Spacer(Modifier.height(8.sdp))
            Field(stringResource(R.string.reg_quantity), i.quantity.toString())
            Field(stringResource(R.string.reg_brand), i.brand)
            Field(stringResource(R.string.reg_model), i.model)
            Field(stringResource(R.string.reg_purchase_date), i.purchaseDate.takeIf { it.isNotBlank() }?.let { prettyDate(it) }.orEmpty())
            Field(stringResource(R.string.reg_serial), i.serialNumber)
            Field(stringResource(R.string.reg_notes), i.notes)

            // Repair history (newest first)
            Spacer(Modifier.height(16.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reg_repairs), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
                if (state.canManage) {
                    Text(
                        stringResource(R.string.reg_add_repair), color = if (state.saving) AuthColors.Muted else AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                        modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !state.saving, role = Role.Button) { onIntent(RegisterIntent.StartRepair) }.padding(horizontal = 8.sdp, vertical = 6.sdp),
                    )
                }
            }
            if (i.repairs.isEmpty()) Text(stringResource(R.string.reg_no_repairs), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            i.repairs.forEach { RepairRow(it, state, onIntent) }
            Spacer(Modifier.height(24.sdp))
        }
        if (state.canManage) {
            Row(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                OutlineAction(stringResource(R.string.reg_delete), R.drawable.ic_delete, enabled = !state.saving, modifier = Modifier.weight(1f), compact = true) { onIntent(RegisterIntent.AskDelete(true)) }
                OutlineAction(stringResource(R.string.reg_edit), enabled = !state.saving, modifier = Modifier.weight(1f), compact = true) { onIntent(RegisterIntent.StartEdit) }
            }
        }
    }
    viewer?.let { start -> PhotoViewer(i.photos.map { absoluteUrl(it.url) }, start) { viewer = null } }
}

@Composable
private fun RepairRow(r: RegisterRepair, state: RegisterState, onIntent: (RegisterIntent) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.sdp)) {
        // Timeline dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.sdp)) {
            Box(Modifier.padding(top = 4.sdp).size(10.sdp).clip(CircleShape).background(if (r.isOpen) Amber else AuthColors.GreenText))
        }
        Spacer(Modifier.width(8.sdp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.repairDate.takeIf { it.isNotBlank() }?.let { prettyDate(it) }.orEmpty(), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.weight(1f))
                RepairStatusChip(r.status)
            }
            Text(r.description, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            val cost = r.cost.takeIf { it.isNotBlank() }?.let { money(it) }
            Text(listOfNotNull(cost, stringResource(paidByLabel(r.paidBy))).joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            if (r.doneBy.isNotBlank()) Text(stringResource(R.string.reg_done_by_value, r.doneBy), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            if (state.canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.sdp), modifier = Modifier.padding(top = 4.sdp)) {
                    if (r.isOpen) LinkText(stringResource(R.string.reg_mark_done), AuthColors.OrangeText, !state.saving) { onIntent(RegisterIntent.MarkRepairDone(r.id)) }
                    LinkText(stringResource(R.string.reg_delete), AuthColors.Error, !state.saving) { onIntent(RegisterIntent.DeleteRepair(r.id)) }
                }
            }
        }
    }
}

// ---------------- add / edit ----------------

@Composable
private fun ItemEditor(state: RegisterState, f: RegisterItemForm, onIntent: (RegisterIntent) -> Unit) {
    BackHandler(enabled = !state.saving) { onIntent(RegisterIntent.CancelForm) }
    fun set(x: RegisterItemForm) = onIntent(RegisterIntent.EditForm(x))
    val edit = !state.saving
    var datePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(stringResource(if (state.editingId == null) R.string.reg_add else R.string.reg_edit_title), state.title) { onIntent(RegisterIntent.CancelForm) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
            // Where
            Label(stringResource(R.string.reg_where))
            PlaceDropdown(state.shownPlaces, state.shownPlaces.firstOrNull { it.unitId == f.unitId && it.roomId == f.roomId }, edit) { p -> set(f.copy(unitId = p.unitId, roomId = p.roomId)) }
            err(state, "unitId")?.let { ErrorLine(it) } ?: err(state, "roomId")?.let { ErrorLine(it) }

            Label(stringResource(R.string.reg_category))
            RegisterRules.CATEGORIES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    row.forEach { c -> Chip(stringResource(categoryLabel(c)), selected = f.category == c, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(f.copy(category = c)) } }
                }
            }

            Input(stringResource(R.string.reg_name), f.name, err(state, "name"), edit, capitalization = KeyboardCapitalization.Words) { set(f.copy(name = it)) }
            val suggestions = state.register?.suggestions?.get(f.category).orEmpty()
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                    suggestions.forEach { s -> SelectPill(s, f.name.equals(s, true), edit) { set(f.copy(name = s)) } }
                }
            }

            Label(stringResource(R.string.reg_quantity))
            Stepper(f.quantity, 1, 500, edit) { set(f.copy(quantity = it)) }
            err(state, "quantity")?.let { ErrorLine(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Input(stringResource(R.string.reg_brand_optional), f.brand, err(state, "brand"), edit, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words) { set(f.copy(brand = it)) }
                Input(stringResource(R.string.reg_model_optional), f.model, err(state, "model"), edit, Modifier.weight(1f), capitalization = KeyboardCapitalization.Characters) { set(f.copy(model = it)) }
            }
            PickerField(stringResource(R.string.reg_purchase_date_optional), f.purchaseDate.takeIf { it.isNotBlank() }?.let { prettyDate(it) }.orEmpty(), err(state, "purchaseDate"), edit,
                onClear = if (f.purchaseDate.isNotBlank()) ({ set(f.copy(purchaseDate = "")) }) else null) { datePicker = true }

            Label(stringResource(R.string.reg_condition))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                Condition.entries.forEach { c -> SelectPill(stringResource(conditionLabel(c)), f.condition == c, edit) { set(f.copy(condition = c)) } }
            }
            Label(stringResource(R.string.reg_working))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                WorkingStatus.entries.forEach { w -> SelectPill(stringResource(workingLabel(w)), f.workingStatus == w, edit) { set(f.copy(workingStatus = w)) } }
            }
            Input(stringResource(R.string.reg_serial_optional), f.serialNumber, err(state, "serialNumber"), edit, capitalization = KeyboardCapitalization.Characters) { set(f.copy(serialNumber = it)) }
            Input(stringResource(R.string.reg_notes_optional), f.notes, err(state, "notes"), edit, singleLine = false) { set(f.copy(notes = it)) }
            Spacer(Modifier.height(16.sdp))
        }
        Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
            AuthPrimaryButton(stringResource(R.string.reg_save), enabled = !state.saving, loading = state.saving, onClick = { onIntent(RegisterIntent.SaveItem) }, fontSize = 15.ssp)
        }
    }
    if (datePicker) PastDatePicker(f.purchaseDate, onDismiss = { datePicker = false }) { d -> datePicker = false; set(f.copy(purchaseDate = d)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepairSheet(state: RegisterState, f: RepairForm, onIntent: (RegisterIntent) -> Unit) {
    fun set(x: RepairForm) = onIntent(RegisterIntent.EditRepair(x))
    @Composable fun e(path: String): String? = state.serverErrors[path] ?: if (state.repairErrors) state.repairProblems[path]?.let { problemText(it) } else null
    var datePicker by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onIntent(RegisterIntent.CancelRepair) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp),
            verticalArrangement = Arrangement.spacedBy(8.sdp),
        ) {
            Text(stringResource(R.string.reg_add_repair), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            PickerField(stringResource(R.string.reg_repair_date), f.repairDate.takeIf { it.isNotBlank() }?.let { prettyDate(it) }.orEmpty(), e("repairDate"), !state.saving) { datePicker = true }
            Input(stringResource(R.string.reg_repair_what), f.description, e("description"), !state.saving, singleLine = false) { set(f.copy(description = it)) }
            Input(stringResource(R.string.reg_repair_cost), f.cost, e("cost"), !state.saving, keyboard = KeyboardType.Decimal) { set(f.copy(cost = it)) }
            Label(stringResource(R.string.insp_who_pays))
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                listOf("OWNER", "TENANT", "SHARED").forEach { p -> SelectPill(stringResource(paidByLabel(p)), f.paidBy == p, !state.saving) { set(f.copy(paidBy = p)) } }
            }
            Input(stringResource(R.string.reg_done_by), f.doneBy, e("doneBy"), !state.saving, capitalization = KeyboardCapitalization.Words) { set(f.copy(doneBy = it)) }
            Label(stringResource(R.string.insp_repair_status))
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                listOf("DONE", "OPEN", "IN_PROGRESS").forEach { s -> SelectPill(stringResource(repairStatusLabel(s)), f.status == s, !state.saving) { set(f.copy(status = s)) } }
            }
            Spacer(Modifier.height(6.sdp))
            AuthPrimaryButton(stringResource(R.string.reg_save_repair), enabled = !state.saving, loading = state.saving, onClick = { onIntent(RegisterIntent.SaveRepair) }, fontSize = 15.ssp)
        }
    }
    if (datePicker) PastDatePicker(f.repairDate, onDismiss = { datePicker = false }) { d -> datePicker = false; set(f.copy(repairDate = d)) }
}

// ---------------- tenant: "What's included" ----------------

/**
 * Tenant: furniture of the chosen flat / room plus building items, by category with icons.
 * Reduced items (no serial, dates, notes or repair costs). Tap → photos. Hidden when empty.
 */
@Composable
fun WhatsIncluded(items: List<RegisterItem>, title: String, onReportProblem: ((RegisterItem) -> Unit)? = null) {
    if (items.isEmpty()) return
    var viewer by remember { mutableStateOf<RegisterItem?>(null) }
    Spacer(Modifier.height(18.sdp))
    Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
    RegisterRules.CATEGORIES.forEach { cat ->
        val inCat = items.filter { it.category == cat }
        if (inCat.isEmpty()) return@forEach
        Text(stringResource(categoryLabel(cat)), color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, modifier = Modifier.padding(top = 10.sdp, bottom = 4.sdp))
        inCat.forEach { i ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                    .clickable(enabled = i.photos.isNotEmpty(), role = Role.Button) { viewer = i }.padding(8.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(i.photos.firstOrNull()?.url, i.category, 44.sdp)
                Spacer(Modifier.width(10.sdp))
                Column(Modifier.weight(1f)) {
                    Text("${i.name} × ${i.quantity}", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                    if (i.brandModel.isNotBlank()) Text(i.brandModel, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                if (i.workingStatus != WorkingStatus.WORKING) WorkingChip(i.workingStatus)
                if (onReportProblem != null) {
                    Spacer(Modifier.width(6.sdp))
                    LinkText(stringResource(R.string.reg_report), AuthColors.OrangeText, true) { onReportProblem(i) }
                }
            }
        }
    }
    viewer?.let { v -> PhotoViewer(v.photos.map { absoluteUrl(it.url) }, 0) { viewer = null } }
}

// ---------------- small parts ----------------

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
private fun Thumb(url: String?, category: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = absoluteUrl(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(painterResource(categoryIcon(category)), null, tint = AuthColors.Muted, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(0.4f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun LinkText(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) = Text(
    text, color = if (enabled) color else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.ssp,
    modifier = Modifier.clip(RoundedCornerShape(6.sdp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 4.sdp, vertical = 4.sdp),
)

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 3.sdp))
}

@Composable
private fun SelectPill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) AuthColors.OrangeText else if (enabled) AuthColors.Body else AuthColors.Muted,
        fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) AuthColors.OrangeSoft else Color.Transparent)
            .border(1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.sdp, vertical = 6.sdp),
    )
}

@Composable
internal fun WorkingChip(w: WorkingStatus) = when (w) {
    WorkingStatus.WORKING -> Pill(stringResource(R.string.reg_w_working), AuthColors.GreenText, AuthColors.GreenBg)
    WorkingStatus.NEEDS_REPAIR -> Pill(stringResource(R.string.reg_w_needs_repair), Amber, Amber.copy(alpha = 0.12f))
    WorkingStatus.NOT_WORKING -> Pill(stringResource(R.string.reg_w_not_working), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
}

@Composable
private fun ConditionChip(c: Condition) {
    val (fg, bg) = when (c) {
        Condition.GOOD -> AuthColors.GreenText to AuthColors.GreenBg
        Condition.FAIR -> Amber to Amber.copy(alpha = 0.12f)
        Condition.POOR -> AuthColors.Error to AuthColors.Error.copy(alpha = 0.1f)
        else -> AuthColors.Body to AuthColors.Surface
    }
    Pill(stringResource(conditionLabel(c)), fg, bg)
}

@Composable
private fun RepairStatusChip(s: String) = when (s) {
    "DONE" -> Pill(stringResource(R.string.insp_rs_done), AuthColors.GreenText, AuthColors.GreenBg)
    "IN_PROGRESS" -> Pill(stringResource(R.string.insp_rs_progress), Amber, Amber.copy(alpha = 0.12f))
    else -> Pill(stringResource(R.string.insp_rs_open), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
}

@Composable
private fun Stepper(value: String, min: Int, max: Int, enabled: Boolean, onChange: (String) -> Unit) {
    val n = value.toIntOrNull() ?: min
    Row(Modifier.clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && n > min) { onChange((n - 1).toString()) }
        Text(n.toString(), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.padding(horizontal = 12.sdp))
        StepButton("+", enabled && n < max) { onChange((n + 1).toString()) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.sdp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp)
    }
}

@Composable
private fun PlaceDropdown(places: List<RegisterPlace>, selected: RegisterPlace?, enabled: Boolean, onPick: (RegisterPlace) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PickerField(stringResource(R.string.reg_where), selected?.label ?: stringResource(R.string.reg_building), null, enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(AuthColors.Card)) {
            places.forEach { p ->
                DropdownMenuItem(text = { Text(p.label, fontFamily = Inter, color = if (p.key == selected?.key) AuthColors.OrangeText else AuthColors.Ink) }, onClick = { open = false; onPick(p) })
            }
        }
    }
}

@Composable
private fun Input(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3, enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickerField(label: String, value: String, error: String?, enabled: Boolean, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, enabled = false,
            label = { Text(label, fontFamily = Inter, maxLines = 1) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
            isError = error != null,
            supportingText = error?.let { e -> { Text(e, fontFamily = Inter, color = AuthColors.Error) } },
            colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.sdp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick))
        if (onClear != null && enabled) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 36.sdp).size(32.sdp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.reg_clear), tint = AuthColors.Body, modifier = Modifier.size(16.sdp)) }
        }
    }
}

/** Date picker that doesn't allow future days. Returns "YYYY-MM-DD". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(current: String, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val today = SimpleDate.today()
    val utc = TimeZone.getTimeZone("UTC")
    fun millis(d: SimpleDate) = Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
    val todayMs = millis(today)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = SimpleDate.parse(current)?.let(::millis) ?: todayMs,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

@Composable
private fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    if (urls.isEmpty()) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, urls.size - 1)) { urls.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = urls[i], contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale == 1f) Offset.Zero else offset + pan } }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.video_close), tint = Color.White, modifier = Modifier.size(20.sdp)) }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1}/${urls.size}", color = Color.White, fontFamily = Inter, fontSize = 14.ssp)
            }
        }
    }
}

@Composable
private fun err(state: RegisterState, path: String): String? =
    state.serverErrors[path] ?: if (state.showErrors) state.problems[path]?.let { problemText(it) } else null

@Composable
private fun problemText(p: RegisterRules.Problem): String = stringResource(
    when (p) {
        RegisterRules.Problem.NAME -> R.string.reg_err_name
        RegisterRules.Problem.QUANTITY -> R.string.reg_err_qty
        RegisterRules.Problem.TOO_LONG -> R.string.reg_err_long
        RegisterRules.Problem.FUTURE_DATE -> R.string.reg_err_future
        RegisterRules.Problem.BAD_DATE -> R.string.reg_err_date
        RegisterRules.Problem.DATE_REQUIRED -> R.string.reg_err_date_required
        RegisterRules.Problem.DESCRIPTION -> R.string.reg_err_description
        RegisterRules.Problem.COST -> R.string.reg_err_cost
    },
)

internal fun categoryLabel(c: String) = when (c) {
    "BEDROOM" -> R.string.reg_cat_bedroom
    "LIVING_ROOM" -> R.string.reg_cat_living
    "KITCHEN" -> R.string.reg_cat_kitchen
    "BATHROOM" -> R.string.reg_cat_bathroom
    "APPLIANCES" -> R.string.reg_cat_appliances
    else -> R.string.reg_cat_other
}

private fun categoryIcon(c: String) = when (c) {
    "BEDROOM", "LIVING_ROOM" -> R.drawable.ic_sofa
    "KITCHEN", "BATHROOM" -> R.drawable.ic_home_outline
    "APPLIANCES" -> R.drawable.ic_sofa
    else -> R.drawable.ic_document
}

private fun conditionLabel(c: Condition) = when (c) {
    Condition.GOOD -> R.string.insp_c_good
    Condition.FAIR -> R.string.insp_c_fair
    Condition.POOR -> R.string.insp_c_poor
    Condition.NOT_APPLICABLE -> R.string.insp_c_na
    Condition.NOT_INSPECTED -> R.string.insp_c_not_inspected
}

internal fun workingLabel(w: WorkingStatus) = when (w) {
    WorkingStatus.WORKING -> R.string.reg_w_working
    WorkingStatus.NEEDS_REPAIR -> R.string.reg_w_needs_repair
    WorkingStatus.NOT_WORKING -> R.string.reg_w_not_working
}

private fun paidByLabel(p: String) = when (p) {
    "TENANT" -> R.string.reg_paid_tenant
    "SHARED" -> R.string.reg_paid_shared
    else -> R.string.reg_paid_owner
}

private fun repairStatusLabel(s: String) = when (s) {
    "IN_PROGRESS" -> R.string.insp_rs_progress
    "DONE" -> R.string.insp_rs_done
    else -> R.string.insp_rs_open
}
