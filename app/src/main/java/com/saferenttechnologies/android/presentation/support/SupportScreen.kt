package com.saferenttechnologies.android.presentation.support

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.SupportRules
import com.saferenttechnologies.android.domain.model.SupportTab
import com.saferenttechnologies.android.domain.model.Ticket
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.RequestDetailSkeleton
import com.saferenttechnologies.android.presentation.common.SkeletonTrailing
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.rememberIdleHint
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.notifications.relativeTimeText
import com.saferenttechnologies.android.presentation.notifications.startOfDay
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.SelectField
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.domain.model.RelativeTime
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

/** "18 Nov, 9:21 PM" (local). */
private fun shortTime(iso: String?): String = isoMillis(iso)?.let { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()

@Composable
private fun ago(iso: String?): String {
    val ms = isoMillis(iso) ?: return ""
    val now = System.currentTimeMillis()
    return relativeTimeText(RelativeTime.of(ms, now, startOfDay(now)))
}

@Composable
fun categoryLabel(key: String): String = stringResource(
    when (key) {
        "PAYMENT" -> R.string.sup_cat_payment
        "TENANT_COMPLAINT" -> R.string.sup_cat_tenant
        "LANDLORD_COMPLAINT" -> R.string.sup_cat_landlord
        "ACCOUNT" -> R.string.sup_cat_account
        "PROPERTY" -> R.string.sup_cat_property
        else -> R.string.sup_cat_other
    },
)

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "IN_PROGRESS" -> R.string.sup_st_progress to Blue
        "RESOLVED" -> R.string.sup_st_resolved to AuthColors.Green
        "CLOSED" -> R.string.sup_st_closed to AuthColors.Muted
        else -> R.string.sup_st_open to AuthColors.Body
    }
    StatusPill(stringResource(label), color)
}

/** Help & support: ticket list, new ticket, ticket chat. */
@Composable
fun SupportScreen(state: SupportState, effects: Flow<SupportEffect>, onIntent: (SupportIntent) -> Unit) {
    BackHandler { onIntent(SupportIntent.Back) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { effects.collect { e -> if (e is SupportEffect.Message) Toast.makeText(context, e.text, Toast.LENGTH_LONG).show() } }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(SupportIntent.Back) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(
                stringResource(
                    when (state.mode) {
                        SupportMode.LIST -> R.string.sup_title
                        SupportMode.NEW -> R.string.sup_new
                        SupportMode.DETAIL -> R.string.sup_ticket
                    },
                ),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
            )
        }
        when (state.mode) {
            SupportMode.LIST -> ListContent(state, onIntent)
            SupportMode.NEW -> NewContent(state, onIntent)
            SupportMode.DETAIL -> DetailContent(state, onIntent)
        }
    }
}

// ---------------- list ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ListContent(state: SupportState, onIntent: (SupportIntent) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.sdp, vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        SupportTab.entries.forEach { t ->
            val label = stringResource(
                when (t) {
                    SupportTab.OPEN -> R.string.sup_tab_open
                    SupportTab.RESOLVED -> R.string.sup_tab_resolved
                    SupportTab.CLOSED -> R.string.sup_tab_closed
                },
            )
            Chip(label, selected = t == state.tab, modifier = Modifier.widthIn(min = 88.sdp), maxLines = 1) { onIntent(SupportIntent.SelectTab(t)) }
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f)) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(SupportIntent.Refresh) }, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton(rows = 5, thumbnail = 40.sdp, roundThumb = true, lines = 3, trailing = SkeletonTrailing.PILL)
                state.items.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.sdp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: stringResource(R.string.sup_empty), color = AuthColors.Muted, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.sdp))
                    AuthPrimaryButton(text = stringResource(R.string.sup_new), enabled = true, loading = false, onClick = { onIntent(SupportIntent.StartNew) }, fontSize = 15.ssp, shadow = false)
                }
                else -> {
                    val listState = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
                    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(SupportIntent.LoadMore) }
                    LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.sdp, end = 16.sdp, top = 12.sdp, bottom = 88.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { t -> TicketCard(t) { onIntent(SupportIntent.Select(t.id)) } }
                    }
                }
            }
        }
        // FAB "New ticket"
        if (state.items.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.sdp).clip(RoundedCornerShape(50)).background(AuthColors.Orange)
                    .clickable(role = Role.Button) { onIntent(SupportIntent.StartNew) }.padding(horizontal = 18.sdp, vertical = 14.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_add), null, tint = Color.White, modifier = Modifier.size(20.sdp))
                Spacer(Modifier.width(8.sdp))
                Text(stringResource(R.string.sup_new), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.ssp)
            }
        }
    }
}

