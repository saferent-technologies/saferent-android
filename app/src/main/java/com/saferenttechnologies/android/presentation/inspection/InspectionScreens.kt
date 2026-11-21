package com.saferenttechnologies.android.presentation.inspection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import com.saferenttechnologies.android.domain.model.Inspection
import com.saferenttechnologies.android.domain.model.InspectionForm
import com.saferenttechnologies.android.domain.model.InspectionRules
import com.saferenttechnologies.android.domain.model.InspectionType
import com.saferenttechnologies.android.domain.model.InventoryItem
import com.saferenttechnologies.android.domain.model.IsoTime
import com.saferenttechnologies.android.domain.model.RepairItem
import com.saferenttechnologies.android.domain.model.ScheduleForm
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.money
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

/** Inspections of one property: list → report → record. Owner / manager / inspector only. */
@Composable
fun InspectionsScreen(
    state: InspectionsState,
    effects: Flow<InspectionsEffect>,
    onIntent: (InspectionsIntent) -> Unit,
    /** Completed move-out → "Move-in vs move-out" for its lease. */
    onCompare: ((leaseId: String) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val registerEmpty = stringResource(R.string.insp_register_empty)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { e ->
            if (e is InspectionsEffect.Message) snackbar.showSnackbar(
                when {
                    e.text == InspectionsViewModel.REGISTER_EMPTY -> registerEmpty
                    e.text.startsWith("@loaded:") -> e.text.removePrefix("@loaded:").toIntOrNull()?.let { n ->
                        context.resources.getQuantityString(R.plurals.insp_register_loaded, n, n)
                    } ?: ""
                    else -> e.text
                },
            )
        }
    }

    // Area chosen → the app camera (photo, watermarked, ≤ 2000 px).
    state.cameraArea?.let {
        CameraCaptureScreen(mode = CameraMode.PHOTO, onCaptured = { p -> onIntent(InspectionsIntent.PhotoTaken(p)) }, onClose = { onIntent(InspectionsIntent.CloseCamera) })
        return
    }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        val selected = state.selected
        when {
            state.editing && selected != null -> RecordScreen(state, selected, onIntent)
            selected != null -> ReportScreen(state, selected, onIntent, onCompare)
            else -> ListScreen(state, onIntent)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp))
    }
    state.schedule?.let { ScheduleSheet(state, it, onIntent) }
    if (state.pickArea) AreaSheet(onIntent)
    if (state.confirmAcknowledge) {
        AlertDialog(
            onDismissRequest = { onIntent(InspectionsIntent.AskAcknowledge(false)) },
            title = { Text(stringResource(R.string.insp_ack_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.insp_ack_body), fontFamily = Inter, color = AuthColors.Body) },
            confirmButton = { TextButton(onClick = { onIntent(InspectionsIntent.Acknowledge) }) { Text(stringResource(R.string.insp_ack_confirm), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { onIntent(InspectionsIntent.AskAcknowledge(false)) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
            containerColor = AuthColors.Card,
        )
    }
}

// ---------------- list ----------------

@Composable
private fun ListScreen(state: InspectionsState, onIntent: (InspectionsIntent) -> Unit) {
    BackHandler { onIntent(InspectionsIntent.Close) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.insp_title), state.propertyTitle) { onIntent(InspectionsIntent.Close) }
        val items = state.items
        Box(Modifier.weight(1f)) {
            when {
                items == null && state.error == null -> ListSkeleton(rows = 4, thumbnail = null, lines = 3)
                items == null -> Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error.orEmpty(), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                    Spacer(Modifier.height(12.sdp))
                    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) { onIntent(InspectionsIntent.Refresh) }
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.insp_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                    items(items, key = { it.id }) { i -> ListRow(i) { onIntent(InspectionsIntent.Select(i.id)) } }
                }
            }
        }
        if (state.canManage) {
            Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
                AuthPrimaryButton(stringResource(R.string.insp_schedule), enabled = !state.busy, loading = false, onClick = { onIntent(InspectionsIntent.StartSchedule) }, leadingIcon = R.drawable.ic_add, fontSize = 15.ssp)
            }
        }
    }
}

