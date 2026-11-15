package com.saferenttechnologies.android.presentation.requests

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import com.saferenttechnologies.android.presentation.common.rememberIdleHint
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.RelativeTime
import com.saferenttechnologies.android.domain.model.RequestEvent
import com.saferenttechnologies.android.domain.model.RequestRules
import com.saferenttechnologies.android.domain.model.RequestTab
import com.saferenttechnologies.android.domain.model.ServiceRequest
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.DetailSkeleton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.notifications.relativeTimeText
import com.saferenttechnologies.android.presentation.notifications.startOfDay
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipGrid
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val Blue = Color(0xFF2563EB)

private fun isoMillis(iso: String?): Long? = iso?.takeIf { it.isNotBlank() }?.let { s ->
    listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull { p ->
        runCatching { SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time }.getOrNull()
    }
}

/** "2 h ago", "Yesterday", "3 Nov 2026". */
@Composable
private fun ago(iso: String?): String {
    val millis = isoMillis(iso) ?: return ""
    val now = System.currentTimeMillis()
    return relativeTimeText(RelativeTime.of(millis, now, startOfDay(now)))
}

private fun isToday(iso: String?): Boolean = isoMillis(iso)?.let { it >= startOfDay(System.currentTimeMillis()) } == true

fun typeIcon(type: String): Int = when (type.uppercase()) {
    "MAINTENANCE" -> R.drawable.ic_edit
    "SERVICE" -> R.drawable.ic_star
    "COMPLAINT" -> R.drawable.ic_flag
    else -> R.drawable.ic_help
}

@Composable
private fun statusColor(status: String): Color = when (status.uppercase()) {
    "IN_PROGRESS", "ASSIGNED", "ACCEPTED" -> Blue
    "RESOLVED", "COMPLETED" -> AuthColors.Green
    "CANCELLED", "CLOSED", "DECLINED" -> AuthColors.Muted
    else -> AuthColors.Body
}

@Composable
private fun StatusChip(r: ServiceRequest) = StatusPill(r.statusLabel.ifBlank { optionLabel(r.status) }, statusColor(r.status))

/** Tenant: raise / my requests / detail. Landlord: needs-action list / detail + update panel. */
@Composable
fun RequestsScreen(state: RequestsState, effects: Flow<RequestsEffect>, onIntent: (RequestsIntent) -> Unit) {
    BackHandler { onIntent(RequestsIntent.Back) }
    val context = LocalContext.current
    val unreadable = stringResource(R.string.pay_proof_unreadable)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            if (e is RequestsEffect.Message) {
                Toast.makeText(context, if (e.text == RequestsViewModel.PHOTO_UNREADABLE) unreadable else e.text, Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(
            when (state.mode) {
                RequestsMode.RAISE -> stringResource(R.string.req_raise_title)
                RequestsMode.DETAIL -> stringResource(R.string.req_detail_title)
                RequestsMode.LIST -> stringResource(if (state.landlord) R.string.req_title_landlord else R.string.req_title_tenant)
            },
        ) { onIntent(RequestsIntent.Back) }
        when (state.mode) {
            RequestsMode.LIST -> ListContent(state, onIntent)
            RequestsMode.RAISE -> RaiseContent(state, onIntent)
            RequestsMode.DETAIL -> DetailContent(state, onIntent)
        }
    }
    state.sheet?.let { RequestActionSheet(state, it, onIntent) }
    state.viewer?.let { (urls, start) -> PhotoViewer(urls, start) { onIntent(RequestsIntent.CloseViewer) } }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
        Spacer(Modifier.width(12.sdp))
        Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
    }
}

