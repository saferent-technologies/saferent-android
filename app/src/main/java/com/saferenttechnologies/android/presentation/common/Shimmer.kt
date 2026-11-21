package com.saferenttechnologies.android.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.presentation.auth.AuthColors

/*
 * Loading shimmers (no library): grey "bones" with a soft light band sweeping across.
 * One [ShimmerHost] drives every bone inside it, so the whole skeleton shines in sync.
 */

private val LocalShimmerBrush = staticCompositionLocalOf<Brush?> { null }

/** The moving gradient: base = Surface, band = white (light) / Border (dark). */
@Composable
fun rememberShimmerBrush(): Brush {
    val base = AuthColors.Surface
    val light = AuthColors.Background.luminance() > 0.5f
    val highlight = if (light) Color.White else AuthColors.Border
    val widthPx = LocalConfiguration.current.screenWidthDp * LocalDensity.current.density
    val band = widthPx * 0.6f
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -band,
        targetValue = widthPx + band,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - band, 0f),
        end = Offset(x, band * 0.25f),
    )
}

/** Shares one brush with all [Bone]s inside → one synced sweep. */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShimmerBrush provides rememberShimmerBrush(), content = content)
}

/** One grey placeholder block. */
@Composable
fun Bone(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.sdp)) {
    val brush = LocalShimmerBrush.current ?: rememberShimmerBrush()
    Box(modifier.clip(shape).background(brush))
}

/** A text line placeholder ([fraction] of the width). */
@Composable
fun BoneLine(fraction: Float = 1f, height: Dp = 12.sdp) {
    Bone(Modifier.fillMaxWidth(fraction).height(height), RoundedCornerShape(6.sdp))
}

// ---------------- ready-made skeletons ----------------

/** What sits at the right end of a list card (matches the real card). */
enum class SkeletonTrailing { NONE, PILL, AMOUNT }

/**
 * List of cards drawn like the real ones: card background + border, thumbnail, text lines and the
 * right-end pill / amount. Pick the numbers per screen so the shimmer and the loaded list line up.
 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    thumbnail: Dp? = 64.sdp,
    roundThumb: Boolean = false,
    lines: Int = 3,
    trailing: SkeletonTrailing = SkeletonTrailing.NONE,
    /** Real list's outer padding (LazyColumn contentPadding). */
    outerPadding: Dp = 16.sdp,
    /** Buttons row under the text (e.g. View / Share). */
    actions: Int = 0,
) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(outerPadding), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            repeat(rows) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
                        .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        thumbnail?.let {
                            Bone(Modifier.size(it), if (roundThumb) CircleShape else RoundedCornerShape(10.sdp))
                            Spacer(Modifier.width(12.sdp))
                        }
                        Column(Modifier.weight(1f).padding(top = 2.sdp), verticalArrangement = Arrangement.spacedBy(7.sdp)) {
                            BoneLine(0.7f, 14.sdp)
                            if (lines >= 2) BoneLine(0.5f)
                            if (lines >= 3) BoneLine(0.32f, 10.sdp)
                        }
                        when (trailing) {
                            SkeletonTrailing.PILL -> { Spacer(Modifier.width(8.sdp)); Bone(Modifier.width(64.sdp).height(22.sdp), RoundedCornerShape(50)) }
                            SkeletonTrailing.AMOUNT -> { Spacer(Modifier.width(8.sdp)); Bone(Modifier.width(70.sdp).height(16.sdp), RoundedCornerShape(6.sdp)) }
                            SkeletonTrailing.NONE -> Unit
                        }
                    }
                    if (actions > 0) {
                        Spacer(Modifier.height(10.sdp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                            repeat(actions) { Bone(Modifier.weight(1f).height(44.sdp), RoundedCornerShape(12.sdp)) }
                        }
                    }
                }
            }
        }
    }
}

/** Dashboard shortcut boxes (icon square, title, body) — 2 per row, a lone last box fills its row. */
@Composable
fun ActionGridSkeleton(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        (0 until count).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                row.forEach {
                    // Same as ActionBox: 42 icon, 12 gap, title (15sp), 2 gap, two lines of body (12sp).
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface).padding(14.sdp)) {
                        Bone(Modifier.size(42.sdp), RoundedCornerShape(12.sdp))
                        Spacer(Modifier.height(12.sdp))
                        TextBone(0.6f, 15.sdp, 20.sdp)
                        Spacer(Modifier.height(2.sdp))
                        TextBone(0.9f, 10.sdp, 16.sdp)
                        TextBone(0.55f, 10.sdp, 16.sdp)
                    }
                }
            }
        }
    }
}

/** One line of text: a bone of [bone] height centred in the text's real line height [line]. */
@Composable
private fun TextBone(fraction: Float, bone: androidx.compose.ui.unit.Dp, line: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(line), contentAlignment = androidx.compose.ui.Alignment.CenterStart) { BoneLine(fraction, bone) }
}

