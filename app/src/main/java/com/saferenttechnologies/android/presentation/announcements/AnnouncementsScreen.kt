package com.saferenttechnologies.android.presentation.announcements

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.saferenttechnologies.android.domain.model.Announcement
import com.saferenttechnologies.android.domain.model.AnnouncementAudience
import com.saferenttechnologies.android.domain.model.AnnouncementRules
import com.saferenttechnologies.android.domain.model.AnnouncementTarget
import com.saferenttechnologies.android.domain.model.AnnouncementViewer
import com.saferenttechnologies.android.domain.model.RelativeTime
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.DetailSkeleton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.notifications.relativeTimeText
import com.saferenttechnologies.android.presentation.notifications.startOfDay
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.presentation.requests.AddPhotoButtons
import com.saferenttechnologies.android.presentation.requests.rememberPhotoPicker
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun isoMillis(iso: String?): Long? = iso?.takeIf { it.isNotBlank() }?.let { s ->
    listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull { p ->
        runCatching { SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time }.getOrNull()
    }
}

@Composable
private fun ago(iso: String?): String {
    val millis = isoMillis(iso) ?: return ""
    val now = System.currentTimeMillis()
    return relativeTimeText(RelativeTime.of(millis, now, startOfDay(now)))
}

/** Landlord: sent list → compose / detail with who saw it. Tenant: received list / detail (view recorded on open). */
@Composable
fun AnnouncementsScreen(state: AnnouncementsState, effects: Flow<AnnouncementsEffect>, onIntent: (AnnouncementsIntent) -> Unit) {
    BackHandler { onIntent(AnnouncementsIntent.Back) }
    val context = LocalContext.current
    val unreadable = stringResource(R.string.pay_proof_unreadable)
    val sent = stringResource(R.string.ann_sent)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            when (e) {
                is AnnouncementsEffect.Message -> Toast.makeText(context, if (e.text == AnnouncementsViewModel.PHOTO_UNREADABLE) unreadable else e.text, Toast.LENGTH_LONG).show()
                AnnouncementsEffect.Sent -> Toast.makeText(context, sent, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(
            stringResource(
                when (state.mode) {
                    AnnouncementsMode.COMPOSE -> R.string.ann_new
                    AnnouncementsMode.DETAIL -> R.string.ann_detail
                    AnnouncementsMode.LIST -> R.string.ann_title
                },
            ),
        ) { onIntent(AnnouncementsIntent.Back) }
        when (state.mode) {
            AnnouncementsMode.LIST -> ListContent(state, onIntent)
            AnnouncementsMode.COMPOSE -> ComposeContent(state, onIntent)
            AnnouncementsMode.DETAIL -> DetailContent(state, onIntent)
        }
    }
    state.imageViewer?.let { url -> ImageViewer(url) { onIntent(AnnouncementsIntent.ViewImage(null)) } }
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
private fun ColumnScope.ListContent(state: AnnouncementsState, onIntent: (AnnouncementsIntent) -> Unit) {
    if (state.landlord) {
        AuthPrimaryButton(
            text = stringResource(R.string.ann_new),
            enabled = true,
            loading = false,
            onClick = { onIntent(AnnouncementsIntent.StartCompose) },
            leadingIcon = R.drawable.ic_campaign,
            fontSize = 15.ssp,
            shadow = false,
            modifier = Modifier.padding(horizontal = 16.sdp, vertical = 6.sdp),
        )
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(AnnouncementsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
        when {
            state.loading && state.items.isEmpty() -> ListSkeleton(rows = 5, thumbnail = 44.sdp, roundThumb = true)
            state.items.isEmpty() -> Text(
                state.error ?: stringResource(if (state.landlord) R.string.ann_empty_landlord else R.string.ann_empty_tenant),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(32.sdp),
            )
            else -> {
                val listState = rememberLazyListState()
                val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
                LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(AnnouncementsIntent.LoadMore) }
                LazyColumn(state = listState, contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp), modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { a -> AnnouncementCard(a, state.landlord) { onIntent(AnnouncementsIntent.Select(a.id)) } }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(a: Announcement, landlord: Boolean, onClick: () -> Unit) {
    val unread = !landlord && !a.viewed
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(if (unread) AuthColors.OrangeSoft.copy(alpha = 0.5f) else AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.sdp),
    ) {
        if (a.imageUrl.isNotBlank()) {
            AsyncImage(
                model = absoluteUrl(a.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.sdp).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface),
            )
        } else {
            Box(Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_campaign), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
            }
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (unread) {
                    Spacer(Modifier.width(6.sdp))
                    Box(Modifier.size(8.sdp).clip(CircleShape).background(AuthColors.Orange))
                }
            }
            Text(a.body, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.sdp))
            Text(
                listOf(if (landlord) a.audienceLabel else a.fromName, ago(a.createdAt)).filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 11.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (landlord) {
            Spacer(Modifier.width(8.sdp))
            // "3 / 5 seen"
            StatusPill(stringResource(R.string.ann_seen_count, a.viewCount, a.recipientCount), if (a.recipientCount > 0 && a.viewCount >= a.recipientCount) AuthColors.Green else AuthColors.Body)
        }
    }
}

// ---------------- compose ----------------

@Composable
private fun ColumnScope.ComposeContent(state: AnnouncementsState, onIntent: (AnnouncementsIntent) -> Unit) {
    val f = state.form
    val picker = rememberPhotoPicker(1) { it.firstOrNull()?.let { uri -> onIntent(AnnouncementsIntent.Image(uri)) } }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.sdp)) {
        Heading(stringResource(R.string.ann_q_who))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            listOf(
                AnnouncementAudience.ALL to R.string.ann_aud_all,
                AnnouncementAudience.PROPERTIES to R.string.ann_aud_properties,
                AnnouncementAudience.TENANTS to R.string.ann_aud_tenants,
            ).forEach { (a, label) ->
                Chip(stringResource(label), selected = f.audience == a, enabled = !state.sending, modifier = Modifier.weight(1f), maxLines = 2) { onIntent(AnnouncementsIntent.Audience(a)) }
            }
        }
        Spacer(Modifier.height(8.sdp))
        when {
            state.targetsLoading -> ListSkeleton(rows = 3, thumbnail = null)
            f.audience == AnnouncementAudience.ALL -> Hint(
                if (state.tenants.isEmpty()) stringResource(R.string.ann_no_tenants)
                else pluralStringResource(R.plurals.ann_all_hint, state.tenants.size, state.tenants.size),
            )
            else -> Picker(state, onIntent)
        }
        if (f.showErrors && f.audience != AnnouncementAudience.ALL && (if (f.audience == AnnouncementAudience.PROPERTIES) f.propertyIds else f.leaseIds).isEmpty()) {
            FieldErr(stringResource(if (f.audience == AnnouncementAudience.PROPERTIES) R.string.ann_err_property else R.string.ann_err_tenant))
        }

        Spacer(Modifier.height(16.sdp))
        Heading(stringResource(R.string.ann_q_title))
        OutlinedTextField(
            value = f.title,
            onValueChange = { onIntent(AnnouncementsIntent.Title(it)) },
            placeholder = { Text(stringResource(R.string.ann_title_ph), fontFamily = Inter) },
            isError = f.showErrors && !AnnouncementRules.titleOk(f.title),
            supportingText = { Counter(f.title.length, AnnouncementRules.TITLE_MAX, if (f.showErrors && !AnnouncementRules.titleOk(f.title)) stringResource(R.string.lease_reason_min, AnnouncementRules.TITLE_MIN) else "") },
            singleLine = true,
            enabled = !state.sending,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.sdp))
        Heading(stringResource(R.string.ann_q_body))
        OutlinedTextField(
            value = f.body,
            onValueChange = { onIntent(AnnouncementsIntent.Body(it)) },
            placeholder = { Text(stringResource(R.string.ann_body_ph), fontFamily = Inter) },
            isError = f.showErrors && !AnnouncementRules.bodyOk(f.body),
            supportingText = { Counter(f.body.length, AnnouncementRules.BODY_MAX, if (f.showErrors && !AnnouncementRules.bodyOk(f.body)) stringResource(R.string.lease_reason_min, AnnouncementRules.BODY_MIN) else "") },
            minLines = 4,
            maxLines = 10,
            enabled = !state.sending,
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.sdp))
        Heading(stringResource(R.string.ann_q_image))
        if (f.image != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface)) {
                AsyncImage(model = f.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.sdp).size(32.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                        .clickable(enabled = !state.sending, role = Role.Button) { onIntent(AnnouncementsIntent.Image(null)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(18.sdp)) }
            }
        } else {
            AddPhotoButtons(picker, enabled = !state.sending)
        }
        Spacer(Modifier.height(20.sdp))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 10.sdp)) {
        AuthPrimaryButton(
            text = stringResource(R.string.ann_send),
            enabled = state.canSend,
            loading = state.sending,
            onClick = { onIntent(AnnouncementsIntent.Send) },
            fontSize = 15.ssp,
        )
    }
}

