package com.saferenttechnologies.android.presentation.listing

import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.manage.UnitAvailabilityChip
import com.saferenttechnologies.android.presentation.common.DetailSkeleton
import com.saferenttechnologies.android.domain.model.UnitOption
import androidx.compose.runtime.key
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.saferenttechnologies.android.domain.model.Disclosure
import com.saferenttechnologies.android.domain.model.ListingDetail
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.explore.Badge
import com.saferenttechnologies.android.presentation.explore.ListingCover
import com.saferenttechnologies.android.presentation.explore.furnishingLabel
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

@Composable
fun ListingDetailScreen(
    state: ListingDetailState,
    effects: Flow<ListingDetailEffect>,
    onIntent: (ListingDetailIntent) -> Unit,
    onApplicationsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val enquirySent = stringResource(R.string.listing_enquiry_sent)
    val reportSent = stringResource(R.string.listing_report_sent)
    LaunchedEffect(effects) {
        effects.collect { e ->
            when (e) {
                is ListingDetailEffect.Message -> snackbar.showSnackbar(
                    when (e.text) {
                        ListingDetailViewModel.ENQUIRY_SENT -> enquirySent
                        ListingDetailViewModel.REPORT_SENT -> reportSent
                        else -> e.text
                    },
                )
                is ListingDetailEffect.Share -> runCatching {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.text)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                is ListingDetailEffect.OpenMaps -> runCatching {
                    val uri = Uri.parse("geo:${e.latitude},${e.longitude}?q=${e.latitude},${e.longitude}(${Uri.encode(e.label)})")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                // The review screen isn't in the app yet → explain with the server's message.
                is ListingDetailEffect.ReviewRequired -> snackbar.showSnackbar(e.message)
                ListingDetailEffect.ApplicationsChanged -> onApplicationsChanged()
            }
        }
    }

    state.viewer?.let { index ->
        PhotoViewer(state.photos.map { absoluteUrl(it.url) }, index) { onIntent(ListingDetailIntent.CloseViewer) }
        return
    }
    BackHandler { onIntent(ListingDetailIntent.Close) }

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        val d = state.detail
        when {
            d == null && state.loading -> DetailSkeleton(Modifier.statusBarsPadding().padding(top = 8.sdp), heroInset = false)
            d == null -> Column(Modifier.align(Alignment.Center).padding(24.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: stringResource(R.string.add_property_error_generic), color = AuthColors.Body, fontFamily = Inter, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.sdp))
                OutlineButton(stringResource(R.string.add_property_retry)) { onIntent(ListingDetailIntent.Retry) }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Apartment: the pager restarts at the first photo when the flat changes.
                    key(state.selectedUnitId) { PhotoHeader(d, state.photos, onIntent) }
                    Column(Modifier.padding(horizontal = 18.sdp)) {
                        TitleBlock(d)
                        if (d.isUnitPriced) {
                            FlatChooserRow(state, d, onIntent)
                            state.selectedUnit?.let { FlatDetailsPanel(it, d) }
                        } else if (d.isRoomPriced) {
                            // PG / hostel: one card per room, apply per bed.
                            SectionTitle(stringResource(R.string.units_rooms))
                            d.rooms.forEach { RoomCard(it, state, onIntent) }
                        } else {
                            PriceCard(state, d)
                            ChooseHome(state, d, onIntent)
                            Highlights(state, d)
                        }
                        if (d.description.isNotBlank()) About(d.description)
                        if (d.amenities.isNotEmpty()) {
                            Amenities(
                                d.amenities,
                                when {
                                    d.isUnitPriced -> stringResource(R.string.apartment_building_amenities, d.unitWord.lowercase())
                                    d.isRoomPriced -> stringResource(R.string.units_common_pg)
                                    else -> stringResource(R.string.add_property_amenities)
                                },
                            )
                        }
                        Disclosures(stringResource(R.string.listing_utilities), d.utilities, d)
                        Disclosures(stringResource(R.string.listing_rules), d.policies, d)
                        LocationCard(d) { onIntent(ListingDetailIntent.OpenMaps) }
                        ReviewsBlock(state)
                        Spacer(Modifier.height(24.sdp))
                    }
                }
                BottomBar(state, onIntent)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp, start = 16.sdp, end = 16.sdp))
    }

    state.apply?.let { ApplySheet(state, it, onIntent) }
    if (state.applied) AppliedSheet { onIntent(ListingDetailIntent.DismissApplied) }
    state.enquiry?.let { EnquiryDialog(state, it, onIntent) }
    state.report?.let { ReportDialog(state, it, onIntent) }
}