@Composable
private fun ListRow(i: Inspection, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).clickable(role = Role.Button, onClick = onClick).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(typeLabel(i.type)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            StatusChip(i.status)
        }
        Text(IsoTime.pretty(i.shownDate), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(8.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp), verticalAlignment = Alignment.CenterVertically) {
            if (i.isCompleted) ConditionChip(i.overall)
            if (i.openRepairs > 0) Pill(androidx.compose.ui.res.pluralStringResource(R.plurals.insp_repairs_open_p, i.openRepairs, i.openRepairs), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
        }
        if (i.landlordAcknowledgedAt.isNotBlank() || i.tenantAcknowledgedAt.isNotBlank()) {
            Spacer(Modifier.height(6.sdp))
            AckLine(i)
        }
    }
}

// ---------------- report (read only) ----------------

@Composable
private fun ReportScreen(state: InspectionsState, i: Inspection, onIntent: (InspectionsIntent) -> Unit, onCompare: ((String) -> Unit)? = null) {
    BackHandler { onIntent(InspectionsIntent.CloseReport) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(typeLabel(i.type)), IsoTime.pretty(i.shownDate)) { onIntent(InspectionsIntent.CloseReport) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
            // Header
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                StatusChip(i.status)
                if (i.result.isNotBlank()) ResultChip(i.result)
            }
            // Move-out done → compare with the move-in of the same lease.
            if (onCompare != null && i.type.uppercase() == "MOVE_OUT" && i.status.uppercase() == "COMPLETED" && !i.leaseId.isNullOrBlank()) {
                Spacer(Modifier.height(8.sdp))
                OutlineAction(stringResource(R.string.cmp_title), R.drawable.ic_document, modifier = Modifier.fillMaxWidth(), compact = true) { onCompare(i.leaseId) }
            }
            if (i.acknowledged) {
                Spacer(Modifier.height(8.sdp))
                AckLine(i)
                Text(stringResource(R.string.insp_locked), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
            }

            Section(stringResource(R.string.insp_condition))
            InspectionRules.CONDITION_KEYS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
                    row.forEach { k ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(10.sdp)) {
                            Text(stringResource(conditionAreaLabel(k)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                            Spacer(Modifier.height(4.sdp))
                            ConditionChip(i.conditions[k] ?: Condition.NOT_INSPECTED)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (i.furniture.isNotEmpty()) {
                Section(stringResource(R.string.insp_furniture))
                i.furniture.forEach { InventoryLine(it) }
            }
            if (i.appliances.isNotEmpty()) {
                Section(stringResource(R.string.insp_appliances))
                i.appliances.forEach { InventoryLine(it) }
            }
            if (i.existingDamages.isNotBlank()) {
                Section(stringResource(R.string.insp_damages))
                Body(i.existingDamages)
            }
            if (i.repairs.isNotEmpty()) {
                Section(stringResource(R.string.insp_repairs))
                i.repairs.forEach { RepairCard(it) }
            }
            if (i.notes.isNotBlank()) {
                Section(stringResource(R.string.insp_remarks))
                Body(i.notes)
            }
            if (i.meterReading.isNotBlank() || i.keysHandedOver.isNotBlank()) {
                Section(stringResource(R.string.insp_handover))
                if (i.meterReading.isNotBlank()) Body(stringResource(R.string.insp_meter_value, i.meterReading))
                if (i.keysHandedOver.isNotBlank()) Body(stringResource(R.string.insp_keys_value, i.keysHandedOver))
            }

            Section(stringResource(R.string.insp_photos))
            PhotoGrid(i, state, editable = state.canEditSelected, onOpen = { viewer = it }, onIntent = onIntent)
            Spacer(Modifier.height(24.sdp))
        }
        // Record (only while nobody acknowledged it) / acknowledge.
        if (state.canEditSelected || state.canAcknowledgeSelected) {
            Column(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                if (state.canEditSelected) {
                    AuthPrimaryButton(
                        stringResource(if (i.isCompleted) R.string.insp_edit_report else R.string.insp_record),
                        enabled = !state.busy, loading = false, onClick = { onIntent(InspectionsIntent.StartRecord) }, fontSize = 15.ssp,
                    )
                }
                if (state.canAcknowledgeSelected) {
                    OutlineAction(stringResource(R.string.insp_ack), R.drawable.ic_check, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        onIntent(InspectionsIntent.AskAcknowledge(true))
                    }
                }
            }
        }
    }
    viewer?.let { start -> PhotoViewer(i.photos.map { absoluteUrl(it.url) }, start) { viewer = null } }
}

@Composable
private fun PhotoGrid(i: Inspection, state: InspectionsState, editable: Boolean, onOpen: (Int) -> Unit, onIntent: (InspectionsIntent) -> Unit) {
    if (i.photos.isEmpty() && state.uploading == 0) Text(stringResource(R.string.insp_no_photos), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    i.photos.withIndex().chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
            row.forEach { (index, p) ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).clickable { onOpen(index) }) {
                    AsyncImage(model = absoluteUrl(p.url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(
                        stringResource(areaLabel(p.category)), color = Color.White, fontFamily = Inter, fontSize = 10.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.sdp).clip(RoundedCornerShape(6.sdp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.sdp, vertical = 2.sdp),
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (state.uploading > 0) {
        Text(stringResource(R.string.insp_uploading, state.uploading), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
        Spacer(Modifier.height(6.sdp))
    }
    if (editable) {
        val camera = rememberPermissionRequester(AppPermission.CAMERA)
        OutlineAction(stringResource(R.string.insp_add_photo), R.drawable.ic_camera, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), compact = true) {
            camera.request { granted -> if (granted) onIntent(InspectionsIntent.PickArea(true)) }
        }
    }
}

@Composable
private fun InventoryLine(r: InventoryItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${r.item} × ${r.quantity}", color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            if (r.inventoryItemId != null) InRegisterTag()
        }
        r.workingStatus?.let {
            com.saferenttechnologies.android.presentation.furnishings.WorkingChip(it)
            Spacer(Modifier.width(6.sdp))
        }
        ConditionChip(r.condition)
    }
}

/** Row linked to the furniture register. */
@Composable
private fun InRegisterTag() {
    Text(
        stringResource(R.string.insp_in_register), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.ssp,
        modifier = Modifier.padding(top = 2.sdp).clip(RoundedCornerShape(50)).background(AuthColors.OrangeSoft).padding(horizontal = 8.sdp, vertical = 1.sdp),
    )
}

@Composable
private fun RepairCard(r: RepairItem) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.item, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.weight(1f))
            PriorityChip(r.priority)
        }
        Spacer(Modifier.height(4.sdp))
        val cost = r.estimatedCost.takeIf { it.isNotBlank() }?.let { money(it) }
        Text(listOfNotNull(stringResource(payLabel(r.responsibility)), cost).joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        if (r.description.isNotBlank()) Text(r.description, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        Spacer(Modifier.height(6.sdp))
        Pill(stringResource(repairStatusLabel(r.status)), if (r.status == "DONE") AuthColors.GreenText else AuthColors.Body, if (r.status == "DONE") AuthColors.GreenBg else AuthColors.Surface)
    }
}

@Composable
private fun AckLine(i: Inspection) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
        AckTick(stringResource(R.string.insp_ack_owner), i.landlordAcknowledgedAt.isNotBlank())
        AckTick(stringResource(R.string.insp_ack_tenant), i.tenantAcknowledgedAt.isNotBlank())
    }
}

