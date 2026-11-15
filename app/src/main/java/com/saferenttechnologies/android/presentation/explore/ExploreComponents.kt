package com.saferenttechnologies.android.presentation.explore

import com.saferenttechnologies.android.presentation.common.BoneLine
import com.saferenttechnologies.android.presentation.common.Bone
import com.saferenttechnologies.android.presentation.common.ShimmerHost
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.Listing
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Not-disclosed / warning colour (amber) used on listings. */
val Amber = Color(0xFFB45309)

fun propertyImageUrl(imageId: String): String = absoluteUrl("/api/v1/files/property-images/$imageId")

/** Cover photo, or a branded placeholder with the type icon. Photos go through the app's OkHttp (auth). */
@Composable
fun ListingCover(imageId: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_building), null, tint = AuthColors.OrangeText.copy(alpha = 0.6f), modifier = Modifier.size(42.sdp))
        if (imageId != null) {
            AsyncImage(model = propertyImageUrl(imageId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Badge(text: String, icon: Int? = null, background: Color = Color.White.copy(alpha = 0.92f), content: Color = AuthColors.Ink) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 9.sdp, vertical = 4.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(painterResource(it), null, tint = content, modifier = Modifier.size(13.sdp))
            Spacer(Modifier.width(4.sdp))
        }
        Text(text, color = content, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp)
    }
}

/** Feed card: 16:10 photo with badges + title, place, distance, price, availability, transparency. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListingCard(item: Listing, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.sdp))) {
            ListingCover(item.coverImageId, Modifier.fillMaxSize())
            // Gradient so the badges read on any photo.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
            )
            Row(Modifier.align(Alignment.TopStart).padding(10.sdp), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                if (item.verified) Badge(stringResource(R.string.explore_verified), R.drawable.ic_shield_check, content = AuthColors.GreenText)
                if (item.ownerVerified) Badge(stringResource(R.string.explore_owner_verified))
            }
            Box(Modifier.align(Alignment.BottomStart).padding(10.sdp)) {
                Badge(listingTypeLabel(item.listingType), background = AuthColors.Orange, content = Color.White)
            }
        }
        Spacer(Modifier.height(10.sdp))
        Text(item.title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(item.propertyTypeLabel, listOf(item.locality, item.city).filter { it.isNotBlank() }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 13.ssp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.distanceKm?.let {
            Text(stringResource(R.string.explore_km_away, Money.distance(it)), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
        }
        Spacer(Modifier.height(6.sdp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Money.range(item.rentFrom, item.rentTo).ifBlank { "—" },
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.ssp,
            )
            Text(stringResource(R.string.explore_per_month), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Spacer(Modifier.weight(1f))
            Badge(
                // Units of a building: "{n} flats / portions / shops available"; PG: "{n} beds available".
                if (com.saferenttechnologies.android.domain.model.UnitPricing.isUnitPriced(item.propertyType, null)) {
                    stringResource(
                        R.string.apartment_flats_available,
                        item.availableCount,
                        com.saferenttechnologies.android.domain.model.UnitPricing.unitWord(item.propertyType).lowercase(),
                    )
                } else if (com.saferenttechnologies.android.domain.model.UnitPricing.isPgType(item.propertyType)) {
                    stringResource(R.string.units_beds_available, item.availableCount)
                } else {
                    stringResource(R.string.explore_available_n, item.availableCount)
                },
                background = AuthColors.GreenBg,
                content = AuthColors.GreenText,
            )
        }
        Spacer(Modifier.height(8.sdp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.explore_transparency, item.completenessScore), color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
            Spacer(Modifier.width(8.sdp))
            LinearProgressIndicator(
                progress = { item.completenessScore.coerceIn(0, 100) / 100f },
                color = if (item.completenessScore >= 80) AuthColors.Green else AuthColors.Orange,
                trackColor = AuthColors.Surface,
                modifier = Modifier.weight(1f).height(4.sdp).clip(RoundedCornerShape(2.sdp)),
            )
        }
    }
}

@Composable
fun listingTypeLabel(type: String): String = stringResource(
    when (type.uppercase()) {
        "LEASE" -> R.string.explore_for_lease
        "SALE" -> R.string.explore_for_sale
        else -> R.string.explore_for_rent
    },
)

/** Shimmering card while the feed loads. */
@Composable
fun SkeletonCard() {
    ShimmerHost {
        Column(Modifier.fillMaxWidth()) {
            Bone(Modifier.fillMaxWidth().aspectRatio(16f / 10f), RoundedCornerShape(20.sdp))
            Spacer(Modifier.height(10.sdp))
            BoneLine(0.7f, 16.sdp)
            Spacer(Modifier.height(6.sdp))
            BoneLine(0.5f)
            Spacer(Modifier.height(8.sdp))
            BoneLine(0.35f, 16.sdp)
        }
    }
}

/** Rounded filter chip (filled brand colour when on). */
@Composable
fun FilterPill(text: String, selected: Boolean, icon: Int? = null, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AuthColors.Orange else AuthColors.Card)
            .border(1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(painterResource(it), null, tint = if (selected) Color.White else AuthColors.Ink, modifier = Modifier.size(16.sdp))
            Spacer(Modifier.width(6.sdp))
        }
        Text(text, color = if (selected) Color.White else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        if (badge > 0) {
            Spacer(Modifier.width(6.sdp))
            Box(
                Modifier.size(18.sdp).clip(RoundedCornerShape(50)).background(if (selected) Color.White else AuthColors.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.toString(), color = if (selected) AuthColors.Orange else Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.ssp)
            }
        }
    }
}
