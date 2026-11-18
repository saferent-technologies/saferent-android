package com.saferenttechnologies.android.presentation.homealerts

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import com.saferenttechnologies.android.presentation.common.keepSwipesInside
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.HomeAlertRules
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** Always in the tree (MainActivity): toasts + the "Open to new homes" sheet when open. */
@Composable
fun HomeAlertHost(state: HomeAlertState, effects: Flow<HomeAlertEffect>, onIntent: (HomeAlertIntent) -> Unit) {
    val context = LocalContext.current
    val on = stringResource(R.string.alert_saved_on)
    val off = stringResource(R.string.alert_saved_off)
    val noLocation = stringResource(R.string.alert_no_location)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            val text = when (e) {
                is HomeAlertEffect.Message -> e.text
                is HomeAlertEffect.Saved -> if (e.enabled) on else off
                HomeAlertEffect.NoLocation -> noLocation
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    if (state.isOpen) HomeAlertSheet(state, onIntent)
    // Active lease (not in notice) → make sure they really want new-home alerts.
    if (state.confirmOn != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onIntent(HomeAlertIntent.CancelConfirm) },
            title = { Text(stringResource(R.string.alert_confirm_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.alert_confirm_body), fontFamily = Inter) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onIntent(HomeAlertIntent.ConfirmOn) }) {
                    Text(stringResource(R.string.alert_confirm_yes), color = AuthColors.OrangeText, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onIntent(HomeAlertIntent.CancelConfirm) }) {
                    Text(stringResource(R.string.alert_confirm_no), color = AuthColors.Body)
                }
            },
            containerColor = AuthColors.Card,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeAlertSheet(state: HomeAlertState, onIntent: (HomeAlertIntent) -> Unit) {
    val d = state.draft
    val location = rememberPermissionRequester(AppPermission.LOCATION)
    // Alerts on but no spot yet → take the current location by itself
    // (our popup → system permission), once per opening of the sheet.
    var autoTried by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(d.enabled) {
        if (d.enabled && !autoTried && !d.hasWhere && d.areaLabel.isBlank()) {
            autoTried = true
            location.request { granted -> if (granted) onIntent(HomeAlertIntent.UseMyLocation) }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(HomeAlertIntent.Close) },
        sheetState = sheetState,
        containerColor = AuthColors.Background,
    ) {
        // Drag the top handle → the sheet follows the finger and closes smoothly.
        // Swipes in the middle only scroll the content (never move the sheet).
        Column(Modifier.fillMaxWidth().keepSwipesInside().verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(stringResource(R.string.alert_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(4.sdp))
            Text(stringResource(R.string.alert_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(14.sdp))

            // Main switch.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(if (d.enabled) AuthColors.OrangeSoft else AuthColors.Field)
                    .border(1.sdp, if (d.enabled) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
                    .clickable(enabled = !state.saving, role = Role.Switch) { onIntent(HomeAlertIntent.Enabled(!d.enabled)) }
                    .padding(horizontal = 14.sdp, vertical = 12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.alert_open_to), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                    Text(stringResource(if (d.enabled) R.string.alert_on_hint else R.string.alert_off_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                Switch(
                    checked = d.enabled,
                    onCheckedChange = { onIntent(HomeAlertIntent.Enabled(it)) },
                    enabled = !state.saving,
                    colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
                )
            }

            if (d.enabled) {
                // Where: tap → the same full-screen search as Explore; GPS icon → where I am now.
                Heading(stringResource(R.string.alert_where))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(AuthColors.Field)
                        .border(1.sdp, if (state.showErrors && !d.hasWhere) AuthColors.Error else AuthColors.Border, RoundedCornerShape(50))
                        .clickable(enabled = !state.saving, role = Role.Button) { onIntent(HomeAlertIntent.OpenSearch) }
                        .padding(start = 14.sdp, end = 4.sdp, top = 4.sdp, bottom = 4.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_search), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp))
                    Spacer(Modifier.width(10.sdp))
                    Text(
                        d.areaLabel.ifBlank { stringResource(R.string.explore_search_hint) },
                        color = if (d.areaLabel.isBlank()) AuthColors.Muted else AuthColors.Ink,
                        fontFamily = Inter,
                        fontSize = 14.ssp,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.locating || (state.areaSearching && !state.searchOpen)) {
                        CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.sdp, modifier = Modifier.padding(11.sdp).size(18.sdp))
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_my_location),
                            stringResource(R.string.alert_use_location),
                            tint = AuthColors.OrangeText,
                            modifier = Modifier.size(40.sdp).clip(CircleShape).clickable(enabled = !state.saving, role = Role.Button) {
                                location.request { granted -> onIntent(if (granted) HomeAlertIntent.UseMyLocation else HomeAlertIntent.LocationDenied) }
                            }.padding(10.sdp),
                        )
                    }
                }
                Text(
                    stringResource(
                        when {
                            state.locating -> R.string.alert_locating
                            d.hasWhere -> R.string.alert_area_ok
                            else -> R.string.alert_area_hint
                        },
                    ),
                    color = if (state.showErrors && !d.hasWhere) AuthColors.Error else AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    modifier = Modifier.padding(start = 14.sdp, top = 4.sdp),
                )

                // How far
                Heading(stringResource(R.string.alert_radius))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    HomeAlertRules.RADII.forEach { km ->
                        Chip(stringResource(R.string.alert_km, km), selected = d.radiusKm == km, enabled = !state.saving, modifier = Modifier.weight(1f), maxLines = 1) {
                            onIntent(HomeAlertIntent.Radius(km))
                        }
                    }
                }

                // Budget
                Heading(stringResource(R.string.alert_budget))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    HomeAlertRules.BUDGETS.forEach { max ->
                        Chip(
                            if (max == null) stringResource(R.string.alert_any) else stringResource(R.string.alert_up_to, Money.inr(max.toString())),
                            selected = d.maxRent == max,
                            enabled = !state.saving,
                            modifier = Modifier.widthIn(min = 64.sdp),
                            maxLines = 1,
                        ) { onIntent(HomeAlertIntent.Budget(max)) }
                    }
                }

                // Types (none picked = all)
                if (state.types.isNotEmpty()) {
                    Heading(stringResource(R.string.alert_types))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                        state.types.forEach { t ->
                            Chip(t.label, selected = t.key in d.propertyTypes, enabled = !state.saving, modifier = Modifier.widthIn(min = 64.sdp), maxLines = 1) {
                                onIntent(HomeAlertIntent.ToggleType(t.key))
                            }
                        }
                    }
                    Text(stringResource(R.string.alert_types_hint), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp, modifier = Modifier.padding(top = 4.sdp))
                }
            }

            Spacer(Modifier.height(18.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.alert_save),
                enabled = state.canSave,
                loading = state.saving,
                onClick = { onIntent(HomeAlertIntent.Save) },
                fontSize = 15.ssp,
            )
        }
    }
    // Over the sheet: the same search screen as Explore.
    if (state.searchOpen) {
        AreaSearch(state, onIntent) {
            location.request { granted -> onIntent(if (granted) HomeAlertIntent.UseMyLocation else HomeAlertIntent.LocationDenied) }
        }
    }
}