@Composable
private fun AckTick(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_add), null, tint = if (done) AuthColors.GreenText else AuthColors.Muted, modifier = Modifier.size(14.sdp))
        Spacer(Modifier.width(4.sdp))
        Text(label, color = if (done) AuthColors.GreenText else AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
    }
}

// ---------------- record report ----------------

@Composable
private fun RecordScreen(state: InspectionsState, i: Inspection, onIntent: (InspectionsIntent) -> Unit) {
    BackHandler(enabled = !state.saving) { onIntent(InspectionsIntent.CloseRecord) }
    val f = state.form
    val edit = !state.saving
    fun set(change: (InspectionForm) -> InspectionForm) = onIntent(InspectionsIntent.EditForm(change(f)))
    @Composable fun e(path: String): String? = errText(state, path)
    var datePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.insp_record), stringResource(typeLabel(i.type))) { onIntent(InspectionsIntent.CloseRecord) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
            // Date + completed
            PickerField(stringResource(R.string.insp_date), IsoTime.pretty(f.inspectionDate), e("inspectionDate"), edit) { datePicker = true }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.sdp)) {
                Text(stringResource(R.string.insp_mark_completed), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Switch(
                    checked = f.complete, onCheckedChange = { c -> set { it.copy(complete = c) } }, enabled = edit,
                    colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
                )
            }

            Section(stringResource(R.string.insp_condition))
            InspectionRules.CONDITION_KEYS.forEach { k ->
                Text(stringResource(conditionAreaLabel(k)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
                Spacer(Modifier.height(4.sdp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.sdp), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                    Condition.entries.forEach { c ->
                        SelectPill(stringResource(conditionLabel(c)), selected = f.conditions[k] == c, enabled = edit, color = conditionColors(c)) {
                            onIntent(InspectionsIntent.SetCondition(k, c))
                        }
                    }
                }
            }

            // Furniture register → linked rows (saved as COMPLETED → the register item is updated).
            Spacer(Modifier.height(16.sdp))
            OutlineAction(
                stringResource(if (state.loadingRegister) R.string.insp_register_loading else R.string.insp_load_register), R.drawable.ic_sofa,
                enabled = edit && !state.loadingRegister, modifier = Modifier.fillMaxWidth(), compact = true,
            ) { onIntent(InspectionsIntent.LoadFromRegister) }
            InventoryEditor(stringResource(R.string.insp_furniture), f.furniture, InspectionRules.FURNITURE_SUGGESTIONS, appliance = false, state, edit, onIntent)
            InventoryEditor(stringResource(R.string.insp_appliances), f.appliances, InspectionRules.APPLIANCE_SUGGESTIONS, appliance = true, state, edit, onIntent)

            Section(stringResource(R.string.insp_damages))
            Input(stringResource(R.string.insp_damages), f.existingDamages, e("existingDamages"), edit, singleLine = false) { v -> set { it.copy(existingDamages = v.take(InspectionRules.MAX_TEXT)) } }

            Section(stringResource(R.string.insp_repairs))
            f.repairs.forEachIndexed { idx, r -> RepairEditor(idx, r, state, edit, onIntent) }
            if (edit && f.repairs.size < InspectionRules.MAX_REPAIRS) {
                OutlineAction(stringResource(R.string.insp_add_repair), R.drawable.ic_add, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(InspectionsIntent.AddRepair) }
            }

            Section(stringResource(R.string.insp_handover))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Input(stringResource(R.string.insp_meter), f.meterReading, e("handoverMeterReading"), edit, Modifier.weight(1f), keyboard = KeyboardType.Decimal) { v -> set { it.copy(meterReading = InspectionRules.cleanDecimal(v, 3).take(14)) } }
                Input(stringResource(R.string.insp_keys), f.keysHandedOver, e("keysHandedOver"), edit, Modifier.weight(1f), keyboard = KeyboardType.Number) { v -> set { it.copy(keysHandedOver = v.filter(Char::isDigit).take(3)) } }
            }

            Section(stringResource(R.string.insp_result))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                InspectionRules.RESULTS.forEach { r ->
                    Chip(stringResource(resultLabel(r)), selected = f.result == r, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) {
                        set { it.copy(result = if (it.result == r) "" else r) }
                    }
                }
            }

            Section(stringResource(R.string.insp_remarks))
            Input(stringResource(R.string.insp_remarks), f.notes, e("notes"), edit, singleLine = false) { v -> set { it.copy(notes = v.take(InspectionRules.MAX_TEXT)) } }

            Section(stringResource(R.string.insp_photos))
            PhotoGrid(i, state, editable = true, onOpen = {}, onIntent = onIntent)
            Spacer(Modifier.height(24.sdp))
        }
        Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
            AuthPrimaryButton(stringResource(R.string.insp_save), enabled = !state.saving, loading = state.saving, onClick = { onIntent(InspectionsIntent.Save) }, fontSize = 15.ssp)
        }
    }
    if (datePicker) {
        DateTimePicker(IsoTime.parse(f.inspectionDate) ?: System.currentTimeMillis(), onDismiss = { datePicker = false }) { ms ->
            datePicker = false
            set { it.copy(inspectionDate = IsoTime.format(ms)) }
        }
    }
}

