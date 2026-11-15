package com.saferenttechnologies.android.presentation.explore

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.Listing
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.listing.ListingDetailViewModel
import com.saferenttechnologies.android.presentation.notifications.BellWithBadge
import com.saferenttechnologies.android.presentation.permission.openAppSettings
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Tenant home when not linked to a home (or "Explore homes" from the dashboard): greeting + bell,
 * search with place suggestions, quick chips, filters / sort, the listing feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: ExploreState,
    effects: Flow<ExploreEffect>,
    onIntent: (ExploreIntent) -> Unit,
    firstName: String,
    unreadNotifications: Int,
    onOpenNotifications: () -> Unit,
    onOpenListing: (String) -> Unit,
    pendingReviews: Int = 0,
    hasApplications: Boolean = false,
    onOpenApplications: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    /** Overflow menu (switch role, sign out) — Explore is the tenant's home. */
    menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val noFix = stringResource(R.string.explore_no_location)
    val allowLocation = stringResource(R.string.explore_allow_location)
    val settings = stringResource(R.string.explore_settings)
    val location = rememberPermissionRequester(AppPermission.LOCATION)

    LaunchedEffect(Unit) { onIntent(ExploreIntent.Start) }
    LaunchedEffect(effects) {
        effects.collect { e ->
            when (e) {
                is ExploreEffect.Message -> snackbar.showSnackbar(if (e.key == ExploreViewModel.MESSAGE_NO_FIX) noFix else e.key)
                ExploreEffect.ScrollToTop -> listState.scrollToItem(0)
            }
        }
    }
    onBack?.let { BackHandler(onBack = it) }

    if (state.searchOpen) {
        SearchScreen(state, onIntent, onNearMe = {
            location.request { granted -> if (granted) onIntent(ExploreIntent.NearMe) else onIntent(ExploreIntent.CloseSearch) }
        })
        return
    }

    fun nearMe() {
        location.request { granted ->
            if (granted) {
                onIntent(ExploreIntent.NearMe)
            } else {
                scope.launch {
                    if (snackbar.showSnackbar(allowLocation, actionLabel = settings) == SnackbarResult.ActionPerformed) context.openAppSettings()
                }
            }
        }
    }

    fun share(item: Listing) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ListingDetailViewModel.shareText(item.id, item.title))
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore && state.items.isNotEmpty()) onIntent(ExploreIntent.LoadMore) }

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (state.offline) {
                Text(
                    stringResource(R.string.explore_offline),
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(AuthColors.Ink).padding(6.sdp),
                )
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onIntent(ExploreIntent.Refresh) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.sdp, end = 16.sdp, bottom = 96.sdp),
                    verticalArrangement = Arrangement.spacedBy(18.sdp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    item(key = "header") {
                        Header(state, firstName, unreadNotifications, onOpenNotifications, onIntent, pendingReviews, hasApplications, onOpenApplications, onBack, ::nearMe, menu)
                    }
                    when {
                        state.loading && state.items.isEmpty() -> items(3) { SkeletonCard() }
                        state.error != null && state.items.isEmpty() -> item(key = "error") {
                            Message(state.error, stringResource(R.string.add_property_retry)) { onIntent(ExploreIntent.Retry) }
                        }
                        state.items.isEmpty() -> item(key = "empty") { EmptyFeed(onIntent) }
                        else -> {
                            items(state.items, key = { it.id }) { item ->
                                ListingCard(item, onClick = { onOpenListing(item.id) }, onLongClick = { share(item) })
                            }
                            item(key = "footer") {
                                Box(Modifier.fillMaxWidth().padding(8.sdp), contentAlignment = Alignment.Center) {
                                    when {
                                        state.loadingMore -> CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(28.sdp))
                                        state.appendFailed -> Text(
                                            stringResource(R.string.explore_load_more_failed),
                                            color = AuthColors.OrangeText,
                                            fontFamily = Inter,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable { onIntent(ExploreIntent.Retry) }.padding(12.sdp),
                                        )
                                        !state.hasMore && state.items.size > 3 -> Text(stringResource(R.string.explore_end), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Scroll to top
        AnimatedVisibility(
            visible = showTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.sdp),
        ) {
            Box(
                Modifier
                    .size(48.sdp)
                    .shadow(8.sdp, CircleShape)
                    .clip(CircleShape)
                    .background(AuthColors.Orange)
                    .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(0) } },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_arrow_upward), stringResource(R.string.explore_to_top), tint = Color.White, modifier = Modifier.size(22.sdp)) }
        }
        if (state.locating) {
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.sdp).clip(RoundedCornerShape(50)).background(AuthColors.Ink).padding(horizontal = 14.sdp, vertical = 8.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.sdp, modifier = Modifier.size(14.sdp))
                Spacer(Modifier.width(8.sdp))
                Text(stringResource(R.string.explore_locating), color = Color.White, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }

    state.draft?.let { FilterSheet(state, it, onIntent) }
    if (state.sortOpen) SortSheet(state, onIntent)
}

