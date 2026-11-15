package com.saferenttechnologies.android.presentation.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.BrowseFilters
import com.saferenttechnologies.android.domain.model.BrowseSort
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipGrid
import com.saferenttechnologies.android.presentation.property.ChipFlow
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

// ---------------- full-screen search ----------------

@Composable
fun SearchScreen(state: ExploreState, onIntent: (ExploreIntent) -> Unit, onNearMe: () -> Unit) {
    BackHandler { onIntent(ExploreIntent.CloseSearch) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(12.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(ExploreIntent.CloseSearch) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(22.sdp)) }
            Spacer(Modifier.width(10.sdp))
            OutlinedTextField(
                value = state.searchText,
                onValueChange = { onIntent(ExploreIntent.SearchTextChanged(it.take(80))) },
                placeholder = { Text(stringResource(R.string.explore_search_hint), fontFamily = Inter) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
                trailingIcon = if (state.searchText.isNotEmpty()) {
                    @Composable {
                        Icon(painterResource(R.drawable.ic_close), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp).clickable { onIntent(ExploreIntent.SearchTextChanged("")) })
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onIntent(ExploreIntent.SubmitSearch(state.searchText)) }),
                shape = RoundedCornerShape(50),
                colors = propertyFieldColors(),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "near") {
                SearchRow(R.drawable.ic_my_location, AnnotatedString(stringResource(R.string.explore_use_location)), null, null, tint = AuthColors.OrangeText, onClick = onNearMe)
            }
            if (state.searchText.isBlank()) {
                if (state.recent.isNotEmpty()) {
                    item(key = "recent") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 10.sdp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.explore_recent), color = AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(R.string.explore_clear),
                                color = AuthColors.OrangeText,
                                fontFamily = Inter,
                                fontSize = 12.ssp,
                                modifier = Modifier.clickable { onIntent(ExploreIntent.ClearRecent) }.padding(6.sdp),
                            )
                        }
                    }
                    items(state.recent, key = { "r-$it" }) { q ->
                        SearchRow(R.drawable.ic_refresh, AnnotatedString(q), null, null) { onIntent(ExploreIntent.SubmitSearch(q)) }
                    }
                }
            } else {
                if (state.suggestionsLoading && state.suggestions.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(20.sdp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(24.sdp))
                        }
                    }
                }
                items(state.suggestions, key = { "${it.kind}-${it.label}" }) { s ->
                    SearchRow(
                        icon = when (s.kind) { "CITY" -> R.drawable.ic_building; "STATE" -> R.drawable.ic_layers; else -> R.drawable.ic_location },
                        title = boldMatch(s.name, state.searchText),
                        subtitle = s.subLabel,
                        trailing = stringResource(R.string.explore_n_homes, s.listingCount),
                    ) { onIntent(ExploreIntent.PickSuggestion(s)) }
                }
                item(key = "free") {
                    SearchRow(R.drawable.ic_search, AnnotatedString(stringResource(R.string.explore_search_for, state.searchText.trim())), null, null) {
                        onIntent(ExploreIntent.SubmitSearch(state.searchText))
                    }
                }
            }
        }
    }
}

private fun boldMatch(name: String, query: String): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else name.indexOf(q, ignoreCase = true)
    if (i < 0) {
        append(name)
    } else {
        append(name.substring(0, i))
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name.substring(i, i + q.length)) }
        append(name.substring(i + q.length))
    }
}

@Composable
private fun SearchRow(icon: Int, title: AnnotatedString, subtitle: String?, trailing: String?, tint: Color = AuthColors.Body, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.sdp, vertical = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.sdp).clip(CircleShape).background(AuthColors.Surface), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(18.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        }
        trailing?.let { Text(it, color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp) }
    }
}