// ---------------- header ----------------

@Composable
private fun PhotoHeader(d: ListingDetail, photos: List<com.saferenttechnologies.android.domain.model.ListingPhoto>, onIntent: (ListingDetailIntent) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        if (photos.isEmpty()) {
            ListingCover(null, Modifier.fillMaxSize())
        } else {
            val pager = rememberPagerState { photos.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = absoluteUrl(photos[i].url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(AuthColors.Surface).clickable { onIntent(ListingDetailIntent.OpenViewer(i)) },
                )
            }
            Row(Modifier.align(Alignment.BottomStart).padding(12.sdp), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                Badge(optionLabel(photos[pager.currentPage].category))
                Badge("${pager.currentPage + 1}/${photos.size}")
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.sdp), horizontalArrangement = Arrangement.spacedBy(5.sdp)) {
                repeat(photos.size.coerceAtMost(8)) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 8.sdp else 6.sdp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.6f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.sdp)) {
            HeaderIcon(R.drawable.ic_chevron_left, stringResource(R.string.add_property_back)) { onIntent(ListingDetailIntent.Close) }
            Spacer(Modifier.weight(1f))
            HeaderIcon(R.drawable.ic_share, stringResource(R.string.listing_share)) { onIntent(ListingDetailIntent.Share) }
            Spacer(Modifier.width(8.sdp))
            HeaderIcon(R.drawable.ic_flag, stringResource(R.string.listing_report)) { onIntent(ListingDetailIntent.StartReport) }
        }
    }
}

@Composable
private fun HeaderIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.sdp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, tint = Color(0xFF0F172A), modifier = Modifier.size(20.sdp)) }
}