@Composable
private fun InventoryEditor(
    title: String,
    rows: List<InventoryItem>,
    suggestions: List<String>,
    appliance: Boolean,
    state: InspectionsState,
    edit: Boolean,
    onIntent: (InspectionsIntent) -> Unit,
) {
    val name = if (appliance) "applianceInventory" else "furnitureInventory"
    Section(title)
    rows.forEachIndexed { idx, r ->
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(stringResource(R.string.insp_item), r.item, errText(state, "$name.$idx.item"), edit, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words) {
                    onIntent(InspectionsIntent.EditInventory(appliance, idx, r.copy(item = it)))
                }
                RemoveIcon(edit) { onIntent(InspectionsIntent.RemoveInventory(appliance, idx)) }
            }
            Spacer(Modifier.height(6.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(r.quantity, edit) { onIntent(InspectionsIntent.EditInventory(appliance, idx, r.copy(quantity = it))) }
                Spacer(Modifier.width(10.sdp))
                ConditionDropdown(r.condition, edit, Modifier.weight(1f)) { onIntent(InspectionsIntent.EditInventory(appliance, idx, r.copy(condition = it))) }
            }
            // Linked to the register: working status too (copied to the item when completed).
            if (r.inventoryItemId != null) {
                Spacer(Modifier.height(6.sdp))
                InRegisterTag()
                Spacer(Modifier.height(4.sdp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                    com.saferenttechnologies.android.domain.model.WorkingStatus.entries.forEach { w ->
                        SelectPill(stringResource(com.saferenttechnologies.android.presentation.furnishings.workingLabel(w)), selected = r.workingStatus == w, enabled = edit, color = AuthColors.OrangeText to AuthColors.OrangeSoft) {
                            onIntent(InspectionsIntent.EditInventory(appliance, idx, r.copy(workingStatus = w)))
                        }
                    }
                }
            }
            errText(state, "$name.$idx.quantity")?.let { ErrorLine(it) }
        }
    }
    errText(state, name)?.let { ErrorLine(it) }
    if (edit) {
        // Quick add: tap a common item.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.sdp), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            suggestions.forEach { s -> SelectPill("+ $s", selected = false, enabled = rows.size < InspectionRules.MAX_ROWS, color = AuthColors.OrangeText to AuthColors.OrangeSoft) { onIntent(InspectionsIntent.AddInventory(appliance, s)) } }
        }
        OutlineAction(stringResource(R.string.insp_add_item), R.drawable.ic_add, enabled = rows.size < InspectionRules.MAX_ROWS, modifier = Modifier.fillMaxWidth(), compact = true) {
            onIntent(InspectionsIntent.AddInventory(appliance))
        }
    }
}