/** Search + check list of properties / tenants, with "Select all". */
@Composable
private fun Picker(state: AnnouncementsState, onIntent: (AnnouncementsIntent) -> Unit) {
    val f = state.form
    val properties = f.audience == AnnouncementAudience.PROPERTIES
    val selected = if (properties) f.propertyIds else f.leaseIds
    val rows = state.pickerRows
    if ((if (properties) state.properties else state.tenants).isEmpty()) return Hint(stringResource(R.string.ann_no_tenants))
    OutlinedTextField(
        value = f.query,
        onValueChange = { onIntent(AnnouncementsIntent.Query(it)) },
        placeholder = { Text(stringResource(if (properties) R.string.ann_search_properties else R.string.tenants_search), fontFamily = Inter) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
        singleLine = true,
        enabled = !state.sending,
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.sdp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ann_selected, selected.size), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.weight(1f))
        if (rows.isNotEmpty()) {
            Text(
                stringResource(if (selected.containsAll(rows.map { it.id })) R.string.ann_clear_all else R.string.ann_select_all),
                color = AuthColors.OrangeText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.ssp,
                modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !state.sending, role = Role.Button) { onIntent(AnnouncementsIntent.SelectAllShown) }.padding(6.sdp),
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)),
    ) {
        if (rows.isEmpty()) Text(stringResource(R.string.req_empty), color = AuthColors.Muted, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(14.sdp))
        rows.forEachIndexed { i, t ->
            TargetRow(t, properties, checked = t.id in selected, enabled = !state.sending) { onIntent(AnnouncementsIntent.Toggle(t.id)) }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Border))
        }
    }
}