// ---------------- filters ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(state: ExploreState, draft: BrowseFilters, onIntent: (ExploreIntent) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun change(f: BrowseFilters) = onIntent(ExploreIntent.DraftChanged(f))
    ModalBottomSheet(onDismissRequest = { onIntent(ExploreIntent.CloseFilters) }, sheetState = sheet, containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.sdp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.explore_filters), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.explore_reset),
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable { onIntent(ExploreIntent.ResetDraft) }.padding(8.sdp),
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp)) {
                Section(stringResource(R.string.explore_looking_for))
                ChipGrid(listOf("RENT", "LEASE", "SALE"), columns = 3) { t, mod ->
                    Chip(listingLookingLabel(t), selected = draft.listingType == t, modifier = mod, maxLines = 1) {
                        change(draft.copy(listingType = if (draft.listingType == t) null else t))
                    }
                }
                Section(stringResource(R.string.explore_category))
                ChipGrid(listOf("RESIDENTIAL", "COMMERCIAL", "LAND"), columns = 3) { c, mod ->
                    Chip(categoryLabel(c), selected = draft.category == c, modifier = mod, maxLines = 1) {
                        change(draft.copy(category = if (draft.category == c) null else c, propertyType = null))
                    }
                }
                val types = state.types.filter { draft.category == null || it.category == draft.category }
                if (types.isNotEmpty()) {
                    Section(stringResource(R.string.explore_property_type))
                    ChipFlow(types.map { it.key }) { key ->
                        val t = types.first { it.key == key }
                        Chip(t.label, selected = draft.propertyType == key) { change(draft.copy(propertyType = if (draft.propertyType == key) null else key)) }
                    }
                }

                Section(stringResource(R.string.explore_budget))
                val min = (draft.minRent ?: 0).toFloat()
                val max = (draft.maxRent ?: BrowseFilters.BUDGET_MAX).toFloat()
                Text(
                    "${Money.inr((draft.minRent ?: 0).toString())} – ${if (draft.maxRent == null) Money.inr(BrowseFilters.BUDGET_MAX.toString()) + "+" else Money.inr(draft.maxRent.toString())}",
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.ssp,
                )
                RangeSlider(
                    value = min..max,
                    onValueChange = { r ->
                        val lo = r.start.toInt().roundTo(BrowseFilters.BUDGET_STEP)
                        val hi = r.endInclusive.toInt().roundTo(BrowseFilters.BUDGET_STEP)
                        change(draft.copy(minRent = lo.takeIf { it > 0 }, maxRent = hi.takeIf { it < BrowseFilters.BUDGET_MAX }))
                    },
                    valueRange = 0f..BrowseFilters.BUDGET_MAX.toFloat(),
                    steps = BrowseFilters.BUDGET_MAX / BrowseFilters.BUDGET_STEP - 1,
                    colors = SliderDefaults.colors(thumbColor = AuthColors.Orange, activeTrackColor = AuthColors.Orange, inactiveTrackColor = AuthColors.Surface, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                    MoneyField(stringResource(R.string.explore_min), draft.minRent, Modifier.weight(1f)) { change(draft.copy(minRent = it)) }
                    MoneyField(stringResource(R.string.explore_max), draft.maxRent, Modifier.weight(1f)) { change(draft.copy(maxRent = it)) }
                }

                Section(stringResource(R.string.explore_bedrooms))
                ChipGrid(listOf("ANY", "1", "2", "3", "4"), columns = 5) { b, mod ->
                    val value = b.toIntOrNull()
                    Chip(if (value == null) stringResource(R.string.explore_any) else "$b+", selected = draft.bedrooms == value, modifier = mod, maxLines = 1) {
                        change(draft.copy(bedrooms = value))
                    }
                }
                Section(stringResource(R.string.explore_furnishing))
                ChipGrid(listOf("ANY", "UNFURNISHED", "SEMI_FURNISHED", "FULLY_FURNISHED"), columns = 2) { f, mod ->
                    val value = f.takeIf { it != "ANY" }
                    Chip(furnishingLabel(f), selected = draft.furnishing == value, modifier = mod, maxLines = 1) { change(draft.copy(furnishing = value)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.sdp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.explore_available_only), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.availableOnly,
                        onCheckedChange = { change(draft.copy(availableOnly = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
                    )
                }
                draft.nearMe?.let { near ->
                    Section(stringResource(R.string.explore_distance))
                    ChipGrid(ExploreViewModel.RADIUS_OPTIONS.map { it.toString() }, columns = 5) { km, mod ->
                        Chip("$km km", selected = near.radiusKm.toString() == km, modifier = mod, maxLines = 1) {
                            change(draft.copy(nearMe = near.copy(radiusKm = km.toInt())))
                        }
                    }
                }
                Spacer(Modifier.height(24.sdp))
            }
            Box(Modifier.fillMaxWidth().background(AuthColors.Background).padding(horizontal = 20.sdp, vertical = 12.sdp).navigationBarsPadding()) {
                AuthPrimaryButton(
                    text = state.draftCount?.let { stringResource(R.string.explore_show_n, it) } ?: stringResource(R.string.explore_show_homes),
                    enabled = true,
                    loading = false,
                    onClick = { onIntent(ExploreIntent.ApplyDraft) },
                )
            }
        }
    }
}

private fun Int.roundTo(step: Int) = (this + step / 2) / step * step

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(20.sdp))
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp)
    Spacer(Modifier.height(10.sdp))
}