// ---------------- list ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ListContent(state: RequestsState, onIntent: (RequestsIntent) -> Unit) {
    // Full labels ("Needs action (3)"), no "…": the row scrolls sideways when it doesn't fit.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.sdp, vertical = 4.sdp),
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        state.tabs.forEach { t ->
            val label = when (t) {
                RequestTab.NEEDS_ACTION -> stringResource(R.string.req_tab_needs_action) + if (state.summary.needsAction > 0) " (${state.summary.needsAction})" else ""
                RequestTab.ACTIVE -> stringResource(if (state.landlord) R.string.req_tab_open else R.string.req_tab_active)
                RequestTab.RESOLVED -> stringResource(R.string.req_tab_resolved)
                RequestTab.ALL -> stringResource(R.string.filter_all)
            }
            Chip(label, selected = t == state.tab, modifier = Modifier.widthIn(min = 72.sdp), maxLines = 1) { onIntent(RequestsIntent.SelectTab(t)) }
        }
    }
    if (!state.landlord) {
        OutlineAction(stringResource(R.string.req_raise_title), R.drawable.ic_add, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 6.sdp)) {
            onIntent(RequestsIntent.StartRaise)
        }
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(RequestsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
        when {
            state.loading && state.items.isEmpty() -> ListSkeleton(rows = 5, thumbnail = 44.sdp, roundThumb = true)
            state.items.isEmpty() -> Text(
                state.error ?: stringResource(R.string.req_empty),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
            )
            else -> {
                val listState = rememberLazyListState()
                val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
                LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(RequestsIntent.LoadMore) }
                LazyColumn(state = listState, contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { r -> RequestCard(r, state.landlord) { onIntent(RequestsIntent.Select(r.id)) } }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(r: ServiceRequest, landlord: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, if (landlord && r.needsAction) AuthColors.Orange.copy(alpha = 0.5f) else AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.sdp),
    ) {
        Box {
            Box(Modifier.size(42.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(typeIcon(r.type)), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
            }
            // Landlord's to-do: a dot on the icon.
            if (landlord && r.needsAction) Box(Modifier.align(Alignment.TopEnd).size(10.sdp).clip(CircleShape).background(AuthColors.Error).border(1.5.sdp, AuthColors.Card, CircleShape))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(r.title.ifBlank { r.categoryLabel }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(if (landlord) r.raisedByName else "", r.propertyTitle, r.place).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (r.lastRemark.isNotBlank()) {
                Text("“${r.lastRemark}”", color = AuthColors.Ink, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.req_updated, ago(r.updatedAt.ifBlank { r.createdAt })), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                if (landlord && isToday(r.lastReminderAt)) {
                    Spacer(Modifier.width(6.sdp))
                    StatusPill(stringResource(R.string.req_reminded_today), Amber)
                }
            }
        }
        Spacer(Modifier.width(8.sdp))
        StatusChip(r)
    }
}

// ---------------- raise ----------------

@Composable
private fun ColumnScope.RaiseContent(state: RequestsState, onIntent: (RequestsIntent) -> Unit) {
    val f = state.raise
    val types = state.options?.types.orEmpty()
    val type = types.firstOrNull { it.key == f.type }
    val picker = rememberPhotoPicker(RequestRules.MAX_PHOTOS - f.photos.size) { onIntent(RequestsIntent.RaiseAddPhotos(it)) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.sdp)) {
        if (state.rentalTitle.isNotBlank()) {
            Text(stringResource(R.string.req_for_rental, state.rentalTitle), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(10.sdp))
        }
        Heading(stringResource(R.string.req_q_what))
        if (types.isEmpty()) ListSkeleton(rows = 2, thumbnail = null)
        ChipGrid(types.map { it.key }, columns = 2) { key, mod ->
            val t = types.first { it.key == key }
            Column(
                mod
                    .clip(RoundedCornerShape(14.sdp))
                    .background(if (f.type == key) AuthColors.OrangeSoft else AuthColors.Field)
                    .border(1.sdp, if (f.type == key) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
                    .clickable(enabled = !state.busy, role = Role.RadioButton) { onIntent(RequestsIntent.RaiseType(key)) }
                    .padding(14.sdp),
            ) {
                Icon(painterResource(typeIcon(key)), null, tint = if (f.type == key) AuthColors.OrangeText else AuthColors.Body, modifier = Modifier.size(22.sdp))
                Spacer(Modifier.height(6.sdp))
                Text(t.label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            }
        }
        if (f.showErrors && f.type == null) FieldErr(stringResource(R.string.req_err_type))

        if (type != null) {
            Spacer(Modifier.height(16.sdp))
            Heading(stringResource(R.string.req_q_category))
            ChipGrid(type.categories.map { it.key }, columns = 2) { key, mod ->
                Chip(type.categories.first { it.key == key }.label, selected = f.category == key, enabled = !state.busy, modifier = mod, maxLines = 2) {
                    onIntent(RequestsIntent.RaiseCategory(key))
                }
            }
            if (f.showErrors && f.category == null) FieldErr(stringResource(R.string.req_err_category))
        }

        Spacer(Modifier.height(16.sdp))
        Heading(stringResource(R.string.req_q_describe))
        val describeHint = rememberIdleHint()
        OutlinedTextField(
            value = f.description,
            onValueChange = { onIntent(RequestsIntent.RaiseDescription(it)) },
            placeholder = describeHint.placeholder(stringResource(placeholderFor(f.type))),
            interactionSource = describeHint.source,
            isError = f.showErrors && !RequestRules.descriptionOk(f.description),
            supportingText = {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (f.showErrors && !RequestRules.descriptionOk(f.description)) stringResource(R.string.lease_reason_min, RequestRules.DESCRIPTION_MIN) else "",
                        fontFamily = Inter,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${f.description.length} / ${RequestRules.DESCRIPTION_MAX}", fontFamily = Inter)
                }
            },
            minLines = 4,
            maxLines = 8,
            enabled = !state.busy,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.sdp))
        Heading(stringResource(R.string.req_q_priority))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            (state.options?.priorities ?: listOf("LOW", "MEDIUM", "HIGH", "URGENT")).forEach { p ->
                Chip(optionLabel(p), selected = f.priority == p, enabled = !state.busy, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(RequestsIntent.RaisePriority(p)) }
            }
        }

        Spacer(Modifier.height(16.sdp))
        Heading(stringResource(R.string.req_q_photos, f.photos.size, RequestRules.MAX_PHOTOS))
        PhotoStrip(f.photos, onRemove = { onIntent(RequestsIntent.RaiseRemovePhoto(it)) })
        if (f.photos.isNotEmpty()) Spacer(Modifier.height(8.sdp))
        AddPhotoButtons(picker, enabled = !state.busy && f.photos.size < RequestRules.MAX_PHOTOS)
        Spacer(Modifier.height(20.sdp))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 10.sdp)) {
        AuthPrimaryButton(
            text = stringResource(R.string.req_send),
            enabled = state.canSend,
            loading = state.busy,
            onClick = { onIntent(RequestsIntent.RaiseSend) },
            fontSize = 15.ssp,
        )
    }
}