@Composable
private fun RepairEditor(idx: Int, r: RepairItem, state: InspectionsState, edit: Boolean, onIntent: (InspectionsIntent) -> Unit) {
    fun change(x: RepairItem) = onIntent(InspectionsIntent.EditRepair(idx, x))
    val p = "repairRequirements.$idx"
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp),
        verticalArrangement = Arrangement.spacedBy(6.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Input(stringResource(R.string.insp_repair_item), r.item, errText(state, "$p.item"), edit, Modifier.weight(1f), capitalization = KeyboardCapitalization.Sentences) { change(r.copy(item = it)) }
            RemoveIcon(edit) { onIntent(InspectionsIntent.RemoveRepair(idx)) }
        }
        Input(stringResource(R.string.insp_repair_details), r.description, errText(state, "$p.description"), edit, singleLine = false) { change(r.copy(description = it)) }
        Input(stringResource(R.string.insp_repair_cost), r.estimatedCost, errText(state, "$p.estimatedCost"), edit, keyboard = KeyboardType.Decimal) { change(r.copy(estimatedCost = it)) }
        Label(stringResource(R.string.insp_priority))
        PillRow(InspectionRules.PRIORITIES, r.priority, edit, { priorityLabel(it) }) { change(r.copy(priority = it)) }
        Label(stringResource(R.string.insp_who_pays))
        PillRow(InspectionRules.RESPONSIBILITIES, r.responsibility, edit, { payLabel(it) }) { change(r.copy(responsibility = it)) }
        Label(stringResource(R.string.insp_repair_status))
        PillRow(InspectionRules.REPAIR_STATUSES, r.status, edit, { repairStatusLabel(it) }) { change(r.copy(status = it)) }
    }
}