@Composable
private fun TitleBlock(d: ListingDetail) {
    Spacer(Modifier.height(16.sdp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
        if (d.verified) Badge(stringResource(R.string.explore_verified), R.drawable.ic_shield_check, background = AuthColors.GreenBg, content = AuthColors.GreenText)
        if (d.ownerVerified) Badge(stringResource(R.string.explore_owner_verified), background = AuthColors.Surface)
    }
    Spacer(Modifier.height(8.sdp))
    Text(d.title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.ssp)
    Text(
        listOf(d.propertyTypeLabel, listOf(d.locality, d.city).filter { it.isNotBlank() }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
        color = AuthColors.Body,
        fontFamily = Inter,
        fontSize = 14.ssp,
    )
    Spacer(Modifier.height(10.sdp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.explore_transparency, d.completenessScore), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.width(8.sdp))
        LinearProgressIndicator(
            progress = { d.completenessScore.coerceIn(0, 100) / 100f },
            color = if (d.completenessScore >= 80) AuthColors.Green else AuthColors.Orange,
            trackColor = AuthColors.Surface,
            modifier = Modifier.weight(1f).height(5.sdp).clip(RoundedCornerShape(3.sdp)),
        )
    }
    if (d.missing.isNotEmpty()) {
        Text(stringResource(R.string.listing_missing, d.missing.joinToString(", ")), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
    }
}

// ---------------- price ----------------

@Composable
private fun PriceCard(state: ListingDetailState, d: ListingDetail) {
    var open by remember { mutableStateOf(false) }
    val unit = state.selectedUnit
    val bed = state.selectedBed
    val breakdown = state.breakdown
    Spacer(Modifier.height(16.sdp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(AuthColors.Surface)
            .animateContentSize()
            .padding(16.sdp),
    ) {
        val price = when {
            unit != null -> Money.inr(unit.monthlyRent)
            bed != null -> Money.inr(bed.rent)
            else -> Money.range(d.rentFrom, d.rentTo)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(price.ifBlank { stringResource(R.string.apartment_rent_not_disclosed) }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 24.ssp)
            Text(stringResource(R.string.explore_per_month), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(bottom = 3.sdp))
        }
        unit?.securityDeposit?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.listing_deposit, Money.inr(it)), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        }
        breakdown?.let { b ->
            Spacer(Modifier.height(10.sdp))
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (b.moveInTotal.isNotBlank()) {
                        Text(stringResource(R.string.listing_move_in_total, Money.inr(b.moveInTotal)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                    }
                    if (b.monthlyTotal.isNotBlank()) {
                        Text(stringResource(R.string.listing_monthly_total, Money.inr(b.monthlyTotal)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                }
                Text(
                    stringResource(if (open) R.string.listing_hide_breakdown else R.string.listing_show_breakdown),
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.ssp,
                )
            }
            if (open) {
                Spacer(Modifier.height(8.sdp))
                b.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
                        Text(line.label + (line.frequency.takeIf { it.isNotBlank() }?.let { " · ${optionLabel(it)}" } ?: ""), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                        Text(
                            if (line.disclosed) Money.inr(line.amount) else stringResource(R.string.listing_not_disclosed),
                            color = if (line.disclosed) AuthColors.Ink else Amber,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.ssp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- choose a home ----------------

@Composable
private fun ChooseHome(state: ListingDetailState, d: ListingDetail, onIntent: (ListingDetailIntent) -> Unit) {
    val units = d.units.filter { it.available }
    val beds = d.beds.filter { it.available }
    if (units.isEmpty() && beds.isEmpty()) return
    SectionTitle(stringResource(R.string.listing_choose_home))
    if (d.usesBeds) {
        beds.forEach { b ->
            OptionRow(
                selected = state.selectedBedId == b.id,
                title = stringResource(R.string.listing_room_bed, b.roomNumber, b.bedNumber),
                subtitle = optionLabel(b.sharingType),
                price = Money.inr(b.rent),
            ) { onIntent(ListingDetailIntent.SelectBed(b.id)) }
        }
    } else {
        units.forEach { u ->
            OptionRow(
                selected = state.selectedUnitId == u.id,
                title = stringResource(R.string.listing_flat_n, u.unitNumber),
                subtitle = listOfNotNull(
                    u.floorNumber?.let { stringResource(R.string.add_property_floor_n, it.toString()) },
                    u.bhkType.ifBlank { null },
                    u.builtUpArea.ifBlank { null }?.let { "$it sq ft" },
                    u.furnishing.ifBlank { null }?.let { furnishingLabel(it) },
                ).joinToString(" · "),
                price = Money.inr(u.monthlyRent),
            ) { onIntent(ListingDetailIntent.SelectUnit(u.id)) }
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, title: String, subtitle: String, price: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .border(if (selected) 2.sdp else 1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
            .background(if (selected) AuthColors.OrangeSoft.copy(alpha = 0.4f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            if (subtitle.isNotBlank()) Text(subtitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Text(price, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp)
    }
}

// ---------------- highlights / about / amenities / disclosures ----------------

@Composable
private fun Highlights(state: ListingDetailState, d: ListingDetail) {
    val u = state.selectedUnit ?: d.units.firstOrNull()
    val items = buildList {
        u?.bedrooms?.let { add(Triple(R.drawable.ic_bed, it.toString(), R.string.listing_h_bedrooms)) }
        u?.bathrooms?.let { add(Triple(R.drawable.ic_bathtub, it.toString(), R.string.listing_h_bathrooms)) }
        u?.builtUpArea?.takeIf { it.isNotBlank() }?.let { add(Triple(R.drawable.ic_area, "$it sq ft", R.string.listing_h_area)) }
        u?.floorNumber?.let { add(Triple(R.drawable.ic_layers, it.toString(), R.string.listing_h_floor)) }
        u?.furnishing?.takeIf { it.isNotBlank() }?.let { add(Triple(R.drawable.ic_sofa, it, R.string.listing_h_furnishing)) }
        d.building.entries.firstOrNull { it.key.contains("parking", true) }?.let { add(Triple(R.drawable.ic_parking, it.value, R.string.listing_h_parking)) }
        d.building.entries.firstOrNull { it.key.contains("lift", true) }?.let { add(Triple(R.drawable.ic_elevator, it.value, R.string.listing_h_lift)) }
    }
    if (items.isEmpty()) return
    SectionTitle(stringResource(R.string.listing_highlights))
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            row.forEach { (icon, value, label) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(10.sdp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(icon), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
                    Spacer(Modifier.height(4.sdp))
                    val shown = if (label == R.string.listing_h_furnishing) furnishingLabel(value) else optionLabel(value)
                    Text(shown, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(label), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun About(text: String) {
    var more by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.listing_about))
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, maxLines = if (more) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.animateContentSize())
    if (text.length > 180) {
        Text(
            stringResource(if (more) R.string.listing_read_less else R.string.listing_read_more),
            color = AuthColors.OrangeText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 13.ssp,
            modifier = Modifier.clickable { more = !more }.padding(vertical = 6.sdp),
        )
    }
}

@Composable
private fun Amenities(list: List<Disclosure>, title: String) {
    var all by remember { mutableStateOf(false) }
    val shown = list.filter { it.status.equals("YES", true) }.ifEmpty { list }
    SectionTitle(title)
    (if (all) shown else shown.take(6)).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            row.forEach { a ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_check_circle), null, tint = AuthColors.Green, modifier = Modifier.size(16.sdp))
                    Spacer(Modifier.width(6.sdp))
                    Text(a.label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (shown.size > 6) {
        Text(
            stringResource(if (all) R.string.listing_show_less else R.string.listing_show_all, shown.size),
            color = AuthColors.OrangeText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 13.ssp,
            modifier = Modifier.clickable { all = !all }.padding(vertical = 6.sdp),
        )
    }
}

/** YES / NO / Not disclosed rows with the server's legend (Not disclosed in amber). */
@Composable
private fun Disclosures(title: String, list: List<Disclosure>, d: ListingDetail) {
    if (list.isEmpty()) return
    SectionTitle(title)
    list.forEach { item ->
        val status = item.status.uppercase()
        val color = when (status) {
            "YES" -> AuthColors.GreenText
            "NO" -> AuthColors.Error
            "NOT_DISCLOSED" -> Amber
            else -> AuthColors.Body
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.sdp), verticalAlignment = Alignment.Top) {
            Text(item.label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
            Text(
                d.disclosureLegend[status]?.takeIf { status != "NOT_DISCLOSED" } ?: if (status == "NOT_DISCLOSED") stringResource(R.string.listing_not_disclosed) else optionLabel(item.status),
                color = color,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (list.any { it.status.equals("NOT_DISCLOSED", true) }) {
        d.disclosureLegend["NOT_DISCLOSED"]?.let { Text(it, color = Amber, fontFamily = Inter, fontSize = 11.ssp) }
    }
}

@Composable
private fun LocationCard(d: ListingDetail, onOpenMaps: () -> Unit) {
    SectionTitle(stringResource(R.string.listing_location))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_location), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(listOf(d.locality, d.city).filter { it.isNotBlank() }.joinToString(", "), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(listOf(d.state, d.pincode).filter { it.isNotBlank() }.joinToString(" "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (d.latitude != null && d.longitude != null) {
            Text(
                stringResource(R.string.listing_open_maps),
                color = AuthColors.OrangeText,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(onClick = onOpenMaps).padding(8.sdp),
            )
        }
    }
}

@Composable
private fun ReviewsBlock(state: ListingDetailState) {
    val r = state.reviews ?: return
    SectionTitle(stringResource(R.string.listing_reviews))
    if (r.reviews.isEmpty()) {
        Text(stringResource(R.string.listing_no_reviews), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        return
    }
    r.summary.average?.let { avg ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_star), null, tint = AuthColors.Orange, modifier = Modifier.size(18.sdp))
            Spacer(Modifier.width(4.sdp))
            Text(String.format(java.util.Locale.US, "%.1f", avg), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
            Text(" · " + stringResource(R.string.listing_reviews_n, r.summary.count), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        }
        Spacer(Modifier.height(8.sdp))
    }
    r.reviews.forEach { rv ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.sdp).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rv.authorName.ifBlank { stringResource(R.string.listing_tenant) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                rv.rating?.let { Text("★ " + String.format(java.util.Locale.US, "%.1f", it), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 12.ssp) }
            }
            if (rv.comment.isNotBlank()) Text(rv.comment, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            if (rv.createdAt.isNotBlank()) Text(prettyDate(rv.createdAt), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.sdp))
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp)
    Spacer(Modifier.height(10.sdp))
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .border(1.5.sdp, if (enabled) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.sdp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- photo viewer ----------------

/** Full-screen pager; pinch to zoom, drag to pan, double tap resets. */
@Composable
private fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            AsyncImage(
                model = urls[i],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.sdp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(R.drawable.ic_close, stringResource(R.string.camera_close), onClose)
            Spacer(Modifier.weight(1f))
            Text("${pager.currentPage + 1}/${urls.size}", color = Color.White, fontFamily = Inter, fontSize = 14.ssp)
        }
    }
}

// ---------------- apartment: choose a flat ----------------

/** Horizontal flat cards (role tab): number, BHK · floor, rent, availability; selected = brand outline. */
@Composable
private fun FlatChooserRow(state: ListingDetailState, d: ListingDetail, onIntent: (ListingDetailIntent) -> Unit) {
    SectionTitle(stringResource(R.string.apartment_choose_flat, d.unitWord.lowercase()))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
        items(d.units, key = { it.id }) { u ->
            val selected = state.selectedUnitId == u.id
            Column(
                Modifier
                    .width(150.sdp)
                    .clip(RoundedCornerShape(14.sdp))
                    .border(if (selected) 2.sdp else 1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
                    .background(if (selected) AuthColors.OrangeSoft.copy(alpha = 0.45f) else AuthColors.Card)
                    .selectable(selected = selected, role = Role.Tab) { onIntent(ListingDetailIntent.SelectUnit(u.id)) }
                    .padding(12.sdp),
            ) {
                Text("${d.unitWord} ${u.unitNumber}", color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.ssp)
                Text(
                    listOfNotNull(u.bhkType.ifBlank { null } ?: u.builtUpArea.ifBlank { null }?.let { "${it.substringBefore('.')} sq ft" }, u.floorNumber?.let { stringResource(R.string.add_property_floor_n, it.toString()) }).joinToString(" · "),
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.sdp))
                val price = Money.inr(u.monthlyRent.ifBlank { u.salePrice })
                Text(
                    if (price.isBlank()) stringResource(R.string.apartment_rent_not_disclosed)
                    else if (u.monthlyRent.isNotBlank()) stringResource(R.string.apartment_per_month, price) else price,
                    color = if (price.isBlank()) Amber else AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.ssp,
                )
                Spacer(Modifier.height(6.sdp))
                // "Available now" green · "Available from 30 Nov 2026" orange · others muted.
                UnitAvailabilityChip(u.availabilityStatus.ifBlank { "AVAILABLE" }, u.availabilityLabel, u.availableFrom, u.availableNow)
            }
        }
    }
}

/** "Flat 101 · 2BHK", facts, what this flat costs (incl. common charges), amenities inside the flat. */
@Composable
private fun FlatDetailsPanel(u: UnitOption, d: ListingDetail) {
    Spacer(Modifier.height(16.sdp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface).padding(16.sdp)) {
        Text(
            listOf("${d.unitWord} ${u.unitNumber}", u.bhkType).filter { it.isNotBlank() }.joinToString(" · "),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.ssp,
        )
        Spacer(Modifier.height(6.sdp))
        Text(
            listOfNotNull(
                u.bedrooms?.let { stringResource(R.string.apartment_beds_n, it) },
                u.bathrooms?.let { stringResource(R.string.apartment_baths_n, it) },
                u.builtUpArea.ifBlank { null }?.let { "${it.substringBefore('.')} sq ft" },
                u.furnishing.ifBlank { null }?.let { furnishingLabel(it) },
                u.floorNumber?.let { stringResource(R.string.add_property_floor_n, it.toString()) },
                u.availableFrom.ifBlank { null }?.takeUnless { u.availabilityStatus.equals("OCCUPIED", true) }
                    ?.let { stringResource(R.string.apartment_available_from, calendarDate(it)) },
            ).joinToString(" · "),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 13.ssp,
        )

        Spacer(Modifier.height(14.sdp))
        Text(stringResource(R.string.apartment_flat_costs, d.unitWord.lowercase()), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
        Spacer(Modifier.height(6.sdp))
        CostRow(stringResource(R.string.apartment_rent), u.monthlyRent.takeIf { it.isNotBlank() }?.let { Money.inr(it) })
        if (u.securityDeposit.isNotBlank()) CostRow(stringResource(R.string.apartment_deposit), Money.inr(u.securityDeposit))
        u.breakdown?.let { b ->
            // Every line: the flat's rent plus the building's common charges ("Not disclosed" in amber).
            b.lines.filterNot { it.label.equals("Rent", true) }.forEach { line ->
                CostRow(
                    line.label + (line.frequency.takeIf { it.isNotBlank() }?.let { " · ${optionLabel(it)}" } ?: ""),
                    if (line.disclosed) Money.inr(line.amount) else null,
                )
            }
            if (b.monthlyTotal.isNotBlank()) CostRow(stringResource(R.string.apartment_monthly_total), Money.inr(b.monthlyTotal), strong = true)
            if (b.moveInTotal.isNotBlank()) CostRow(stringResource(R.string.apartment_move_in_total), Money.inr(b.moveInTotal), strong = true)
        }

        val inFlat = u.amenities.filter { it.status.equals("YES", true) }
        if (inFlat.isNotEmpty()) {
            Spacer(Modifier.height(14.sdp))
            Text(stringResource(R.string.apartment_in_this_flat, d.unitWord.lowercase()), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Spacer(Modifier.height(8.sdp))
            com.saferenttechnologies.android.presentation.property.ChipFlow(inFlat.map { it.label }) { label ->
                Badge(label, R.drawable.ic_check, background = AuthColors.Card, content = AuthColors.Ink)
            }
        }
    }
}

@Composable
private fun CostRow(label: String, amount: String?, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
        Text(label, color = if (strong) AuthColors.Ink else AuthColors.Body, fontFamily = Inter, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(
            amount ?: stringResource(R.string.listing_not_disclosed),
            color = if (amount == null) Amber else AuthColors.Ink,
            fontFamily = Inter,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.ssp,
        )
    }
}

// ---------------- PG / hostel: room cards ----------------

/**
 * Sharing, rent per bed, deposit, AC / bathroom, photo strip, amenity chips, expandable
 * "What a bed in this room costs", beds with availability and "Apply" per AVAILABLE bed (bedId).
 */
@Composable
private fun RoomCard(room: com.saferenttechnologies.android.domain.model.RoomOption, state: ListingDetailState, onIntent: (ListingDetailIntent) -> Unit) {
    var open by remember(room.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.sdp)
            .clip(RoundedCornerShape(16.sdp))
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(16.sdp))
            .animateContentSize()
            .padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.add_property_room_n, room.roomNumber), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
                Text(
                    listOfNotNull(
                        room.sharingType.ifBlank { null }?.let { stringResource(R.string.units_sharing, optionLabel(it)) },
                        room.acAvailable.takeIf { it.equals("YES", true) }?.let { stringResource(R.string.units_ac) },
                        room.attachedBathroom.takeIf { it.equals("YES", true) }?.let { stringResource(R.string.units_attached_bath) },
                    ).joinToString(" · "),
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.inr(room.rentPerBed).ifBlank { stringResource(R.string.apartment_rent_not_disclosed) },
                    color = if (room.rentPerBed.isBlank()) Amber else AuthColors.Ink,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.ssp,
                )
                Text(stringResource(R.string.units_per_bed, "").trim(), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
            }
        }
        if (room.securityDeposit.isNotBlank()) {
            Text(stringResource(R.string.listing_deposit, Money.inr(room.securityDeposit)), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (room.totalBeds > 0) {
            Text(stringResource(R.string.units_beds_free, room.availableBeds, room.totalBeds), color = AuthColors.GreenText, fontFamily = Inter, fontSize = 12.ssp)
        }
        // Photo strip (this room's photos).
        if (room.imageIds.isNotEmpty()) {
            Spacer(Modifier.height(8.sdp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                items(room.imageIds) { id ->
                    AsyncImage(
                        model = com.saferenttechnologies.android.presentation.explore.propertyImageUrl(id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(112.sdp).aspectRatio(4f / 3f).clip(RoundedCornerShape(8.sdp)).background(AuthColors.Surface),
                    )
                }
            }
        }
        val inRoom = room.amenities.filter { it.status.equals("YES", true) }
        if (inRoom.isNotEmpty()) {
            Spacer(Modifier.height(8.sdp))
            com.saferenttechnologies.android.presentation.property.ChipFlow(inRoom.map { it.label }) { label ->
                Badge(label, R.drawable.ic_check, background = AuthColors.Surface, content = AuthColors.Ink)
            }
        }
        room.breakdown?.let { b ->
            Spacer(Modifier.height(8.sdp))
            Text(
                stringResource(R.string.units_bed_costs) + if (open) " ▲" else " ▼",
                color = AuthColors.OrangeText,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.ssp,
                modifier = Modifier.clickable { open = !open }.padding(vertical = 4.sdp),
            )
            if (open) {
                b.lines.forEach { line ->
                    CostRow(line.label + (line.frequency.takeIf { it.isNotBlank() }?.let { " · ${optionLabel(it)}" } ?: ""), if (line.disclosed) Money.inr(line.amount) else null)
                }
                if (b.monthlyTotal.isNotBlank()) CostRow(stringResource(R.string.apartment_monthly_total), Money.inr(b.monthlyTotal), strong = true)
                if (b.moveInTotal.isNotBlank()) CostRow(stringResource(R.string.apartment_move_in_total), Money.inr(b.moveInTotal), strong = true)
            }
        }
        // Beds → apply for one (bedId).
        if (room.beds.isNotEmpty()) {
            Spacer(Modifier.height(8.sdp))
            room.beds.forEach { bed ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.units_bed_n, bed.bedNumber), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                    if (bed.rent.isNotBlank() && bed.rent != room.rentPerBed) {
                        Text(Money.inr(bed.rent), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                        Spacer(Modifier.width(8.sdp))
                    }
                    if (bed.available) {
                        val mine = state.detail?.myApplication?.bedId == bed.id
                        Text(
                            stringResource(if (mine) R.string.listing_application_sent else R.string.units_apply_bed),
                            color = if (mine) AuthColors.GreenText else androidx.compose.ui.graphics.Color.White,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.ssp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.sdp))
                                .background(if (mine) AuthColors.GreenBg else AuthColors.Orange)
                                .clickable(enabled = !mine && !state.busy, role = Role.Button) {
                                    onIntent(ListingDetailIntent.SelectBed(bed.id))
                                    onIntent(ListingDetailIntent.StartApply)
                                }
                                .padding(horizontal = 14.sdp, vertical = 8.sdp),
                        )
                    } else {
                        Badge(optionLabel(bed.availabilityStatus.ifBlank { "OCCUPIED" }), background = AuthColors.Surface, content = AuthColors.Body)
                    }
                }
            }
        }
    }
}