private fun placeholderFor(type: String?): Int = when (type) {
    "SERVICE" -> R.string.req_ph_service
    "COMPLAINT" -> R.string.req_ph_complaint
    "OTHER" -> R.string.req_ph_other
    else -> R.string.req_ph_maintenance
}

@Composable
private fun Heading(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp, modifier = Modifier.padding(bottom = 8.sdp))
}

@Composable
private fun FieldErr(text: String) {
    Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
}

// ---------------- detail ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.DetailContent(state: RequestsState, onIntent: (RequestsIntent) -> Unit) {
    val r = state.detail
    if (r == null) {
        if (state.detailLoading) DetailSkeleton(hero = false)
        return
    }
    val urlOf = r.photos.associate { it.id to absoluteUrl(it.url) }
    PullToRefreshBox(isRefreshing = state.detailLoading, onRefresh = { onIntent(RequestsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.sdp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(46.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                    Icon(painterResource(typeIcon(r.type)), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
                }
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f)) {
                    Text(r.title.ifBlank { r.categoryLabel }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 19.ssp)
                    Text(listOf(r.typeLabel, r.categoryLabel).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                }
                StatusChip(r)
            }
            Spacer(Modifier.height(10.sdp))
            Info(listOf(r.propertyTitle, r.place).filter { it.isNotBlank() }.joinToString(" · "))
            Info(
                listOfNotNull(
                    if (state.landlord && r.raisedByName.isNotBlank()) stringResource(R.string.req_raised_by, r.raisedByName) else null,
                    r.createdAt.takeIf { it.isNotBlank() }?.let { stringResource(R.string.req_raised_on, prettyDate(it)) },
                    stringResource(R.string.req_priority_x, optionLabel(r.priority)),
                ).joinToString(" · "),
            )

            Spacer(Modifier.height(14.sdp))
            Text(
                r.description,
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontSize = 14.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
            )
            // Photos of the request itself (update photos sit on their timeline line).
            val main = r.photos.filter { it.updateId == null }.map { absoluteUrl(it.url) }
            if (main.isNotEmpty()) {
                Spacer(Modifier.height(10.sdp))
                PhotoStrip(main, onOpen = { onIntent(RequestsIntent.ViewPhotos(main, it)) })
            }

            // Timeline
            Spacer(Modifier.height(18.sdp))
            Heading(stringResource(R.string.req_timeline))
            r.timeline.forEachIndexed { i, e -> TimelineRow(e, last = i == r.timeline.lastIndex, landlord = state.landlord, urlOf = urlOf, onIntent = onIntent) }

            // Landlord update panel
            if (state.landlord && r.can.update) {
                Spacer(Modifier.height(16.sdp))
                UpdatePanel(state, r, onIntent)
            }
            Spacer(Modifier.height(16.sdp))
        }
    }
    ActionBar(state, r, onIntent)
}