// ---------------- schedule ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(state: InspectionsState, form: ScheduleForm, onIntent: (InspectionsIntent) -> Unit) {
    fun change(f: ScheduleForm) = onIntent(InspectionsIntent.EditSchedule(f))
    var picker by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onIntent(InspectionsIntent.CancelSchedule) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp)) {
            Text(stringResource(R.string.insp_schedule), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(12.sdp))
            Label(stringResource(R.string.insp_type))
            InspectionType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
                    row.forEach { t ->
                        Chip(stringResource(typeLabel(t.name)), selected = form.type == t, modifier = Modifier.weight(1f), maxLines = 1) {
                            change(form.copy(type = t, leaseId = if (t.needsLease) form.leaseId else null))
                        }
                    }
                }
            }
            PickerField(stringResource(R.string.insp_when), IsoTime.pretty(form.scheduledAt), null, true) { picker = true }

            if (form.type.needsLease) {
                Spacer(Modifier.height(8.sdp))
                Label(stringResource(R.string.insp_lease))
                if (state.leases.isEmpty()) {
                    Text(stringResource(R.string.insp_no_lease), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                } else {
                    state.leases.forEach { l ->
                        Chip(l.label, selected = form.leaseId == l.id, modifier = Modifier.fillMaxWidth().padding(bottom = 6.sdp), maxLines = 1) {
                            change(form.copy(leaseId = l.id, unitId = l.unitId, bedId = l.bedId, roomId = null))
                        }
                    }
                }
            } else if (state.units.isNotEmpty() || state.rooms.isNotEmpty()) {
                Spacer(Modifier.height(8.sdp))
                Label(stringResource(R.string.insp_place))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                    SelectPill(stringResource(R.string.insp_whole_property), selected = form.unitId == null && form.roomId == null, enabled = true, color = AuthColors.OrangeText to AuthColors.OrangeSoft) {
                        change(form.copy(unitId = null, roomId = null, bedId = null))
                    }
                    state.units.forEach { u -> SelectPill(u.label, form.unitId == u.id, true, AuthColors.OrangeText to AuthColors.OrangeSoft) { change(form.copy(unitId = u.id, roomId = null, bedId = null)) } }
                    state.rooms.forEach { r -> SelectPill(r.label, form.roomId == r.id, true, AuthColors.OrangeText to AuthColors.OrangeSoft) { change(form.copy(roomId = r.id, unitId = null, bedId = null)) } }
                }
            }
            Spacer(Modifier.height(10.sdp))
            Input(stringResource(R.string.insp_schedule_notes), form.notes, null, true, singleLine = false) { change(form.copy(notes = it.take(1000))) }
            state.scheduleError?.let {
                ErrorLine(
                    when (it) {
                        InspectionsViewModel.SCHEDULE_DATE -> stringResource(R.string.insp_pick_when)
                        InspectionsViewModel.LEASE_NEEDED -> stringResource(R.string.insp_pick_lease)
                        else -> it
                    },
                )
            }
            Spacer(Modifier.height(14.sdp))
            AuthPrimaryButton(stringResource(R.string.insp_schedule_go), enabled = !state.saving, loading = state.saving, onClick = { onIntent(InspectionsIntent.SubmitSchedule) }, fontSize = 15.ssp)
        }
    }
    if (picker) {
        DateTimePicker(IsoTime.parse(form.scheduledAt) ?: System.currentTimeMillis(), onDismiss = { picker = false }) { ms ->
            picker = false
            change(form.copy(scheduledAt = IsoTime.format(ms)))
        }
    }
}

