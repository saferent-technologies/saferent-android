package com.saferenttechnologies.android.presentation.smart

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.ComparisonRules
import com.saferenttechnologies.android.domain.model.Condition
import com.saferenttechnologies.android.domain.model.InspectionDiff
import com.saferenttechnologies.android.domain.model.InspectionPhoto
import com.saferenttechnologies.android.domain.model.ItemChange
import com.saferenttechnologies.android.domain.model.ItemSnapshot
import com.saferenttechnologies.android.domain.model.QrResolution
import com.saferenttechnologies.android.domain.model.QrTarget
import com.saferenttechnologies.android.domain.model.ReminderRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.inspection.PhotoViewer
import com.saferenttechnologies.android.presentation.inspection.areaLabel
import com.saferenttechnologies.android.presentation.inspection.conditionLabel
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// ======================= move-in vs move-out =======================

@Composable
fun ComparisonScreen(state: ComparisonState, onIntent: (ComparisonIntent) -> Unit, onStartMoveOut: (() -> Unit)?) {
    BackHandler { onIntent(ComparisonIntent.Close) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.cmp_title), state.title) { onIntent(ComparisonIntent.Close) }
        val data = state.data
        val diff = data?.comparison?.takeIf { data.ready }
        when {
            data == null && state.error == null -> ListSkeleton(rows = 6, thumbnail = null, lines = 2)
            data == null -> ErrorBox(state.error.orEmpty()) { onIntent(ComparisonIntent.Refresh) }
            diff == null -> Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if ("MOVE_IN" in data.missing && "MOVE_OUT" !in data.missing) R.string.cmp_need_move_in else R.string.cmp_need_move_out),
                    color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp,
                )
                if (state.landlord && onStartMoveOut != null && "MOVE_OUT" in data.missing) {
                    Spacer(Modifier.height(16.sdp))
                    AuthPrimaryButton(stringResource(R.string.cmp_go_inspections), enabled = true, loading = false, onClick = onStartMoveOut, fontSize = 15.ssp)
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp, vertical = 8.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                    Summary(diff)
                    // Dates of both inspections.
                    Text(
                        listOfNotNull(
                            data.moveIn?.inspectionDate?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.cmp_move_in_on, calendarDate(it)) },
                            data.moveOut?.inspectionDate?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.cmp_move_out_on, calendarDate(it)) },
                        ).joinToString(" · "),
                        color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp,
                    )
                    if (diff.areas.isNotEmpty()) {
                        Section(stringResource(R.string.cmp_areas))
                        Card {
                            HeaderRow()
                            diff.areas.forEach { a ->
                                ChangeRow(a.label.ifBlank { a.key }, conditionText(a.before), conditionText(a.after), a.change, 0)
                            }
                        }
                    }
                    if (diff.furniture.isNotEmpty()) {
                        Section(stringResource(R.string.cmp_furniture))
                        Card { HeaderRow(); diff.furniture.forEach { ItemRow(it) } }
                    }
                    if (diff.appliances.isNotEmpty()) {
                        Section(stringResource(R.string.cmp_appliances))
                        Card { HeaderRow(); diff.appliances.forEach { ItemRow(it) } }
                    }
                    if (diff.meterIn != null || diff.meterOut != null || diff.keysIn != null || diff.keysOut != null) {
                        Section(stringResource(R.string.cmp_meter_keys))
                        Card {
                            if (diff.meterIn != null || diff.meterOut != null) {
                                LabelValue(
                                    stringResource(R.string.cmp_meter),
                                    "${diff.meterIn?.let(ComparisonRules::number) ?: "?"} → ${diff.meterOut?.let(ComparisonRules::number) ?: "?"}" +
                                        (diff.meterUsed?.let { " " + stringResource(R.string.cmp_units_used, ComparisonRules.number(it)) } ?: ""),
                                )
                            }
                            if (diff.keysIn != null || diff.keysOut != null) {
                                LabelValue(
                                    stringResource(R.string.cmp_keys),
                                    "${diff.keysIn ?: "?"} → ${diff.keysOut ?: "?"}" + (diff.keysMissing?.takeIf { it > 0 }?.let { " " + stringResource(R.string.cmp_missing_paren, it) } ?: ""),
                                    warn = (diff.keysMissing ?: 0) > 0,
                                )
                            }
                        }
                    }
                    if (diff.damagesIn != null || diff.damagesOut != null) {
                        Section(stringResource(R.string.cmp_damages))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                            DamageBox(stringResource(R.string.cmp_move_in), diff.damagesIn, Modifier.weight(1f))
                            DamageBox(stringResource(R.string.cmp_move_out), diff.damagesOut, Modifier.weight(1f))
                        }
                    }
                    if (diff.repairs.isNotEmpty()) {
                        Section(stringResource(R.string.cmp_repairs))
                        diff.repairs.forEach { r ->
                            Card {
                                Text(r.item, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                                if (r.description.isNotBlank()) Text(r.description, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                                Text(
                                    listOfNotNull(
                                        r.estimatedCost?.let { money(ComparisonRules.number(it)) },
                                        r.responsibility.takeIf { it.isNotBlank() }?.let { stringResource(responsibilityLabel(it)) },
                                    ).joinToString(" · "),
                                    color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp,
                                )
                            }
                        }
                    }
                    Photos(state, data.moveIn?.photos.orEmpty(), data.moveOut?.photos.orEmpty(), onIntent) { urls, i -> viewer = urls to i }
                    Spacer(Modifier.height(16.sdp))
                }
            }
        }
    }
    viewer?.let { (urls, i) -> PhotoViewer(urls, i) { viewer = null } }
}

