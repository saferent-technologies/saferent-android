package com.saferenttechnologies.android.presentation.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppNotification
import com.saferenttechnologies.android.domain.model.RelativeTime
import com.saferenttechnologies.android.domain.usecase.IncomingPush
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(state: NotificationsState, onIntent: (NotificationsIntent) -> Unit) {
    BackHandler { onIntent(NotificationsIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    val notAvailable = stringResource(R.string.notifications_not_available)
    val gone = stringResource(R.string.notifications_item_gone)
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        onIntent(NotificationsIntent.MessageShown)
        snackbar.showSnackbar(
            when (m) {
                NotificationsViewModel.MESSAGE_NOT_AVAILABLE -> notAvailable
                NotificationsViewModel.MESSAGE_GONE -> gone
                else -> m
            },
        )
    }

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(NotificationsIntent.Close) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp)) }
                Spacer(Modifier.width(12.sdp))
                Text(
                    stringResource(R.string.notifications_title),
                    color = AuthColors.Ink,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.ssp,
                    modifier = Modifier.weight(1f),
                )
                val canMarkAll = state.unread > 0 || state.items.any { it.isUnread }
                Text(
                    stringResource(R.string.notifications_mark_all),
                    color = if (canMarkAll) AuthColors.OrangeText else AuthColors.Muted,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.ssp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.sdp))
                        .clickable(enabled = canMarkAll, role = Role.Button) { onIntent(NotificationsIntent.MarkAllRead) }
                        .padding(horizontal = 8.sdp, vertical = 10.sdp),
                )
            }
            Row(Modifier.padding(horizontal = 16.sdp, vertical = 8.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                Chip(stringResource(R.string.notifications_tab_all), selected = !state.unreadOnly) { onIntent(NotificationsIntent.SelectTab(false)) }
                Chip(
                    if (state.unread > 0) stringResource(R.string.notifications_tab_unread_n, state.unread) else stringResource(R.string.notifications_tab_unread),
                    selected = state.unreadOnly,
                ) { onIntent(NotificationsIntent.SelectTab(true)) }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(NotificationsIntent.Refresh) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                when {
                    state.items.isEmpty() && state.loading ->
                        CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.align(Alignment.Center))
                    state.items.isEmpty() && state.error != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.sdp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.sdp))
                        Text(
                            stringResource(R.string.add_property_retry),
                            color = AuthColors.OrangeText,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(role = Role.Button) { onIntent(NotificationsIntent.Refresh) }.padding(12.sdp),
                        )
                    }
                    state.items.isEmpty() -> EmptyState(Modifier.align(Alignment.Center))
                    else -> NotificationList(state, onIntent)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }
}

@Composable
private fun NotificationList(state: NotificationsState, onIntent: (NotificationsIntent) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(NotificationsIntent.LoadMore) }

    val now = System.currentTimeMillis()
    val startOfToday = remember(now / 60_000) { startOfDay(now) }
    val (today, earlier) = state.items.partition { (it.createdAtMillis ?: 0) >= startOfToday }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.sdp), modifier = Modifier.fillMaxSize()) {
        if (today.isNotEmpty()) {
            item(key = "h-today") { GroupHeader(stringResource(R.string.notifications_today)) }
            items(today, key = { it.id }) { NotificationRow(it, now, startOfToday) { onIntent(NotificationsIntent.Tap(it)) } }
        }
        if (earlier.isNotEmpty()) {
            item(key = "h-earlier") { GroupHeader(stringResource(R.string.notifications_earlier)) }
            items(earlier, key = { it.id }) { NotificationRow(it, now, startOfToday) { onIntent(NotificationsIntent.Tap(it)) } }
        }
        if (state.loading) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(12.sdp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(28.sdp))
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        color = AuthColors.Muted,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.ssp,
        modifier = Modifier.padding(start = 20.sdp, end = 20.sdp, top = 14.sdp, bottom = 4.sdp),
    )
}

@Composable
private fun NotificationRow(n: AppNotification, now: Long, startOfToday: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (n.isUnread) AuthColors.OrangeSoft.copy(alpha = 0.35f) else Color.Transparent)
            .padding(horizontal = 16.sdp, vertical = 12.sdp),
    ) {
        Box(Modifier.padding(top = 6.sdp).size(8.sdp).clip(CircleShape).background(if (n.isUnread) AuthColors.Orange else Color.Transparent))
        Spacer(Modifier.width(10.sdp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    n.title,
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = if (n.isUnread) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.ssp,
                    modifier = Modifier.weight(1f),
                )
                n.createdAtMillis?.let {
                    Spacer(Modifier.width(8.sdp))
                    Text(relativeTimeText(RelativeTime.of(it, now, startOfToday)), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                }
            }
            if (n.body.isNotBlank()) {
                Spacer(Modifier.height(2.sdp))
                Text(n.body, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.padding(24.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.sdp).clip(CircleShape).background(AuthColors.Surface), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_notifications), null, tint = AuthColors.Muted, modifier = Modifier.size(30.sdp))
        }
        Spacer(Modifier.height(14.sdp))
        Text(stringResource(R.string.notifications_empty), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
    }
}

@Composable
private fun relativeTimeText(t: RelativeTime): String = when (t) {
    RelativeTime.JustNow -> stringResource(R.string.notifications_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.notifications_minutes_ago, t.n)
    is RelativeTime.Hours -> stringResource(R.string.notifications_hours_ago, t.n)
    RelativeTime.Yesterday -> stringResource(R.string.notifications_yesterday)
    is RelativeTime.Date -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t.millis))
}

private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

// ---------------- bell + in-app banner ----------------

/** Bell with an unread badge (hidden at 0, "9+" above 9). */
@Composable
fun BellWithBadge(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(44.sdp)) {
        Box(
            Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_notifications), stringResource(R.string.notifications_title), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp))
        }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.sdp, y = (-2).sdp)
                    .heightIn(min = 18.sdp)
                    .widthIn(min = 18.sdp)
                    .clip(RoundedCornerShape(50))
                    .background(AuthColors.Error)
                    .border(1.5.sdp, AuthColors.Background, RoundedCornerShape(50))
                    .padding(horizontal = 4.sdp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badgeText(unread), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.ssp)
            }
        }
    }
}

fun badgeText(unread: Int): String = if (unread > 9) "9+" else unread.toString()

/** "Title — tap to open" at the top while the app is open; hides after a few seconds. */
@Composable
fun InAppPushBanner(push: IncomingPush?, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(push) {
        if (push != null) {
            delay(5_000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = push != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val title = push?.title.orEmpty()
        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.sdp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.sdp))
                .background(AuthColors.Card)
                .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 14.sdp, vertical = 12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_notifications), null, tint = AuthColors.OrangeText, modifier = Modifier.size(18.sdp))
            }
            Spacer(Modifier.width(10.sdp))
            Text(
                stringResource(R.string.notifications_banner, title),
                color = AuthColors.Ink,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