@Composable
private fun MoneyField(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { t -> onChange(t.filter(Char::isDigit).take(7).toIntOrNull()) },
        label = { Text(label, fontFamily = Inter) },
        prefix = { Text("₹", fontFamily = Inter) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.sdp),
        colors = propertyFieldColors(),
        modifier = modifier,
    )
}

@Composable
fun listingLookingLabel(t: String) = stringResource(
    when (t) {
        "LEASE" -> R.string.explore_lease
        "SALE" -> R.string.explore_buy
        else -> R.string.explore_rent
    },
)

@Composable
fun categoryLabel(c: String) = stringResource(
    when (c) {
        "COMMERCIAL" -> R.string.explore_commercial
        "LAND" -> R.string.explore_land
        else -> R.string.explore_residential
    },
)

@Composable
fun furnishingLabel(f: String) = stringResource(
    when (f.uppercase()) {
        "UNFURNISHED" -> R.string.explore_unfurnished
        "SEMI_FURNISHED" -> R.string.explore_semi_furnished
        "FULLY_FURNISHED" -> R.string.explore_fully_furnished
        else -> R.string.explore_any
    },
)

// ---------------- sort ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(state: ExploreState, onIntent: (ExploreIntent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onIntent(ExploreIntent.CloseSort) }, containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(
                stringResource(R.string.explore_sort_by),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
                modifier = Modifier.padding(horizontal = 20.sdp, vertical = 8.sdp),
            )
            BrowseSort.entries.filter { it != BrowseSort.NEAREST || state.filters.nearMe != null }.forEach { sort ->
                val selected = state.filters.sort == sort
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onIntent(ExploreIntent.SelectSort(sort)) }.padding(horizontal = 20.sdp, vertical = 14.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sortLabel(sort), color = if (selected) AuthColors.OrangeText else AuthColors.Ink, fontFamily = Inter, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.ssp, modifier = Modifier.weight(1f))
                    if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
                }
            }
        }
    }
}

@Composable
fun sortLabel(s: BrowseSort) = stringResource(
    when (s) {
        BrowseSort.NEWEST -> R.string.explore_sort_newest
        BrowseSort.RENT_ASC -> R.string.explore_sort_rent_asc
        BrowseSort.RENT_DESC -> R.string.explore_sort_rent_desc
        BrowseSort.COMPLETENESS -> R.string.explore_sort_completeness
        BrowseSort.NEAREST -> R.string.explore_sort_nearest
    },
)