@Composable
private fun Summary(d: InspectionDiff) {
    val s = d.summary
    val issues = s.hasIssues
    val fg = if (issues) Amber else AuthColors.GreenText
    val bg = if (issues) Amber.copy(alpha = 0.12f) else AuthColors.GreenBg
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(bg).padding(12.sdp)) {
        Text(stringResource(if (issues) R.string.cmp_has_issues else R.string.cmp_no_changes), color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
        if (issues) {
            val parts = listOfNotNull(
                s.areasWorse.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.cmp_areas_worse, it, it) },
                s.itemsWorse.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.cmp_items_worse, it, it) },
                s.itemsMissing.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.cmp_items_missing, it, it) },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        }
    }
}

@Composable
private fun HeaderRow() = Row(Modifier.fillMaxWidth()) {
    Text("", modifier = Modifier.weight(1.2f))
    Text(stringResource(R.string.cmp_move_in), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp, modifier = Modifier.weight(1f))
    Text(stringResource(R.string.cmp_move_out), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(72.sdp))
}

@Composable
private fun ChangeRow(label: String, before: String, after: String, change: String, missing: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, modifier = Modifier.weight(1.2f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(before, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(after, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Box(Modifier.width(72.sdp), contentAlignment = Alignment.CenterEnd) { ChangeChip(change, missing) }
    }
}

@Composable
private fun ItemRow(i: ItemChange) = ChangeRow(i.item, snapshotText(i.before), snapshotText(i.after), i.change, i.missing)

/** "4 · Good"; null → "—" would be a dash, so "None". */
@Composable
private fun snapshotText(s: ItemSnapshot?): String =
    s?.let { listOfNotNull(it.quantity.toString(), it.condition?.let { c -> conditionText(c) }).joinToString(" · ") } ?: stringResource(R.string.cmp_none)

@Composable
private fun conditionText(c: String?): String = Condition.of(c)?.let { stringResource(conditionLabel(it)) } ?: stringResource(R.string.cmp_none)

/** WORSE red, BETTER green, MISSING red "1 missing", ADDED blue; SAME / NOT_COMPARABLE: no chip. */
@Composable
private fun ChangeChip(change: String, missing: Int) {
    if (!ComparisonRules.showChip(change)) return
    val (text, fg, bg) = when (change) {
        "WORSE" -> Triple(stringResource(R.string.cmp_worse), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
        "BETTER" -> Triple(stringResource(R.string.cmp_better), AuthColors.GreenText, AuthColors.GreenBg)
        "MISSING" -> Triple(stringResource(R.string.cmp_n_missing, missing.coerceAtLeast(1)), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
        else -> Triple(stringResource(R.string.cmp_added), Color(0xFF2563EB), Color(0xFF2563EB).copy(alpha = 0.1f))
    }
    Pill(text, fg, bg)
}

@Composable
private fun DamageBox(title: String, text: String?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp)) {
        Text(title, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
        Text(text ?: stringResource(R.string.cmp_none), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
    }
}

/** Move-in / Move-out tabs, grid, tap → full screen; "Compare" = same area side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Photos(state: ComparisonState, moveIn: List<InspectionPhoto>, moveOut: List<InspectionPhoto>, onIntent: (ComparisonIntent) -> Unit, onView: (List<String>, Int) -> Unit) {
    if (moveIn.isEmpty() && moveOut.isEmpty()) return
    val shared = ComparisonRules.sharedCategories(moveIn, moveOut)
    Section(stringResource(R.string.cmp_photos))
    Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        Chip(stringResource(R.string.cmp_move_in_n, moveIn.size), selected = !state.compare && !state.moveOutPhotos, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(ComparisonIntent.PhotoTab(false)) }
        Chip(stringResource(R.string.cmp_move_out_n, moveOut.size), selected = !state.compare && state.moveOutPhotos, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(ComparisonIntent.PhotoTab(true)) }
        if (shared.isNotEmpty()) Chip(stringResource(R.string.cmp_compare), selected = state.compare, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(ComparisonIntent.Compare(!state.compare)) }
    }
    if (state.compare) {
        shared.forEach { cat ->
            val a = moveIn.filter { it.category.equals(cat, true) }
            val b = moveOut.filter { it.category.equals(cat, true) }
            Text(stringResource(areaLabel(cat)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Thumb(a.first().url, Modifier.weight(1f)) { onView(a.map { absoluteUrl(it.url) }, 0) }
                Thumb(b.first().url, Modifier.weight(1f)) { onView(b.map { absoluteUrl(it.url) }, 0) }
            }
        }
    } else {
        val list = if (state.moveOutPhotos) moveOut else moveIn
        if (list.isEmpty()) Text(stringResource(R.string.cmp_no_photos), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        val urls = list.map { absoluteUrl(it.url) }
        list.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                row.forEachIndexed { c, p -> Thumb(p.url, Modifier.weight(1f)) { onView(urls, r * 3 + c) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Signed-in image (the shared image loader adds the Firebase token). */
@Composable
private fun Thumb(url: String, modifier: Modifier, onClick: () -> Unit) {
    AsyncImage(
        model = absoluteUrl(url), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onClick),
    )
}

private fun responsibilityLabel(r: String) = when (r) {
    "TENANT" -> R.string.cmp_resp_tenant
    "LANDLORD", "OWNER" -> R.string.cmp_resp_owner
    "SHARED" -> R.string.cmp_resp_shared
    else -> R.string.cmp_resp_other
}

// ======================= QR codes (owner) =======================

@Composable
fun QrCodesScreen(state: QrState, onIntent: (QrIntent) -> Unit) {
    BackHandler { onIntent(QrIntent.Close) }
    val context = LocalContext.current
    val footer = stringResource(R.string.qr_card_footer)
    val shareTitle = stringResource(R.string.qr_share_title)
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.qr_title), state.title) { onIntent(QrIntent.Close) }
        val codes = state.codes
        when {
            codes == null && state.error == null -> ListSkeleton(rows = 3, thumbnail = null, lines = 2)
            codes == null -> ErrorBox(state.error.orEmpty()) { onIntent(QrIntent.Retry) }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(12.sdp)) {
                Text(stringResource(R.string.qr_help), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                codes.all.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                        row.forEach { t ->
                            QrCard(t, Modifier.weight(1f), onShare = { bmp -> QrImages.share(context, bmp, t.code, shareTitle) }, onPrint = { bmp -> QrImages.print(context, bmp, "SafeRent ${t.title}") }, footer = footer)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCard(t: QrTarget, modifier: Modifier, footer: String, onShare: (Bitmap) -> Unit, onPrint: (Bitmap) -> Unit) {
    // Drawn off the main thread; the shareable card (QR + label) is made once per code.
    val card by produceState<Bitmap?>(null, t.url, t.title) { value = withContext(Dispatchers.Default) { QrImages.card(t.url, t.title, footer) } }
    Column(
        modifier.clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).background(Color.White).padding(10.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.sdp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            card?.let { Image(it.asImageBitmap(), contentDescription = t.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            OutlineAction(stringResource(R.string.qr_share), enabled = card != null, modifier = Modifier.weight(1f), compact = true) { card?.let(onShare) }
            OutlineAction(stringResource(R.string.qr_print), enabled = card != null, modifier = Modifier.weight(1f), compact = true) { card?.let(onPrint) }
        }
    }
}

/** "Scan QR" chip for top bars (home, owner / inspector screens). */
@Composable
fun ScanQrButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(R.drawable.ic_qr), stringResource(R.string.qr_scan), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
}

/** After a scan: "<title> · <flat>" + one button per action; role NONE → the server's message. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultSheet(r: QrResolution, onAction: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            Text(r.title.ifBlank { r.propertyCode }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            val actions = r.actions.filter { it in com.saferenttechnologies.android.domain.model.QrLinks.ACTIONS }
            if (actions.isEmpty()) {
                Text(r.message ?: stringResource(R.string.qr_nothing), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
            } else {
                r.message?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
                actions.forEachIndexed { i, a ->
                    if (i == 0) {
                        AuthPrimaryButton(stringResource(actionLabel(a)), enabled = true, loading = false, onClick = { onAction(a) }, fontSize = 15.ssp)
                    } else {
                        OutlineAction(stringResource(actionLabel(a)), modifier = Modifier.fillMaxWidth()) { onAction(a) }
                    }
                }
            }
        }
    }
}

private fun actionLabel(a: String) = when (a) {
    "OPEN_PROPERTY" -> R.string.qr_a_open_property
    "START_INSPECTION" -> R.string.qr_a_start_inspection
    "OPEN_FURNITURE" -> R.string.qr_a_furniture
    "OPEN_LEASE" -> R.string.qr_a_lease
    "RAISE_REQUEST" -> R.string.qr_a_request
    "EMERGENCY" -> R.string.qr_a_emergency
    "VIEW_LISTING" -> R.string.qr_a_listing
    else -> R.string.qr_a_visit
}

// ======================= smart rent reminders =======================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderSheet(state: ReminderState, effects: Flow<ReminderEffect>, onIntent: (ReminderIntent) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { e -> if (e is ReminderEffect.Message && e.text.isNotBlank()) android.widget.Toast.makeText(context, e.text, android.widget.Toast.LENGTH_LONG).show() }
    }
    ModalBottomSheet(
        onDismissRequest = { onIntent(ReminderIntent.Close) },
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(12.sdp)) {
            Text(stringResource(R.string.rem_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            val data = state.data
            when {
                data == null && state.error == null -> ListSkeleton(rows = 3, thumbnail = null, lines = 1)
                data == null -> ErrorBox(state.error.orEmpty()) { onIntent(ReminderIntent.Retry) }
                else -> {
                    val f = state.form
                    val edit = !state.saving
                    SwitchRow(stringResource(R.string.rem_auto), f.enabled, edit) { onIntent(ReminderIntent.Enabled(it)) }
                    if (f.enabled) {
                        Text(stringResource(R.string.rem_before), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                            ReminderRules.BEFORE_OPTIONS.forEach { d ->
                                Chip(androidx.compose.ui.res.pluralStringResource(R.plurals.rem_days, d, d), selected = d in f.beforeDays, enabled = edit, maxLines = 1) { onIntent(ReminderIntent.ToggleBefore(d)) }
                            }
                        }
                        Text(stringResource(R.string.rem_before_note, ReminderRules.MAX_BEFORE), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                        Text(stringResource(R.string.rem_late), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                            ReminderRules.OVERDUE_OPTIONS.forEach { d ->
                                Chip(androidx.compose.ui.res.pluralStringResource(R.plurals.rem_days, d, d), selected = d in f.overdueDays, enabled = edit, maxLines = 1) { onIntent(ReminderIntent.ToggleOverdue(d)) }
                            }
                        }
                        SwitchRow(stringResource(R.string.rem_smart), f.smartEarly, edit) { onIntent(ReminderIntent.SmartEarly(it)) }
                    }
                    Text(
                        stringResource(R.string.rem_note) + (data.sendingHours.takeIf { it.isNotBlank() }?.let { "\n" + stringResource(R.string.rem_hours, it) } ?: ""),
                        color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).padding(10.sdp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalAlignment = Alignment.CenterVertically) {
                        OutlineAction(stringResource(R.string.rem_defaults), enabled = edit && f != data.defaults, modifier = Modifier.weight(1f), compact = true) { onIntent(ReminderIntent.Defaults) }
                        Box(Modifier.weight(1.4f)) {
                            AuthPrimaryButton(stringResource(R.string.rem_save), enabled = state.changed && edit, loading = state.saving, onClick = { onIntent(ReminderIntent.Save) }, fontSize = 15.ssp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
    }
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
private fun Section(text: String) =
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.padding(top = 6.sdp))

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
    verticalArrangement = Arrangement.spacedBy(4.sdp),
    content = content,
)

@Composable
private fun LabelValue(label: String, value: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(value, color = if (warn) AuthColors.Error else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) = Text(
    text, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp, maxLines = 1,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.sdp, vertical = 3.sdp),
)

@Composable
private fun ErrorBox(text: String, onRetry: () -> Unit) = Column(Modifier.fillMaxWidth().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh, onClick = onRetry)
}