@Composable
private fun Info(text: String) {
    if (text.isBlank()) return
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(top = 2.sdp))
}

/** "Landlord · Status → In progress", remark, photos, time — with a dot + line on the left. */
@Composable
private fun TimelineRow(e: RequestEvent, last: Boolean, landlord: Boolean, urlOf: Map<String, String>, onIntent: (RequestsIntent) -> Unit) {
    val who = when (e.byRole) {
        "TENANT" -> stringResource(if (landlord) R.string.lease_the_tenant else R.string.req_you).replaceFirstChar { it.uppercase() }
        "LANDLORD" -> stringResource(if (landlord) R.string.req_you else R.string.lease_the_landlord).replaceFirstChar { it.uppercase() }
        "STAFF" -> stringResource(R.string.req_staff)
        else -> e.byName.ifBlank { optionLabel(e.byRole) }
    }
    val what = when (e.kind) {
        "CREATED" -> stringResource(R.string.req_ev_created)
        "STATUS" -> stringResource(R.string.req_ev_status, e.toStatusLabel)
        "REMARK" -> stringResource(R.string.req_ev_remark)
        "REMINDER" -> stringResource(if (landlord) R.string.req_ev_reminder_landlord else R.string.req_ev_reminder_tenant)
        "RESOLVED" -> stringResource(R.string.req_ev_resolved)
        "REOPENED" -> stringResource(R.string.req_ev_reopened)
        "CANCELLED" -> stringResource(R.string.req_ev_cancelled)
        else -> optionLabel(e.kind)
    }
    val color = when (e.kind) {
        "RESOLVED" -> AuthColors.Green
        "REOPENED", "REMINDER" -> Amber
        "CANCELLED" -> AuthColors.Muted
        "STATUS" -> Blue
        else -> AuthColors.Orange
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(18.sdp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.sdp))
            Box(Modifier.size(10.sdp).clip(CircleShape).background(color))
            if (!last) Box(Modifier.width(2.sdp).weight(1f).background(AuthColors.Border))
        }
        Spacer(Modifier.width(10.sdp))
        Column(Modifier.weight(1f).padding(bottom = 14.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$who · $what", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                Text(ago(e.at), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
            }
            if (e.remark.isNotBlank()) Text(e.remark, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(top = 2.sdp))
            val photos = e.photoIds.mapNotNull { urlOf[it] }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(6.sdp))
                PhotoStrip(photos, onOpen = { onIntent(RequestsIntent.ViewPhotos(photos, it)) })
            }
        }
    }
}