/** A card of small number boxes, 3 per row (dashboard "Your properties" / rent). */
@Composable
fun FigureCardSkeleton(boxes: Int) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
        // Same as FigureCard: title (12sp), 8 gap, boxes of value (15sp) + label (11sp).
        Box(Modifier.padding(start = 2.sdp)) { TextBone(0.3f, 10.sdp, 16.sdp) }
        Spacer(Modifier.height(8.sdp))
        Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
            (0 until boxes).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    row.forEach {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Card)
                                .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp),
                        ) {
                            TextBone(0.55f, 15.sdp, 20.sdp)
                            Spacer(Modifier.height(2.sdp))
                            TextBone(0.8f, 9.sdp, 13.sdp)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Detail page: 4:3 hero, title, a few info cards (listing, invitation, property, lease). */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier, hero: Boolean = true, heroInset: Boolean = true) {
    ShimmerHost {
        Column(modifier.fillMaxWidth()) {
            if (hero) {
                Bone(
                    Modifier.fillMaxWidth().then(if (heroInset) Modifier.padding(horizontal = 16.sdp) else Modifier).aspectRatio(4f / 3f),
                    RoundedCornerShape(if (heroInset) 18.sdp else 0.sdp),
                )
                Spacer(Modifier.height(16.sdp))
            }
            Column(Modifier.padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                BoneLine(0.7f, 20.sdp)
                BoneLine(0.45f)
                Spacer(Modifier.height(6.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    repeat(3) { Bone(Modifier.weight(1f).height(30.sdp), RoundedCornerShape(50)) }
                }
                Spacer(Modifier.height(6.sdp))
                Bone(Modifier.fillMaxWidth().height(96.sdp), RoundedCornerShape(16.sdp))
                Spacer(Modifier.height(4.sdp))
                repeat(4) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) { BoneLine(0.5f) }
                        Box(Modifier.weight(1f)) { BoneLine(0.7f) }
                    }
                }
                Spacer(Modifier.height(6.sdp))
                Bone(Modifier.fillMaxWidth().height(72.sdp), RoundedCornerShape(14.sdp))
            }
        }
    }
}

/** Form page (wizard steps): label + field blocks. */
@Composable
fun FormSkeleton(modifier: Modifier = Modifier, fields: Int = 5) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            BoneLine(0.55f, 20.sdp)
            BoneLine(0.8f)
            Spacer(Modifier.height(6.sdp))
            repeat(fields) { Bone(Modifier.fillMaxWidth().height(54.sdp), RoundedCornerShape(12.sdp)) }
        }
    }
}

/** Tenant dashboard while it loads: greeting + bell, lease card (2 buttons), shortcut boxes. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier, actions: Int = 5) {
    ShimmerHost {
        // Same padding as the dashboard (16 sides, 20 top / bottom) and the greeting's two lines.
        Column(modifier.fillMaxSize().background(AuthColors.Background).padding(horizontal = 16.sdp, vertical = 20.sdp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextBone(0.3f, 11.sdp, 18.sdp)
                    TextBone(0.55f, 19.sdp, 26.sdp)
                }
                Spacer(Modifier.width(8.sdp))
                Bone(Modifier.size(TOP_ICON), CircleShape)
            }
            Spacer(Modifier.height(16.sdp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface).padding(18.sdp)) {
                BoneLine(0.8f, 17.sdp)
                Spacer(Modifier.height(8.sdp))
                BoneLine(0.55f, 12.sdp)
                Spacer(Modifier.height(6.sdp))
                BoneLine(0.4f, 11.sdp)
                Spacer(Modifier.height(14.sdp))
                Bone(Modifier.fillMaxWidth().height(44.sdp), RoundedCornerShape(12.sdp))
                Spacer(Modifier.height(8.sdp))
                Bone(Modifier.fillMaxWidth().height(44.sdp), RoundedCornerShape(12.sdp))
            }
            Spacer(Modifier.height(12.sdp))
            ActionGridSkeleton(actions)
        }
    }
}

/** Landlord property screen: full-width 4:3 gallery, title + status, tab chips, flat cards. */
@Composable
fun PropertyDetailSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Bone(Modifier.fillMaxWidth().aspectRatio(4f / 3f), RoundedCornerShape(0.sdp))
            Column(Modifier.padding(16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BoneLine(0.7f, 20.sdp) }
                    Bone(Modifier.width(72.sdp).height(22.sdp), RoundedCornerShape(50))
                }
                BoneLine(0.5f)
                Spacer(Modifier.height(4.sdp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    repeat(4) { Bone(Modifier.weight(1f).height(36.sdp), RoundedCornerShape(50)) }
                }
            }
        }
        ListSkeleton(rows = 3, thumbnail = 72.sdp, lines = 2, trailing = SkeletonTrailing.PILL, outerPadding = 16.sdp)
        }
    }
}