/** Same full-screen search as Explore (our areas / cities), over the sheet. */
@Composable
private fun AreaSearch(state: HomeAlertState, onIntent: (HomeAlertIntent) -> Unit, onUseLocation: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onIntent(HomeAlertIntent.CloseSearch) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        com.saferenttechnologies.android.presentation.explore.LocationSearchContent(
            text = state.searchText,
            onText = { onIntent(HomeAlertIntent.AreaText(it)) },
            onBack = { onIntent(HomeAlertIntent.CloseSearch) },
            onUseLocation = onUseLocation,
            suggestions = state.suggestions,
            loading = state.areaSearching,
            onPick = { onIntent(HomeAlertIntent.PickArea(it)) },
            emptyText = if (state.areaNotFound) stringResource(R.string.alert_area_not_found) else null,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(16.sdp))
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp, modifier = Modifier.padding(bottom = 8.sdp))
}

/**
 * Card for Explore and the tenant dashboard: "Open to new homes" + its switch.
 * Tap the card → settings sheet; flip the switch → on / off right there.
 */
@Composable
fun HomeAlertBanner(
    enabled: Boolean?,
    areaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    val on = enabled == true
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(if (on) AuthColors.OrangeSoft else AuthColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 14.sdp, end = 10.sdp, top = 10.sdp, bottom = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_notifications), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
        Spacer(Modifier.width(10.sdp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.alert_open_to), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(
                if (on) stringResource(R.string.alert_banner_on, areaLabel.ifBlank { stringResource(R.string.alert_area_set) })
                else stringResource(R.string.alert_banner_off),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        Spacer(Modifier.width(8.sdp))
        if (onToggle != null) {
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                enabled = !busy && enabled != null,
                colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
            )
        } else {
            Text(
                stringResource(if (on) R.string.alert_state_on else R.string.alert_state_off),
                color = if (on) AuthColors.Green else AuthColors.Muted,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 12.ssp,
            )
        }
    }
}

/** Compact top-bar pill next to the bell: dot (green = on) + "Open to homes". Tap → settings. */
@Composable
fun OpenToHomesChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(com.saferenttechnologies.android.presentation.common.TOP_ICON).clip(RoundedCornerShape(50))
            .background(if (on) AuthColors.OrangeSoft else AuthColors.Surface)
            .border(1.sdp, if (on) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.sdp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) AuthColors.Green else AuthColors.Muted),
        )
        Spacer(Modifier.width(6.sdp))
        Text(stringResource(R.string.alert_open_short), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.ssp, maxLines = 1)
    }
}