@Composable
private fun TargetRow(t: AnnouncementTarget, property: Boolean, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick).padding(horizontal = 12.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.sdp).clip(RoundedCornerShape(6.sdp))
                .background(if (checked) AuthColors.Orange else Color.Transparent)
                .border(1.5.sdp, if (checked) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(6.sdp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(16.sdp)) }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (property) t.subtitle.toIntOrNull()?.let { pluralStringResource(R.plurals.ann_tenants_n, it, it) }.orEmpty() else t.subtitle
            if (sub.isNotBlank()) Text(sub, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Counter(length: Int, max: Int, error: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(error, fontFamily = Inter, modifier = Modifier.weight(1f))
        Text("$length / $max", fontFamily = Inter)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp, modifier = Modifier.padding(bottom = 8.sdp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = AuthColors.Body,
        fontFamily = Inter,
        fontSize = 13.ssp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
    )
}

@Composable
private fun FieldErr(text: String) {
    Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
}

// ---------------- detail ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.DetailContent(state: AnnouncementsState, onIntent: (AnnouncementsIntent) -> Unit) {
    val a = state.detail
    if (a == null) {
        if (state.detailLoading) DetailSkeleton(hero = true)
        return
    }
    PullToRefreshBox(isRefreshing = state.detailLoading && state.detail != null, onRefresh = { onIntent(AnnouncementsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
            if (a.imageUrl.isNotBlank()) {
                val url = absoluteUrl(a.imageUrl)
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface)
                        .clickable(role = Role.Image) { onIntent(AnnouncementsIntent.ViewImage(url)) },
                )
                Spacer(Modifier.height(14.sdp))
            }
            Text(a.title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(4.sdp))
            Text(
                listOfNotNull(
                    if (state.landlord) a.audienceLabel.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ann_to_x, it) }
                    else a.fromName.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ann_from_x, it) },
                    a.createdAt.takeIf { it.isNotBlank() }?.let { prettyDate(it) },
                ).joinToString(" · "),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 13.ssp,
            )
            Spacer(Modifier.height(14.sdp))
            Text(a.body, color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp, lineHeight = 22.ssp)
            if (state.landlord) {
                Spacer(Modifier.height(20.sdp))
                Viewers(state, a, onIntent)
            }
            Spacer(Modifier.height(20.sdp))
        }
    }
}

