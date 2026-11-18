package com.saferenttechnologies.android.presentation.tenant

import com.saferenttechnologies.android.presentation.common.SkeletonTrailing
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.ListSkeleton
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipGrid
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

@Composable
fun MyApplicationsScreen(state: MyApplicationsState, onIntent: (MyApplicationsIntent) -> Unit, onOpenListing: (String) -> Unit) {
    BackHandler { onIntent(MyApplicationsIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        onIntent(MyApplicationsIntent.MessageShown)
        if (m.isNotBlank()) snackbar.showSnackbar(m)
    }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(MyApplicationsIntent.Close) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
                Spacer(Modifier.width(12.sdp))
                Text(stringResource(R.string.explore_my_applications), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            }
            Box(Modifier.padding(horizontal = 16.sdp)) {
                ChipGrid(MyApplicationsViewModel.STATUSES, columns = 4) { s, mod ->
                    Chip(optionLabel(s), selected = state.status == s, modifier = mod, maxLines = 1) { onIntent(MyApplicationsIntent.SelectStatus(s)) }
                }
            }
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton(rows = 4, thumbnail = null, lines = 3, trailing = SkeletonTrailing.PILL)
                state.error != null && state.items.isEmpty() -> Text(state.error, color = AuthColors.Body, fontFamily = Inter, modifier = Modifier.padding(24.sdp))
                state.items.isEmpty() -> Text(stringResource(R.string.applications_empty), color = AuthColors.Body, fontFamily = Inter, modifier = Modifier.padding(24.sdp))
                else -> LazyColumn(contentPadding = PaddingValues(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
                    items(state.items, key = { it.id }) { a ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.sdp))
                                .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
                                .clickable(enabled = a.propertyId.isNotBlank()) { onOpenListing(a.propertyId) }
                                .padding(14.sdp),
                        ) {
                            Text(a.propertyTitle, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                            Text(listOf(a.place, a.city).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                            Text(
                                listOfNotNull(
                                    a.proposedMoveIn.takeIf { it.isNotBlank() }?.let { stringResource(R.string.manage_move_in, prettyDate(it)) },
                                    a.occupants?.let { stringResource(R.string.manage_family, it) },
                                ).joinToString(" · "),
                                color = AuthColors.Muted,
                                fontFamily = Inter,
                                fontSize = 12.ssp,
                            )
                            if (a.status.equals("PENDING", true)) {
                                Spacer(Modifier.height(8.sdp))
                                Text(
                                    stringResource(R.string.listing_withdraw),
                                    color = if (state.busyId == a.id) AuthColors.Muted else AuthColors.Error,
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.ssp,
                                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = state.busyId == null) { onIntent(MyApplicationsIntent.Withdraw(a.id)) }.padding(6.sdp),
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }
}