/** Landlord: Pending / In progress / Resolved + remark + photos → "Send update". */
@Composable
private fun UpdatePanel(state: RequestsState, r: ServiceRequest, onIntent: (RequestsIntent) -> Unit) {
    val statuses = state.options?.landlordStatuses?.ifEmpty { null }
        ?: listOf("OPEN" to stringResource(R.string.req_st_pending), "IN_PROGRESS" to stringResource(R.string.req_st_progress), "RESOLVED" to stringResource(R.string.req_st_resolved))
    val selected = state.updStatus ?: r.status
    val picker = rememberPhotoPicker(RequestRules.MAX_PHOTOS - state.updPhotos.size) { onIntent(RequestsIntent.UpdAddPhotos(it)) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp)) {
        Heading(stringResource(R.string.req_update_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            statuses.forEach { (key, label) ->
                Chip(label, selected = selected == key, enabled = !state.busy, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(RequestsIntent.UpdStatus(key)) }
            }
        }
        Spacer(Modifier.height(10.sdp))
        // No floating label: the "e.g." example shows in the empty box and goes away on tap.
        val remarkHint = rememberIdleHint()
        OutlinedTextField(
            value = state.updRemark,
            onValueChange = { onIntent(RequestsIntent.UpdRemark(it)) },
            placeholder = remarkHint.placeholder(stringResource(R.string.req_remark_ph)),
            interactionSource = remarkHint.source,
            minLines = 2,
            maxLines = 5,
            enabled = !state.busy,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.sdp))
        PhotoStrip(state.updPhotos, onRemove = { onIntent(RequestsIntent.UpdRemovePhoto(it)) })
        if (state.updPhotos.isNotEmpty()) Spacer(Modifier.height(8.sdp))
        AddPhotoButtons(picker, enabled = !state.busy && state.updPhotos.size < RequestRules.MAX_PHOTOS)
        Spacer(Modifier.height(12.sdp))
        AuthPrimaryButton(text = stringResource(R.string.req_send_update), enabled = state.canSendUpdate, loading = state.busy, onClick = { onIntent(RequestsIntent.SendUpdate) }, fontSize = 15.ssp, shadow = false)
    }
}