/** "Seen by 3 of 5" + Seen / Not seen tabs with names, place and time. */
@Composable
private fun Viewers(state: AnnouncementsState, a: Announcement, onIntent: (AnnouncementsIntent) -> Unit) {
    val total = state.viewers.size.takeIf { it > 0 } ?: a.recipientCount
    val seen = if (state.viewers.isNotEmpty()) state.seen.size else a.viewCount
    Text(stringResource(R.string.ann_seen_by, seen, total), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
    Spacer(Modifier.height(8.sdp))
    // Progress bar (seen / total).
    Box(Modifier.fillMaxWidth().height(6.sdp).clip(RoundedCornerShape(3.sdp)).background(AuthColors.Surface)) {
        if (total > 0 && seen > 0) Box(Modifier.fillMaxWidth(seen.toFloat() / total).height(6.sdp).background(AuthColors.Green))
    }
    Spacer(Modifier.height(12.sdp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        Chip(stringResource(R.string.ann_tab_seen, state.seen.size), selected = state.seenTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(AnnouncementsIntent.SeenTab(true)) }
        Chip(stringResource(R.string.ann_tab_not_seen, state.notSeen.size), selected = !state.seenTab, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(AnnouncementsIntent.SeenTab(false)) }
    }
    Spacer(Modifier.height(10.sdp))
    val rows = if (state.seenTab) state.seen else state.notSeen
    when {
        state.viewersLoading && state.viewers.isEmpty() -> ListSkeleton(rows = 3, thumbnail = 36.sdp, roundThumb = true)
        rows.isEmpty() -> Text(
            stringResource(if (state.seenTab) R.string.ann_none_seen else R.string.ann_all_seen),
            color = AuthColors.Muted,
            fontFamily = Inter,
            fontSize = 13.ssp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(20.sdp),
        )
        else -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))) {
            rows.forEachIndexed { i, v ->
                ViewerRow(v)
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Border))
            }
        }
    }
}

@Composable
private fun ViewerRow(v: AnnouncementViewer) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 10.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.sdp).clip(CircleShape).background(if (v.seen) AuthColors.Green.copy(alpha = 0.12f) else AuthColors.Surface), contentAlignment = Alignment.Center) {
            Text(v.name.trim().take(1).uppercase().ifBlank { "?" }, color = if (v.seen) AuthColors.Green else AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.ssp)
        }
        Spacer(Modifier.width(10.sdp))
        Column(Modifier.weight(1f)) {
            Text(v.name.ifBlank { "—" }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (v.place.isNotBlank()) Text(v.place, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (v.seen) Text(ago(v.viewedAt), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
    }
}

@Composable
private fun ImageViewer(url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.sdp).size(44.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(22.sdp)) }
        }
    }
}
