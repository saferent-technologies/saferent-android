package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.domain.model.ChangeBadges
import com.saferenttechnologies.android.domain.model.ChangeBadge
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertyListItem
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

fun coverUrl(imageId: String?): String? = imageId?.let { absoluteUrl("/api/v1/files/property-images/$it") }

@Composable
fun filterLabel(filter: PropertyFilter): String = stringResource(
    when (filter) {
        PropertyFilter.ALL -> R.string.filter_all
        PropertyFilter.LIVE -> R.string.filter_live
        PropertyFilter.UNDER_VERIFICATION -> R.string.filter_under_verification
        PropertyFilter.NEEDS_CHANGES -> R.string.filter_needs_changes
        PropertyFilter.APPROVED -> R.string.filter_approved
        PropertyFilter.DRAFTS -> R.string.filter_drafts
        PropertyFilter.INACTIVE -> R.string.filter_inactive
        PropertyFilter.ARCHIVED -> R.string.filter_archived
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPropertiesScreen(
    state: MyPropertiesState,
    onIntent: (MyPropertiesIntent) -> Unit,
    onOpenProperty: (String) -> Unit,
    onAddProperty: () -> Unit,
    onEditDraft: (String) -> Unit = {},
) {
    BackHandler { onIntent(MyPropertiesIntent.Close) }
    Column(
        Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(MyPropertiesIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.my_properties_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(MyPropertiesIntent.QueryChanged(it)) },
            placeholder = { Text(stringResource(R.string.my_properties_search), fontFamily = Inter) },
            singleLine = true,
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.sdp, vertical = 10.sdp),
            horizontalArrangement = Arrangement.spacedBy(8.sdp),
        ) {
            items(PropertyFilter.entries) { f ->
                Chip(filterLabel(f), selected = f == state.filter) { onIntent(MyPropertiesIntent.SelectFilter(f)) }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(MyPropertiesIntent.Refresh) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                state.items.isEmpty() && state.loading -> CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.align(Alignment.Center))
                state.items.isEmpty() -> EmptyState(state, onAddProperty, onRetry = { onIntent(MyPropertiesIntent.Refresh) })
                else -> {
                    val listState = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= listState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.hasMore) onIntent(MyPropertiesIntent.LoadMore) }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.sdp),
                        verticalArrangement = Arrangement.spacedBy(12.sdp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { item -> PropertyCard(item, onEdit = { onEditDraft(item.id) }) { onOpenProperty(item.id) } }
                        if (state.loading) {
                            item { Box(Modifier.fillMaxWidth().padding(12.sdp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(28.sdp)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(state: MyPropertiesState, onAddProperty: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.sdp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_building), null, tint = AuthColors.Muted, modifier = Modifier.size(48.sdp))
        Spacer(Modifier.height(14.sdp))
        Text(
            state.error ?: stringResource(R.string.my_properties_empty, filterLabel(state.filter)),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 15.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.sdp))
        if (state.error != null) {
            AuthPrimaryButton(text = stringResource(R.string.add_property_retry), enabled = true, loading = false, onClick = onRetry)
        } else {
            AuthPrimaryButton(text = stringResource(R.string.home_add_property), enabled = true, loading = false, leadingIcon = R.drawable.ic_add, onClick = onAddProperty)
        }
    }
}

@Composable
private fun PropertyCard(item: PropertyListItem, onEdit: () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(16.sdp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(150.sdp).background(AuthColors.Surface)) {
            coverUrl(item.coverImageId)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } ?: Icon(painterResource(R.drawable.ic_building), null, tint = AuthColors.Muted, modifier = Modifier.align(Alignment.Center).size(40.sdp))
            PropertyStatusChip(item.publicationStatus, item.activeStatus, Modifier.align(Alignment.TopStart).padding(10.sdp).background(AuthColors.Card, RoundedCornerShape(50)))
            // Change request of a live listing.
            changesBadge(item)?.let { (text, color) ->
                StatusPill(text, color, Modifier.align(Alignment.TopEnd).padding(10.sdp).background(AuthColors.Card, RoundedCornerShape(50)))
            }
        }
        Column(Modifier.padding(14.sdp)) {
            Text(item.title.ifBlank { item.name.ifBlank { item.code } }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.code, item.propertyTypeLabel, listOf(item.locality, item.city).filter { it.isNotBlank() }.joinToString(", "))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rentRange(item.rentFrom, item.rentTo), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.my_properties_available_occupied, item.availableCount, item.occupiedCount), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            Spacer(Modifier.height(10.sdp))
            LinearProgressIndicator(
                progress = { item.completenessScore.coerceIn(0, 100) / 100f },
                color = if (item.completenessScore >= 80) AuthColors.Green else AuthColors.Orange,
                trackColor = AuthColors.Surface,
                modifier = Modifier.fillMaxWidth().height(5.sdp).clip(RoundedCornerShape(3.sdp)),
            )
            // Draft → continue the wizard from where it was left.
            if (item.publicationStatus == "DRAFT" && item.activeStatus != "ARCHIVED") {
                Spacer(Modifier.height(12.sdp))
                OutlineAction(stringResource(R.string.my_properties_edit_draft), R.drawable.ic_edit, modifier = Modifier.fillMaxWidth(), onClick = onEdit)
            }
        }
    }
}

/** "Unsent changes" (amber) · "Changes in review" (blue) · "Changes rejected" (red); none otherwise. */
@Composable
fun changesBadge(item: PropertyListItem): Pair<String, androidx.compose.ui.graphics.Color>? = when (ChangeBadges.of(item)) {
    ChangeBadge.UNSENT -> stringResource(R.string.changes_badge_unsent) to com.saferenttechnologies.android.presentation.explore.Amber
    ChangeBadge.IN_REVIEW -> stringResource(R.string.changes_badge_review) to androidx.compose.ui.graphics.Color(0xFF2563EB)
    ChangeBadge.REJECTED -> stringResource(R.string.changes_badge_rejected) to AuthColors.Error
    null -> null
}