/** "Which area is this photo of?" before the camera. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaSheet(onIntent: (InspectionsIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(InspectionsIntent.PickArea(false)) },
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp)) {
            Text(stringResource(R.string.insp_which_area), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(12.sdp))
            InspectionRules.PHOTO_AREAS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
                    row.forEach { a -> Chip(stringResource(areaLabel(a)), selected = false, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(InspectionsIntent.AreaChosen(a)) } }
                }
            }
        }
    }
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
private fun Section(text: String) {
    Spacer(Modifier.height(18.sdp))
    Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
    Spacer(Modifier.height(8.sdp))
}

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, modifier = Modifier.padding(bottom = 4.sdp))

@Composable
private fun Body(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.padding(bottom = 4.sdp))

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 3.sdp))
}

@Composable
private fun SelectPill(text: String, selected: Boolean, enabled: Boolean, color: Pair<Color, Color>, onClick: () -> Unit) {
    val (fg, bg) = color
    Text(
        text,
        color = if (selected) fg else if (enabled) AuthColors.Body else AuthColors.Muted,
        fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) bg else Color.Transparent)
            .border(1.sdp, if (selected) fg else AuthColors.Border, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.sdp, vertical = 6.sdp),
    )
}

@Composable
private fun PillRow(values: List<String>, selected: String, enabled: Boolean, label: (String) -> Int, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
        values.forEach { v -> SelectPill(stringResource(label(v)), v == selected, enabled, AuthColors.OrangeText to AuthColors.OrangeSoft) { onPick(v) } }
    }
}

@Composable
private fun ConditionChip(c: Condition) {
    val (fg, bg) = conditionColors(c)
    Pill(stringResource(conditionLabel(c)), fg, bg)
}

@Composable
private fun conditionColors(c: Condition): Pair<Color, Color> = when (c) {
    Condition.GOOD -> AuthColors.GreenText to AuthColors.GreenBg
    Condition.FAIR -> Amber to Amber.copy(alpha = 0.12f)
    Condition.POOR -> AuthColors.Error to AuthColors.Error.copy(alpha = 0.1f)
    else -> AuthColors.Body to AuthColors.Surface
}

@Composable
private fun StatusChip(status: String) = when (status.uppercase()) {
    "COMPLETED" -> Pill(stringResource(R.string.insp_status_completed), AuthColors.GreenText, AuthColors.GreenBg)
    "CANCELLED" -> Pill(stringResource(R.string.insp_status_cancelled), AuthColors.Body, AuthColors.Surface)
    else -> Pill(stringResource(R.string.insp_status_scheduled), AuthColors.OrangeText, AuthColors.OrangeSoft)
}

@Composable
private fun ResultChip(result: String) = when (result) {
    "PASSED" -> Pill(stringResource(R.string.insp_result_passed), AuthColors.GreenText, AuthColors.GreenBg)
    "FAILED" -> Pill(stringResource(R.string.insp_result_failed), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
    else -> Pill(stringResource(R.string.insp_result_repairs), Amber, Amber.copy(alpha = 0.12f))
}

@Composable
private fun PriorityChip(p: String) {
    val urgent = p == "HIGH" || p == "URGENT"
    Pill(stringResource(priorityLabel(p)), if (urgent) AuthColors.Error else AuthColors.Body, if (urgent) AuthColors.Error.copy(alpha = 0.1f) else AuthColors.Surface)
}

@Composable
private fun RemoveIcon(enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    Box(Modifier.padding(start = 6.sdp).size(36.sdp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.insp_remove), tint = AuthColors.Error, modifier = Modifier.size(18.sdp))
    }
}

@Composable
private fun Stepper(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val n = value.toIntOrNull() ?: 0
    Row(
        Modifier.clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", enabled && n > 0) { onChange((n - 1).toString()) }
        Text(n.toString(), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.padding(horizontal = 8.sdp))
        StepButton("+", enabled && n < 1000) { onChange((n + 1).toString()) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.sdp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp)
    }
}

@Composable
private fun ConditionDropdown(value: Condition, enabled: Boolean, modifier: Modifier, onPick: (Condition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp))
                .clickable(enabled = enabled, role = Role.Button) { open = true }.padding(horizontal = 10.sdp, vertical = 8.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { ConditionChip(value) }
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(AuthColors.Card)) {
            Condition.entries.forEach { c ->
                DropdownMenuItem(text = { Text(stringResource(conditionLabel(c)), fontFamily = Inter) }, onClick = { open = false; onPick(c) })
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
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read only box that opens the date + time picker. */
@Composable
private fun PickerField(label: String, value: String, error: String?, enabled: Boolean, onClick: () -> Unit) {
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
    }
}

/** Date, then time (phone's time zone) → epoch millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(initial: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val local = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    // The date picker works in UTC midnight millis.
    val utcStart = remember(initial) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    var dayMillis by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = utcStart)
    val timeState = rememberTimePickerState(initialHour = local.get(Calendar.HOUR_OF_DAY), initialMinute = local.get(Calendar.MINUTE), is24Hour = false)
    if (dayMillis == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { dayMillis = dateState.selectedDateMillis ?: utcStart }) { Text(stringResource(R.string.insp_next), color = AuthColors.OrangeText) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val d = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayMillis!! }
                    val out = Calendar.getInstance().apply {
                        clear()
                        set(d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute)
                    }
                    onPicked(out.timeInMillis)
                }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
            text = { TimePicker(state = timeState) },
            containerColor = AuthColors.Card,
        )
    }
}

/** Full screen photos: swipe, pinch to zoom. */
@Composable
internal fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = urls[i], contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
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