@Composable
private fun TicketCard(t: Ticket, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).clickable(role = Role.Button, onClick = onClick).padding(12.sdp),
    ) {
        Box(Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_help), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(t.subject, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Text(categoryLabel(t.category), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.req_updated, ago(t.updatedAt.ifBlank { t.createdAt })), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                if (t.messageCount > 0) {
                    Spacer(Modifier.width(8.sdp))
                    Icon(painterResource(R.drawable.ic_chat), null, tint = AuthColors.Muted, modifier = Modifier.size(13.sdp))
                    Spacer(Modifier.width(3.sdp))
                    Text(t.messageCount.toString(), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                }
            }
        }
        Spacer(Modifier.width(8.sdp))
        StatusChip(t.status)
    }
}

// ---------------- new ticket ----------------

@Composable
private fun ColumnScope.NewContent(state: SupportState, onIntent: (SupportIntent) -> Unit) {
    val f = state.form
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
        Heading(stringResource(R.string.sup_q_about))
        // Full labels, scroll sideways, never cut.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            state.categories.forEach { c ->
                Chip(categoryLabel(c), selected = f.category == c, enabled = !state.sending, modifier = Modifier.widthIn(min = 72.sdp), maxLines = 1) { onIntent(SupportIntent.Category(c)) }
            }
        }
        if (f.showErrors && f.category == null) FieldErr(stringResource(R.string.sup_err_category))

        if (state.properties.isNotEmpty()) {
            Spacer(Modifier.height(14.sdp))
            val none = stringResource(R.string.sup_property_none)
            SelectField(
                label = stringResource(R.string.sup_property),
                options = listOf("") + state.properties.map { it.id },
                value = f.propertyId.orEmpty(),
                error = null,
                enabled = !state.sending,
                labels = mapOf("" to none) + state.properties.associate { it.id to it.title },
                placeholder = none,
            ) { onIntent(SupportIntent.Property(it.ifBlank { null })) }
        }

        Spacer(Modifier.height(14.sdp))
        Heading(stringResource(R.string.sup_subject))
        val subjectHint = rememberIdleHint()
        val subjectErr = state.fieldErrors["subject"] ?: if (f.showErrors && !SupportRules.subjectOk(f.subject)) stringResource(R.string.lease_reason_min, SupportRules.SUBJECT_MIN) else null
        OutlinedTextField(
            value = f.subject,
            onValueChange = { onIntent(SupportIntent.Subject(it)) },
            placeholder = subjectHint.placeholder(stringResource(R.string.sup_subject_ph)),
            interactionSource = subjectHint.source,
            isError = subjectErr != null,
            supportingText = { Counter(f.subject.length, SupportRules.SUBJECT_MAX, subjectErr) },
            singleLine = true,
            enabled = !state.sending,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        Heading(stringResource(R.string.sup_description))
        val descHint = rememberIdleHint()
        val descErr = state.fieldErrors["description"] ?: if (f.showErrors && !SupportRules.descriptionOk(f.description)) stringResource(R.string.lease_reason_min, SupportRules.DESCRIPTION_MIN) else null
        OutlinedTextField(
            value = f.description,
            onValueChange = { onIntent(SupportIntent.Description(it)) },
            placeholder = descHint.placeholder(stringResource(R.string.sup_description_ph)),
            interactionSource = descHint.source,
            isError = descErr != null,
            supportingText = { Counter(f.description.length, SupportRules.DESCRIPTION_MAX, descErr) },
            minLines = 5,
            maxLines = 10,
            enabled = !state.sending,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )

        Heading(stringResource(R.string.req_q_priority))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            SupportRules.PRIORITIES.forEach { p ->
                Chip(optionLabel(p), selected = f.priority == p, enabled = !state.sending, modifier = Modifier.widthIn(min = 72.sdp), maxLines = 1) { onIntent(SupportIntent.Priority(p)) }
            }
        }
        state.fieldErrors["priority"]?.let { FieldErr(it) }
        Spacer(Modifier.height(20.sdp))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 10.sdp)) {
        AuthPrimaryButton(text = stringResource(R.string.sup_send), enabled = state.canSend, loading = state.sending, onClick = { onIntent(SupportIntent.Send) }, fontSize = 15.ssp)
    }
}

@Composable
private fun Counter(length: Int, max: Int, error: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(error.orEmpty(), fontFamily = Inter, modifier = Modifier.weight(1f))
        Text("$length / $max", fontFamily = Inter)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp, modifier = Modifier.padding(top = 6.sdp, bottom = 8.sdp))
}