/** Bottom buttons, only those `can` allows. */
@Composable
private fun ActionBar(state: RequestsState, r: ServiceRequest, onIntent: (RequestsIntent) -> Unit) {
    val can = r.can
    val picker = rememberPhotoPicker(RequestRules.MAX_PHOTOS) { onIntent(RequestsIntent.AddPhotos(it)) }
    val showRemind = !state.landlord && r.isOpen
    if (!(showRemind || can.resolve || can.reopen || can.cancel || can.addPhotos)) return
    Column(Modifier.fillMaxWidth().background(AuthColors.Background).padding(horizontal = 16.sdp, vertical = 10.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        if (can.reopen) {
            AuthPrimaryButton(text = stringResource(R.string.req_reopen), enabled = !state.busy, loading = false, onClick = { onIntent(RequestsIntent.ShowSheet(RequestSheet.REOPEN)) }, fontSize = 15.ssp, shadow = false)
        }
        if (can.resolve) {
            AuthPrimaryButton(text = stringResource(R.string.req_resolved_btn), enabled = !state.busy, loading = false, onClick = { onIntent(RequestsIntent.ShowSheet(RequestSheet.RESOLVE)) }, fontSize = 15.ssp, shadow = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            if (showRemind) {
                // Off → "Reminded today" (once a day; the server decides).
                OutlineAction(
                    stringResource(if (can.remind) R.string.req_remind else R.string.req_reminded_today),
                    R.drawable.ic_notifications,
                    enabled = can.remind && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { onIntent(RequestsIntent.Remind) }
            }
            if (can.addPhotos) {
                OutlineAction(stringResource(R.string.req_add_photos), R.drawable.ic_camera, enabled = !state.busy, modifier = Modifier.weight(1f), onClick = picker.gallery)
            }
        }
        if (can.cancel) {
            Text(
                stringResource(R.string.req_withdraw),
                color = if (state.busy) AuthColors.Muted else AuthColors.Error,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).clickable(enabled = !state.busy, role = Role.Button) {
                    onIntent(RequestsIntent.ShowSheet(RequestSheet.CANCEL))
                }.padding(8.sdp),
            )
        }
    }
}

/** Resolve (remark optional) · Reopen (remark required + photos) · Withdraw (remark optional). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestActionSheet(state: RequestsState, sheet: RequestSheet, onIntent: (RequestsIntent) -> Unit) {
    val picker = rememberPhotoPicker(RequestRules.MAX_PHOTOS - state.sheetPhotos.size) { onIntent(RequestsIntent.SheetAddPhotos(it)) }
    ModalBottomSheet(
        onDismissRequest = { onIntent(RequestsIntent.DismissSheet) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(
                stringResource(
                    when (sheet) {
                        RequestSheet.RESOLVE -> R.string.req_sheet_resolve
                        RequestSheet.REOPEN -> R.string.req_sheet_reopen
                        RequestSheet.CANCEL -> R.string.req_sheet_cancel
                    },
                ),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
            )
            Spacer(Modifier.height(12.sdp))
            val required = sheet == RequestSheet.REOPEN
            OutlinedTextField(
                value = state.sheetRemark,
                onValueChange = { onIntent(RequestsIntent.SheetRemark(it)) },
                label = { Text(stringResource(if (required) R.string.req_reopen_why else R.string.req_remark_optional), fontFamily = Inter) },
                isError = state.sheetError,
                supportingText = if (state.sheetError) {
                    @Composable { Text(stringResource(R.string.lease_reason_min, RequestRules.REMARK_MIN), fontFamily = Inter) }
                } else null,
                minLines = 2,
                maxLines = 5,
                enabled = !state.busy,
                colors = propertyFieldColors(),
                shape = RoundedCornerShape(12.sdp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (sheet == RequestSheet.REOPEN) {
                Spacer(Modifier.height(10.sdp))
                PhotoStrip(state.sheetPhotos, onRemove = { onIntent(RequestsIntent.SheetRemovePhoto(it)) })
                if (state.sheetPhotos.isNotEmpty()) Spacer(Modifier.height(8.sdp))
                AddPhotoButtons(picker, enabled = !state.busy && state.sheetPhotos.size < RequestRules.MAX_PHOTOS)
            }
            Spacer(Modifier.height(16.sdp))
            AuthPrimaryButton(
                text = stringResource(
                    when (sheet) {
                        RequestSheet.RESOLVE -> R.string.req_resolved_btn
                        RequestSheet.REOPEN -> R.string.req_reopen
                        RequestSheet.CANCEL -> R.string.req_withdraw
                    },
                ),
                enabled = !state.busy && (!required || RequestRules.remarkOk(state.sheetRemark)),
                loading = state.busy,
                onClick = { onIntent(RequestsIntent.SheetSubmit) },
                fontSize = 15.ssp,
            )
        }
    }
}

/** Full-screen photos, swipe between them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start) { urls.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(model = urls[i], contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.sdp).size(44.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(22.sdp)) }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 13.ssp,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp),
                )
            }
        }
    }
}