/** Server message for [path] first; local check only after Save was tapped. */
@Composable
private fun errText(state: InspectionsState, path: String): String? =
    state.serverErrors[path] ?: if (state.showErrors) state.problems[path]?.let { p ->
        stringResource(
            when (p) {
                InspectionRules.Problem.DATE_REQUIRED -> R.string.insp_err_date
                InspectionRules.Problem.ITEM_LENGTH -> R.string.insp_err_item
                InspectionRules.Problem.QUANTITY -> R.string.insp_err_qty
                InspectionRules.Problem.TOO_MANY -> R.string.insp_err_too_many
                InspectionRules.Problem.REPAIR_ITEM -> R.string.insp_err_repair_item
                InspectionRules.Problem.DESCRIPTION -> R.string.insp_err_description
                InspectionRules.Problem.COST -> R.string.insp_err_cost
                InspectionRules.Problem.TEXT_LONG -> R.string.insp_err_text
                InspectionRules.Problem.KEYS -> R.string.insp_err_keys
                InspectionRules.Problem.METER -> R.string.insp_err_meter
            },
        )
    } else null

private fun typeLabel(t: String) = when (t.uppercase()) {
    "MOVE_IN" -> R.string.insp_type_move_in
    "MOVE_OUT" -> R.string.insp_type_move_out
    "VERIFICATION" -> R.string.insp_type_verification
    else -> R.string.insp_type_routine
}

internal fun conditionLabel(c: Condition) = when (c) {
    Condition.GOOD -> R.string.insp_c_good
    Condition.FAIR -> R.string.insp_c_fair
    Condition.POOR -> R.string.insp_c_poor
    Condition.NOT_APPLICABLE -> R.string.insp_c_na
    Condition.NOT_INSPECTED -> R.string.insp_c_not_inspected
}

internal fun conditionAreaLabel(key: String) = when (key) {
    "propertyCondition" -> R.string.insp_a_overall
    "wallCondition" -> R.string.insp_a_walls
    "flooringCondition" -> R.string.insp_a_flooring
    "doorsAndWindows" -> R.string.insp_a_doors
    "electricalCondition" -> R.string.insp_a_electrical
    "plumbingCondition" -> R.string.insp_a_plumbing
    "bathroomCondition" -> R.string.insp_a_bathroom
    "kitchenCondition" -> R.string.insp_a_kitchen
    "furnitureCondition" -> R.string.insp_a_furniture
    else -> R.string.insp_a_appliances
}

private fun resultLabel(r: String) = when (r) {
    "PASSED" -> R.string.insp_result_passed
    "FAILED" -> R.string.insp_result_failed
    else -> R.string.insp_result_repairs
}

private fun priorityLabel(p: String) = when (p) {
    "LOW" -> R.string.insp_p_low
    "HIGH" -> R.string.insp_p_high
    "URGENT" -> R.string.insp_p_urgent
    else -> R.string.insp_p_medium
}

private fun payLabel(r: String) = when (r) {
    "TENANT" -> R.string.insp_pay_tenant
    "SHARED" -> R.string.insp_pay_shared
    else -> R.string.insp_pay_owner
}

private fun repairStatusLabel(s: String) = when (s) {
    "IN_PROGRESS" -> R.string.insp_rs_progress
    "DONE" -> R.string.insp_rs_done
    else -> R.string.insp_rs_open
}

internal fun areaLabel(a: String) = when (a) {
    "LIVING_ROOM" -> R.string.insp_area_living
    "BEDROOM" -> R.string.insp_area_bedroom
    "KITCHEN" -> R.string.insp_area_kitchen
    "BATHROOM" -> R.string.insp_area_bathroom
    "BALCONY" -> R.string.insp_area_balcony
    "ENTRANCE" -> R.string.insp_area_entrance
    "METER_READING" -> R.string.insp_area_meter
    else -> R.string.insp_area_other
}