@Composable
private fun FieldErr(text: String) {
    Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
}

// ---------------- detail (chat) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.DetailContent(state: SupportState, onIntent: (SupportIntent) -> Unit) {
    val t = state.detail
    if (t == null) {
        if (state.detailLoading) RequestDetailSkeleton()
        return
    }
    // Header
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 6.sdp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(t.subject, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.sdp))
            StatusChip(t.status)
        }
        Text(
            listOfNotNull(categoryLabel(t.category), shortTime(t.createdAt).ifBlank { null }, t.propertyTitle.ifBlank { null }).joinToString(" · "),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 12.ssp,
        )
        ConnectedLine(SupportRules.agentName(t), t.closed)
    }
    Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Border))
    val listState = rememberLazyListState()
    val count = t.messages.size + 1
    LaunchedEffect(count, state.scrollTick) { if (count > 0) listState.animateScrollToItem(count - 1) }
    PullToRefreshBox(isRefreshing = state.detailLoading && state.detail != null, onRefresh = { onIntent(SupportIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(12.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.fillMaxSize()) {
            // First bubble = what I wrote when raising it.
            item(key = "desc") { Bubble(mine = true, author = null, body = t.description, time = t.createdAt) }
            items(t.messages, key = { it.id }) { m ->
                val mine = state.isMine(m.authorId, m.fromSupport)
                Bubble(mine = mine, author = if (mine) null else m.author?.fullName.orEmpty(), body = m.body, time = m.createdAt)
            }
        }
    }
    if (t.closed) {
        Text(
            stringResource(R.string.sup_closed_note),
            color = AuthColors.Muted,
            fontFamily = Inter,
            fontSize = 13.ssp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(AuthColors.Surface).padding(14.sdp),
        )
    } else {
        ReplyBar(state, onIntent)
    }
}

/** Small line under the title: "● Connected to Ravi Kumar" once staff opened it, else "Waiting for an agent to join". */
@Composable
private fun ConnectedLine(name: String?, closed: Boolean) {
    Row(Modifier.padding(top = 4.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.sdp).clip(CircleShape).background(if (name != null && !closed) AuthColors.Green else AuthColors.Muted))
        Spacer(Modifier.width(6.sdp))
        Text(
            if (name != null) stringResource(if (closed) R.string.sup_was_connected_name else R.string.sup_connected_name, name)
            else stringResource(R.string.sup_waiting_agent),
            color = if (name != null) AuthColors.Ink else AuthColors.Muted,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 12.ssp,
        )
    }
}

@Composable
private fun Bubble(mine: Boolean, author: String?, body: String, time: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.sdp)
                .clip(RoundedCornerShape(topStart = 14.sdp, topEnd = 14.sdp, bottomStart = if (mine) 14.sdp else 4.sdp, bottomEnd = if (mine) 4.sdp else 14.sdp))
                .background(if (mine) AuthColors.OrangeSoft else AuthColors.Surface)
                .padding(horizontal = 12.sdp, vertical = 8.sdp),
        ) {
            if (!mine) {
                Text(
                    listOf(stringResource(R.string.sup_safe_rent_support), author.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.ssp,
                )
                Spacer(Modifier.height(2.sdp))
            }
            Text(body, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            Text(shortTime(time), color = AuthColors.Muted, fontFamily = Inter, fontSize = 10.ssp, modifier = Modifier.align(Alignment.End).padding(top = 2.sdp))
        }
    }
}

@Composable
private fun ReplyBar(state: SupportState, onIntent: (SupportIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AuthColors.Card).padding(horizontal = 12.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hint = rememberIdleHint()
        OutlinedTextField(
            value = state.reply,
            onValueChange = { onIntent(SupportIntent.ReplyText(it)) },
            placeholder = hint.placeholder(stringResource(R.string.sup_reply_ph)),
            interactionSource = hint.source,
            maxLines = 4,
            enabled = !state.replying,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(22.sdp),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.sdp))
        val can = state.canReply
        Box(
            Modifier.size(46.sdp).clip(CircleShape).background(if (can) AuthColors.Orange else AuthColors.Border)
                .clickable(enabled = can, role = Role.Button) { onIntent(SupportIntent.SendReply) },
            contentAlignment = Alignment.Center,
        ) {
            if (state.replying) androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
            else Icon(painterResource(R.drawable.ic_arrow_forward), stringResource(R.string.sup_send_reply), tint = Color.White, modifier = Modifier.size(20.sdp))
        }
    }
}
