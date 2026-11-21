package com.saferenttechnologies.android.presentation.visits

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.Visit
import com.saferenttechnologies.android.domain.model.VisitRules
import com.saferenttechnologies.android.domain.model.VisitSlots
import com.saferenttechnologies.android.domain.model.WeeklyWindow
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

private val Blue = Color(0xFF2563EB)

// ======================= tenant: listing button + booking sheet =======================

/** Listing detail: "Book a visit", or my upcoming visit with "View". Hidden when bookings are off. */
@Composable
fun ListingVisitButton(slots: VisitSlots?, onBook: () -> Unit, onView: () -> Unit) {
    val s = slots ?: return
    val mine = s.myUpcomingVisit
    if (mine != null) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.sdp).clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface)
                .clickable(role = Role.Button, onClick = onView).padding(12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.visit_your_visit, VisitRules.dateTime(mine.scheduledAt)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                Text(stringResource(statusLabel(mine.status, host = false)), color = statusColor(mine.status).first, fontFamily = Inter, fontSize = 12.ssp)
            }
            Text(stringResource(R.string.visit_view), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
        }
    } else if (s.enabled) {
        Spacer(Modifier.height(12.sdp))
        OutlineAction(stringResource(R.string.visit_book), R.drawable.ic_home_outline, modifier = Modifier.fillMaxWidth(), onClick = onBook)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSheet(b: BookingState, onIntent: (TenantVisitsIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(TenantVisitsIntent.CloseBooking) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp)) {
            Text(stringResource(R.string.visit_book), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Text(b.title, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.sdp))
            when {
                b.loading -> com.saferenttechnologies.android.presentation.common.ShimmerHost {
                    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) { repeat(5) { com.saferenttechnologies.android.presentation.common.Bone(Modifier.width(52.sdp).height(64.sdp), RoundedCornerShape(12.sdp)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) { repeat(3) { com.saferenttechnologies.android.presentation.common.Bone(Modifier.width(88.sdp).height(36.sdp), RoundedCornerShape(50)) } }
                    }
                }
                b.days.isEmpty() -> Text(b.error ?: stringResource(R.string.visit_no_slots), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                else -> {
                    // Date strip (only dates with free slots).
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalAlignment = Alignment.CenterVertically) {
                        b.days.forEach { d ->
                            val (wd, day, mon) = VisitRules.dayParts(d.date)
                            val sel = d.date == b.date
                            Column(
                                Modifier.width(56.sdp).clip(RoundedCornerShape(12.sdp)).background(if (sel) AuthColors.OrangeSoft else Color.Transparent)
                                    .border(if (sel) 2.sdp else 1.sdp, if (sel) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(12.sdp))
                                    .clickable(role = Role.Button) { onIntent(TenantVisitsIntent.PickDate(d.date)) }.padding(vertical = 8.sdp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(wd, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
                                Text(day, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
                                Text(mon, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
                            }
                        }
                        if (b.canLoadMore) {
                            Text(
                                stringResource(if (b.loadingMore) R.string.visit_loading else R.string.visit_more_dates), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                                modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !b.loadingMore, role = Role.Button) { onIntent(TenantVisitsIntent.LoadMoreDays) }.padding(10.sdp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.sdp))
                    // Slots of the chosen day (India time).
                    val slots = b.days.firstOrNull { it.date == b.date }?.slots.orEmpty()
                    slots.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
                            row.forEach { s -> Chip(VisitRules.time(s.start), selected = s.start == b.start, enabled = !b.saving, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(TenantVisitsIntent.PickSlot(s.start)) } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    // Flat / bed (when the listing has several).
                    if (b.places.size > 1) {
                        Label(stringResource(R.string.visit_which_place))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                            Pill(stringResource(R.string.visit_any_place), b.place == null) { onIntent(TenantVisitsIntent.PickPlace(null)) }
                            b.places.forEach { p -> Pill(p.label, b.place == p) { onIntent(TenantVisitsIntent.PickPlace(p)) } }
                        }
                    }
                    Label(stringResource(R.string.visit_visitors))
                    Stepper(b.visitors, 1, 10, !b.saving) { onIntent(TenantVisitsIntent.Visitors(it)) }
                    Spacer(Modifier.height(8.sdp))
                    Input(stringResource(R.string.visit_note), b.note, !b.saving, placeholder = stringResource(R.string.visit_note_hint), singleLine = false) { onIntent(TenantVisitsIntent.Note(it)) }
                    Spacer(Modifier.height(6.sdp))
                    Text(stringResource(if (b.approvalMode == "AUTO") R.string.visit_info_auto else R.string.visit_info_manual), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    b.error?.let { ErrorLine(if (it == TenantVisitsViewModel.PICK_SLOT) stringResource(R.string.visit_pick_slot) else it) }
                    Spacer(Modifier.height(12.sdp))
                    AuthPrimaryButton(stringResource(R.string.visit_book_go), enabled = b.start != null && !b.saving, loading = b.saving, onClick = { onIntent(TenantVisitsIntent.Book) }, fontSize = 15.ssp)
                }
            }
        }
    }
}

// ======================= tenant: My visits =======================

@Composable
fun MyVisitsScreen(state: TenantVisitsState, effects: Flow<TenantVisitsEffect>, onIntent: (TenantVisitsIntent) -> Unit, onApply: (propertyId: String) -> Unit) {
    BackHandler { onIntent(TenantVisitsIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) { effects.collect { e -> if (e is TenantVisitsEffect.Message && e.text.isNotBlank()) snackbar.showSnackbar(e.text) } }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(stringResource(R.string.visit_my_title)) { onIntent(TenantVisitsIntent.Close) }
            Row(Modifier.padding(horizontal = 16.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Chip(stringResource(R.string.visit_tab_upcoming), selected = !state.pastTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(TenantVisitsIntent.Tab(false)) }
                Chip(stringResource(R.string.visit_tab_past), selected = state.pastTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(TenantVisitsIntent.Tab(true)) }
            }
            val list = state.shown
            Box(Modifier.weight(1f)) {
                when {
                    list == null && state.error == null -> ListSkeleton(rows = 4, thumbnail = null, lines = 3)
                    list == null -> ErrorBox(state.error.orEmpty()) { onIntent(TenantVisitsIntent.Refresh) }
                    list.isEmpty() -> Empty(stringResource(if (state.pastTab) R.string.visit_none_past else R.string.visit_none_upcoming))
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                        list.forEach { v -> TenantVisitCard(v, state.focusId == v.id, state.busy, onIntent, onApply) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }
    state.cancelFor?.let {
        ReasonDialog(stringResource(R.string.visit_cancel_title), state.cancelReason, state.busy, { r -> onIntent(TenantVisitsIntent.CancelReason(r)) }, { onIntent(TenantVisitsIntent.AskCancel(null)) }) {
            onIntent(TenantVisitsIntent.ConfirmCancel)
        }
    }
    state.feedback?.let { FeedbackSheet(it, state.busy, onIntent) }
}

@Composable
private fun TenantVisitCard(v: Visit, focused: Boolean, busy: Boolean, onIntent: (TenantVisitsIntent) -> Unit, onApply: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(if (focused) 2.sdp else 1.sdp, if (focused) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(4.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.propertyTitle, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(v.status, host = false)
        }
        Text(VisitRules.dateTime(v.scheduledAt), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        if (v.place.isNotBlank()) Text(v.place, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        // Confirmed: address, directions, host, instructions.
        if (v.status == "SCHEDULED" || v.status == "COMPLETED") {
            if (v.address.isNotBlank()) Text(v.address, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(top = 4.sdp)) {
                if (v.latitude != null && v.longitude != null) {
                    OutlineAction(stringResource(R.string.visit_directions), R.drawable.ic_my_location, modifier = Modifier.weight(1f), compact = true) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${v.latitude},${v.longitude}"))) }
                    }
                }
                if (v.hostPhone.isNotBlank()) {
                    OutlineAction(stringResource(R.string.visit_call_host, v.hostName.ifBlank { stringResource(R.string.visit_host) }), R.drawable.ic_call, modifier = Modifier.weight(1f), compact = true) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${v.hostPhone}"))) }
                    }
                }
            }
            if (v.instructions.isNotBlank()) Note(v.instructions)
        }
        // Why it didn't happen.
        reasonLine(v, host = false)?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        v.feedback?.let { Text(stringResource(R.string.visit_your_feedback, "★".repeat(it.rating)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(top = 4.sdp)) {
            if (v.canCancel) OutlineAction(stringResource(R.string.visit_cancel), enabled = !busy, modifier = Modifier.weight(1f), compact = true) { onIntent(TenantVisitsIntent.AskCancel(v.id)) }
            if (v.canGiveFeedback) OutlineAction(stringResource(R.string.visit_how_was), enabled = !busy, modifier = Modifier.weight(1f), compact = true) { onIntent(TenantVisitsIntent.OpenFeedback(v.id)) }
            if (v.feedback?.interested == true && v.propertyId.isNotBlank()) OutlineAction(stringResource(R.string.visit_apply), modifier = Modifier.weight(1f), compact = true) { onApply(v.propertyId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSheet(f: FeedbackForm, busy: Boolean, onIntent: (TenantVisitsIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(TenantVisitsIntent.OpenFeedback(null)) },
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            Text(stringResource(R.string.visit_how_was), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                (1..5).forEach { n ->
                    Text(
                        if (n <= f.rating) "★" else "☆", color = if (n <= f.rating) Amber else AuthColors.Muted, fontSize = 32.ssp,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = !busy, role = Role.Button) { onIntent(TenantVisitsIntent.EditFeedback(f.copy(rating = n))) }.padding(4.sdp),
                    )
                }
            }
            Label(stringResource(R.string.visit_interested))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Chip(stringResource(R.string.inv_yes), selected = f.interested == true, enabled = !busy, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(TenantVisitsIntent.EditFeedback(f.copy(interested = true))) }
                Chip(stringResource(R.string.inv_no), selected = f.interested == false, enabled = !busy, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(TenantVisitsIntent.EditFeedback(f.copy(interested = false))) }
            }
            Input(stringResource(R.string.visit_comment), f.comment, !busy, singleLine = false) { onIntent(TenantVisitsIntent.EditFeedback(f.copy(comment = it))) }
            AuthPrimaryButton(stringResource(R.string.visit_send_feedback), enabled = f.rating in 1..5 && f.interested != null && !busy, loading = busy, onClick = { onIntent(TenantVisitsIntent.SendFeedback) }, fontSize = 15.ssp)
        }
    }
}

// ======================= host: visits + availability =======================

@Composable
fun HostVisitsScreen(state: HostVisitsState, effects: Flow<HostVisitsEffect>, onIntent: (HostVisitsIntent) -> Unit) {
    BackHandler { onIntent(HostVisitsIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) { effects.collect { e -> if (e is HostVisitsEffect.Message && e.text.isNotBlank()) snackbar.showSnackbar(e.text) } }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(stringResource(R.string.visit_host_title), state.title) { onIntent(HostVisitsIntent.Close) }
            if (state.propertyId != null) {
                Row(Modifier.padding(horizontal = 16.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    Chip(stringResource(R.string.visit_tab_requests), selected = !state.availabilityTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(HostVisitsIntent.Tab(false)) }
                    Chip(stringResource(R.string.visit_tab_availability), selected = state.availabilityTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(HostVisitsIntent.Tab(true)) }
                }
            }
            if (state.availabilityTab) AvailabilityEditor(state, onIntent) else HostList(state, onIntent)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }
    state.ask?.let { a ->
        val title = stringResource(
            when (a.action) {
                "reject" -> R.string.visit_decline_title
                "complete" -> R.string.visit_done_title
                else -> R.string.visit_cancel_title
            },
        )
        ReasonDialog(title, a.text, state.busy, { onIntent(HostVisitsIntent.Ask(a.copy(text = it))) }, { onIntent(HostVisitsIntent.Ask(null)) }, optional = a.action == "complete") {
            onIntent(HostVisitsIntent.SendAsk)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.HostList(state: HostVisitsState, onIntent: (HostVisitsIntent) -> Unit) {
    val items = state.items
    Box(Modifier.weight(1f)) {
        when {
            items == null && state.error == null -> ListSkeleton(rows = 4, thumbnail = null, lines = 3)
            items == null -> ErrorBox(state.error.orEmpty()) { onIntent(HostVisitsIntent.Refresh) }
            items.isEmpty() -> Empty(stringResource(R.string.visit_none_host))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                listOf(
                    R.string.visit_section_confirm to VisitRules.needsConfirmation(items),
                    R.string.visit_section_upcoming to VisitRules.upcoming(items),
                    R.string.visit_section_past to VisitRules.past(items),
                ).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        Text(stringResource(title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp, modifier = Modifier.padding(top = 6.sdp))
                        list.forEach { v -> HostVisitCard(v, state.propertyId == null, state.focusId == v.id, state.busy, onIntent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostVisitCard(v: Visit, showProperty: Boolean, focused: Boolean, busy: Boolean, onIntent: (HostVisitsIntent) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(if (focused) 2.sdp else 1.sdp, if (focused) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(4.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(VisitRules.dateTime(v.scheduledAt), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            StatusChip(v.status, host = true)
        }
        if (showProperty && v.propertyTitle.isNotBlank()) Text(v.propertyTitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(v.visitorName, androidx.compose.ui.res.pluralStringResource(R.plurals.visit_people, v.visitors, v.visitors)).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f),
            )
            if (v.visitorPhone.isNotBlank()) {
                Box(
                    Modifier.size(34.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(role = Role.Button) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${v.visitorPhone}"))) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_call), stringResource(R.string.inv_call), tint = AuthColors.OrangeText, modifier = Modifier.size(16.sdp)) }
            }
        }
        if (v.note.isNotBlank()) Note(v.note)
        reasonLine(v, host = true)?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        if (v.outcome.isNotBlank()) Text(stringResource(R.string.visit_outcome_value, v.outcome), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        v.feedback?.let { fb ->
            Text(
                listOfNotNull("★".repeat(fb.rating) + "☆".repeat((5 - fb.rating).coerceAtLeast(0)), if (fb.interested) stringResource(R.string.visit_interested_tag) else null).joinToString("  "),
                color = Amber, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
            )
            if (fb.comment.isNotBlank()) Text(fb.comment, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        // Buttons per status (the server checks again).
        val buttons = buildList {
            if (VisitRules.canConfirm(v)) add(Triple(R.string.visit_confirm, true) { onIntent(HostVisitsIntent.Confirm(v.id)) })
            if (VisitRules.canReject(v)) add(Triple(R.string.visit_decline, false) { onIntent(HostVisitsIntent.Ask(VisitAsk(v.id, "reject"))) })
            if (VisitRules.canComplete(v)) {
                add(Triple(R.string.visit_done, true) { onIntent(HostVisitsIntent.Ask(VisitAsk(v.id, "complete"))) })
                add(Triple(R.string.visit_no_show, false) { onIntent(HostVisitsIntent.NoShow(v.id)) })
            } else if (v.status == "SCHEDULED") {
                add(Triple(R.string.visit_cancel, false) { onIntent(HostVisitsIntent.Ask(VisitAsk(v.id, "cancel"))) })
            }
        }
        if (buttons.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(top = 6.sdp)) {
                buttons.forEach { (label, _, action) -> OutlineAction(stringResource(label), enabled = !busy, modifier = Modifier.weight(1f), compact = true, onClick = action) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AvailabilityEditor(state: HostVisitsState, onIntent: (HostVisitsIntent) -> Unit) {
    val data = state.settingsData
    if (data == null) {
        Box(Modifier.weight(1f)) { ListSkeleton(rows = 5, thumbnail = null, lines = 2) }
        return
    }
    val f = state.form
    fun set(x: com.saferenttechnologies.android.domain.model.VisitSettings) = onIntent(HostVisitsIntent.EditSettings(x))
    val edit = !state.savingSettings
    var timeFor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var dayMenu by remember { mutableStateOf<Int?>(null) }
    var blockPicker by remember { mutableStateOf(false) }
    @Composable fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) state.problems[path]?.let { problemText(it) } else null

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.visit_take_bookings), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            Switch(checked = f.enabled, onCheckedChange = { set(f.copy(enabled = it)) }, enabled = edit, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
        }
        Label(stringResource(R.string.visit_approval))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Chip(stringResource(R.string.visit_manual), selected = f.approvalMode == "MANUAL", enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(f.copy(approvalMode = "MANUAL")) }
            Chip(stringResource(R.string.visit_auto), selected = f.approvalMode == "AUTO", enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(f.copy(approvalMode = "AUTO")) }
        }
        Label(stringResource(R.string.visit_slot_length))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            VisitRules.SLOT_LENGTHS.forEach { m -> Chip(stringResource(R.string.visit_minutes, m), selected = f.slotMinutes == m, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(f.copy(slotMinutes = m)) } }
        }
        StepRow(stringResource(R.string.visit_per_slot), f.capacityPerSlot, 1, 10, edit) { set(f.copy(capacityPerSlot = it)) }
        StepRow(stringResource(R.string.visit_notice_hours, f.minNoticeHours), f.minNoticeHours, 0, 72, edit) { set(f.copy(minNoticeHours = it)) }
        StepRow(stringResource(R.string.visit_days_ahead, f.maxDaysAhead), f.maxDaysAhead, 1, 60, edit) { set(f.copy(maxDaysAhead = it)) }

        // Who shows the property
        Label(stringResource(R.string.visit_who_shows))
        HostDropdown(data.hosts, f.hostId, edit) { set(f.copy(hostId = it)) }

        // Weekly windows
        Label(stringResource(R.string.visit_days_times))
        f.weekly.forEachIndexed { i, w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Pill(stringResource(dayLabel(w.day)), true) { if (edit) dayMenu = i }
                    DropdownMenu(expanded = dayMenu == i, onDismissRequest = { dayMenu = null }, modifier = Modifier.background(AuthColors.Card)) {
                        (0..6).forEach { d -> DropdownMenuItem(text = { Text(stringResource(dayLabel(d)), fontFamily = Inter) }, onClick = { dayMenu = null; onIntent(HostVisitsIntent.EditWindow(i, w.copy(day = d))) }) }
                    }
                }
                Spacer(Modifier.width(8.sdp))
                Pill(hhmmLabel(w.start), false) { if (edit) timeFor = i to true }
                Text(" to ", color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                Pill(hhmmLabel(w.end), false) { if (edit) timeFor = i to false }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(34.sdp).clip(CircleShape).clickable(enabled = edit, role = Role.Button) { onIntent(HostVisitsIntent.RemoveWindow(i)) }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.reg_delete), tint = AuthColors.Error, modifier = Modifier.size(16.sdp))
                }
            }
            e("weekly.$i")?.let { ErrorLine(it) }
        }
        e("weekly")?.let { ErrorLine(it) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            Pill(stringResource(R.string.visit_add_time), false) { if (edit && f.weekly.size < VisitRules.MAX_WINDOWS) onIntent(HostVisitsIntent.AddWindow(WeeklyWindow(6, "10:00", "13:00"))) }
            Pill(stringResource(R.string.visit_preset_weekends), false) { if (edit) onIntent(HostVisitsIntent.Preset(VisitRules.weekends())) }
            Pill(stringResource(R.string.visit_preset_evenings), false) { if (edit) onIntent(HostVisitsIntent.Preset(VisitRules.weekdayEvenings())) }
        }

        // No visits on
        Label(stringResource(R.string.visit_blocked))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            f.blockedDates.forEach { d -> Pill(prettyDate(d) + "  ✕", true) { if (edit) onIntent(HostVisitsIntent.UnblockDate(d)) } }
            Pill(stringResource(R.string.visit_add_date), false) { if (edit) blockPicker = true }
        }

        Input(stringResource(R.string.visit_instructions), f.instructions, edit, singleLine = false, error = e("instructions"), placeholder = stringResource(R.string.visit_instructions_hint)) { set(f.copy(instructions = it)) }
        Text(stringResource(R.string.visit_instructions_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(8.sdp))
    }
    Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
        AuthPrimaryButton(stringResource(R.string.visit_save), enabled = state.settingsChanged && !state.savingSettings, loading = state.savingSettings, onClick = { onIntent(HostVisitsIntent.SaveSettings) }, fontSize = 15.ssp)
    }

    timeFor?.let { (i, start) ->
        val w = f.weekly.getOrNull(i)
        if (w != null) {
            TimeDialog(if (start) w.start else w.end, onDismiss = { timeFor = null }) { t ->
                timeFor = null
                onIntent(HostVisitsIntent.EditWindow(i, if (start) w.copy(start = t) else w.copy(end = t)))
            }
        }
    }
    if (blockPicker) FutureDatePicker(onDismiss = { blockPicker = false }) { d -> blockPicker = false; onIntent(HostVisitsIntent.BlockDate(d)) }
}

// ======================= small parts =======================

@Composable
private fun TopBar(title: String, subtitle: String = "", onBack: () -> Unit) {
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
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, modifier = Modifier.padding(top = 6.sdp, bottom = 4.sdp))

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun Note(text: String) = Text(
    text, color = AuthColors.Ink, fontFamily = Inter, fontSize = 12.ssp,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).padding(10.sdp),
)

@Composable
private fun Empty(text: String) = Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
}

@Composable
private fun ErrorBox(text: String, onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh, onClick = onRetry)
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (selected) AuthColors.OrangeText else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) AuthColors.OrangeSoft else Color.Transparent)
            .border(1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.sdp, vertical = 7.sdp),
    )
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && value > min) { onChange(value - 1) }
        Text(value.toString(), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.padding(horizontal = 12.sdp))
        StepButton("+", enabled && value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepRow(label: String, value: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
        Stepper(value, min, max, enabled, onChange)
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.sdp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp)
    }
}

@Composable
private fun Input(label: String, value: String, enabled: Boolean, singleLine: Boolean = true, error: String? = null, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        placeholder = placeholder?.let { p -> { Text(p, fontFamily = Inter) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, enabled = enabled, isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HostDropdown(hosts: List<com.saferenttechnologies.android.domain.model.VisitHost>, selected: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = hosts.firstOrNull { it.id == selected }?.let { "${it.fullName} (${roleLabel(it.role)})" } ?: stringResource(R.string.visit_host_auto)
    Box {
        Pill(label, true) { if (enabled) open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(AuthColors.Card)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.visit_host_auto), fontFamily = Inter) }, onClick = { open = false; onPick(null) })
            hosts.forEach { h -> DropdownMenuItem(text = { Text("${h.fullName} (${roleLabel(h.role)})", fontFamily = Inter) }, onClick = { open = false; onPick(h.id) }) }
        }
    }
}

@Composable
private fun roleLabel(role: String) = stringResource(
    when (role) {
        "OWNER" -> R.string.visit_role_owner
        "MANAGER" -> R.string.visit_role_manager
        else -> R.string.visit_role_agent
    },
)

/** Required reason (3–300), or an optional note for "Done". */
@Composable
private fun ReasonDialog(title: String, text: String, busy: Boolean, onText: (String) -> Unit, onDismiss: () -> Unit, optional: Boolean = false, onSend: () -> Unit) {
    val ok = optional || VisitRules.reasonOk(text)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = {
            Input(stringResource(if (optional) R.string.visit_outcome else R.string.visit_reason), text, !busy, singleLine = false) { onText(it.take(300)) }
        },
        confirmButton = { TextButton(enabled = ok && !busy, onClick = onSend) { Text(stringResource(R.string.visit_send), color = if (ok && !busy) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        containerColor = AuthColors.Card,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(current: String, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val (h, m) = current.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 10) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val st = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(String.format(java.util.Locale.US, "%02d:%02d", st.hour, st.minute)) }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        text = { TimePicker(state = st) },
        containerColor = AuthColors.Card,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FutureDatePicker(onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val today = SimpleDate.today()
    val utc = TimeZone.getTimeZone("UTC")
    val todayMs = Calendar.getInstance(utc).apply { clear(); set(today.year, today.month - 1, today.day) }.timeInMillis
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayMs,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMs
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

/** "10:00" → "10:00 AM". */
private fun hhmmLabel(t: String): String {
    val (h, m) = t.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val ap = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, ap)
}

private fun dayLabel(d: Int) = when (d) {
    0 -> R.string.visit_d_sun
    1 -> R.string.visit_d_mon
    2 -> R.string.visit_d_tue
    3 -> R.string.visit_d_wed
    4 -> R.string.visit_d_thu
    5 -> R.string.visit_d_fri
    else -> R.string.visit_d_sat
}

@Composable
private fun StatusChip(status: String, host: Boolean) {
    val (fg, bg) = statusColor(status)
    Text(
        stringResource(statusLabel(status, host)), color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 3.sdp),
    )
}

@Composable
private fun statusColor(s: String): Pair<Color, Color> = when (s) {
    "REQUESTED" -> Amber to Amber.copy(alpha = 0.12f)
    "SCHEDULED" -> Blue to Blue.copy(alpha = 0.1f)
    "COMPLETED" -> AuthColors.GreenText to AuthColors.GreenBg
    "REJECTED", "NO_SHOW" -> AuthColors.Error to AuthColors.Error.copy(alpha = 0.1f)
    else -> AuthColors.Body to AuthColors.Surface
}

private fun statusLabel(s: String, host: Boolean) = when (s) {
    "REQUESTED" -> if (host) R.string.visit_s_waiting_you else R.string.visit_s_waiting
    "SCHEDULED" -> R.string.visit_s_confirmed
    "REJECTED" -> R.string.visit_s_declined
    "COMPLETED" -> R.string.visit_s_done
    "NO_SHOW" -> R.string.visit_s_no_show
    else -> R.string.visit_s_cancelled
}

/** "Cancelled by owner: …", "Declined: …", "Not confirmed before the visit time." */
@Composable
private fun reasonLine(v: Visit, host: Boolean): String? = when (v.status) {
    "REJECTED" -> v.rejectReason.takeIf { it.isNotBlank() }?.let { stringResource(R.string.visit_declined_reason, it) }
    "CANCELLED" -> {
        val who = when (v.cancelledBy) {
            "YOU" -> stringResource(R.string.visit_by_you)
            "VISITOR" -> stringResource(R.string.visit_by_visitor)
            "OWNER" -> stringResource(if (host) R.string.visit_by_you else R.string.visit_by_owner)
            else -> null
        }
        when {
            v.cancelReason.isBlank() -> null
            who == null -> v.cancelReason
            else -> "$who: ${v.cancelReason}"
        }
    }
    else -> null
}

@Composable
private fun problemText(p: VisitRules.Problem) = stringResource(
    when (p) {
        VisitRules.Problem.NO_WINDOWS -> R.string.visit_err_no_windows
        VisitRules.Problem.TOO_SHORT -> R.string.visit_err_short
        VisitRules.Problem.OVERLAP -> R.string.visit_err_overlap
        VisitRules.Problem.BAD_TIME -> R.string.visit_err_time
        VisitRules.Problem.INSTRUCTIONS -> R.string.visit_err_instructions
        VisitRules.Problem.TOO_MANY -> R.string.visit_err_too_many
    },
)