/** Lease screen: bordered header (title, place, status, info rows), deposit card, action button. */
@Composable
fun LeaseSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(16.sdp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(16.sdp)).padding(16.sdp)) {
                Row {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                        BoneLine(0.7f, 18.sdp)
                        BoneLine(0.4f, 12.sdp)
                    }
                    Bone(Modifier.width(60.sdp).height(22.sdp), RoundedCornerShape(50))
                }
                Spacer(Modifier.height(10.sdp))
                BoneLine(0.5f, 13.sdp)
                Spacer(Modifier.height(10.sdp))
                repeat(5) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.sdp)) {
                        Box(Modifier.weight(1f)) { BoneLine(0.5f, 11.sdp) }
                        Box(Modifier.weight(1f)) { BoneLine(0.75f, 11.sdp) }
                    }
                }
            }
            Spacer(Modifier.height(12.sdp))
            Bone(Modifier.fillMaxWidth().height(84.sdp), RoundedCornerShape(14.sdp))
            Spacer(Modifier.height(16.sdp))
            Bone(Modifier.fillMaxWidth().height(48.sdp), RoundedCornerShape(12.sdp))
        }
    }
}

/** Request detail: icon + title + status, info lines, description box, timeline dots. */
@Composable
fun RequestDetailSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(horizontal = 16.sdp)) {
            Row {
                Bone(Modifier.size(46.sdp), CircleShape)
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                    BoneLine(0.7f, 19.sdp)
                    BoneLine(0.45f, 12.sdp)
                }
                Bone(Modifier.width(64.sdp).height(22.sdp), RoundedCornerShape(50))
            }
            Spacer(Modifier.height(12.sdp))
            BoneLine(0.6f, 12.sdp)
            Spacer(Modifier.height(6.sdp))
            BoneLine(0.75f, 12.sdp)
            Spacer(Modifier.height(14.sdp))
            Bone(Modifier.fillMaxWidth().height(80.sdp), RoundedCornerShape(12.sdp))
            Spacer(Modifier.height(18.sdp))
            BoneLine(0.25f, 16.sdp)
            Spacer(Modifier.height(10.sdp))
            repeat(3) {
                Row(Modifier.padding(bottom = 14.sdp)) {
                    Bone(Modifier.size(10.sdp), CircleShape)
                    Spacer(Modifier.width(14.sdp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                        BoneLine(0.6f, 12.sdp)
                        BoneLine(0.8f, 11.sdp)
                    }
                }
            }
        }
    }
}

/** Announcement detail: rounded 4:3 image, title, meta, body lines. */
@Composable
fun AnnouncementDetailSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
            Bone(Modifier.fillMaxWidth().aspectRatio(4f / 3f), RoundedCornerShape(14.sdp))
            Spacer(Modifier.height(6.sdp))
            BoneLine(0.75f, 20.sdp)
            BoneLine(0.45f, 12.sdp)
            Spacer(Modifier.height(8.sdp))
            repeat(4) { BoneLine(if (it == 3) 0.6f else 1f, 13.sdp) }
        }
    }
}

/**
 * "Review tenant" result while it loads: the same blocks and sizes as the real report: person card,
 * rent record (3 number boxes + rows), earlier landlords' reviews, past leases.
 */
@Composable
fun TenantReportSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxWidth()) {
            // Person card: 44 avatar + name (16sp) / ID · phone (12sp) / member since (11sp).
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Bone(Modifier.size(44.sdp), CircleShape)
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f)) {
                    TextBone(0.55f, 15.sdp, 22.sdp)
                    TextBone(0.75f, 11.sdp, 16.sdp)
                    TextBone(0.4f, 10.sdp, 15.sdp)
                }
            }
            // Rent record: title, 3 boxes (value 18sp + label 11sp), 2 payment rows.
            ReportSectionBone()
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                repeat(3) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
                        TextBone(0.5f, 18.sdp, 24.sdp)
                        TextBone(0.7f, 10.sdp, 15.sdp)
                    }
                }
            }
            repeat(2) {
                Spacer(Modifier.height(6.sdp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)).padding(10.sdp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        TextBone(0.4f, 12.sdp, 18.sdp)
                        TextBone(0.3f, 10.sdp, 16.sdp)
                    }
                    Bone(Modifier.width(56.sdp).height(22.sdp), RoundedCornerShape(50))
                }
            }
            // Reviews from earlier landlords: 2 cards (name + stars, two lines of text, date).
            ReportSectionBone()
            repeat(2) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.sdp).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TextBone(0.45f, 12.sdp, 18.sdp) }
                        Bone(Modifier.width(36.sdp).height(12.sdp), RoundedCornerShape(4.sdp))
                    }
                    TextBone(0.95f, 11.sdp, 18.sdp)
                    TextBone(0.7f, 11.sdp, 18.sdp)
                    TextBone(0.35f, 9.sdp, 15.sdp)
                }
            }
        }
    }
}

/** Section title of the report (16sp, 20 above, 8 below). */
@Composable
private fun ReportSectionBone() {
    Spacer(Modifier.height(20.sdp))
    TextBone(0.45f, 14.sdp, 21.sdp)
    Spacer(Modifier.height(8.sdp))
}
