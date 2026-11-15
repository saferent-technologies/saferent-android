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

/** List of cards: thumbnail + 3 lines (properties, applications, notifications …). */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    thumbnail: Dp? = 64.sdp,
    roundThumb: Boolean = false,
) {
    ShimmerHost {
        Column(modifier.fillMaxWidth().padding(16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            repeat(rows) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
                ) {
                    thumbnail?.let {
                        Bone(Modifier.size(it), if (roundThumb) CircleShape else RoundedCornerShape(10.sdp))
                        Spacer(Modifier.width(12.sdp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.sdp)) {
                        BoneLine(0.75f, 14.sdp)
                        BoneLine(0.5f)
                        BoneLine(0.35f)
                    }
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

/** Tenant / landlord dashboard while it loads: greeting, big card, two small cards. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier.fillMaxSize().background(AuthColors.Background).padding(20.sdp), verticalArrangement = Arrangement.spacedBy(12.sdp)) {
            Row {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                    BoneLine(0.4f)
                    BoneLine(0.65f, 22.sdp)
                }
                Bone(Modifier.size(44.sdp), CircleShape)
            }
            Spacer(Modifier.height(6.sdp))
            Bone(Modifier.fillMaxWidth().height(150.sdp), RoundedCornerShape(16.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                Bone(Modifier.weight(1f).height(84.sdp), RoundedCornerShape(14.sdp))
                Bone(Modifier.weight(1f).height(84.sdp), RoundedCornerShape(14.sdp))
            }
            Bone(Modifier.fillMaxWidth().height(52.sdp), RoundedCornerShape(14.sdp))
        }
    }
}