@Composable
private fun Header(
    state: ExploreState,
    firstName: String,
    unread: Int,
    onOpenNotifications: () -> Unit,
    onIntent: (ExploreIntent) -> Unit,
    pendingReviews: Int,
    hasApplications: Boolean,
    onOpenApplications: () -> Unit,
    onBack: (() -> Unit)?,
    onNearMe: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    Column {
        Spacer(Modifier.height(12.sdp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let {
                Box(
                    Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = it),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
                Spacer(Modifier.width(12.sdp))
            }
            com.saferenttechnologies.android.presentation.common.Greeting(firstName, Modifier.weight(1f), subtitle = stringResource(R.string.explore_subtitle))
            Spacer(Modifier.width(8.sdp))
            BellWithBadge(unread, onOpenNotifications)
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier.padding(start = 6.sdp).size(TOP_ICON).clip(CircleShape).clickable(role = Role.Button) { open = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.explore_menu), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AuthColors.Card) {
                        menu.forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label, fontFamily = Inter, color = AuthColors.Ink) }, onClick = { open = false; action() })
                        }
                    }
                }
            }
        }
        if (pendingReviews > 0) {
            Spacer(Modifier.height(12.sdp))
            Text(
                stringResource(R.string.explore_review_banner),
                color = Amber,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(Amber.copy(alpha = 0.1f)).padding(12.sdp),
            )
        }
        Spacer(Modifier.height(14.sdp))
        // Search bar → full-screen search
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(6.sdp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(AuthColors.Card)
                .clickable(role = Role.Button) { onIntent(ExploreIntent.OpenSearch) }
                .padding(horizontal = 16.sdp, vertical = 14.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
            Spacer(Modifier.width(10.sdp))
            Text(
                state.searchLabel.ifBlank { stringResource(R.string.explore_search_hint) },
                color = if (state.searchLabel.isBlank()) AuthColors.Muted else AuthColors.Ink,
                fontFamily = Inter,
                fontSize = 15.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.sdp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            items(QuickChip.entries) { chip ->
                val on = QuickChips.isActive(chip, state.filters, state.types)
                FilterPill(quickChipLabel(chip), on, icon = if (chip == QuickChip.NEAR_ME) R.drawable.ic_my_location else null) {
                    if (chip == QuickChip.NEAR_ME && !on) onNearMe() else onIntent(ExploreIntent.ToggleChip(chip))
                }
            }
        }
        Spacer(Modifier.height(10.sdp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            FilterPill(stringResource(R.string.explore_filters), state.filters.activeCount > 0, icon = R.drawable.ic_tune, badge = state.filters.activeCount) {
                onIntent(ExploreIntent.OpenFilters)
            }
            FilterPill(stringResource(R.string.explore_sort), false, icon = R.drawable.ic_sort) { onIntent(ExploreIntent.OpenSort) }
            if (hasApplications) FilterPill(stringResource(R.string.explore_my_applications), false) { onOpenApplications() }
        }
        Spacer(Modifier.height(14.sdp))
        // Result header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val near = state.filters.nearMe
                Text(
                    when {
                        state.loading && state.items.isEmpty() -> stringResource(R.string.explore_searching)
                        near != null -> stringResource(R.string.explore_homes_near, state.total)
                        state.searchLabel.isNotBlank() -> stringResource(R.string.explore_homes_in, state.total, state.searchLabel)
                        else -> stringResource(R.string.explore_homes, state.total)
                    },
                    color = AuthColors.Ink,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.ssp,
                )
                if (near != null) {
                    Spacer(Modifier.height(6.sdp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                        items(ExploreViewModel.RADIUS_OPTIONS) { km ->
                            FilterPill(stringResource(R.string.explore_within_km, km), near.radiusKm == km) { onIntent(ExploreIntent.SetRadius(km)) }
                        }
                    }
                }
            }
            if (!state.filters.isEmpty) {
                Text(
                    stringResource(R.string.explore_clear_all),
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable { onIntent(ExploreIntent.ClearAll) }.padding(8.sdp),
                )
            }
        }
    }
}

@Composable
fun quickChipLabel(chip: QuickChip): String = stringResource(
    when (chip) {
        QuickChip.NEAR_ME -> R.string.explore_chip_near_me
        QuickChip.APARTMENT -> R.string.explore_chip_apartment
        QuickChip.HOUSE -> R.string.explore_chip_house
        QuickChip.PG -> R.string.explore_chip_pg
        QuickChip.VILLA -> R.string.explore_chip_villa
        QuickChip.COMMERCIAL -> R.string.explore_chip_commercial
        QuickChip.UNDER_10K -> R.string.explore_chip_under_10k
        QuickChip.UNDER_20K -> R.string.explore_chip_under_20k
        QuickChip.FURNISHED -> R.string.explore_chip_furnished
        QuickChip.BHK_2 -> R.string.explore_chip_2bhk
    },
)

@Composable
private fun EmptyFeed(onIntent: (ExploreIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_home_outline), null, tint = AuthColors.OrangeText, modifier = Modifier.size(40.sdp))
        }
        Spacer(Modifier.height(16.sdp))
        Text(stringResource(R.string.explore_empty_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
            FilterPill(stringResource(R.string.explore_clear_filters), true) { onIntent(ExploreIntent.ClearAll) }
            FilterPill(stringResource(R.string.explore_search_nearby), false, icon = R.drawable.ic_search) { onIntent(ExploreIntent.OpenSearch) }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.sdp))
        Text(
            action,
            color = AuthColors.OrangeText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.sdp)).border(1.sdp, AuthColors.Orange, RoundedCornerShape(8.sdp)).clickable(onClick = onAction).padding(horizontal = 18.sdp, vertical = 10.sdp),
        )
    }
}
